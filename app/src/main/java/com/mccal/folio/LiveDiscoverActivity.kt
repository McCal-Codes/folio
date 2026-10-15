package com.mccal.folio

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.window.embedding.ActivityEmbeddingController
import java.lang.ref.WeakReference

internal enum class DiscoverStartTimeout { STALE, RETRY, FAILED }

/** A launch can be discarded before Activity.onCreate, so no lifecycle callback can clear it. */
internal class DiscoverHostStartGate(private var nextToken: Long = System.nanoTime()) {
    private var owner = WeakReference<Any>(null)
    private var pendingToken: Long? = null
    private var activeToken: Long? = null
    private var attempts = 0

    fun request(candidate: Any): Long? {
        if (owner.get() !== candidate) {
            owner = WeakReference(candidate); pendingToken = null; activeToken = null; attempts = 0
        }
        if (pendingToken != null || attempts >= MAX_ATTEMPTS) return null
        // A missing host means an activity using the previously accepted token did not survive.
        activeToken = null
        attempts++
        return (++nextToken).also { pendingToken = it }
    }

    fun attached(token: Long): Boolean {
        if (token == activeToken) return true
        if (token != pendingToken) return false
        pendingToken = null; activeToken = token; attempts = 0
        return true
    }

    fun timedOut(candidate: Any, token: Long): DiscoverStartTimeout {
        if (owner.get() !== candidate || pendingToken != token) return DiscoverStartTimeout.STALE
        pendingToken = null
        return if (attempts < MAX_ATTEMPTS) DiscoverStartTimeout.RETRY else DiscoverStartTimeout.FAILED
    }

    fun reset(candidate: Any? = null) {
        owner = WeakReference(candidate); pendingToken = null; activeToken = null; attempts = 0
        nextToken++
    }

    private companion object { const val MAX_ATTEMPTS = 2 }
}

/** A persistent, transparent token host. Only Google's own window receives feed touches. */
internal object LiveDiscover {
    var host = WeakReference<LiveDiscoverActivity>(null)
    var owner = WeakReference<MainActivity>(null)
    val message = mutableStateOf<String?>("Connecting to Discover…")
    var onNativeProgress: ((Float) -> Unit)? = null
    var onHomeRequest: (() -> Unit)? = null
    var fullSize = androidx.compose.ui.geometry.Size.Zero
    var pagerOrigin = androidx.compose.ui.geometry.Offset.Zero
    var homeLayer: androidx.compose.ui.graphics.layer.GraphicsLayer? = null
    var pageWidth = 1f
    var viewport = Rect()
    var nativePosition = 0f
    var progress = 0f
    var pagerOwnsMotion = false
    var allowNativeOpen = true
    private var pendingEndpoint: Float? = null
    private val driver = DiscoverPageDriver()
    private val startGate = DiscoverHostStartGate()
    private val externalResultOwners = mutableSetOf<Pair<String, String>>()
    // Isolate Compose UI tests from the external service; native integration tests leave this on.
    internal var attachNativeFeed = true

    fun prepare(activity: MainActivity, bounds: Rect, width: Float) {
        if (!attachNativeFeed || externalResultOwners.isNotEmpty() || !DiscoverBounds.available || bounds.isEmpty ||
            activity.isFinishing || activity.isDestroyed ||
            !activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return
        pageWidth = width
        viewport = Rect(bounds)
        DiscoverBounds.updateViewport(activity, bounds)
        owner = WeakReference(activity)
        if (host.get() != null) return
        // Start the Discover host only once Home is really in front. Spotlight closes it, and a launch from a result
        // leaves Home started but not resumed; starting it then put Home back over the app just opened (#371).
        // onResume calls prepare() again, so Discover still comes back when Home returns.
        if (!mayStartDiscoverHost(activity.lifecycle.currentState)) return
        val token = startGate.request(activity) ?: return
        message.value = "Connecting to Discover…"
        val options = ActivityOptions.makeCustomAnimation(activity, 0, 0).toBundle().apply {
            DiscoverBounds.launchOptions(suppressOverlayAnimation = true)?.let(::putAll)
        }
        val launched = runCatching {
            activity.startActivity(Intent(activity, LiveDiscoverActivity::class.java)
                .putExtra(HOST_START_TOKEN, token).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION), options)
        }.isSuccess
        if (!launched) {
            handleStartTimeout(activity, token)
            return
        }
        activity.window.decorView.postDelayed({
            handleStartTimeout(activity, token)
        }, HOST_START_TIMEOUT_MS)
    }

