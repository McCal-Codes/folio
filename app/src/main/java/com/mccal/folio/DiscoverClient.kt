package com.mccal.folio

import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.WindowManager
import android.view.Gravity
import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter

/** Small, independently implemented client for Google's optional launcher-overlay service.
 * The wire method order is documented in docs/DISCOVER.md. No Google/Lawnchair code is bundled.
 * All state changes and listener calls run on the main thread. A generation owns its callbacks.
 */
internal class DiscoverClient(
    private val activity: Activity,
    private val verticalStatus: Boolean,
    private val onState: (String?) -> Unit,
    private val onVisible: () -> Unit,
    private val onProgress: (Float) -> Unit,
    private val onClosed: () -> Unit,
    private val pagerDriven: Boolean = false,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var desiredProgress = 0f
    private var pageScrolling = false
    private var pageRequest = 0
    private var connection: ServiceConnection? = null
    private var remote: IBinder? = null
    private var generation = 0
    private var resumed = false
    private val dismissal = DiscoverDismissal()
    private var closing = false
    private var everVisible = false
    private var ready = false
    private var openRequested = false
    private var lastProgress = 1f
    private var closeCompleted = false
    private var closeAnimator: ValueAnimator? = null

    fun connect() {
        if (activity.isDestroyed || activity.isFinishing) return
        disconnect()
        trace { "connect: pagerDriven=$pagerDriven verticalStatus=$verticalStatus" }
        onState("Connecting to Discover…")
        val attempt = generation
        val callback = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == INTERFACE_TRANSACTION) { reply?.writeString(CALLBACK); return true }
                if (code !in 1..2) return super.onTransact(code, data, reply, flags)
                data.enforceInterface(CALLBACK)
                val scroll = if (code == 1) data.readFloat() else 0f
                val status = if (code == 2) data.readInt() else 0
                handler.post {
                    if (generation != attempt || remote == null) return@post
                    trace { if (code == 2) "status=$status ready=${status and 1 != 0} closing=$closing resumed=$resumed"
                        else "scroll=%.3f tracking=%b closing=%b".format(scroll, dismissal.tracking, closing) }
                    if (code == 2) {
                        if (closing) return@post
                        ready = status and 1 != 0
                        if (ready && resumed) { if (pagerDriven) applyPage() else show() }
                        else if (!ready) { openRequested = false; dismissal.suspend(); onState("Discover is unavailable right now.") }
                    } else if (pagerDriven && scroll.isFinite() && scroll in 0f..1f) {
                        if (!resumed) return@post
                        lastProgress = scroll
                        if (scroll > 0f) { everVisible = true; onState(null); onVisible() }
                        onProgress(scroll)
                    } else if (!scroll.isFinite() || scroll !in 0f..1f) return@post
                    else if (closing) {
                        // Our settling animation now owns the position. Google may still emit
                        // a rebound from the native gesture that initiated this transition.
                        return@post
                    } else if (scroll >= .99f) {
                        if (resumed) dismissal.progress(scroll, feedWidthDp())
                        lastProgress = 1f
                        onProgress(1f)
                        everVisible = true
                        onState(null)
                        onVisible()
                    } else if (resumed && dismissal.tracking) {
                        lastProgress = scroll
                        onProgress(scroll)
                        if (dismissal.progress(scroll, feedWidthDp())) closeForHome()
                    }
                }
                return true
            }
        }
        val binding = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (generation != attempt) return
                // A rejected or stale Google binding can accept one-way transactions without
                // supplying the overlay interface. Show recovery instead of waiting for callbacks.
                if (runCatching { service.interfaceDescriptor }.getOrNull() != OVERLAY) {
                    failed("Google didn't accept the feed connection. Restart Google, then retry.", attempt)
                    return
                }
                remote = service
                trace { "bound to ${name.flattenToShortString()}" }
                handler.post {
                    if (generation != attempt || activity.isDestroyed) return@post
                    val attrs = WindowManager.LayoutParams().apply {
                        copyFrom(activity.window.attributes)
                        gravity = Gravity.TOP or Gravity.LEFT
                        if (pagerDriven) {
                            // Feed touches go to Google, but revealing its window must not
                            // steal focus from a Home gesture already in progress.
                            flags = (flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()) or
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        }
                        // Google's overlay also reads the legacy window flags when attaching.
                        @Suppress("DEPRECATION")
                        if (verticalStatus) flags = flags or WindowManager.LayoutParams.FLAG_FULLSCREEN
                    }
                    // The activity token in LayoutParams is required; the decor's window token
                    // cannot be substituted. Actual feed bounds come from activity embedding.
                    val bundle = Bundle().apply {
                        putParcelable("layout_params", attrs)
                        putParcelable("configuration", activity.resources.configuration)
                        putInt("client_options", 1)
                    }
                    trace { "windowAttached: resumed=$resumed bounds=${activity.window.decorView.width}x${activity.window.decorView.height}" }
                    send(14) { writeInt(1); bundle.writeToParcel(this, 0); writeStrongBinder(callback) }
                    // Resume attachment, then wait for Google's ready status before opening.
                    if (resumed) send(8)
                }
            }
            override fun onServiceDisconnected(name: ComponentName) {
                if (generation == attempt) {
                    remote = null; ready = false; openRequested = false; dismissal.suspend()
                    onState("Discover disconnected. Tap Retry to reconnect.")
                }
            }
            override fun onNullBinding(name: ComponentName) {
                failed("The Google app did not provide a feed. Update the Google app, or use Today View instead " +
                    "(Settings \u203a Today View).", attempt)
            }
            override fun onBindingDied(name: ComponentName) { failed("Discover disconnected. Tap Retry to reconnect.", attempt) }
        }
        val intent = Intent(OVERLAY_ACTION).setPackage(GOOGLE_PACKAGE)
            // Launcher3's own client asks for version 9 of the overlay protocol, and so must we: the Google app
            // answers an older number with no binder at all, which is why Discover looked broken (r/GalaxyFold).
            .setData(Uri.parse("app://${activity.packageName}:${Process.myUid()}?v=$OVERLAY_VERSION&cv=$OVERLAY_VERSION"))
        try {
            if (activity.bindService(intent, binding, Context.BIND_AUTO_CREATE)) connection = binding
            else onState("Install or enable the Google app to use Discover.")
        } catch (e: RuntimeException) {
            Log.w(TAG, "Cannot bind Discover", e)
            onState("The Google app couldn't connect to Discover.")
        }
        handler.postDelayed({
            if (generation == attempt && connection != null && !(if (pagerDriven) ready else everVisible)) {
                disconnect()
                onState("Discover is taking a while. You can retry or open Google.")
            }
        }, 12_000)
    }

    fun resume() { resumed = true; if (ready) { if (pagerDriven) { send(8); applyPage() } else show() } else if (remote != null) send(8) }
    fun page(progress: Float, scrolling: Boolean) {
        val request = ++pageRequest
        desiredProgress = progress.coerceIn(0f, 1f)
        if (!ready || !resumed) return
        if (scrolling && !pageScrolling) { pageScrolling = true; send(1) }
        send(2) { writeFloat(desiredProgress) }
        if (!scrolling && pageScrolling) { pageScrolling = false; send(3) }
        if (!scrolling && desiredProgress == 0f) send(6) { writeInt(0) }
        if (!scrolling && (desiredProgress == 0f || desiredProgress == 1f)) reconcileEndpoint(request, desiredProgress, 3)
    }
    private fun reconcileEndpoint(request: Int, endpoint: Float, attempts: Int) {
        if (attempts == 0 || kotlin.math.abs(lastProgress - endpoint) < .001f) return
        handler.postDelayed({
            if (request != pageRequest || !ready || !resumed || remote == null || pageScrolling ||
                kotlin.math.abs(lastProgress - endpoint) < .001f) return@postDelayed
            // Google's endScroll can finish an older settle after our final position,
            // especially after a window resize. Reassert the endpoint once it can
            // accept a new interaction; a new user drag cancels this reconciliation.
            send(1); send(2) { writeFloat(endpoint) }; send(3)
            if (endpoint == 0f) send(6) { writeInt(0) }
            reconcileEndpoint(request, endpoint, attempts - 1)
        }, 80)
    }
    private fun applyPage() {
        if (desiredProgress > 0f) { send(1); send(2) { writeFloat(desiredProgress) }; send(3) }
        else send(6) { writeInt(0) }
    }
    fun pause() { resumed = false; openRequested = false; dismissal.suspend(); if (!closing && !pagerDriven) onProgress(1f); if (remote != null) send(7) }
    private fun feedWidthDp() = activity.window.decorView.width / activity.resources.displayMetrics.density
    // The launcher has already entered Discover. Opening Google's window should not add a slide.
    private fun show() {
        if (closing || openRequested || !ready) return
        openRequested = true
        send(8); send(9) { writeInt(0) }
    }

    /** Keep the live feed moving until Home has been revealed underneath it. */
    fun closeForHome(): Boolean {
        if (closing) return true
        if (remote == null || !everVisible) return false
        closing = true
        dismissal.suspend()
        val attempt = generation
        send(1) // startScroll: the page now owns the native overlay position.
        closeAnimator = ValueAnimator.ofFloat(lastProgress, 0f).apply {
            duration = 260
            interpolator = android.view.animation.DecelerateInterpolator(1.3f)
            addUpdateListener {
                val position = it.animatedValue as Float
                onProgress(position)
                send(2) { writeFloat(position) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    send(3) // endScroll
                    send(6) { writeInt(0) }
                    finishClose(attempt)
                }
            })
            start()
        }
        return true
    }

    private fun finishClose(attempt: Int) {
        if (generation != attempt || !closing || closeCompleted) return
        closeCompleted = true
        onProgress(0f)
        // Let the final Home preview reach a display frame before handing back to Home.
        activity.window.decorView.postOnAnimation {
            activity.window.decorView.postOnAnimation {
                if (generation == attempt && closing) onClosed()
            }
        }
    }
    private fun failed(message: String, attempt: Int) {
        trace { "failed: $message" }
        if (generation != attempt) return
        disconnect()
        onState(message)
    }

    private fun send(code: Int, payload: Parcel.() -> Unit = {}) {
        val binder = remote ?: return
        trace { "send ${transactionName(code)}" }
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(OVERLAY); data.payload()
            if (!binder.transact(code, data, null, IBinder.FLAG_ONEWAY)) throw RemoteException("Unsupported transaction $code")
        } catch (e: RemoteException) {
            Log.w(TAG, "Discover connection lost", e)
            remote = null; ready = false; dismissal.suspend()
            onState("Discover disconnected. Tap Retry to reconnect.")
        } catch (e: RuntimeException) {
            Log.w(TAG, "Discover protocol unavailable", e)
            remote = null; ready = false; dismissal.suspend()
            onState("This Google app version couldn't open Discover.")
        } finally { data.recycle() }
    }

    fun disconnect() {
        generation++; pageRequest++; pageScrolling = false
        closeAnimator?.removeAllListeners(); closeAnimator?.cancel(); closeAnimator = null
        handler.removeCallbacksAndMessages(null)
        if (remote != null) { send(6) { writeInt(0) }; send(5) { writeInt(0) } }
        remote = null; ready = false; closing = false; closeCompleted = false; openRequested = false; lastProgress = 1f; dismissal.suspend(); everVisible = false
        connection?.let { runCatching { activity.unbindService(it) } }
        connection = null
    }

    companion object {
        const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"

        const val OVERLAY_ACTION = "com.android.launcher3.WINDOW_OVERLAY"

        /**
         * Turn on with `adb shell setprop log.tag.FolioDiscover DEBUG`, then watch `adb logcat -s FolioDiscover`.
         * Off in everyone's build: nothing is written unless the tag is switched on by hand.
         */
        internal inline fun trace(message: () -> String) {
            if (android.util.Log.isLoggable(TAG, android.util.Log.DEBUG)) android.util.Log.d(TAG, message())
        }

        /** Launcher3's overlay AIDL, by transaction number, so the log reads like the protocol. */
        private fun transactionName(code: Int) = when (code) {
            1 -> "startScroll"; 2 -> "onScroll"; 3 -> "endScroll"; 6 -> "closeOverlay"
            7 -> "onPause"; 8 -> "onResume"; 9 -> "openOverlay"; 14 -> "windowAttached"; 15 -> "setActivityState"
            else -> "transaction $code"
        }

        /** The overlay protocol version Launcher3's own client asks for; the Google app refuses versions it doesn't know. */
        private const val OVERLAY_VERSION = 9
        const val TAG = "FolioDiscover"
        private const val OVERLAY = "com.google.android.libraries.launcherclient.ILauncherOverlay"
        private const val CALLBACK = "com.google.android.libraries.launcherclient.ILauncherOverlayCallback"
    }
}
