package com.mccal.folio

import android.content.SharedPreferences
import androidx.compose.ui.layout.onSizeChanged
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.view.Display
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.exp
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle
import com.mccal.folio.duet.DuetShader
import com.mccal.folio.duet.DuetStyle
import com.mccal.folio.duet.DuetStyles

/**
 * iPhone Duo–style fold effect, as dynamic as a Galaxy Z Fold allows.
 *
 * Android doesn't require the public hinge sensor to be continuous, and on a Galaxy Z Fold8 it reports only 0°, 90°
 * and 180° to apps (Samsung's finer readings stay with its own components); One UI decides when displays switch.
 * So on stepped sensors the effect is *step-anchored and speed-adaptive*: each real step is a checkpoint, the motion
 * between checkpoints is predicted from how fast this person folds (learned over time), and a late or early step
 * bends the animation instead of snapping it. Phones whose sensor proves continuous follow the angle itself
 * (see [HingeTracker]).
 *
 * Visual model (from the MIT Three.js recreation): the half left of the hinge is blurred with
 * radius ∝ m·e^1.35 and darkened toward its outer edge; m is 1 half-folded and 0 flat.
 */
@Composable
fun FoldTransitionHost(enabled: Boolean = true, intensity: Float = 1f, stayAwake: Boolean = true,
    snapshotMorph: Boolean = false, haptics: Boolean = true, style: DuetStyle = DuetStyles.IPHONE,
    direction: com.mccal.folio.duet.DuetDirection = com.mccal.folio.duet.DuetDirection.BOTH, reduceMotion: Boolean = false,
    content: @Composable () -> Unit) {
    // Which screen we're on, by size in both dimensions, so rotating the cover to landscape never looks like an unfold.
    val expanded = LocalConfiguration.current.fitsRegularHomeLayout()
    val view = LocalView.current
    val context = LocalContext.current
    // Where the hinge is and which half moves, from the real fold and the display's rotation, so the effect is
    // right in portrait, upside down and on a rotated cover, not just in the unfolded landscape it was tuned in.
    val hinge = LocalHinge.current
    val rotation = view.display?.rotation ?: android.view.Surface.ROTATION_0
    val shader = remember { if (Build.VERSION.SDK_INT >= 33) DuetShader() else null }
    val fold = remember { FoldTimeline(context) }
    fold.unfoldStart = style.startAt
    // The screen's own corner radius, so a tilted pane and the slightly shrunk open screen have the panel's corners.
    // Read when a fold starts rather than at first composition: the window's insets (and so its rounded corners) can
    // arrive after the first frame, and a 0 kept from then would square off the panel's corners until the next switch.
    var cornerPx by remember(view, expanded) { mutableFloatStateOf(0f) }
    // Android 15+ files a layer with no motion hint as "normal", which the Fold8 runs at 60 Hz. While the fold
    // animates, ask for the display's top rate; once it settles, hand the choice back to the system (dynamic).
    val fastest = remember(view, expanded) { view.display?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 0f }
    var fast by remember { mutableStateOf(false) }
    fun animating(on: Boolean) {
        if (Build.VERSION.SDK_INT < 35 || on == fast || fastest <= 0f) return
        fast = on
        view.requestedFrameRate = if (on) fastest else android.view.View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
    }
    DisposableEffect(view) { onDispose { animating(false) } }
    fold.stayAwake = stayAwake
    // One light tick as the hinge passes halfway, opening or closing (idea from FoldFX).
    val tick by androidx.compose.runtime.rememberUpdatedState(enabled && haptics)
    fold.onHalfway = { if (tick) view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 34) android.view.HapticFeedbackConstants.SEGMENT_TICK else android.view.HapticFeedbackConstants.CLOCK_TICK) }
    // m: 0 = clean, 1 = fully half-folded look. cover = whole-screen mode on the cover display.
    var m by remember { mutableFloatStateOf(0f) }
    // Screenshot morph (fallback style): snapshots of Folio's own screen taken the moment the hinge
    // starts moving, drawn over the new display and melted into the live UI. Memory only, never saved.
    val contentLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var coverShot by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var innerShot by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var morph by remember { mutableFloatStateOf(1f) } // 0 = snapshot fully shown, 1 = done
    val morphEnabled by androidx.compose.runtime.rememberUpdatedState(enabled && snapshotMorph)
    fold.onOpeningStarted = { if (morphEnabled && direction.allows(true) && !fold.expanded) scope.launch { coverShot = runCatching { contentLayer.toImageBitmap() }.getOrNull() } }
    fold.onClosingStarted = { if (morphEnabled && direction.allows(false) && fold.expanded) scope.launch { innerShot = runCatching { contentLayer.toImageBitmap() }.getOrNull() } }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> fold.start()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> fold.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); fold.stop() }
    }

    // Decide during composition so the very first frame on the new display is already covered.
    if (expanded != fold.expanded) {
        fold.expanded = expanded
        fold.onDisplaySwitched(SystemClock.uptimeMillis())
        m = if (expanded) fold.unfoldStart else START_M_ON_COVER
        // Cover the very first frame on the new display with the snapshot (held until the panel is lit).
        if (enabled && snapshotMorph && (if (expanded) coverShot != null else innerShot != null)) morph = 0f
    }

    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
        var litFrames = 0
        var lastFrame = 0L
        while (true) {
            // Idle: sleep until the hinge moves or the display switches, so a fold starts on its first frame rather
            // than up to one poll later. The timeout is only a backstop.
            if (!fold.busy && m == 0f) { animating(false); lastFrame = 0L; kotlinx.coroutines.withTimeoutOrNull(IDLE_WAIT_MS) { fold.wake.receive() }; continue }
            animating(enabled)
            if (cornerPx == 0f) cornerPx = screenCornerPx(view)
            withFrameNanos { frame ->
                val now = SystemClock.uptimeMillis()
                val dt = if (lastFrame == 0L) 16f else ((frame - lastFrame) / 1_000_000f).coerceIn(1f, 64f)
                lastFrame = frame
                if (fold.waitingForPanel) {
                    val lit = view.display?.state == Display.STATE_ON
                    litFrames = if (lit) litFrames + 1 else 0
                    if (litFrames >= 2 || now - fold.switchedAt > LIT_TIMEOUT_MS) {
                        fold.onPanelLit(now); litFrames = 0
                        if (morphEnabled && (if (fold.expanded) coverShot != null else innerShot != null)) { fold.morphFrom = now; morph = 0f }
                    }
                }
                val target = fold.targetM(now)
                // Follow the target closely but never jump: small time constant, frame-rate independent.
                val next = m + (target - m) * (1f - exp(-dt / fold.followMs))
                fold.trace(now, target, next)
                m = if (target == 0f && next < .003f) 0f else next
                if (fold.morphFrom >= 0) {
                    val t = ((now - fold.morphFrom) / (if (fold.expanded) MORPH_UNFOLD_MS else MORPH_FOLD_MS)).coerceIn(0f, 1f)
                    morph = easeInOutSine(t)
                    if (t >= 1f) { fold.morphFrom = -1L; morph = 1f; if (fold.expanded) coverShot = null }
                }
            }
        }
    } }

    // The Duo shader always drives the rotating half; the iPhone Duo style adds the still right half on top.
    // Opening only or closing only: the other way plays nothing. Read per frame, since the way can change mid-fold.
    val plays by androidx.compose.runtime.rememberUpdatedState(direction)
    fun useBlurEffect() = enabled && plays.allows(fold.opening)
    fun useMotion() = useBlurEffect()
    // Fold motion (ripple, depth, light) on the open screen: follows m, both ways, off under Reduce Motion.
    val motionOptions by rememberFoldMotionOptions(context)
    var hostSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val motion = remember(motionOptions, rotation, hinge, hostSize, enabled, reduceMotion) {
        if (!enabled || reduceMotion || !motionOptions.any || hostSize.width == 0) null
        else {
            val g = foldGeometry(rotation, hinge, hostSize.width.toFloat(), hostSize.height.toFloat())
            val extent = (if (g.horizontal) hostSize.height else hostSize.width).toFloat()
            FoldMotionScope({ if (fold.expanded && useMotion()) m else 0f }, motionOptions, g.horizontal, g.hingePx, maxOf(g.hingePx, extent - g.hingePx))
        }
    }

    // The Duo effect wraps both the live screen and the still picture (so the cover's blur applies to both),
    // while the recording below it captures the clean screen (a snapshot must never have blur baked in).
    Box(Modifier.fillMaxSize().onSizeChanged { hostSize = it }.then(
        if (shader != null) Modifier.graphicsLayer {
            renderEffect = if (useBlurEffect() && m > 0f && Build.VERSION.SDK_INT >= 33) shader.effect(size.width, size.height, (m * intensity).coerceIn(0f, 1.5f),
                cover = !fold.expanded, geometry = foldGeometry(rotation, hinge, size.width, size.height), style = style,
                cornerPx = cornerPx) else null
            // The open screen settles up to full size as it clears, and eases back down as it folds.
            val settle = when {
                !useBlurEffect() -> 1f
                fold.expanded -> if (reduceMotion) 1f else 1f - FOLD_SCALE * m.coerceIn(0f, 1f)
                else -> coverSettleScale(m, reduceMotion)
            }
            scaleX = settle; scaleY = settle
            // Shrunk, the screen's square edges would show inside the panel's rounded ones.
            clip = settle < 1f
            shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerPx)
        } else Modifier.drawWithContent {
            drawContent()
            if (useBlurEffect() && m > 0f) {
                if (fold.expanded) drawRect(Brush.horizontalGradient(0f to Color.Black.copy(alpha = (m * style.darkening).coerceIn(0f, 1f)),
                    .5f to Color.Transparent, startX = 0f, endX = size.width))
                else drawRect(Color.Black.copy(alpha = (.5f * m * style.darkening).coerceIn(0f, 1f)))
            }
        })) {
        Box(Modifier.fillMaxSize()
            // Keep a live recording of Folio's screen so a snapshot can be taken instantly when folding starts.
            .then(if (enabled && snapshotMorph) Modifier.drawWithContent {
                contentLayer.record { this@drawWithContent.drawContent() }
                drawLayer(contentLayer)
            } else Modifier)) {
            androidx.compose.runtime.CompositionLocalProvider(LocalFoldMotion provides motion) { content() }
        }
        // A faint light travelling down the hinge as the open screen unfolds or folds.
        motion?.takeIf { it.options.light }?.let { FoldMotionLight(it) }
        // The still picture maps the cover 1:1 onto the inner half only in the natural orientation; rotated, the
        // pictures don't line up, so the blur carries the transition on its own.
        if (snapshotMorph && morph < 1f && rotation == android.view.Surface.ROTATION_0) SnapshotMorph(fold.expanded, coverShot, innerShot) { morph }
        // A faint light along the hinge edge while the cover opens.
        if (enabled && !fold.expanded && fold.opening && useBlurEffect() && !reduceMotion) androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val a = coverLightAlpha(m, false)
            if (a <= 0.002f) return@Canvas
            val light = Color.White.copy(alpha = a)
            val geometry = foldGeometry(rotation, hinge, size.width, size.height)
            val brush = when (coverHingeEdge(geometry)) {
                CoverEdge.LEFT -> Brush.horizontalGradient(0f to light, 1f to Color.Transparent, startX = 0f, endX = size.width * COVER_LIGHT_REACH)
                CoverEdge.RIGHT -> Brush.horizontalGradient(0f to Color.Transparent, 1f to light, startX = size.width * (1f - COVER_LIGHT_REACH), endX = size.width)
                CoverEdge.TOP -> Brush.verticalGradient(0f to light, 1f to Color.Transparent, startY = 0f, endY = size.height * COVER_LIGHT_REACH)
                CoverEdge.BOTTOM -> Brush.verticalGradient(0f to Color.Transparent, 1f to light, startY = size.height * (1f - COVER_LIGHT_REACH), endY = size.height)
            }
            drawRect(brush)
        }
        // Whole screen dims as it folds, like the display powering down with the hinge.
        if (enabled && direction.allows(false) && fold.closing) androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = (m * FOLD_DIM).coerceIn(0f, FOLD_DIM)))
        }
    }
}