    private fun handleStartTimeout(activity: MainActivity, token: Long) {
        when (startGate.timedOut(activity, token)) {
            DiscoverStartTimeout.STALE -> Unit
            DiscoverStartTimeout.RETRY -> {
                if (activity.isFinishing || activity.isDestroyed ||
                    !activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
                    startGate.reset(activity)
                else activity.window.decorView.post { prepare(activity, viewport, pageWidth) }
            }
            DiscoverStartTimeout.FAILED -> {
                if (activity.isFinishing || activity.isDestroyed ||
                    !activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
                    startGate.reset(activity)
                else message.value = activity.getString(R.string.discover_couldnt_start)
            }
        }
    }

    // The inset native viewport travels the same distance as the larger Compose page.
    fun pageProgress(native: Float) = discoverPageProgress(native, pageWidth, viewport.width().toFloat())
    fun nativeProgress(page: Float) = discoverNativeProgress(page, pageWidth, viewport.width().toFloat())
    fun page(progress: Float, scrolling: Boolean, towardFeed: Boolean = false) {
        this.progress = progress
        pagerOwnsMotion = scrolling
        pendingEndpoint = if (scrolling || nativePosition == nativeProgress(progress)) null
            else if (progress == 0f || progress == 1f) progress else null
        driver.request(nativeProgress(progress), scrolling, towardFeed)?.let { host.get()?.page(it.position, it.scrolling) }
    }
    fun native(progress: Float): Boolean {
        nativePosition = progress
        if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_progress_received",
            "native=$progress page=${this.progress} pagerOwns=$pagerOwnsMotion pending=$pendingEndpoint allowOpen=$allowNativeOpen")
        if (pagerOwnsMotion) {
            if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_progress_rejected",
                "native=$progress reason=pager_owns_motion")
            return false
        }
        pendingEndpoint?.let { endpoint ->
            // Binder updates can arrive after Compose has settled. A late echo must not
            // pull the pager back to an intermediate position or start another gesture.
            if (kotlin.math.abs(progress - endpoint) < .001f) pendingEndpoint = null
            if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_progress_rejected",
                "native=$progress reason=pending_endpoint endpoint=$endpoint")
            return false
        }
        // Google can capture entry from the first Home page. Other home pages and
        // All apps must never be pulled across by an offscreen native callback.
        if (this.progress == 0f && (progress == 0f || !allowNativeOpen)) {
            if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_progress_rejected",
                "native=$progress reason=closed_or_native_open_disabled allowOpen=$allowNativeOpen")
            return false
        }
        val page = pageProgress(progress)
        this.progress = page
        if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_progress_accepted",
            "native=$progress page=$page")
        onNativeProgress?.invoke(page)
        return true
    }
    fun attached(activity: LiveDiscoverActivity): Boolean {
        if (!startGate.attached(activity.intent.getLongExtra(HOST_START_TOKEN, Long.MIN_VALUE))) return false
        host = WeakReference(activity)
        if (externalResultOwners.isNotEmpty()) { host.clear(); activity.finish() }
        return true
    }
    fun detached(activity: LiveDiscoverActivity) {
        if (host.get() !== activity) return
        host.clear()
        // A HOME intent can clear this child after Main's onResume/layout already ran.
        owner.get()?.let { main -> main.window.decorView.post {
            if (!main.isFinishing && !main.isDestroyed &&
                main.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
                prepare(main, viewport, pageWidth)
        } }
    }
    fun retry() {
        host.get()?.connect() ?: owner.get()?.let { activity ->
            startGate.reset(activity)
            prepare(activity, viewport, pageWidth)
        }
    }

    fun setExternalResultPending(activity: MainActivity, ownerKey: String, reason: String, active: Boolean) {
        owner = WeakReference(activity)
        val key = ownerKey to reason
        if (active) {
            externalResultOwners.add(key)
            startGate.reset()
            val current = host.get()
            host.clear()
            current?.finish()
        } else {
            externalResultOwners.remove(key)
            if (externalResultOwners.isEmpty()) activity.window.decorView.post {
                if (!activity.isFinishing && !activity.isDestroyed &&
                    activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
                    prepare(activity, viewport, pageWidth)
            }
        }
    }

    internal fun hasExternalResultPending(ownerKey: String, reason: String) =
        (ownerKey to reason) in externalResultOwners

    private const val HOST_START_TOKEN = "duo_discover_start_token"
    private const val HOST_START_TIMEOUT_MS = 1_500L
}

class LiveDiscoverActivity : ComponentActivity() {
    private lateinit var client: DiscoverClient
    private lateinit var frame: DiscoverFrame
    private var request = 0
    private var vertical = true
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        if (!LiveDiscover.attached(this) || isFinishing) { finish(); return }
        enableEdgeToEdge()
        window.setWindowAnimations(0)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        vertical = org.json.JSONObject(getSharedPreferences("launcher", 0).getString("state", "{}") ?: "{}").optBoolean("verticalStatus", true)
        if (vertical) WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.statusBars())
        setContentView(View(this))
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { LiveDiscover.onHomeRequest?.invoke() }
        })
        frame = DiscoverFrame(this, vertical, live = true)
        client = DiscoverClient(this, vertical,
            onState = { LiveDiscover.message.value = it; if (it != null) frame.hide() },
            onVisible = frame::reveal,
            onProgress = { if (LiveDiscover.native(it)) frame.liveProgress = it else frame.invalidate() },
            onClosed = {}, pagerDriven = true)
        window.decorView.post { connect() }
    }
    fun connect() {
        val attempt = ++request
        fun attach(remaining: Int) {
            if (attempt != request || isDestroyed || isFinishing) return
            val decor = window.decorView
            if (ActivityEmbeddingController.getInstance(this).isActivityEmbedded(this) &&
                DiscoverBounds.matchesViewport(decor.width, decor.height)) {
                client.connect()
                client.page(LiveDiscover.nativeProgress(LiveDiscover.progress), LiveDiscover.pagerOwnsMotion)
            } else if (remaining > 0) decor.postOnAnimation { attach(remaining - 1) }
            else LiveDiscover.message.value = getString(R.string.discover_couldnt_fit)
        }
        window.decorView.postOnAnimation { attach(30) }
    }
    fun invalidateFrame() { if (::frame.isInitialized) frame.invalidate() }
    fun statusMode(value: Boolean) { if (vertical != value && !isFinishing) { vertical = value; recreate() } }
    fun page(progress: Float, scrolling: Boolean) {
        if (::frame.isInitialized) frame.liveProgress = progress
        if (::client.isInitialized) client.page(progress, scrolling)
    }
    // Focus moves between Home's controls and Google's window during a page drag. Both
    // activities remain visible; only leaving the task should pause the native transport.
    override fun onStart() { super.onStart(); if (::client.isInitialized) client.resume() }
    override fun onStop() { if (::client.isInitialized) client.pause(); super.onStop() }
    override fun onDestroy() {
        request++
        LiveDiscover.detached(this)
        if (::client.isInitialized) client.disconnect()
        if (::frame.isInitialized) frame.hide()
        super.onDestroy()
    }
}

/** Whether Home may start the Discover host now: only while it is resumed, never while an app it just opened takes over (#371). */
internal fun mayStartDiscoverHost(state: androidx.lifecycle.Lifecycle.State): Boolean =
    state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
