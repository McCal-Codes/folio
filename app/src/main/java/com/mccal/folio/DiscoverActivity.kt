package com.mccal.folio

import androidx.compose.ui.res.stringResource
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.UserManager
import android.widget.Toast
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.window.WindowSdkExtensions
import androidx.window.embedding.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.lang.ref.WeakReference

class DuoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        DiscoverEmbedding.initialize(this)
        DiscoverBounds.initialize(this)
        // Matches the Market's background-refresh setting to reality, so turning it off really stops it.
        MarketRefreshJob.schedule(this)
    }
}

internal object DiscoverEmbedding {
    private fun attributes(ratio: Float) = SplitAttributes.Builder()
        .setSplitType(SplitAttributes.SplitType.ratio(ratio))
        .setLayoutDirection(SplitAttributes.LayoutDirection.RIGHT_TO_LEFT).build()

    // Window 1.5.1's lint does not recognize these direct version guards with this toolchain.
    @SuppressLint("RequiresWindowSdk")
    fun initialize(context: Context) {
        val controller = SplitController.getInstance(context)
        RuleController.getInstance(context).addRule(SplitPairRule.Builder(setOf(SplitPairFilter(
            ComponentName(context, DiscoverActivity::class.java),
            ComponentName(context, DiscoverFeedActivity::class.java), null)))
            .setMinWidthDp(0).setMinHeightDp(0).setMinSmallestWidthDp(0)
            .setMaxAspectRatioInPortrait(EmbeddingAspectRatio.ALWAYS_ALLOW)
            .setMaxAspectRatioInLandscape(EmbeddingAspectRatio.ALWAYS_ALLOW)
            .setFinishSecondaryWithPrimary(SplitRule.FinishBehavior.ALWAYS)
            .setFinishPrimaryWithSecondary(SplitRule.FinishBehavior.ALWAYS)
            .setDefaultSplitAttributes(attributes(.22f)).setClearTop(true).setTag("duo-discover").build())
        if (WindowSdkExtensions.getInstance().extensionVersion >= 2) {
          controller.setSplitAttributesCalculator { params ->
            if (params.splitRuleTag != "duo-discover") return@setSplitAttributesCalculator params.defaultSplitAttributes
            val density = params.parentConfiguration.densityDpi / 160f
            val widthDp = params.parentWindowMetrics.bounds.width() / density
            val dockWidth = DiscoverBounds.dockWidth(context, widthDp)
            attributes(discoverDockFraction(widthDp, dockWidth))
          }
        }
    }

    fun supported(context: Context) = WindowSdkExtensions.getInstance().extensionVersion >= 6 &&
        SplitController.getInstance(context).splitSupportStatus == SplitController.SplitSupportStatus.SPLIT_AVAILABLE
}

/** Keeps the full launcher out of the split. Its pages and drag coordinates stay unchanged. */
internal object DiscoverSession {
    var host = WeakReference<DiscoverActivity>(null)
    var feed = WeakReference<DiscoverFeedActivity>(null)
    var apps: List<AppEntry> = emptyList()
    fun dismiss() { host.get()?.finishAndRemoveTask(); host.clear(); feed.clear(); apps = emptyList(); DiscoverMotion.reset() }
    fun requestHome(activity: Activity) { feed.get()?.returnHome() ?: home(activity) }
    fun home(activity: Activity, search: Boolean = false) {
        activity.startActivity(Intent.makeMainActivity(ComponentName(activity, MainActivity::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra("duo_destination", if (search) "search" else "home"))
        // Main removes this task after its first draw, keeping the preview visible through
        // the window handoff instead of exposing a blank compositor frame.
    }
}

private fun ComponentActivity.configureDiscoverWindow(vertical: Boolean) {
    // The page coordinates its own motion. Also clear the window-level style that Google
    // inherits from LayoutParams; NO_ANIMATION alone only governs the activity handoff.
    window.setWindowAnimations(0)
    enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
    WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (vertical) hide(WindowInsetsCompat.Type.statusBars()) else show(WindowInsetsCompat.Type.statusBars())
    }
}

/** Observe gestures delivered to our own windows; native feed touches stay entirely with Google. */
abstract class DiscoverPageActivity : ComponentActivity() {
    private val homeSwipe by lazy {
        DiscoverHomeSwipe(72f * resources.displayMetrics.density, ViewConfiguration.get(this).scaledTouchSlop.toFloat())
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> homeSwipe.down(event.x, event.y)
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> homeSwipe.cancel()
            MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1 && homeSwipe.move(event.x, event.y)) {
                // Cancel the in-progress press/scroll before leaving, so no dock app or Retry
                // button can fire when this gesture ends on Home.
                val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                try { super.dispatchTouchEvent(cancel) } finally { cancel.recycle() }
                DiscoverSession.requestHome(this)
                return true
            }
        }
        return super.dispatchTouchEvent(event)
    }
}