/**
 * iPhone Duo's trick (per hands-on reviews and chuspeeism/iphone-duo): content doesn't move. The panel with the
 * rear cameras stays put, so the cover screen's picture is shown on the inner screen's right half at the same
 * physical size and position (hinge-side edge on the hinge, top-aligned) while the left half comes into focus
 * (the Duo shader on the live screen). Folding does the reverse. The still picture then fades into the live UI.
 * Both Fold screens share a density, so 1:1 pixels means the same physical size.
 */
@Composable
private fun SnapshotMorph(expanded: Boolean, coverShot: androidx.compose.ui.graphics.ImageBitmap?,
    innerShot: androidx.compose.ui.graphics.ImageBitmap?, progress: () -> Float) {
    // Hold the still picture while the rotating half clears, then hand over to the live UI.
    fun stillAlpha(p: Float) = 1f - ((p - STILL_HOLD) / (1f - STILL_HOLD)).coerceIn(0f, 1f)
    if (expanded) coverShot?.let { shot ->
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = stillAlpha(progress()) }) {
            val hinge = size.width / 2
            clipRect(left = hinge) {
                drawImage(shot, dstOffset = androidx.compose.ui.unit.IntOffset(hinge.toInt(), 0),
                    dstSize = androidx.compose.ui.unit.IntSize(shot.width, shot.height))
            }
        }
    } else innerShot?.let { shot ->
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = stillAlpha(progress()) }) {
            // The inner right half, 1:1, with the hinge on the cover's left edge.
            val hinge = shot.width / 2
            drawImage(shot, srcOffset = androidx.compose.ui.unit.IntOffset(hinge, 0),
                srcSize = androidx.compose.ui.unit.IntSize(shot.width - hinge, shot.height),
                dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                dstSize = androidx.compose.ui.unit.IntSize(shot.width - hinge, shot.height))
        }
    }
}