class DiscoverActivity : DiscoverPageActivity() {
    private val model: LauncherModel by viewModels()
    private val fullSize = mutableStateOf(Size.Zero)
    private var feedLaunched = false
    private var viewportReady = false
    @SuppressLint("RequiresWindowSdk") // Collection is directly guarded by extensionVersion >= 6.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        feedLaunched = savedInstanceState != null
        DiscoverSession.host = WeakReference(this)
        DiscoverBounds.resetViewport()
        configureDiscoverWindow(model.state.value.verticalStatus)
        val bounds = windowManager.currentWindowMetrics.bounds
        fullSize.value = Size(bounds.width().toFloat(), bounds.height().toFloat())
        if (!DiscoverEmbedding.supported(this)) {
            Toast.makeText(this, R.string.this_device_can_t_show_discover_beside, Toast.LENGTH_LONG).show()
            DiscoverSession.home(this); return
        }
        lifecycleScope.launch {
          if (WindowSdkExtensions.getInstance().extensionVersion >= 6) {
            ActivityEmbeddingController.getInstance(this@DiscoverActivity).embeddedActivityWindowInfo(this@DiscoverActivity).collect {
                fullSize.value = Size(it.parentHostBounds.width().toFloat(), it.parentHostBounds.height().toFloat())
            }
          }
        }
        val monitor = DeviceStatusMonitor(this).also { lifecycle.addObserver(it) }
        val startupApps = DiscoverSession.apps
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            val status = ScreenshotMode.status(monitor.state.collectAsStateWithLifecycle().value, ScreenshotMode.on.collectAsStateWithLifecycle().value)
            DuoTheme(rememberSavedAppearance().dark) {
            // Discover is its own window, so it needs the Glass setting too (the Side Bar outline follows it).
            androidx.compose.runtime.CompositionLocalProvider(LocalGlassLook provides GlassLook(state.widgetGlass, state.glassOutline)) {
                BackHandler { DiscoverSession.requestHome(this) }
                DiscoverDock(if (state.loading) state.copy(apps = startupApps) else state, status, fullSize.value, onLaunch = ::launchApp,
                    onHome = { DiscoverSession.requestHome(this) }, onSearch = { DiscoverSession.home(this, search = true) },
                    onReady = { viewportReady = true; openFeed() })
            }
            }
        }
        if (!DiscoverBounds.available) window.decorView.post { viewportReady = true; openFeed() }
    }
    private fun openFeed() {
        if (!viewportReady || feedLaunched || isFinishing) return
        feedLaunched = true
        // Attach in place: this activity is content within the Discover page, not another page.
        startActivity(Intent(this, DiscoverFeedActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION), DiscoverBounds.launchOptions())
    }
    override fun onResume() { super.onResume(); model.refresh(); configureDiscoverWindow(model.state.value.verticalStatus) }
    override fun onDestroy() { if (DiscoverSession.host.get() === this) DiscoverSession.host.clear(); super.onDestroy() }
    private fun launchApp(app: AppEntry) {
        try {
            val user = getSystemService(UserManager::class.java).getUserForSerialNumber(app.userSerial)
                ?: throw IllegalStateException("Profile is unavailable")
            val launcherApps = getSystemService(LauncherApps::class.java)
            app.shortcutId?.let { launcherApps.startShortcut(app.packageName, it, null, null, user) }
                ?: launcherApps.startMainActivity(app.component, user, null, null)
        } catch (_: RuntimeException) { Toast.makeText(this, getString(R.string.app_is_unavailable, app.label), Toast.LENGTH_SHORT).show() }
    }
}

class DiscoverFeedActivity : DiscoverPageActivity() {
    private lateinit var client: DiscoverClient
    private lateinit var frame: DiscoverFrame
    private val message = mutableStateOf<String?>("Connecting to Discover…")
    private var returnAnimator: ValueAnimator? = null
    private var connectRequest = 0
    @SuppressLint("RequiresWindowSdk") // Collection is directly guarded by extensionVersion >= 6.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiscoverSession.feed = WeakReference(this)
        val vertical = runCatching { JSONObject(getSharedPreferences("launcher", 0).getString("state", "{}") ?: "{}").optBoolean("verticalStatus", true) }.getOrDefault(true)
        configureDiscoverWindow(vertical)
        frame = DiscoverFrame(this, vertical)
        val bounds = windowManager.maximumWindowMetrics.bounds
        frame.fullSize = Size(bounds.width().toFloat(), bounds.height().toFloat())
        lifecycleScope.launch {
          if (WindowSdkExtensions.getInstance().extensionVersion >= 6) {
            ActivityEmbeddingController.getInstance(this@DiscoverFeedActivity).embeddedActivityWindowInfo(this@DiscoverFeedActivity).collect {
                frame.fullSize = Size(it.parentHostBounds.width().toFloat(), it.parentHostBounds.height().toFloat())
                frame.origin = androidx.compose.ui.geometry.Offset(it.boundsInParentHost.left.toFloat(), it.boundsInParentHost.top.toFloat())
            }
          }
        }
        client = DiscoverClient(this, vertical,
            onState = { message.value = it; if (it != null) frame.hide() },
            onVisible = frame::reveal, onProgress = { DiscoverMotion.progress.floatValue = it; frame.invalidate() },
            onClosed = { DiscoverSession.home(this) })
        setContent {
            DuoTheme(rememberSavedAppearance().dark) {
                BackHandler { returnHome() }
                var showMessage by remember { mutableStateOf(false) }
                LaunchedEffect(message.value) {
                    showMessage = false
                    // A fast connection should not flash a Retry/loading panel for one frame.
                    if (message.value == "Connecting to Discover…") { delay(650); frame.hide() }
                    showMessage = message.value != null
                }
                val progress = DiscoverMotion.progress.floatValue
                Box(Modifier.fillMaxSize()) {
                    if (!DiscoverBounds.available) DuneWallpaper()
                    Surface(if (DiscoverBounds.available) Modifier.fillMaxSize()
                        else Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(FolioSpace.MEDIUM.dp),
                        shape = RoundedCornerShape(if (DiscoverBounds.available) 16.dp else 26.dp),
                        color = Glass, border = BorderStroke(1.dp, Color.White.copy(alpha = .4f))) {
                        // Recovery is only shown while connecting or after a real error. A native
                        // swipe must never reveal the old loading controls behind a loaded feed.
                        if (showMessage) Column(Modifier.fillMaxSize().graphicsLayer {
                            translationX = -(1f - progress) * DiscoverMotion.pageWidth
                        }.padding(FolioSpace.XXL.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.discover), style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(16.dp))
                            Text(message.value ?: "Google Discover", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(20.dp))
                            FolioButton(stringResource(R.string.retry), ::connectSafely, style = FolioButtonStyle.TONAL,
                                icon = Icons.Rounded.Refresh, tag = "discover-retry")
                            TextButton(onClick = ::openGoogle) { Text(stringResource(R.string.open_google)) }
                            TextButton(onClick = ::returnHome) { Text(stringResource(R.string.back_to_home)) }
                        }
                    }
                    Canvas(Modifier.fillMaxSize()) {
                        // Read the same progress as the native frame so both windows agree.
                        if (progress < 1f) DiscoverMotion.drawHome(drawContext.canvas.nativeCanvas,
                            frame.origin.x, frame.origin.y, frame.fullSize.width, frame.fullSize.height)
                    }
                }
            }
        }
        // Never attach a full-screen overlay when a host failed to embed this activity.
        window.decorView.post { connectSafely() }
    }
    override fun onResume() { super.onResume(); if (::client.isInitialized) client.resume() }
    override fun onPause() { if (::client.isInitialized) client.pause(); super.onPause() }
    override fun onDestroy() {
        connectRequest++
        returnAnimator?.removeAllListeners(); returnAnimator?.cancel()
        if (DiscoverSession.feed.get() === this) DiscoverSession.feed.clear()
        if (::client.isInitialized) client.disconnect()
        if (::frame.isInitialized) frame.hide()
        super.onDestroy()
    }
    fun returnHome() {
        if (isFinishing || returnAnimator != null) return
        if (::client.isInitialized && client.closeForHome()) return
        if (::client.isInitialized) client.disconnect()
        // Recovery and pre-connection swipes use the same incoming Home preview.
        returnAnimator = ValueAnimator.ofFloat(DiscoverMotion.progress.floatValue, 0f).apply {
            duration = 240
            addUpdateListener { DiscoverMotion.progress.floatValue = it.animatedValue as Float; frame.invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { DiscoverSession.home(this@DiscoverFeedActivity) }
            })
            start()
        }
    }
    private fun connectSafely() {
        if (isDestroyed || isFinishing) return
        frame.hide()
        val request = ++connectRequest
        fun attachWhenSized(remaining: Int) {
            if (request != connectRequest || isDestroyed || isFinishing || returnAnimator != null) return
            val decor = window.decorView
            if (ActivityEmbeddingController.getInstance(this).isActivityEmbedded(this) &&
                DiscoverBounds.matchesViewport(decor.width, decor.height)) client.connect()
            else if (remaining > 0) decor.postOnAnimation { attachWhenSized(remaining - 1) }
            else message.value = "Discover couldn't fit beside the dock. Return home and try again."
        }
        // Embedding can be reported before the decor has received its inset size. Attaching
        // Google during that gap can briefly create a full-screen white native window.
        window.decorView.postOnAnimation { attachWhenSized(20) }
    }
    private fun openGoogle() {
        val intent = packageManager.getLaunchIntentForPackage(DiscoverClient.GOOGLE_PACKAGE)
        if (intent != null) runCatching { startActivity(intent) }
        else Toast.makeText(this, R.string.install_or_enable_the_google_app_first, Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun DiscoverDock(state: LauncherState, status: DeviceStatus, fullSize: Size,
    onLaunch: (AppEntry) -> Unit, onHome: () -> Unit, onSearch: () -> Unit, onReady: () -> Unit) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val fullWidth = fullSize.width / density.density
    val classScale = androidx.compose.ui.platform.LocalConfiguration.current.classScale
    val preset = if (fullWidth * classScale >= 650f && fullSize.height / density.density * classScale >= HOME_REGULAR_MIN_HEIGHT_DP) state.expanded else state.compact
    val apps = remember(state.apps) { state.apps.associateBy { it.id } }
    val progress = DiscoverMotion.progress.floatValue
    val backgroundRevision = LauncherBackgroundCache.revision.intValue
    val backgroundPhoto = remember(backgroundRevision) {
        LauncherBackgroundCache.bitmap?.takeUnless { it.isRecycled }?.asImageBitmap()
    }
    DiscoverMotion.pageWidth = (fullWidth - preset.dockWidth - 28f) * density.density
    Box(Modifier.fillMaxSize().testTag("discover-chrome").semantics { testTagsAsResourceId = true }) {
        Canvas(Modifier.fillMaxSize()) {
            val offset = fullSize.width - size.width
            translate(left = -offset) {
                // Draw the same full-screen wallpaper coordinates in this narrow viewport.
                val native = drawContext.canvas
                val painter = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
                painter.draw(density, layoutDirection, native, fullSize) {
                    drawLauncherBackground(backgroundPhoto, DuoAppearanceRuntime.dark)
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (DiscoverBounds.available) Box(Modifier.fillMaxHeight().width((fullWidth - preset.dockWidth - 28).dp)
                .padding(start = FolioSpace.LARGE.dp, top = FolioSpace.LARGE.dp, bottom = FolioSpace.LARGE.dp).onGloballyPositioned {
                    val outer = it.boundsInWindow()
                    val padding = 16 * density.density
                    DiscoverBounds.updateViewport(context, android.graphics.Rect(
                        (outer.left + padding).toInt(), (outer.top + padding).toInt(),
                        (outer.right - padding).toInt(), (outer.bottom - padding).toInt()))
                    onReady()
                }) {
                Surface(Modifier.fillMaxSize().graphicsLayer { translationX = -(1f - progress) * DiscoverMotion.pageWidth },
                    shape = RoundedCornerShape(30.dp), color = Glass.copy(alpha = .92f), border = BorderStroke(1.dp, Color.White.copy(alpha = .5f))) {}
            }
            Canvas(Modifier.fillMaxSize()) {
                val insets = androidx.core.view.ViewCompat.getRootWindowInsets((context as Activity).window.decorView)
                    ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                if (progress < 1f) DiscoverMotion.drawHome(drawContext.canvas.nativeCanvas,
                    fullSize.width - size.width, (insets?.top ?: 0).toFloat(), fullSize.width, fullSize.height)
            }
            var statusHeight by remember { mutableFloatStateOf(0f) }
            // Discover's page is laid out beside the Side Bar, so its dock stays there whatever Home uses.
            val geometry = homeGeometry(fullWidth, maxHeight.value, preset.copy(dockPlacement = DockPlacement.SIDE), state.labels,
                statusHeight = if (state.verticalStatus) statusHeight + 22f else 0f,
                labelHeight = with(density) { 14.sp.toDp().value } + 6f, inLibrary = true,
                homeBottomSpace = if (context.getSystemService(android.app.role.RoleManager::class.java)
                    .isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) 44f else 88f, classScale = classScale, appRows = state.homeAppRows)
            if (state.verticalStatus) StatusRail(status, Modifier.align(Alignment.TopEnd).padding(end = FolioSpace.MEDIUM.dp)
                .offset(y = geometry.statusTop.dp).width(preset.dockWidth.dp)
                .onSizeChanged {
                    // The whole rail, location slot included: the dock goes below all of it.
                    statusHeight = it.height / density.density
                },
                compact = maxHeight < 500.dp, iconSize = dockIconSize(geometry.iconSize).dp)
            Surface(Modifier.align(Alignment.TopEnd).padding(end = FolioSpace.MEDIUM.dp).offset(y = geometry.dockTop.dp)
                .width(preset.dockWidth.dp).height(geometry.dockHeight.dp).testTag("discover-dock"),
                shape = RoundedCornerShape(30.dp), color = Glass.copy(alpha = .32f), border = BorderStroke(1.dp, Color.White.copy(alpha = .3f))) {
                Column(Modifier.padding(vertical = FolioSpace.SMALL.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    state.dock.forEachIndexed { index, id ->
                        val app = apps[id]
                        Box(Modifier.fillMaxWidth().height(geometry.dockRowHeight.dp).testTag("discover-dock-slot-$index")
                            .semantics { contentDescription = app?.label ?: "Choose dock app on home" }
                            .clickable(role = Role.Button) { if (app != null) onLaunch(app) else onHome() }, contentAlignment = Alignment.Center) {
                            if (app != null) AppIcon(app, null,
                                Modifier.size(dockIconSize(geometry.iconSize).dp).clip(RoundedCornerShape(11.dp)))
                            else Icon(Icons.Rounded.Home, null, tint = Color.White)
                        }
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomEnd).padding(end = FolioSpace.MEDIUM.dp, bottom = FolioSpace.MEDIUM.dp).width(preset.dockWidth.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Home is physically to the right of Discover, matching our fixed page order.
                FilledTonalIconButton(onClick = onHome, Modifier.testTag("discover-home")) { Icon(Icons.Rounded.ArrowForward, "Back to home") }
                Spacer(Modifier.height(8.dp))
                FilledTonalIconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search apps") }
            }
        }
    }
}