/** Hinge steps + learned timing → target effect strength over time. */
internal class FoldTimeline(private val context: Context, private val suLauncher: SuLauncher = ProcessSuLauncher) : SensorEventListener {
    /** Rung whenever something happens that can start the effect, so the idle loop wakes at once. */
    val wake = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val hinge: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
    private val prefs = context.getSharedPreferences("folio", 0)

    var expanded = false
    var stayAwake = true
    var switchedAt = -1L; private set
    var waitingForPanel = false; private set

    // What this phone's hinge sensor reports, learned from its readings and remembered. The Fold8's public sensor is
    // stepped (0/90/180), so the motion between steps is predicted from learned timing; a continuous sensor is
    // followed directly.
    private fun publicTracker() = HingeTracker(if (hinge == null) HingeCapability.POSTURE_ONLY
        else if (prefs.getString(hingeCapabilityKey(HingeSource.PUBLIC_SENSOR), null) == HingeCapability.CONTINUOUS.name) HingeCapability.CONTINUOUS else HingeCapability.STEPPED)
    private var tracker = publicTracker()
    // Which feed the readings come from (ADR 0012): the public sensor, or the root helper when the owner has tested it and switched it on.
    private var source = HingeSource.PUBLIC_SENSOR
    private var rootFeed: RootHingeFeed? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val continuous get() = tracker.capability == HingeCapability.CONTINUOUS
    private var angleAt = 0L
    // A real movement, not sensor jitter: what the stall checks measure from on continuous sensors.
    private var movedAt = 0L
    private var movedFrom = 0f
    // Highest angle while not folding, and lowest while folding: a fold or a reopen is a real change from these.
    private var peak = 0f
    private var trough = 0f

    // Unfold (inner display): lit → flat.
    private var litAt = -1L
    private var flatAt = -1L
    private var litAngle = HingeTracker.FLAT_ENTER_DEG
    private var predictedOpenMs = prefs.getFloat("fold_open_ms", 520f)

    // Fold (inner display): 180→90 step → 0 step.
    private var closeStartAt = -1L
    private var closedAt = -1L
    private var reopenedAt = -1L
    private var predictedCloseMs = prefs.getFloat("fold_close_ms", 650f)

    // Cover display after folding, and while starting to open from the cover.
    private var coverLitAt = -1L
    private var coverOpeningAt = -1L
    /**
     * The hinge angle for the cover, smoothed far less than [HingeTracker.visual]: the 70 ms filter trails a 300 deg/s opening by about 20
     * degrees (traced 7 Oct 2026), so the cover blur reacted about 60 ms behind the hand. Only the cover uses it.
     */
    internal var coverAngle: Float? = null; private set
    private var coverAngleNs = 0L
    // The cover's effect can rise only so fast (see COVER_RISE_PER_S), and always falls as fast as the hand goes back.
    private var coverTarget = 0f
    private var coverTargetAt = 0L
    private val appContext = context.applicationContext

    /** Frame-by-frame log for tuning, read with logcat. Off unless `adb shell setprop log.tag.FolioFoldTrace DEBUG` was run. */
    private var tracing = false
    fun trace(now: Long, target: Float, m: Float) {
        if (!tracing) return
        runCatching {
            android.util.Log.d(TRACE_TAG, "t=$now src=$source cap=${tracker.capability} raw=${tracker.raw} vis=${tracker.visual?.let { "%.1f".format(it) }} " +
                "exp=$expanded wait=$waitingForPanel close=${closeStartAt >= 0} reopen=${reopenedAt >= 0} coverOpen=${coverOpeningAt >= 0} lit=${litAt >= 0} " +
                "target=${"%.3f".format(target)} m=${"%.3f".format(m)} follow=${followMs.toInt()}")
        }
    }
    private fun traceEvent(text: String) { if (tracing) runCatching { android.util.Log.d(TRACE_TAG, "event $text raw=${tracker.raw} expanded=$expanded") } }

    var onOpeningStarted: (() -> Unit)? = null
    var onClosingStarted: (() -> Unit)? = null
    var onHalfway: (() -> Unit)? = null
    /** Uptime when the screenshot morph started on the new display, or -1. */
    var morphFrom = -1L
    /** Folding from the open screen right now. */
    val closing get() = closeStartAt >= 0 && expanded
    /** How frosted the open screen is when it lights: the style's own start (iPhone Duo starts where the real hinge is). */
    var unfoldStart = START_M_ON_UNFOLD
    /** Which way the hinge last went: true once it starts opening or lands on the open screen, false once it folds. */
    var opening = true
        private set
    val busy get() = morphFrom >= 0 || waitingForPanel || litAt >= 0 || closeStartAt >= 0 || reopenedAt >= 0 || coverLitAt >= 0 || coverOpeningAt >= 0

    fun start() {
        tracing = runCatching { android.util.Log.isLoggable(TRACE_TAG, android.util.Log.DEBUG) }.getOrDefault(false)
        val apk = context.applicationInfo.sourceDir
        val su = RootHingeStore.suPath(context)
        // The kill switch and the root options are read again whenever they change, so turning them off stops the helper at once.
        listOf(SystemBridge.PREFS, RootHingeStore.PREFS).forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(rootGate) }
        if (su != null && rootAllowed()) {
            // A continuous feed proven by the owner's test: followed directly, remembered apart from the public sensor's.
            useSource(HingeSource.ROOT_HELPER, HingeCapability.CONTINUOUS)
            rootFeed = RootHingeFeed(suLauncher, apk, su,
                onSample = { s -> main.post { if (source == HingeSource.ROOT_HELPER) { onAngle(s.angleDegrees, s.timestampNanos, SystemClock.uptimeMillis()); wake.trySend(Unit) } } },
                onLost = { main.post { rootLost() } }, now = { SystemClock.elapsedRealtime() }).also { it.start() }
        } else registerPublic()
    }

    fun stop() {
        listOf(SystemBridge.PREFS, RootHingeStore.PREFS).forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(rootGate) }
        rootFeed?.stop(); rootFeed = null
        sensors?.unregisterListener(this)
    }

    /** Whether the root hinge feed may run right now: the owner's options and the System Bridge switch. */
    private fun rootAllowed() = RootHingeStore.advanced(context) && RootHingeStore.useInFold(context) &&
        hingeSource(SystemBridge.broker(context)) == HingeSource.ROOT_HELPER

    private val rootGate = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        main.post {
            if (rootFeed != null && !rootAllowed()) {
                rootFeed?.stop(); rootFeed = null
                registerPublic()
                wake.trySend(Unit)
            }
        }
    }

    private fun registerPublic() {
        useSource(HingeSource.PUBLIC_SENSOR, null)
        hinge?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    /** The root feed ended by itself: say so, and carry on with the public sensor, whose stepped prediction always works. */
    private fun rootLost() {
        if (source != HingeSource.ROOT_HELPER) return
        rootFeed?.stop(); rootFeed = null
        RootHingeStore.markLost(context)
        registerPublic()
        wake.trySend(Unit)
    }

    private fun useSource(next: HingeSource, learned: HingeCapability?) {
        if (source == next && learned == null) return
        source = next
        tracker = if (next == HingeSource.ROOT_HELPER) HingeTracker(learned ?: HingeCapability.CONTINUOUS) else publicTracker()
    }

    fun onDisplaySwitched(now: Long) {
        traceEvent("display switched, now expanded=$expanded")
        switchedAt = now; waitingForPanel = true; opening = expanded
        wake.trySend(Unit)
        litAt = -1L; flatAt = -1L; closeStartAt = -1L; closedAt = -1L; reopenedAt = -1L; coverLitAt = -1L; coverOpeningAt = -1L
    }

    fun onPanelLit(now: Long) {
        traceEvent("panel lit")
        waitingForPanel = false
        if (expanded) { litAt = now; litAngle = tracker.visual ?: HingeTracker.FLAT_ENTER_DEG; if (tracker.flat) flatAt = now } else coverLitAt = now
    }

    override fun onSensorChanged(event: SensorEvent) {
        onAngle(event.values.firstOrNull() ?: return, event.timestamp, SystemClock.uptimeMillis())
        wake.trySend(Unit)
    }

    /** One hinge reading: split from [onSensorChanged] so recorded folds can be played through it in tests. */
    internal fun onAngle(value: Float, timestampNs: Long, now: Long) {
        val previous = tracker.raw
        val wasFlat = tracker.flat
        val wasClosed = tracker.closed
        if (tracker.feed(value, timestampNs)) prefs.edit().putString(hingeCapabilityKey(source), tracker.capability.name).apply()
        angleAt = now
        if (!expanded) {
            val before = coverAngle
            coverAngle = if (before == null || timestampNs <= coverAngleNs) value
                else before + (1f - kotlin.math.exp(-((timestampNs - coverAngleNs) / 1e9f) / COVER_SMOOTH_S)) * (value - before)
            coverAngleNs = timestampNs
        } else coverAngle = null
        if (previous == null) { peak = value; movedFrom = value; movedAt = now; return }
        if (previous == value) return
        // A phone held still for a while starts a fresh reference, so an old maximum can't turn a small move into a fold.
        if (now - movedAt > STALL_MS && closeStartAt < 0) peak = previous
        if (kotlin.math.abs(value - movedFrom) >= MOVE_DEG) { movedFrom = value; movedAt = now }
        if (HingeTracker.crossedHalfway(previous, value)) onHalfway?.invoke()
        if (expanded) {
            when {
                // Reached flat while revealing: learn how long lit → flat takes for this person.
                tracker.flat && !wasFlat && litAt >= 0 && flatAt < 0 -> {
                    flatAt = now
                    learnOpen((now - litAt).toFloat())
                }
                // Started folding from flat: keep One UI from sleeping, and start the fold-away.
                wasFlat && !tracker.flat -> startClosing(now, value)
                // Nearly closed: learn how long the fold takes, and make sure the bridge is up.
                tracker.closed && closeStartAt >= 0 && closedAt < 0 -> {
                    closedAt = now
                    learnClose((now - closeStartAt).toFloat())
                    if (stayAwake) FoldBridgeActivity.start(appContext)
                }
                // Folding that didn't start from flat (e.g. from half-open): only a real drop counts,
                // so sensor jitter or adjusting a propped phone never starts the bridge.
                !tracker.flat && closeStartAt < 0 && peak - value >= MIN_FOLD_DROP_DEG -> {
                    startClosing(now, value)
                    if (tracker.closed) closedAt = now
                }
                // Opened back up before closing.
                closeStartAt >= 0 && value - trough >= REOPEN_DEG -> {
                    followMs = FOLLOW_MS
                    closeStartAt = -1L; closedAt = -1L; reopenedAt = now; peak = value; opening = true
                    FoldBridgeActivity.cancel()
                }
            }
            if (closeStartAt >= 0) trough = minOf(trough, value) else peak = maxOf(peak, value)
        } else {
            when {
                // Starting to open on the cover: blur the whole cover screen.
                wasClosed && !tracker.closed -> { followMs = FOLLOW_MS; coverOpeningAt = now; coverTarget = 0f; coverTargetAt = 0L; opening = true; onOpeningStarted?.invoke() }
                tracker.closed -> { coverOpeningAt = -1L; coverTarget = 0f; coverTargetAt = 0L }
            }
        }
    }

    private fun startClosing(now: Long, value: Float) {
        followMs = FOLLOW_MS
        closeStartAt = now; closedAt = -1L; reopenedAt = -1L; trough = value; opening = false
        onClosingStarted?.invoke()
        if (stayAwake) FoldBridgeActivity.start(appContext)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** How fast the effect follows its target. Quick while it tracks the hand; slower when it lets go, so a release is not a snap. */
    var followMs = FOLLOW_MS; private set

    /** The effect strength for the hinge as it is now: nothing at flat, growing as the phone closes, a little short of full at closed. */
    private fun foldStrengthAtAngle(): Float = easeInOutSine(((HingeTracker.FLAT_ENTER_DEG - (tracker.visual ?: HingeTracker.FLAT_ENTER_DEG)) /
        (HingeTracker.FLAT_ENTER_DEG - HingeTracker.CLOSED_ENTER_DEG)).coerceIn(0f, 1f)) * HOLD_M_BEFORE_CLOSED

    fun targetM(now: Long): Float = when {
        waitingForPanel -> if (expanded) unfoldStart else START_M_ON_COVER

        // Unfold, like iPhone Duo: the rotating (left) half fades from dark and blurred to clear. Recording the
        // Fold8 showed the panel lights only as the hinge is nearly flat, so a hinge-bound fade was over before it
        // was visible. The fade is a steady, fixed-length ease from the moment the panel is lit instead.
        expanded && litAt >= 0 -> {
            val t = ((now - litAt) / UNFOLD_FADE_MS).coerceIn(0f, 1f)
            if (t >= 1f) { litAt = -1L; flatAt = -1L; 0f }
            else {
                val timed = unfoldStart * (1f - easeInOutSine(t))
                // With a continuous sensor, a hand that's already flat clears sooner; the timed fade still caps it,
                // so holding the phone half open never leaves Home blurred.
                val flatDeg = HingeTracker.FLAT_ENTER_DEG
                val byAngle = (tracker.visual ?: flatDeg).let { a -> if (litAngle >= flatDeg) 0f else ((flatDeg - a) / (flatDeg - litAngle)).coerceIn(0f, 1f) }
                if (continuous) minOf(timed, unfoldStart * byAngle) else timed
            }
        }

        // Fold: blur builds with the hinge (continuous) or at the learned speed (stepped), complete at closed.
        expanded && closeStartAt >= 0 -> {
            val since = (now - closeStartAt).toFloat()
            val stalled = closedAt < 0 && (if (continuous) now - movedAt > STALL_MS else now - angleAt > predictedCloseMs + STALL_MS)
            when {
                stalled -> { closeStartAt = -1L; peak = tracker.raw ?: 0f; if (continuous) followMs = RELEASE_FOLLOW_MS; 0f } // deliberately half-open (flex mode): clear, gently on a continuous angle
                closedAt >= 0 && now - closedAt > CLOSED_STALL_MS -> { closeStartAt = -1L; closedAt = -1L; 0f } // never stuck dimmed
                closedAt >= 0 -> 1f
                continuous -> foldStrengthAtAngle()
                else -> easeInOutSine((since / predictedCloseMs).coerceIn(0f, 1f)) * HOLD_M_BEFORE_CLOSED
            }
        }

        // Reopened before closing. With a continuous angle the effect keeps following the hinge back up to flat, so closing
        // halfway and opening again is one smooth motion in both directions; with steps it settles back.
        expanded && reopenedAt >= 0 -> {
            if (continuous) {
                when {
                    tracker.flat -> { reopenedAt = -1L; 0f }
                    // Held part way (flex mode) or never reaching flat: let go, gently, rather than hold or snap.
                    now - movedAt > STALL_MS || now - reopenedAt > REOPEN_FOLLOW_MS -> { reopenedAt = -1L; followMs = RELEASE_FOLLOW_MS; 0f }
                    else -> foldStrengthAtAngle()
                }
            } else { if (now - reopenedAt > FINISH_MS * 2) reopenedAt = -1L; 0f }
        }

        // Opening from the cover: quick whole-screen blur until the inner display takes over.
        !expanded && coverOpeningAt >= 0 -> {
            if (now - coverOpeningAt > COVER_OPEN_STALL_MS) { coverOpeningAt = -1L; 0f }
            else if (continuous) {
                val want = coverBuildAtAngle(coverAngle ?: tracker.visual ?: 0f)
                // Going up it may rise at most COVER_RISE_PER_S, so a flick still builds over a visible moment and never steps by a fifth in a frame;
                // going back down it follows the hand at once.
                coverTarget = if (coverTargetAt == 0L) minOf(want, COVER_FIRST_STEP) // the first frame starts gently
                    else if (want <= coverTarget) want
                    else minOf(want, coverTarget + COVER_RISE_PER_S * ((now - coverTargetAt).coerceIn(0L, 100L) / 1000f))
                coverTargetAt = now
                coverTarget
            }
            else easeInOutSine(((now - coverOpeningAt) / COVER_OPEN_MS).coerceIn(0f, 1f)).also {
                // Remembered, so a angle that turns out to be continuous carries on from here and not from nothing.
                coverTarget = it; coverTargetAt = now
            }
        }

        // Cover after folding: short focus-in.
        !expanded && coverLitAt >= 0 -> {
            val t = ((now - coverLitAt) / COVER_MS).coerceIn(0f, 1f)
            if (t >= 1f) { coverLitAt = -1L; 0f } else START_M_ON_COVER * (1f - easeInOutSine(t))
        }
        else -> 0f
    }

    private fun learnOpen(ms: Float) {
        predictedOpenMs = (predictedOpenMs * .7f + ms.coerceIn(200f, 1400f) * .3f)
        prefs.edit().putFloat("fold_open_ms", predictedOpenMs).apply()
    }

    private fun learnClose(ms: Float) {
        predictedCloseMs = (predictedCloseMs * .7f + ms.coerceIn(250f, 1800f) * .3f)
        prefs.edit().putFloat("fold_close_ms", predictedCloseMs).apply()
    }
}
/**
 * How far along the cover's opening is, at a hinge angle: a straight ramp from closed to the handoff. The Duo shader eases it once (a
 * smoothstep, as Apple's outer screen does with angle / 90), so this must stay linear: an ease here as well stacked a second one on
 * top and made the frost appear late and rush in through the middle (found 7 Oct 2026, comparing with the Duo model).
 */
internal fun coverBuildAtAngle(angle: Float): Float =
    ((angle - COVER_BUILD_START_DEG) / (COVER_BUILD_END_DEG - COVER_BUILD_START_DEG)).coerceIn(0f, 1f)

/**
 * A soft light along the cover's hinge edge while it opens: nothing at the start, strongest half way, gone by the handoff, like light
 * spilling out of the gap. Kept faint on purpose; none under Reduce Motion.
 */
internal fun coverLightAlpha(m: Float, reduceMotion: Boolean): Float =
    if (reduceMotion) 0f else kotlin.math.sin(Math.PI.toFloat() * m.coerceIn(0f, 1f)) * COVER_LIGHT

/** Which edge of the cover is the hinge edge: left in the natural orientation, bottom at 90, right at 180, top at 270 (as [foldGeometry]). */
internal enum class CoverEdge { LEFT, RIGHT, TOP, BOTTOM }
internal fun coverHingeEdge(g: FoldGeometry): CoverEdge = when {
    !g.horizontal && !g.movingAfterHinge -> CoverEdge.LEFT
    !g.horizontal -> CoverEdge.RIGHT
    !g.movingAfterHinge -> CoverEdge.TOP
    else -> CoverEdge.BOTTOM
}

/** How far the cover content recedes while the effect is on, so the cover has depth and not only a blur; none under Reduce Motion. */
internal fun coverSettleScale(m: Float, reduceMotion: Boolean): Float = if (reduceMotion) 1f else 1f - COVER_SCALE * m.coerceIn(0f, 1f)

private fun easeOutCubic(t: Float): Float { val u = 1f - t; return 1f - u * u * u }
private fun easeInOutSine(t: Float): Float = (-(kotlin.math.cos(Math.PI * t) - 1) / 2).toFloat()

@RequiresApi(33)
/** Share of the morph during which the still picture stays fully visible. */
private const val STILL_HOLD = .55f
private const val MORPH_UNFOLD_MS = 650f
private const val MORPH_FOLD_MS = 420f
/** Inner panel lights around 120–135° on Z Fold: the cover half is still ~50° from flat. */
private const val START_M_ON_UNFOLD = 1f
/** Unfold fade length (the iPhone Duo reveal reads as ~half a second). */
private const val UNFOLD_FADE_MS = 520f
/** Strongest whole-screen dim while folding. */
private const val FOLD_DIM = .6f
private const val HOLD_M_BEFORE_CLOSED = .9f
// Measured on a Galaxy Z Fold8: the hinge reports only 0/90/180° on a 200 ms grid, so "flat" can arrive up to
// 200 ms after the panel is really flat. Finish faster once it does so the reveal doesn't trail the hand.
private const val FINISH_MS = 120f
private const val STALL_MS = 900f
/** Traced on the Fold8 (28 Sep 2026): at 380 ms with an ease-out, the cover had cleared before Samsung's own screen-on
 * fade was over, so nobody saw it. Longer, and held at first, it reads as the cover coming into focus. */
private const val COVER_MS = 560f
/** The cover lights right at closed, where the Duo outer screen is nearly clean: a light settle. */
private const val START_M_ON_COVER = 1f
private const val COVER_OPEN_MS = 300f
/** The cover's own angle filter: about 7 degrees behind a 300 deg/s opening, against about 21 for the shared one. */
private const val COVER_SMOOTH_S = .025f
/**
 * The cover effect rises by at most this much per second (a full build takes at least 0.17 s): traced flicks stepped 16 to 31% in one frame. At 5 a
 * 557 deg/s flick reached only 85% before the inner screen took over (traced 7 Oct 2026), so 6, which reaches full in time.
 */
private const val COVER_RISE_PER_S = 6f
/** The very first frame of a continuous opening can show at most this much, so the effect starts from nothing. */
private const val COVER_FIRST_STEP = .02f
/** From the closed threshold to 90 degrees, as the Duo model (angle / 90): the cover reads open past 12 degrees and is full by the handoff. */
private const val COVER_BUILD_START_DEG = 5f
private const val COVER_BUILD_END_DEG = 90f
/** How much smaller the cover content gets at full effect (the open screen uses 3%); a little more, since the cover is the smaller screen. */
private const val COVER_SCALE = .045f
/** The brightest the hinge light gets (white at 14%): a hint of light, not a glow. */
private const val COVER_LIGHT = .14f
/** How far into the cover the light reaches from the hinge edge. */
private const val COVER_LIGHT_REACH = .42f
private const val COVER_OPEN_STALL_MS = 2_000L
private const val FOLLOW_MS = 28f
private const val TRACE_TAG = "FolioFoldTrace"
/** Letting go of the effect (a held, half-open phone): a short, soft release instead of a snap. */
private const val RELEASE_FOLLOW_MS = 150f
/** After reopening, how long the effect may keep following the hinge before it lets go on its own. */
private const val REOPEN_FOLLOW_MS = 4_000L
private const val MIN_FOLD_DROP_DEG = 20f
private const val CLOSED_STALL_MS = 1_800L
private const val LIT_TIMEOUT_MS = 1_200L
private const val IDLE_WAIT_MS = 500L
/** How much smaller the open screen is at the start of the reveal (97%). */
private const val FOLD_SCALE = .03f
private const val MOVE_DEG = 3f
private const val REOPEN_DEG = 15f

/**
 * The fold effect on a preview (Settings): [m] 0 is open and clear, 1 half folded. Same shader, sweep and scale as Home,
 * with the hinge down the middle and the left half moving.
 */
@Composable
internal fun Modifier.foldPreviewEffect(style: DuetStyle = DuetStyles.IPHONE, m: () -> Float): Modifier {
    val shader = remember { if (Build.VERSION.SDK_INT >= 33) DuetShader() else null }
    return graphicsLayer {
        val value = m()
        val settle = 1f - FOLD_SCALE * value.coerceIn(0f, 1f)
        scaleX = settle; scaleY = settle
        if (shader != null && Build.VERSION.SDK_INT >= 33) renderEffect = if (value > 0f)
            shader.effect(size.width, size.height, value, cover = false, geometry = FoldGeometry(false, size.width / 2f, false), style = style) else null
    }.then(if (shader == null) Modifier.drawWithContent {
        drawContent()
        // Below Android 13 there's no shader, so a style only sets how dark the shade gets.
        drawRect(Brush.horizontalGradient(0f to Color.Black.copy(alpha = (m() * style.darkening).coerceIn(0f, 1f)), .5f to Color.Transparent, startX = 0f, endX = size.width))
    } else Modifier)
}

/** The display's corner radius in pixels (the top-left one; the Fold8's four match), or 0 where Android doesn't say. */
internal fun screenCornerPx(view: android.view.View): Float =
    if (Build.VERSION.SDK_INT >= 31) view.rootWindowInsets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 0f
    else 0f

/** The fold effect's layout: which way the hinge runs, where it is, and which side of it moves. */
internal data class FoldGeometry(val horizontal: Boolean, val hingePx: Float, val movingAfterHinge: Boolean)

/**
 * In the natural orientation (unfolded landscape; cover portrait) the moving half, or the cover's hinge edge, is
 * on the left. Display rotation moves that edge: 90° to the bottom, 180° to the right, 270° to the top. A real
 * hinge from WindowManager gives the exact position; otherwise it's the middle.
 */
internal fun foldGeometry(rotation: Int, hinge: Hinge?, width: Float, height: Float): FoldGeometry {
    val horizontal = rotation == android.view.Surface.ROTATION_90 || rotation == android.view.Surface.ROTATION_270
    val after = rotation == android.view.Surface.ROTATION_90 || rotation == android.view.Surface.ROTATION_180
    val middle = if (horizontal) height / 2f else width / 2f
    val position = hinge?.takeIf { it.vertical != horizontal }?.let { (it.startPx + it.endPx) / 2f } ?: middle
    return FoldGeometry(horizontal, position, after)
}
