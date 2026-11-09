package com.mccal.folio

import android.app.Activity
import android.app.Presentation
import android.os.Bundle
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.platform.ComposeView
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInQuad
import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.isActive
import androidx.compose.foundation.border
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Half-open pose from Jetpack WindowManager: null when flat or closed. */
@Composable
internal fun rememberHalfOpenPose(activity: Activity): FoldingFeature.Orientation? {
    val info by remember(activity) { WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity) }
        .collectAsStateWithLifecycle(initialValue = null)
    val fold = info?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull()
    return fold?.takeIf { it.state == FoldingFeature.State.HALF_OPENED }?.orientation
}

/** How StandBy came on. It decides the layout and what ends it. */
internal enum class StandByWay { HALF_OPEN, CHARGING, TENT }

/** The switches in Settings › Fold & Displays › StandBy. */
internal data class StandByWays(
    val halfOpen: Boolean,
    val charging: Boolean,
    val tent: Boolean,
    val offCharger: Boolean = false,
)

/**
 * What the phone is doing, as far as an app can tell. [hingeHalfway] is the public hinge sensor at its middle step: a
 * Galaxy Z Fold8 gives apps only 0°, 90° and 180° ([HingeTracker]). [onCover] means Folio's window is phone-sized.
 */
internal data class StandBySignals(
    val halfOpen: Boolean = false,
    val plugged: Boolean = false,
    val sideways: Boolean = false,
    val still: Boolean = false,
    val hingeHalfway: Boolean = false,
    val onCover: Boolean = false,
    /** Android's own device state says tent (true) or something else (false); null when the phone does not say, then [hingeHalfway] decides. */
    val tentState: Boolean? = null,
    val batteryLevel: Int? = null,
)

/**
 * Whether StandBy should be on, and why, or null.
 *
 * iPhone's own rule, charging, on its side and still, is the one that works in any pose. On the Fold8 the posture a
 * tent really is (TENT) isn't open to apps, and a narrow tent reads 0° on the hinge, the same as closed (measured on
 * the phone, 1 Oct 2026). So the laptop pose comes from WindowManager on the inner screen, as before. A tent off the
 * charger needs Folio on the cover and the phone to say tent: Android's device state when it reports one (a Fold8 does,
 * [DeviceStateTent]), otherwise the hinge at its middle step. Pass `still = true` to ask whether a way is
 * ready apart from the phone settling: dismissing StandBy holds until that changes, not until the next tap jiggles it.
 */
internal fun standByWay(ways: StandByWays, s: StandBySignals): StandByWay? = when {
    ways.halfOpen && s.halfOpen -> StandByWay.HALF_OPEN
    ways.charging && s.plugged && s.sideways && s.still -> StandByWay.CHARGING
    ways.offCharger && !s.plugged && s.sideways && s.still && (s.batteryLevel == null || s.batteryLevel >= 20) -> StandByWay.CHARGING
    ways.tent && s.onCover && (s.tentState ?: s.hingeHalfway) && s.sideways && s.still -> StandByWay.TENT
    else -> null
}

/** Gravity mostly along the phone's x axis, on its side, and every recent reading close to it, still. */
internal data class MotionState(val sideways: Boolean = false, val still: Boolean = false)

/**
 * On its side and still, from accelerometer readings.
 *
 * Sideways is gravity mostly along the phone's x axis, as nightstand (MIT) tests it, so it holds whether or not the
 * screen turned with the phone, and up to about 60° of lean on a stand. Still is every reading of the last [windowMs]
 * within [tolerance] of their average, which nightstand and StandBy-Android don't check: a phone held sideways on a
 * cable hasn't been set down.
 */
internal class StillnessTracker(private val windowMs: Long = 2_500, private val tolerance: Float = .6f) {
    private val samples = ArrayDeque<FloatArray>()

    fun add(x: Float, y: Float, z: Float, timeMs: Long): MotionState {
        samples.addLast(floatArrayOf(x, y, z, timeMs.toFloat()))
        while (samples.size > 1 && timeMs - samples.first()[3] > windowMs) samples.removeFirst()
        val n = samples.size
        val mx = samples.sumOf { it[0].toDouble() }.toFloat() / n
        val my = samples.sumOf { it[1].toDouble() }.toFloat() / n
        val mz = samples.sumOf { it[2].toDouble() }.toFloat() / n
        val settled = timeMs - samples.first()[3] >= windowMs * .8f && samples.all {
            val dx = it[0] - mx; val dy = it[1] - my; val dz = it[2] - mz
            dx * dx + dy * dy + dz * dz <= tolerance * tolerance
        }
        return MotionState(sideways = kotlin.math.abs(mx) >= SIDEWAYS_GRAVITY && kotlin.math.abs(mx) > 2 * kotlin.math.abs(my), still = settled)
    }
}

/**
 * The signals [standByWay] needs. Motion and the hinge are watched only while Home is started and a way that needs
 * them could begin: a charger in for "charging", the tent switch on with Folio on the cover. The laptop pose needs
 * neither; WindowManager reports it.
 */
@Composable
internal fun rememberStandBySignals(activity: Activity, ways: StandByWays, plugged: Boolean, halfOpen: Boolean, batteryLevel: Int? = null): StandBySignals {
    val onCover = !LocalConfiguration.current.fitsRegularHomeLayout()
    val started = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.STARTED)
    val wantMotion = started && ((ways.charging && plugged) || ways.offCharger || (ways.tent && onCover))
    // The phone's own word for the pose, while a tent could begin. Null until it speaks, or for good when it cannot.
    var tentState by remember { mutableStateOf<Boolean?>(null) }
    val wantTentState = started && ways.tent && onCover
    DisposableEffect(wantTentState) {
        val watcher = if (wantTentState) DeviceStateWatcher(activity) { tentState = it }.also { it.start() } else null
        onDispose { watcher?.stop(); tentState = null }
    }
    // The hinge sensor is only the way back for a phone that does not report the pose.
    val wantHinge = wantTentState && tentState == null
    var motion by remember { mutableStateOf(MotionState()) }
    var hinge by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(wantMotion, wantHinge) {
        val sensors = activity.getSystemService(SensorManager::class.java)
        val tracker = StillnessTracker()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER ->
                        motion = tracker.add(event.values[0], event.values[1], event.values[2], event.timestamp / 1_000_000)
                    Sensor.TYPE_HINGE_ANGLE -> hinge = event.values[0]
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        if (wantMotion) sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        if (wantHinge) sensors?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
            ?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        onDispose { sensors?.unregisterListener(listener); motion = MotionState(); hinge = null }
    }
    return StandBySignals(halfOpen = halfOpen, plugged = plugged, sideways = motion.sideways, still = motion.still,
        hingeHalfway = hinge?.let { it in 30f..150f } == true, onCover = onCover, tentState = tentState, batteryLevel = batteryLevel)
}

/**
 * iPhone-style StandBy for a phone set down half-open: a big clock, date, next alarm, battery and
 * now playing. Dim red at night. Tap anywhere or open the phone flat to leave.
 */
@OptIn(androidx.window.core.ExperimentalWindowApi::class)
@Composable
internal fun StandByOverlay(way: StandByWay?, ready: Boolean, pose: FoldingFeature.Orientation?, blocked: Boolean, status: DeviceStatus,
    onShowing: (Boolean) -> Unit = {}, config: StandByEffectiveConfig = StandByEffectiveConfig.LEGACY_0_6_9) {
    var active by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf<StandByWay?>(null) }
    // Stillness only decides when StandBy comes on. Once up it stays while its way is ready (still plugged in, still
    // sideways, still half-open), so a bump doesn't hide it; and dismissing holds until then too, so the jiggle of the
    // tap that dismissed it can't bring it straight back.
    LaunchedEffect(ready) { if (!ready) { active = false; dismissed = false } }
    LaunchedEffect(way, blocked) {
        if (way == null || blocked || dismissed || active) return@LaunchedEffect
        delay(ENTER_DELAY_MS) // only after the phone has been set down, not while folding through
        shown = way
        active = true
    }
    LaunchedEffect(way, active) {
        if (active && way != null) {
            shown = way
        }
    }
    val view = LocalView.current
    val awake = active && standByHoldsScreen(shown, status.charging)
    DisposableEffect(awake) { view.keepScreenOn = awake; onDispose { view.keepScreenOn = false } }
    // On the cover, StandBy is sideways even when Home doesn't turn (auto-rotate off): ask for landscape while it's up.
    // Android honors that below 600 dp; the inner screen has the fold to lay out by instead.
    val activity = androidx.activity.compose.LocalActivity.current
    val turn = active && shown != StandByWay.HALF_OPEN
    DisposableEffect(turn) {
        val before = activity?.requestedOrientation
        if (turn) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { if (turn && before != null) activity.requestedOrientation = before }
    }
    BackHandler(active) { active = false; dismissed = true }
    // Home keeps everything else off StandBy while it's up (MainActivity: the island, the headphones card, the setup reminder).
    DisposableEffect(active) { onShowing(active); onDispose { if (active) onShowing(false) } }

    val displayManager = remember(activity) { activity?.getSystemService(android.content.Context.DISPLAY_SERVICE) as? DisplayManager }
    val currentDisplayId = activity?.display?.displayId ?: Display.DEFAULT_DISPLAY
    val otherDisplay = remember(displayManager, currentDisplayId, active) {
        if (active) {
            displayManager?.displays?.firstOrNull { it.displayId != currentDisplayId && it.isValid }
        } else null
    }

    val tick by rememberMinuteTick()
    val scheduleNight = isStandByNight(displayNow(tick))
    val roomDark = rememberRoomLightNight(
        enabled = config.activePackage && config.nightMode == StandByNightMode.ROOM_LIGHT,
        initialNight = scheduleNight,
    )
    val ambientNight = resolveStandByNight(config.nightMode, scheduleNight = scheduleNight, roomDark = roomDark)
    val ambientClockInk = if (ambientNight) standByNightPalette(config.nightColor).clock else standByClockColor(config.clockColor)
    val wantAmbient = config.activePackage && config.ambientLamp

    DisposableEffect(otherDisplay, active, ambientClockInk, ambientNight, wantAmbient, config.steadyClock) {
        var presentation: StandByAmbientPresentation? = null
        if (active && wantAmbient && otherDisplay != null && activity != null) {
            try {
                presentation = StandByAmbientPresentation(activity, otherDisplay, ambientClockInk, ambientNight, config.steadyClock).apply { show() }
            } catch (_: Exception) {
                presentation = null
            }
        }
        onDispose {
            try { presentation?.dismiss() } catch (_: Exception) {}
            presentation = null
        }
    }

    val ambientController = remember(activity) {
        if (activity != null) {
            runCatching { androidx.window.area.WindowAreaController.getOrCreate() }.getOrNull()
        } else null
    }

    LaunchedEffect(active, wantAmbient, ambientClockInk, ambientNight, activity, ambientController, config.steadyClock) {
        if (!active || !wantAmbient || activity == null || ambientController == null) return@LaunchedEffect
        try {
            ambientController.windowAreaInfos.collect { infos ->
                val rearInfo = infos.firstOrNull { it.type == androidx.window.area.WindowAreaInfo.Type.TYPE_REAR_FACING }
                val capability = rearInfo?.getCapability(androidx.window.area.WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA)
                if (capability != null && capability.status == androidx.window.area.WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE) {
                    val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
                    ambientController.presentContentOnWindowArea(
                        rearInfo.token,
                        activity,
                        executor,
                        object : androidx.window.area.WindowAreaPresentationSessionCallback {
                            // A window on the rear display has no activity behind it, so the glow brings its own lifecycle
                            // (without one, Compose throws "ViewTreeLifecycleOwner not found" when the view attaches).
                            private var owner: OverlayOwner? = null
                            override fun onSessionStarted(session: androidx.window.area.WindowAreaSessionPresenter) {
                                val glowOwner = OverlayOwner().also { it.start(); owner = it }
                                val composeView = ComposeView(session.context).apply {
                                    setViewTreeLifecycleOwner(glowOwner)
                                    setViewTreeSavedStateRegistryOwner(glowOwner)
                                    setContent {
                                        AmbientScreenGlow(ambientClockInk, ambientNight, config.steadyClock, reduceMotionEnabled(session.context))
                                    }
                                }
                                session.setContentView(composeView)
                            }
                            override fun onSessionEnded(t: Throwable?) { owner?.stop(); owner = null }
                            override fun onContainerVisibilityChanged(isVisible: Boolean) {}
                        }
                    )
                }
            }
        } catch (_: Throwable) {}
    }

    val reduceMotion = LocalReduceMotion.current
    val cfg = LocalConfiguration.current
    val hinge = LocalHinge.current
    val posture = resolveStandByPosture(
        StandByPostureSignals(
            widthDp = cfg.screenWidthDp.toFloat(),
            heightDp = cfg.screenHeightDp.toFloat(),
            classScale = cfg.classScale,
            way = shown ?: way,
            halfOpenVerticalCrease = pose?.let { it == FoldingFeature.Orientation.VERTICAL },
            hingeSpansPx = hinge?.spans.orEmpty(),
        )
    )
    val (dx, dy) = rememberBurnInOffset(tick)
    val enterTransition = if (reduceMotion) androidx.compose.animation.EnterTransition.None else fadeIn(tween(StandBySafetyCaps.ENTER_FADE_MS))
    val exitTransition = if (reduceMotion) androidx.compose.animation.ExitTransition.None else fadeOut(tween(StandBySafetyCaps.EXIT_FADE_MS))

    AnimatedVisibility(active, enter = enterTransition, exit = exitTransition) {
        val dismissModifier = if (config.tapEmptySpaceCloses) {
            Modifier.clickable(remember { MutableInteractionSource() }, null) { active = false; dismissed = true }
        } else {
            Modifier
        }
        Box(Modifier.fillMaxSize().background(Color.Black).then(dismissModifier)) {
            StandByFace(
                status = status,
                posture = posture,
                modifier = Modifier.fillMaxSize().safeDrawingPadding().offset(dx, dy),
                config = config,
                locked = false,
                onDismiss = { active = false; dismissed = true },
            )
            if (!config.tapEmptySpaceCloses) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .safeDrawingPadding()
                        .padding(FolioSpace.MEDIUM.dp)
                        .minimumInteractiveComponentSize()
                        .size(FolioTouch.MIN.dp)
                        .clip(CircleShape)
                        .clickable { active = false; dismissed = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.standby2_close),
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/**
 * Whether StandBy holds the screen on. Only the laptop pose off the charger does, as it always has. On a charger the
 * screen follows its timeout, so the phone locks as usual: with Folio StandBy chosen as the screen saver Android then
 * carries StandBy on over the lock screen, and without it the screen turns off. A tent off the charger follows the
 * timeout too, rather than draining the battery.
 */
internal fun standByHoldsScreen(shown: StandByWay?, charging: Boolean): Boolean = shown == StandByWay.HALF_OPEN && !charging

/**
 * Whether this phone folds, from the hinge sensor Android lists for it: a capability, not a list of models. The laptop
 * and tent ways need a hinge; charging on its side and the screen saver work on any phone.
 */
internal fun hasHinge(context: android.content.Context): Boolean =
    context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_SENSOR_HINGE_ANGLE)

/** The ways Settings offers, in its order: the fold's two only with a hinge, the charger's once its gate is open. */
internal fun standByWaysOffered(hinge: Boolean, chargingOpen: Boolean): List<StandByWay> = buildList {
    if (hinge) add(StandByWay.HALF_OPEN)
    if (chargingOpen) add(StandByWay.CHARGING)
    if (hinge && chargingOpen) add(StandByWay.TENT)
}

/** Red and dim from 10 PM to 6 AM. */
internal fun isStandByNight(now: LocalDateTime): Boolean = now.hour >= 22 || now.hour < 6

internal data class StandByNightPalette(
    val clock: Color,
    val text: Color,
    val soft: Color,
    val card: Color,
)

internal fun standByNightPalette(nightColor: String): StandByNightPalette = when (nightColor) {
    "amber" -> StandByNightPalette(
        clock = Color(0xFFCC7A00L),
        text = Color(0xFFFFB340L),
        soft = Color(0xFFFFA01CL),
        card = Color(0xFF1F1404L),
    )
    "blue" -> StandByNightPalette(
        clock = Color(0xFF2563EBL),
        text = Color(0xFF60A5FAL),
        soft = Color(0xFF3B82F6L),
        card = Color(0xFF050B1FL),
    )
    else -> StandByNightPalette(
        clock = FolioColors.StandByNight,
        text = FolioColors.StandByNightText,
        soft = FolioColors.StandByNightSoft,
        card = FolioColors.StandByNightCard,
    )
}

internal data class StandByColorSwatch(val id: String, val color: Color, val nameRes: Int)

internal val STANDBY_CLOCK_COLORS = listOf(
    StandByColorSwatch("white", FolioColors.StandByWhite, R.string.standby2_color_white),
    StandByColorSwatch("amber", FolioColors.StandByAmber, R.string.standby2_color_amber),
    StandByColorSwatch("cyan", FolioColors.StandByBlue, R.string.standby2_color_blue),
    StandByColorSwatch("coral", FolioColors.StandByCoral, R.string.standby2_color_coral),
    StandByColorSwatch("mint", FolioColors.StandByMint, R.string.standby2_color_mint),
    StandByColorSwatch("purple", FolioColors.StandByPurple, R.string.standby2_color_purple),
)

internal fun standByClockColor(name: String): Color = when (name.lowercase()) {
    "amber" -> FolioColors.StandByAmber
    "cyan" -> FolioColors.StandByBlue
    "coral" -> FolioColors.StandByCoral
    "mint" -> FolioColors.StandByMint
    "purple" -> FolioColors.StandByPurple
    else -> FolioColors.StandByWhite
}

internal val STANDBY_CLOCK_STYLES = listOf(
    StandByClockLook.THIN to R.string.standby2_clock_thin,
    StandByClockLook.BOLD to R.string.standby2_clock_bold,
    StandByClockLook.ANALOG to R.string.standby2_clock_analog,
    StandByClockLook.NUMERALS to R.string.standby2_clock_numerals,
    StandByClockLook.FLIP to R.string.standby2_clock_flip,
    StandByClockLook.WORLD to R.string.standby2_clock_world,
    StandByClockLook.STACK to R.string.standby2_clock_stack,
    StandByClockLook.WORDS to R.string.standby2_clock_words,
    StandByClockLook.NEON to R.string.standby2_clock_neon,
    StandByClockLook.SOLAR to R.string.standby2_clock_solar,
    StandByClockLook.NIXIE to R.string.standby2_clock_nixie,
    StandByClockLook.MINIMAL to R.string.standby2_clock_minimal,
)

/**
 * 120Hz continuous sweeping second hand angle for analog clocks during daytime.
 * Automatically halts when night mode or reduce motion is active to eliminate GPU wakeups.
 */
@Composable
internal fun rememberContinuousSecondAngle(night: Boolean): Float {
    val reduceMotion = LocalReduceMotion.current
    if (night || reduceMotion) return 0f
    var timeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis {
                timeMillis = System.currentTimeMillis()
            }
        }
    }
    val sec = (timeMillis / 1000) % 60
    val millis = timeMillis % 1000
    return (sec + millis / 1000f) * 6f
}

/**
 * Soft 6-second radial greeting glow on docking / activation (SetDown inspired).
 * Smoothly rises over 1.8s and gently fades away over 4.2s.
 */
@Composable
internal fun StandByStartGlow(color: Color, enabled: Boolean) {
    if (!enabled) return
    val reduceMotion = LocalReduceMotion.current
    if (reduceMotion) return
    val glowAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        glowAlpha.animateTo(0.25f, tween(1800, easing = EaseOutQuad))
        glowAlpha.animateTo(0f, tween(4200, easing = EaseInQuad))
    }
    if (glowAlpha.value > 0.001f) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(color.copy(alpha = glowAlpha.value), Color.Transparent),
                            center = center,
                            radius = size.maxDimension * 0.7f,
                        )
                    )
                }
        )
    }
}

/**
 * Burn-in protection offset (`docs/plan-standby-2.md` §0 & §7): a slow 1.5 s glide between minute steps
 * ([StandBySafetyCaps.BURN_IN_GLIDE_MS]), or an instant cut when [LocalReduceMotion] is enabled.
 */
@Composable
internal fun rememberBurnInOffset(tick: Long): Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp> {
    val reduceMotion = LocalReduceMotion.current
    val (rawX, rawY) = burnInShift(tick)
    val spec = if (reduceMotion) snap<androidx.compose.ui.unit.Dp>() else tween(StandBySafetyCaps.BURN_IN_GLIDE_MS)
    val dx by animateDpAsState(targetValue = rawX.dp, animationSpec = spec, label = "burnInX")
    val dy by animateDpAsState(targetValue = rawY.dp, animationSpec = spec, label = "burnInY")
    return dx to dy
}

/**
 * Observes `Sensor.TYPE_LIGHT` via [RoomLightTracker] ONLY while StandBy is visible, [enabled] is true
 * (`config.activePackage && config.nightMode == StandByNightMode.ROOM_LIGHT`), and the device offers a light sensor.
 * Returns `null` when disabled or when no light sensor exists so [resolveStandByNight] falls back cleanly.
 */
@Composable
internal fun rememberRoomLightNight(enabled: Boolean, initialNight: Boolean): Boolean? {
    val context = LocalContext.current
    var roomDark by remember(enabled) { mutableStateOf<Boolean?>(null) }
    DisposableEffect(enabled) {
        if (!enabled) {
            roomDark = null
            return@DisposableEffect onDispose {}
        }
        val sensors = context.getSystemService(SensorManager::class.java)
        val lightSensor = sensors?.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (lightSensor == null) {
            roomDark = null
            return@DisposableEffect onDispose {}
        }
        val tracker = RoomLightTracker(initialNight = initialNight)
        roomDark = tracker.isNight
        // A steady room sends no new reading, so the change that is waiting out its delay is asked again when the delay ends.
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var lastLux = Float.NaN
        val recheck = object : Runnable {
            override fun run() {
                if (lastLux.isNaN()) return
                val now = android.os.SystemClock.elapsedRealtime()
                roomDark = tracker.onReading(lastLux, now)
                tracker.pendingRemainingMs(now)?.let { handler.postDelayed(this, it + 50L) }
            }
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_LIGHT && event.values.isNotEmpty()) {
                    val nowElapsedMs = android.os.SystemClock.elapsedRealtime()
                    lastLux = event.values[0]
                    roomDark = tracker.onReading(lastLux, nowElapsedMs)
                    handler.removeCallbacks(recheck)
                    tracker.pendingRemainingMs(nowElapsedMs)?.let { handler.postDelayed(recheck, it + 50L) }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose {
            sensors.unregisterListener(listener)
            handler.removeCallbacks(recheck)
            roomDark = null
        }
    }
    return roomDark
}

/** The clock and what's next, side by side or [stacked]; the same on Home and in the screen saver. */
@Composable
internal fun StandByFace(
    status: DeviceStatus,
    stacked: Boolean,
    modifier: Modifier = Modifier,
    config: StandByEffectiveConfig = StandByEffectiveConfig.LEGACY_0_6_9,
) {
    StandByFace(
        status = status,
        posture = if (stacked) StandByPosture.PORTRAIT else StandByPosture.LANDSCAPE,
        modifier = modifier,
        config = config,
    )
}

internal class StandByAmbientPresentation(
    context: android.content.Context,
    display: Display,
    private val clockColor: Color,
    private val night: Boolean,
    private val steady: Boolean = true,
) : Presentation(context, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            addFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            )
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
        }
        // A Presentation is a plain Dialog on another display: give the glow and its window a lifecycle of their own.
        val glowOwner = OverlayOwner().also { it.start(); owner = it }
        val composeView = ComposeView(context).apply {
            setContent {
                AmbientScreenGlow(clockColor, night, steady, reduceMotionEnabled(context))
            }
        }
        listOfNotNull(composeView, window?.decorView).forEach {
            it.setViewTreeLifecycleOwner(glowOwner)
            it.setViewTreeSavedStateRegistryOwner(glowOwner)
        }
        setContentView(composeView)
    }

    override fun onStop() {
        owner?.stop(); owner = null
        super.onStop()
    }

    private var owner: OverlayOwner? = null
}

/** True when StandBy keeps its clock and glow still (the default). Set from [StandByEffectiveConfig.steadyClock]. */
internal val LocalStandBySteady = staticCompositionLocalOf { true }

/**
 * The only place a StandBy loop may run (StandBySteadyGuardTest fails on any other `rememberInfiniteTransition`).
 * It returns the still value [rest] unless the "Keep the clock steady" switch is off, it is daytime and Reduce Motion
 * is off; only then does it ease between [from] and [to].
 */
@Composable
internal fun standByPulse(
    from: Float, to: Float, rest: Float, durationMs: Int, night: Boolean, label: String,
    easing: Easing = LinearEasing,
    steady: Boolean = LocalStandBySteady.current,
    reduceMotion: Boolean = LocalReduceMotion.current,
): State<Float> {
    if (steady || night || reduceMotion) return remember(rest) { mutableFloatStateOf(rest) }
    return rememberInfiniteTransition(label = label).animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(animation = tween(durationMs, easing = easing), repeatMode = RepeatMode.Reverse),
        label = label,
    )
}

@Composable
private fun AmbientScreenGlow(accentColor: Color, night: Boolean, steady: Boolean = true, reduceMotion: Boolean = false) {
    val breath by standByPulse(0.45f, 0.85f, rest = 0.65f, durationMs = 4000, night = night, label = "glow_intensity",
        easing = FastOutSlowInEasing, steady = steady, reduceMotion = reduceMotion)

    val glowColor = if (night) FolioColors.StandByCrimson else accentColor

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = maxOf(size.width, size.height) * 0.75f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = if (night) 0.35f * breath else 0.45f * breath),
                        glowColor.copy(alpha = if (night) 0.12f * breath else 0.18f * breath),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius
                ),
                center = center,
                radius = maxRadius
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.NightsStay,
                contentDescription = null,
                tint = (if (night) FolioColors.StandByNightSoft else accentColor).copy(alpha = 0.4f * breath),
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = stringResource(R.string.standby2_ambient_lamp),
                color = (if (night) FolioColors.StandByNightSoft else Color.White).copy(alpha = 0.3f * breath),
                fontSize = FolioType.GROUP_LABEL.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )
        }
    }
}

/**
 * The 5-posture StandBy face (`BOOK`, `TABLETOP`, `TENT`, `LANDSCAPE`, `PORTRAIT`), shared between Home's
 * [StandByOverlay] and Android's [StandByScreenSaver].
 *
 * - `TABLETOP` (half-open horizontal crease): upper vertical half holds [BigClock] and [StandByStatusChips]
 *   (`FoldRole.INFO`, away from fingers); lower flat half holds [StandByCards] (`FoldRole.CONTROLS`, `ADP-13`).
 * - `BOOK`, `TENT`, `LANDSCAPE`: left half holds [BigClock], right half holds [StandByInfo] (with crease clearance on
 *   `BOOK`).
 * - `PORTRAIT`: top half holds [BigClock], bottom half holds [StandByInfo].
 */
@Composable
internal fun StandByFace(
    status: DeviceStatus,
    posture: StandByPosture,
    modifier: Modifier = Modifier,
    config: StandByEffectiveConfig = StandByEffectiveConfig.LEGACY_0_6_9,
    locked: Boolean = false,
    onDismiss: (() -> Unit)? = null,
) {
    CompositionLocalProvider(LocalStandBySteady provides config.steadyClock) {
        StandByFaceContent(status, posture, modifier, config, locked, onDismiss)
    }
}

@Composable
private fun StandByFaceContent(
    status: DeviceStatus,
    posture: StandByPosture,
    modifier: Modifier,
    config: StandByEffectiveConfig,
    locked: Boolean,
    onDismiss: (() -> Unit)?,
) {
    val context = LocalContext.current
    val tick by rememberMinuteTick()
    val now = displayNow(tick)
    val scheduleNight = isStandByNight(now)
    val roomDark = rememberRoomLightNight(
        enabled = config.activePackage && config.nightMode == StandByNightMode.ROOM_LIGHT,
        initialNight = scheduleNight,
    )
    val night = resolveStandByNight(config.nightMode, scheduleNight = scheduleNight, roomDark = roomDark)
    val palette = standByNightPalette(config.nightColor)

    var editingClock by remember { mutableStateOf(false) }
    var activeLook by remember(config.clockLook) { mutableStateOf(config.clockLook) }
    var activeColorName by remember(config.clockColor) { mutableStateOf(config.clockColor) }

    // At night the big clock stays a dim color, which is enough at its size; the smaller text uses brighter hues that
    // reach the 4.5:1 small text needs (it was 2.2:1), so the date, chips and cards stay readable in the dark.
    val baseClockColor = if (night) palette.clock else standByClockColor(activeColorName)
    val ink = if (night) palette.text else Color.White
    val soft = if (night) palette.soft else Color.White.copy(alpha = .6f)
    val creasePadding = if (posture.hasCrease) FolioSpace.LARGE.dp else 0.dp

    val alarm = remember(tick) { UpNext.nextAlarm(context) }
    val nowMs = System.currentTimeMillis()
    val sunriseProgress = if (config.sunriseBeforeAlarm && alarm != null) {
        val diffMs = alarm - nowMs
        if (diffMs in 0..(15 * 60 * 1000L)) {
            1f - (diffMs.toFloat() / (15 * 60 * 1000f))
        } else 0f
    } else 0f

    val clockInk = if (sunriseProgress > 0f) androidx.compose.ui.graphics.lerp(baseClockColor, FolioColors.StandBySunrise, sunriseProgress) else baseClockColor

    if (editingClock) {
        BackHandler { editingClock = false }
    }

    // Night brightness 0 means "black until you touch it" (docs/plan-standby-2.md §3 & §7).
    var touchWakeTrigger by remember { mutableIntStateOf(0) }
    var touchAwake by remember { mutableStateOf(false) }
    LaunchedEffect(touchWakeTrigger, night, config.nightBrightnessPercent) {
        if (touchWakeTrigger > 0 && night && config.activePackage && config.nightBrightnessPercent == 0) {
            touchAwake = true
            delay(TOUCH_WAKE_HOLD_MS)
            touchAwake = false
        } else {
            touchAwake = false
        }
    }
    val effectiveNightPct = when {
        night && config.activePackage && config.nightBrightnessPercent == 0 && touchAwake -> 60
        night && config.activePackage && sunriseProgress > 0f -> maxOf(config.nightBrightnessPercent, (sunriseProgress * 70).toInt())
        else -> config.nightBrightnessPercent
    }
    val nightDimAlpha = if (night && config.activePackage) (effectiveNightPct / 100f).coerceIn(0f, 1f) else 1f
    val touchWakeModifier = if (night && config.activePackage && config.nightBrightnessPercent == 0) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                touchWakeTrigger++
            }
        }
    } else {
        Modifier
    }
    val dimmedModifier = modifier.then(touchWakeModifier).graphicsLayer { alpha = nightDimAlpha }

    val sessionStartMs = remember { System.currentTimeMillis() }
    val lockScreenShowNotifs = remember(locked, tick) {
        if (!locked) true
        else runCatching { android.provider.Settings.Secure.getInt(context.contentResolver, "lock_screen_show_notifications", 1) != 0 }.getOrDefault(true)
    }
    val wantNotifs = config.activePackage && config.notifMode != StandByNotifMode.OFF && (!locked || lockScreenShowNotifs)
    val rawNotifs = if (wantNotifs) IslandListenerService.notifications.collectAsState().value else emptyList()
    val notifSummary = remember(rawNotifs, sessionStartMs, config.notifMode, locked, lockScreenShowNotifs) {
        if (!wantNotifs || rawNotifs.isEmpty()) {
            StandByNotifSummary()
        } else {
            val needColors = config.notifMode == StandByNotifMode.COLORS
            val candidates = rawNotifs.mapNotNull { item ->
                if (item.postTime < sessionStartMs || item.ongoing) return@mapNotNull null
                if (item.importance != IMPORTANCE_UNKNOWN && item.importance < 3) return@mapNotNull null
                if (!item.canShowBadge || (locked && item.visibility == -1)) return@mapNotNull null
                val resolvedColor = if (!needColors) null else item.color ?: item.icon?.let { bmp ->
                    runCatching {
                        val w = bmp.width.coerceAtLeast(1)
                        val h = bmp.height.coerceAtLeast(1)
                        val step = maxOf(1, minOf(w, h) / 8)
                        val samples = mutableListOf<Int>()
                        var y = 0
                        while (y < h) {
                            var x = 0
                            while (x < w) {
                                samples.add(bmp.getPixel(x, y))
                                x += step
                            }
                            y += step
                        }
                        sampleStandByIconColor(samples.toIntArray())
                    }.getOrNull()
                }
                StandByNotifCandidate(
                    key = item.key,
                    packageName = item.packageName,
                    postTime = item.postTime,
                    color = resolvedColor,
                    ongoing = item.ongoing,
                    importance = item.importance,
                    canShowBadge = item.canShowBadge,
                    visibility = item.visibility,
                    silenced = item.silenced,
                )
            }
            resolveStandByNotifications(
                items = candidates,
                sessionStartMs = sessionStartMs,
                mode = config.notifMode,
                locked = locked,
                lockScreenShowNotifs = lockScreenShowNotifs,
            )
        }
    }

    val cardBg = if (night) palette.card else FolioColors.SecondaryBackground

    Box(Modifier.fillMaxSize()) {
        if (sunriseProgress > 0f) {
            StandBySunriseOverlay(sunriseProgress)
        }
        if (!night) {
            StandByStartGlow(color = clockInk, enabled = config.activePackage)
        }
        when (posture) {
            StandByPosture.TABLETOP -> Column(dimmedModifier) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(bottom = creasePadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BigClock(
                        now = now,
                        ink = clockInk,
                        soft = soft,
                        clockLook = activeLook,
                        night = night,
                        cardBg = cardBg,
                        modifier = Modifier.fillMaxWidth(),
                        isEditing = editingClock,
                        onEnterEdit = {
                            if (!night && config.activePackage) {
                                editingClock = true
                            }
                        },
                        onLookChanged = { newLook ->
                            activeLook = newLook
                            FolioSettingsBridge.setTweakOption(context, STANDBY2_ID, "clock", newLook.name.lowercase())
                        },
                        onColorChanged = { newColor ->
                            activeColorName = newColor
                            FolioSettingsBridge.setTweakOption(context, STANDBY2_ID, "clockColor", newColor)
                        },
                        activeColorName = activeColorName,
                        onDone = { editingClock = false },
                    )
                    if (!editingClock) {
                        Spacer(Modifier.height(FolioSpace.MEDIUM.dp))
                        StandByStatusChips(status, ink, soft, config.showChargingLine)
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = creasePadding)
                        .padding(horizontal = FolioSpace.XXL.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    StandByCards(now, status, ink, soft, cardBg, night, config, notifSummary, locked, lockScreenShowNotifs, onDismiss)
                }
            }
            StandByPosture.BOOK, StandByPosture.TENT, StandByPosture.LANDSCAPE -> Row(dimmedModifier) {
                BigClock(
                    now = now,
                    ink = clockInk,
                    soft = soft,
                    clockLook = activeLook,
                    night = night,
                    cardBg = cardBg,
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(end = creasePadding),
                    isEditing = editingClock,
                    onEnterEdit = {
                        if (!night && config.activePackage) {
                            editingClock = true
                        }
                    },
                    onLookChanged = { newLook ->
                        activeLook = newLook
                        FolioSettingsBridge.setTweakOption(context, STANDBY2_ID, "clock", newLook.name.lowercase())
                    },
                    onColorChanged = { newColor ->
                        activeColorName = newColor
                        FolioSettingsBridge.setTweakOption(context, STANDBY2_ID, "clockColor", newColor)
                    },
                    activeColorName = activeColorName,
                    onDone = { editingClock = false },
                )
                StandByInfo(now, status, ink, soft, cardBg, night, config, notifSummary, locked, lockScreenShowNotifs, onDismiss, Modifier.weight(1f).fillMaxHeight().padding(start = creasePadding))
            }
            StandByPosture.PORTRAIT -> Column(dimmedModifier) {
                BigClock(
                    now = now,
                    ink = clockInk,
                    soft = soft,
                    clockLook = activeLook,
                    night = night,
                    cardBg = cardBg,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    isEditing = editingClock,
                    onEnterEdit = {
                        if (!night && config.activePackage) {
                            editingClock = true
                        }
                    },
                    onLookChanged = { newLook ->
                        activeLook = newLook
                        FolioSettingsBridge.setTweakOption(context, STANDBY2_ID, "clock", newLook.name.lowercase())
                    },
                    onColorChanged = { newColor ->
                        activeColorName = newColor
                        FolioSettingsBridge.setTweakOption(context, STANDBY2_ID, "clockColor", newColor)
                    },
                    activeColorName = activeColorName,
                    onDone = { editingClock = false },
                )
                StandByInfo(now, status, ink, soft, cardBg, night, config, notifSummary, locked, lockScreenShowNotifs, onDismiss, Modifier.weight(1f).fillMaxWidth())
            }
        }
        StandByEdgeGlow(
            summary = notifSummary,
            mode = config.notifMode,
            night = night,
        )
    }
}

@Composable
private fun BigClock(
    now: LocalDateTime,
    ink: Color,
    soft: Color,
    clockLook: StandByClockLook,
    night: Boolean,
    cardBg: Color,
    modifier: Modifier,
    isEditing: Boolean = false,
    onEnterEdit: (() -> Unit)? = null,
    onLookChanged: ((StandByClockLook) -> Unit)? = null,
    onColorChanged: ((String) -> Unit)? = null,
    activeColorName: String = "white",
    onDone: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isEditing) 0.88f else 1f,
        animationSpec = FolioMotion.spring(FolioMotion.Bounce),
        label = "editScale"
    )

    val editGestureModifier = if (!isEditing && onEnterEdit != null && !night) {
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEnterEdit()
                }
            )
        }
    } else if (isEditing && onLookChanged != null) {
        Modifier.pointerInput(Unit) {
            var totalDrag = 0f
            detectVerticalDragGestures(
                onDragStart = { totalDrag = 0f },
                onDragEnd = {},
                onDragCancel = {},
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    totalDrag += dragAmount
                    if (totalDrag < -60f) {
                        totalDrag = 0f
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val idx = STANDBY_CLOCK_STYLES.indexOfFirst { it.first == clockLook }
                        val nextIdx = if (idx >= 0) (idx + 1) % STANDBY_CLOCK_STYLES.size else 0
                        onLookChanged(STANDBY_CLOCK_STYLES[nextIdx].first)
                    } else if (totalDrag > 60f) {
                        totalDrag = 0f
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val idx = STANDBY_CLOCK_STYLES.indexOfFirst { it.first == clockLook }
                        val prevIdx = if (idx >= 0) (idx - 1 + STANDBY_CLOCK_STYLES.size) % STANDBY_CLOCK_STYLES.size else 0
                        onLookChanged(STANDBY_CLOCK_STYLES[prevIdx].first)
                    }
                }
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier.then(editGestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedVisibility(
                visible = isEditing,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(200)),
            ) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(FolioRadius.GROUPED_CARD.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val idx = STANDBY_CLOCK_STYLES.indexOfFirst { it.first == clockLook }
                                val prevIdx = if (idx >= 0) (idx - 1 + STANDBY_CLOCK_STYLES.size) % STANDBY_CLOCK_STYLES.size else 0
                                onLookChanged?.invoke(STANDBY_CLOCK_STYLES[prevIdx].first)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.ChevronLeft,
                            contentDescription = stringResource(R.string.standby2_previous_style),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    val styleLabelRes = STANDBY_CLOCK_STYLES.firstOrNull { it.first == clockLook }?.second ?: R.string.standby2_clock_thin
                    Text(
                        stringResource(styleLabelRes),
                        color = Color.White,
                        fontSize = FolioType.COMPACT.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val idx = STANDBY_CLOCK_STYLES.indexOfFirst { it.first == clockLook }
                                val nextIdx = if (idx >= 0) (idx + 1) % STANDBY_CLOCK_STYLES.size else 0
                                onLookChanged?.invoke(STANDBY_CLOCK_STYLES[nextIdx].first)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = stringResource(R.string.standby2_next_style),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .then(
                        if (isEditing) {
                            Modifier
                                .clip(RoundedCornerShape(FolioRadius.PANEL.dp))
                                .border(1.5.dp, ink.copy(alpha = 0.4f), RoundedCornerShape(FolioRadius.PANEL.dp))
                                .background(cardBg.copy(alpha = 0.5f))
                                .padding(16.dp)
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (clockLook) {
                    StandByClockLook.ANALOG -> AnalogClock(now, ink, soft, night, Modifier)
                    StandByClockLook.NUMERALS -> NumeralsClock(now, ink, soft, night, Modifier)
                    StandByClockLook.FLIP -> FlipClock(now, ink, soft, night, cardBg, Modifier)
                    StandByClockLook.WORLD -> WorldClock(now, ink, soft, night, Modifier)
                    StandByClockLook.STACK -> StackClock(now, ink, soft, Modifier)
                    StandByClockLook.WORDS -> WordsClock(now, ink, soft, Modifier)
                    StandByClockLook.NEON -> NeonClock(now, ink, soft, night, cardBg, Modifier)
                    StandByClockLook.SOLAR -> SolarClock(now, ink, soft, night, Modifier)
                    StandByClockLook.NIXIE -> NixieClock(now, ink, soft, night, cardBg, Modifier)
                    StandByClockLook.MINIMAL -> MinimalClock(now, ink, soft, night, Modifier)
                    StandByClockLook.BOLD, StandByClockLook.THIN -> {
                        val context = LocalContext.current
                        val is24 = android.text.format.DateFormat.is24HourFormat(context)
                        val hourVal = if (is24) now.hour else (if (now.hour % 12 == 0) 12 else now.hour % 12)
                        val hrStr = if (is24) String.format("%02d", hourVal) else hourVal.toString()
                        val mnStr = String.format("%02d", now.minute)
                        val weight = if (clockLook == StandByClockLook.BOLD) FontWeight.Bold else FontWeight.Thin
                        val clockSp = (if (clockLook == StandByClockLook.BOLD) 104 else 116).sp

                        val colonAlpha by standByPulse(0.35f, 1f, rest = 1f, durationMs = 1000, night = night, label = "colonAlpha")

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    hrStr,
                                    color = ink,
                                    fontSize = clockSp,
                                    fontWeight = weight,
                                    lineHeight = 120.sp,
                                    style = TextStyle(fontFeatureSettings = "tnum")
                                )
                                Text(
                                    ":",
                                    color = ink.copy(alpha = colonAlpha),
                                    fontSize = clockSp,
                                    fontWeight = weight,
                                    lineHeight = 120.sp,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                Text(
                                    mnStr,
                                    color = ink,
                                    fontSize = clockSp,
                                    fontWeight = weight,
                                    lineHeight = 120.sp,
                                    style = TextStyle(fontFeatureSettings = "tnum")
                                )
                            }
                            Text(
                                now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))),
                                color = soft,
                                fontSize = FolioType.TITLE3.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isEditing,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(200)),
            ) {
                Column(
                    modifier = Modifier.padding(top = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        STANDBY_CLOCK_COLORS.forEach { swatch ->
                            val isSelected = swatch.id.equals(activeColorName, ignoreCase = true)
                            val dotScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.25f else 1.0f,
                                label = "swatchScale"
                            )
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onColorChanged?.invoke(swatch.id)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        Modifier
                                            .size(34.dp)
                                            .border(2.dp, Color.White, CircleShape)
                                    )
                                }
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .graphicsLayer { scaleX = dotScale; scaleY = dotScale }
                                        .background(swatch.color, CircleShape)
                                )
                            }
                        }
                    }

                    Text(
                        stringResource(R.string.standby2_swipe_to_change),
                        color = soft.copy(alpha = 0.7f),
                        fontSize = FolioType.GROUP_LABEL.sp,
                        fontWeight = FontWeight.Normal
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(FolioRadius.GROUPED_CARD.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDone?.invoke()
                            }
                            .padding(horizontal = 24.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.standby2_done),
                            color = Color.White,
                            fontSize = FolioType.COMPACT.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalogClock(now: LocalDateTime, ink: Color, soft: Color, night: Boolean, modifier: Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val secAngle = if (!night && !reduceMotion) rememberContinuousSecondAngle(night) else 0f
    val min = now.minute
    val hour = now.hour % 12
    val minAngle = (min + secAngle / 360f) * 6f
    val hourAngle = (hour + minAngle / 360f) * 30f

    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(180.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 6f

            // Outer dial ring
            drawCircle(
                color = soft.copy(alpha = 0.25f),
                radius = radius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // Hour ticks
            for (i in 0 until 12) {
                val angleRad = Math.toRadians((i * 30.0) - 90.0)
                val isMajor = i % 3 == 0
                val tickLen = if (isMajor) 14.dp.toPx() else 8.dp.toPx()
                val tickWidth = if (isMajor) 3.dp.toPx() else 1.5.dp.toPx()
                val startX = (center.x + (radius - tickLen) * Math.cos(angleRad)).toFloat()
                val startY = (center.y + (radius - tickLen) * Math.sin(angleRad)).toFloat()
                val endX = (center.x + (radius - 2.dp.toPx()) * Math.cos(angleRad)).toFloat()
                val endY = (center.y + (radius - 2.dp.toPx()) * Math.sin(angleRad)).toFloat()
                drawLine(
                    color = if (isMajor) ink else soft.copy(alpha = 0.6f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = tickWidth,
                    cap = StrokeCap.Round
                )
            }

            // Hour hand
            val hrRad = Math.toRadians(hourAngle.toDouble() - 90.0)
            val hrLen = radius * 0.52f
            drawLine(
                color = ink,
                start = center - Offset((10.dp.toPx() * Math.cos(hrRad)).toFloat(), (10.dp.toPx() * Math.sin(hrRad)).toFloat()),
                end = center + Offset((hrLen * Math.cos(hrRad)).toFloat(), (hrLen * Math.sin(hrRad)).toFloat()),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Minute hand
            val minRad = Math.toRadians(minAngle.toDouble() - 90.0)
            val minLen = radius * 0.78f
            drawLine(
                color = ink,
                start = center - Offset((12.dp.toPx() * Math.cos(minRad)).toFloat(), (12.dp.toPx() * Math.sin(minRad)).toFloat()),
                end = center + Offset((minLen * Math.cos(minRad)).toFloat(), (minLen * Math.sin(minRad)).toFloat()),
                strokeWidth = 3.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Second hand (daytime only)
            if (!night && !reduceMotion) {
                val secRad = Math.toRadians(secAngle.toDouble() - 90.0)
                val secLen = radius * 0.85f
                drawLine(
                    color = soft,
                    start = center - Offset((14.dp.toPx() * Math.cos(secRad)).toFloat(), (14.dp.toPx() * Math.sin(secRad)).toFloat()),
                    end = center + Offset((secLen * Math.cos(secRad)).toFloat(), (secLen * Math.sin(secRad)).toFloat()),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Center pivot
            drawCircle(color = ink, radius = 5.dp.toPx(), center = center)
            drawCircle(color = soft, radius = 2.5.dp.toPx(), center = center)
        }
        Spacer(Modifier.height(FolioSpace.MEDIUM.dp))
        Text(now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))), color = soft, fontSize = FolioType.TITLE3.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FlipCard(text: String, ink: Color, night: Boolean, cardBg: Color) {
    val reduceMotion = LocalReduceMotion.current
    var prevText by remember { mutableStateOf(text) }
    var currentText by remember { mutableStateOf(text) }
    val flipProgress = remember { Animatable(1f) }

    LaunchedEffect(text) {
        if (text != currentText) {
            prevText = currentText
            currentText = text
            if (!reduceMotion && !night) {
                flipProgress.snapTo(0f)
                flipProgress.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
            } else {
                flipProgress.snapTo(1f)
            }
        }
    }

    val progress = flipProgress.value
    val isFlipping = progress < 1f
    val angle = progress * 180f
    val bgColor = if (night) cardBg else FolioColors.StandByCardDark
    val cardWidth = 100.dp
    val cardHeight = 110.dp
    val halfHeight = 53.dp
    val flipDigitSize = 68.sp
    val flipCorner = FolioRadius.GROUP.dp

    Box(
        Modifier.size(cardWidth, cardHeight),
        contentAlignment = Alignment.Center
    ) {
        // Upper background half: displays the NEW text top half
        Box(
            Modifier
                .size(cardWidth, halfHeight)
                .align(Alignment.TopCenter)
                .clip(RoundedCornerShape(topStart = flipCorner, topEnd = flipCorner))
                .background(bgColor),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                currentText,
                color = ink,
                fontSize = flipDigitSize,
                fontWeight = FontWeight.Bold,
                style = TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.offset(y = 27.dp)
            )
        }

        // Lower background half: displays the OLD text bottom half (while flipping) or NEW (when finished)
        Box(
            Modifier
                .size(cardWidth, halfHeight)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(bottomStart = flipCorner, bottomEnd = flipCorner))
                .background(bgColor),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                if (isFlipping) prevText else currentText,
                color = ink,
                fontSize = flipDigitSize,
                fontWeight = FontWeight.Bold,
                style = TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.offset(y = (-27).dp)
            )
        }

        // The flipping flap
        if (isFlipping) {
            if (angle < 90f) {
                // Top flap folding forward/down showing OLD text top half
                Box(
                    Modifier
                        .size(cardWidth, halfHeight)
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            rotationX = -angle
                            cameraDistance = 16f * density
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        }
                        .clip(RoundedCornerShape(topStart = flipCorner, topEnd = flipCorner))
                        .background(bgColor),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        prevText,
                        color = ink,
                        fontSize = flipDigitSize,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        modifier = Modifier.offset(y = 27.dp)
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = (angle / 90f) * 0.4f))
                    )
                }
            } else {
                // Bottom flap falling into place showing NEW text bottom half
                Box(
                    Modifier
                        .size(cardWidth, halfHeight)
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            rotationX = 180f - angle
                            cameraDistance = 16f * density
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                        .clip(RoundedCornerShape(bottomStart = flipCorner, bottomEnd = flipCorner))
                        .background(bgColor),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        currentText,
                        color = ink,
                        fontSize = flipDigitSize,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        modifier = Modifier.offset(y = (-27).dp)
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = ((180f - angle) / 90f) * 0.4f))
                    )
                }
            }
        }

        // Center split crease line
        Box(
            Modifier
                .size(cardWidth, 2.dp)
                .align(Alignment.Center)
                .background(Color.Black.copy(alpha = 0.75f))
        )
    }
}

@Composable
private fun FlipClock(now: LocalDateTime, ink: Color, soft: Color, night: Boolean, cardBg: Color, modifier: Modifier) {
    val context = LocalContext.current
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val hourVal = if (is24) now.hour else (if (now.hour % 12 == 0) 12 else now.hour % 12)
    val hr = String.format("%02d", hourVal)
    val mn = String.format("%02d", now.minute)

    val colonAlpha by standByPulse(0.35f, 1f, rest = 1f, durationMs = 1000, night = night, label = "flipColonAlpha")

    val flipColonSize = 44.sp

    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FlipCard(hr, ink, night, cardBg)
            Text(":", color = soft.copy(alpha = colonAlpha * 0.7f), fontSize = flipColonSize, fontWeight = FontWeight.Bold)
            FlipCard(mn, ink, night, cardBg)
        }
        Spacer(Modifier.height(FolioSpace.MEDIUM.dp))
        Text(now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))), color = soft, fontSize = FolioType.TITLE3.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WorldClock(now: LocalDateTime, ink: Color, soft: Color, night: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val pattern = if (is24) "HH:mm" else "h:mm"
    val localZone = ZoneId.systemDefault()
    val localInstant = now.atZone(localZone).toInstant()
    val utcTime = localInstant.atZone(ZoneId.of("UTC")).toLocalDateTime()
    val tokyoTime = localInstant.atZone(ZoneId.of("Asia/Tokyo")).toLocalDateTime()
    val worldDigitSize = 76.sp

    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            now.format(DateTimeFormatter.ofPattern(pattern)),
            color = ink,
            fontSize = worldDigitSize,
            fontWeight = FontWeight.Medium,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
        val amPmPrefix = if (!is24) now.format(DateTimeFormatter.ofPattern("a")) + " · " else ""
        Text(
            amPmPrefix + "${localZone.id.substringAfterLast('/')} · " + now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))),
            color = soft,
            fontSize = FolioType.BODY.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(FolioSpace.COMFY.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(FolioRadius.GROUP.dp))
                .background(if (night) soft.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.standby2_utc), color = soft, fontSize = FolioType.GROUP_LABEL.sp, fontWeight = FontWeight.SemiBold)
                Text(utcTime.format(DateTimeFormatter.ofPattern("HH:mm")), color = ink, fontSize = FolioType.CALLOUT.sp, fontWeight = FontWeight.Bold, style = TextStyle(fontFeatureSettings = "tnum"))
            }
            Box(Modifier.width(1.dp).height(24.dp).background(soft.copy(alpha = 0.3f)))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.standby2_tokyo), color = soft, fontSize = FolioType.GROUP_LABEL.sp, fontWeight = FontWeight.SemiBold)
                Text(tokyoTime.format(DateTimeFormatter.ofPattern("HH:mm")), color = ink, fontSize = FolioType.CALLOUT.sp, fontWeight = FontWeight.Bold, style = TextStyle(fontFeatureSettings = "tnum"))
            }
        }
    }
}

@Composable
private fun StandBySunriseOverlay(progress: Float) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val glowAlpha = (clamped * 0.45f).coerceIn(0f, 0.45f)
                val centerOffset = Offset(size.width / 2f, size.height * 0.85f)
                val maxDim = maxOf(size.width, size.height)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            FolioColors.StandBySolarSun.copy(alpha = glowAlpha),
                            FolioColors.StandBySolarArc.copy(alpha = glowAlpha * 0.7f),
                            FolioColors.StandBySolarGlow.copy(alpha = glowAlpha * 0.35f),
                            Color.Transparent,
                        ),
                        center = centerOffset,
                        radius = maxDim * 0.85f,
                    )
                )
            }
    )
}

@Composable
private fun NumeralsClock(now: LocalDateTime, ink: Color, soft: Color, night: Boolean, modifier: Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val secAngle = if (!night && !reduceMotion) rememberContinuousSecondAngle(night) else 0f
    val min = now.minute
    val hour = now.hour % 12
    val minAngle = (min + secAngle / 360f) * 6f
    val hourAngle = (hour + minAngle / 360f) * 30f
    val textMeasurer = rememberTextMeasurer()

    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(190.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 6.dp.toPx()

            // Outer dial ring
            drawCircle(
                color = soft.copy(alpha = 0.25f),
                radius = radius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // Hour ticks and numerals
            val numeralRadius = radius - 18.dp.toPx()
            val cardinals = mapOf(0 to "12", 3 to "3", 6 to "6", 9 to "9")

            for (i in 0 until 12) {
                val angleRad = Math.toRadians((i * 30.0) - 90.0)
                val isCardinal = cardinals.containsKey(i)
                if (isCardinal) {
                    val numStr = cardinals[i]!!
                    val textLayout = textMeasurer.measure(
                        text = numStr,
                        style = TextStyle(
                            color = ink,
                            fontSize = FolioType.SUBHEAD.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    val numX = (center.x + numeralRadius * Math.cos(angleRad)).toFloat() - (textLayout.size.width / 2f)
                    val numY = (center.y + numeralRadius * Math.sin(angleRad)).toFloat() - (textLayout.size.height / 2f)
                    drawText(textLayout, topLeft = Offset(numX, numY))
                } else {
                    val tickLen = 6.dp.toPx()
                    val startX = (center.x + (radius - tickLen) * Math.cos(angleRad)).toFloat()
                    val startY = (center.y + (radius - tickLen) * Math.sin(angleRad)).toFloat()
                    val endX = (center.x + (radius - 2.dp.toPx()) * Math.cos(angleRad)).toFloat()
                    val endY = (center.y + (radius - 2.dp.toPx()) * Math.sin(angleRad)).toFloat()
                    drawLine(
                        color = soft.copy(alpha = 0.5f),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Hour hand
            val hrRad = Math.toRadians(hourAngle.toDouble() - 90.0)
            val hrLen = radius * 0.48f
            drawLine(
                color = ink,
                start = center - Offset((10.dp.toPx() * Math.cos(hrRad)).toFloat(), (10.dp.toPx() * Math.sin(hrRad)).toFloat()),
                end = center + Offset((hrLen * Math.cos(hrRad)).toFloat(), (hrLen * Math.sin(hrRad)).toFloat()),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Minute hand
            val minRad = Math.toRadians(minAngle.toDouble() - 90.0)
            val minLen = radius * 0.72f
            drawLine(
                color = ink,
                start = center - Offset((12.dp.toPx() * Math.cos(minRad)).toFloat(), (12.dp.toPx() * Math.sin(minRad)).toFloat()),
                end = center + Offset((minLen * Math.cos(minRad)).toFloat(), (minLen * Math.sin(minRad)).toFloat()),
                strokeWidth = 3.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Second hand (daytime only)
            if (!night && !reduceMotion) {
                val secRad = Math.toRadians(secAngle.toDouble() - 90.0)
                val secLen = radius * 0.80f
                drawLine(
                    color = soft,
                    start = center - Offset((14.dp.toPx() * Math.cos(secRad)).toFloat(), (14.dp.toPx() * Math.sin(secRad)).toFloat()),
                    end = center + Offset((secLen * Math.cos(secRad)).toFloat(), (secLen * Math.sin(secRad)).toFloat()),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Center pivot
            drawCircle(color = ink, radius = 5.dp.toPx(), center = center)
            drawCircle(color = soft, radius = 2.5.dp.toPx(), center = center)
        }
        Spacer(Modifier.height(FolioSpace.MEDIUM.dp))
        Text(now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))), color = soft, fontSize = FolioType.TITLE3.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StackClock(now: LocalDateTime, ink: Color, soft: Color, modifier: Modifier) {
    val context = LocalContext.current
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val hourVal = if (is24) now.hour else (if (now.hour % 12 == 0) 12 else now.hour % 12)
    val hr = String.format("%02d", hourVal)
    val mn = String.format("%02d", now.minute)

    val numFs = 92.sp
    val dateFs = 18.sp

    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            hr,
            color = ink,
            fontSize = numFs,
            fontWeight = FontWeight.Bold,
            lineHeight = 84.sp,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
        Text(
            mn,
            color = ink,
            fontSize = numFs,
            fontWeight = FontWeight.Bold,
            lineHeight = 84.sp,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
        Spacer(Modifier.height(FolioSpace.SMALL.dp))
        Text(
            now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))),
            color = soft,
            fontSize = dateFs,
            fontWeight = FontWeight.Medium
        )
    }
}

internal fun formatTimeWords(hour: Int, minute: Int): Pair<String, String> {
    val hourWords = arrayOf(
        "TWELVE", "ONE", "TWO", "THREE", "FOUR", "FIVE",
        "SIX", "SEVEN", "EIGHT", "NINE", "TEN", "ELEVEN", "TWELVE"
    )
    val h = if (hour % 12 == 0) 12 else hour % 12
    val nextH = if ((hour + 1) % 12 == 0) 12 else (hour + 1) % 12
    val currentHourName = hourWords[h]
    val nextHourName = hourWords[nextH]

    return when (minute) {
        0 -> Pair(currentHourName, "O'CLOCK")
        15 -> Pair("QUARTER PAST", currentHourName)
        30 -> Pair("HALF PAST", currentHourName)
        45 -> Pair("QUARTER TO", nextHourName)
        in 1..29 -> Pair("${minuteToWords(minute)} PAST", currentHourName)
        else -> Pair("${minuteToWords(60 - minute)} TO", nextHourName)
    }
}

private fun minuteToWords(min: Int): String = when (min) {
    1 -> "ONE"
    2 -> "TWO"
    3 -> "THREE"
    4 -> "FOUR"
    5 -> "FIVE"
    6 -> "SIX"
    7 -> "SEVEN"
    8 -> "EIGHT"
    9 -> "NINE"
    10 -> "TEN"
    11 -> "ELEVEN"
    12 -> "TWELVE"
    13 -> "THIRTEEN"
    14 -> "FOURTEEN"
    20 -> "TWENTY"
    in 21..29 -> "TWENTY-${minuteToWords(min - 20)}"
    else -> "$min"
}

@Composable
private fun WordsClock(now: LocalDateTime, ink: Color, soft: Color, modifier: Modifier) {
    val (line1, line2) = remember(now.hour, now.minute) {
        formatTimeWords(now.hour, now.minute)
    }
    val wordsFs = 30.sp
    val wordsDateFs = 18.sp

    Column(
        modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.standby2_it_is),
            color = soft,
            fontSize = FolioType.SUBHEAD.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 4.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            line1,
            color = ink,
            fontSize = wordsFs,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            line2,
            color = ink,
            fontSize = wordsFs,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(FolioSpace.MEDIUM.dp))
        Text(
            now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))),
            color = soft,
            fontSize = wordsDateFs,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun NeonClock(now: LocalDateTime, ink: Color, soft: Color, night: Boolean, cardBg: Color, modifier: Modifier) {
    val context = LocalContext.current
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val glowColor = if (night) ink else (if (ink == FolioColors.StandByWhite || ink == Color.White) FolioColors.StandByNeonBlue else ink)
    val pulseAlpha by standByPulse(0.85f, 1f, rest = 1f, durationMs = 1200, night = night, label = "neonAlpha")
    val textGlow = glowColor.copy(alpha = if (night) 0.8f else (0.95f * pulseAlpha))
    val neonDigitSize = 86.sp

    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .clip(RoundedCornerShape(FolioRadius.PANEL.dp))
                .background(if (night) cardBg else FolioColors.StandByNeonBg)
                .border(
                    width = 2.dp,
                    color = textGlow.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(FolioRadius.PANEL.dp)
                )
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = now.format(DateTimeFormatter.ofPattern(pattern)),
                color = if (night) ink else FolioColors.StandByNeonCyan,
                fontSize = neonDigitSize,
                fontWeight = FontWeight.Bold,
                style = TextStyle(
                    fontFeatureSettings = "tnum",
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = textGlow,
                        blurRadius = 36f,
                        offset = Offset.Zero
                    )
                )
            )
        }
        Spacer(Modifier.height(FolioSpace.MEDIUM.dp))
        Text(
            now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))),
            color = soft,
            fontSize = FolioType.TITLE3.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SolarClock(now: LocalDateTime, ink: Color, soft: Color, night: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val timePattern = if (is24) "HH:mm" else "h:mm"
    val totalMinutes = now.hour * 60 + now.minute
    val isDay = totalMinutes in 360..1080
    val sunProgress = if (isDay) {
        (totalMinutes - 360) / 720f
    } else {
        val nightMin = if (totalMinutes > 1080) totalMinutes - 1080 else totalMinutes + 360
        nightMin / 720f
    }

    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(width = 240.dp, height = 90.dp)) {
            val w = size.width
            val h = size.height
            val horizonY = h - 16.dp.toPx()
            val arcTopY = 16.dp.toPx()

            drawLine(
                color = soft.copy(alpha = 0.3f),
                start = Offset(16.dp.toPx(), horizonY),
                end = Offset(w - 16.dp.toPx(), horizonY),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(24.dp.toPx(), horizonY)
                quadraticTo(w / 2f, arcTopY - 10.dp.toPx(), w - 24.dp.toPx(), horizonY)
            }
            drawPath(
                path = path,
                color = if (isDay) (if (night) ink else FolioColors.StandBySolarSun).copy(alpha = 0.6f) else soft.copy(alpha = 0.25f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            val t = sunProgress.coerceIn(0f, 1f)
            val p0 = Offset(24.dp.toPx(), horizonY)
            val p1 = Offset(w / 2f, arcTopY - 10.dp.toPx())
            val p2 = Offset(w - 24.dp.toPx(), horizonY)
            val bx = (1 - t) * (1 - t) * p0.x + 2 * (1 - t) * t * p1.x + t * t * p2.x
            val by = (1 - t) * (1 - t) * p0.y + 2 * (1 - t) * t * p1.y + t * t * p2.y

            val sunColor = if (night) ink else (if (isDay) FolioColors.StandBySolarSun else FolioColors.StandBySolarNight)
            drawCircle(
                color = sunColor.copy(alpha = 0.25f),
                radius = 12.dp.toPx(),
                center = Offset(bx, by)
            )
            drawCircle(
                color = sunColor,
                radius = 6.dp.toPx(),
                center = Offset(bx, by)
            )
        }

        val solarTimeFs = 72.sp
        val solarDateFs = 18.sp

        Spacer(Modifier.height(FolioSpace.SMALL.dp))
        Text(
            text = now.format(DateTimeFormatter.ofPattern(timePattern)),
            color = ink,
            fontSize = solarTimeFs,
            fontWeight = FontWeight.Bold,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
        Text(
            now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))),
            color = soft,
            fontSize = solarDateFs,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun NixieClock(
    now: LocalDateTime,
    ink: Color,
    soft: Color,
    night: Boolean,
    cardBg: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val hourVal = if (is24) now.hour else (if (now.hour % 12 == 0) 12 else now.hour % 12)
    val hrStr = String.format("%02d", hourVal)
    val mnStr = String.format("%02d", now.minute)
    val glowColor = if (night) ink else (if (ink == FolioColors.StandByWhite || ink == Color.White) FolioColors.StandByNixieGlow else ink)
    val filamentColor = if (night) ink.copy(alpha = 0.9f) else FolioColors.StandByNixieFilament

    val flicker by standByPulse(0.94f, 1f, rest = 1f, durationMs = 260, night = night, label = "nixieFlickerAlpha")

    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NixieTube(hrStr[0].toString(), glowColor, filamentColor, flicker)
            NixieTube(hrStr[1].toString(), glowColor, filamentColor, flicker)
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(glowColor.copy(alpha = 0.85f * flicker)))
                Box(Modifier.size(8.dp).clip(CircleShape).background(glowColor.copy(alpha = 0.85f * flicker)))
            }
            NixieTube(mnStr[0].toString(), glowColor, filamentColor, flicker)
            NixieTube(mnStr[1].toString(), glowColor, filamentColor, flicker)
        }
        Spacer(Modifier.height(FolioSpace.MEDIUM.dp))
        Text(now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))), color = soft, fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun NixieTube(
    digit: String,
    glowColor: Color,
    filamentColor: Color,
    flicker: Float,
) {
    val tubeShape = RoundedCornerShape(FolioRadius.FLOATING.dp)
    val glowDigitSize = 62.sp
    val filamentDigitSize = 60.sp

    Box(
        modifier = Modifier
            .size(width = 62.dp, height = 110.dp)
            .clip(tubeShape)
            .background(FolioColors.StandByNixieGlass)
            .border(1.5.dp, glowColor.copy(alpha = 0.25f), tubeShape)
            .drawBehind {
                val meshColor = Color.White.copy(alpha = 0.04f)
                var y = 8.dp.toPx()
                while (y < size.height - 8.dp.toPx()) {
                    drawLine(meshColor, Offset(8.dp.toPx(), y), Offset(size.width - 8.dp.toPx(), y), strokeWidth = 1f)
                    y += 8.dp.toPx()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            color = glowColor.copy(alpha = 0.75f * flicker),
            fontSize = glowDigitSize,
            fontWeight = FontWeight.Black,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
        Text(
            text = digit,
            color = filamentColor.copy(alpha = 0.95f * flicker),
            fontSize = filamentDigitSize,
            fontWeight = FontWeight.Bold,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}

@Composable
private fun MinimalClock(
    now: LocalDateTime,
    ink: Color,
    soft: Color,
    night: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val hourVal = if (is24) now.hour else (if (now.hour % 12 == 0) 12 else now.hour % 12)
    val hrStr = if (is24) String.format("%02d", hourVal) else hourVal.toString()
    val mnStr = String.format("%02d", now.minute)
    val minimalDigitSize = 88.sp

    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(hrStr, color = ink, fontSize = minimalDigitSize, fontWeight = FontWeight.ExtraLight, style = TextStyle(fontFeatureSettings = "tnum"))
            Box(Modifier.width(2.dp).height(56.dp).background(soft.copy(alpha = 0.35f)))
            Text(mnStr, color = ink.copy(alpha = 0.75f), fontSize = minimalDigitSize, fontWeight = FontWeight.ExtraLight, style = TextStyle(fontFeatureSettings = "tnum"))
        }
        Spacer(Modifier.height(FolioSpace.TINY.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(FolioRadius.ROW.dp))
                .background(Color.White.copy(alpha = if (night) 0.06f else 0.1f))
                .padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))),
                color = soft,
                fontSize = FolioType.FOOTNOTE.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StandByInfo(
    now: LocalDateTime,
    status: DeviceStatus,
    ink: Color,
    soft: Color,
    cardBg: Color,
    night: Boolean,
    config: StandByEffectiveConfig,
    notifSummary: StandByNotifSummary,
    locked: Boolean,
    lockScreenShowNotifs: Boolean,
    onDismiss: (() -> Unit)?,
    modifier: Modifier,
) {
    Column(
        modifier.padding(FolioSpace.XXL.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(FolioSpace.COMFY.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StandByStatusChips(status, ink, soft, config.showChargingLine)
        StandByCards(now, status, ink, soft, cardBg, night, config, notifSummary, locked, lockScreenShowNotifs, onDismiss)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StandByStatusChips(status: DeviceStatus, ink: Color, soft: Color, showChargingLine: Boolean = false) {
    val context = LocalContext.current
    val tick by rememberMinuteTick()
    val alarm = remember(tick) { UpNext.nextAlarm(context) }
    val chargeLine = formatStandByChargeLine(
        showChargingLine = showChargingLine,
        charging = status.charging,
        battery = status.battery,
        chargeFullInMs = status.chargeFullInMs,
        chargeOnHold = status.chargeOnHold,
        onHoldFormat = { pct -> context.getString(R.string.standby2_charge_on_hold, pct) },
        fullInFormat = { dur -> context.getString(R.string.standby2_charge_full_in, dur) },
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FolioSpace.TINY.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(FolioSpace.TINY.dp),
        ) {
            status.battery?.let { level ->
                InfoChip(if (status.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd, "$level%", ink, soft)
            }
            alarm?.let {
                val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
                val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "EEE HH:mm" else "EEE h:mm a" // english-only
                InfoChip(Icons.Rounded.Alarm, t.format(DateTimeFormatter.ofPattern(pattern)), ink, soft)
            }
        }
        chargeLine?.let { line ->
            Text(line, color = soft, fontSize = FolioType.COMPACT.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StandByCards(
    now: LocalDateTime,
    status: DeviceStatus,
    ink: Color,
    soft: Color,
    cardBg: Color,
    night: Boolean,
    config: StandByEffectiveConfig,
    notifSummary: StandByNotifSummary,
    locked: Boolean,
    lockScreenShowNotifs: Boolean,
    onDismiss: (() -> Unit)?,
) {
    val context = LocalContext.current
    val tick by rememberMinuteTick()
    val lockHide = locked && !lockScreenShowNotifs
    val activity = IslandListenerService.activity.collectAsState().value
    val media = IslandListenerService.media.collectAsState().value ?: (activity as? IslandActivity.Media)
    val timer = IslandListenerService.timer.collectAsState().value ?: (activity as? IslandActivity.Timer)
    val showMedia = !lockHide && media != null && config.playerStyle != StandByPlayerStyle.OFF

    if (config.timersBesideMedia && timer != null && !lockHide) {
        StandByTimerCard(timer, ink, soft, cardBg)
    }

    // Up Next / Interactive Widget Carousel while nothing is playing
    val next by produceState<UpNextEvent?>(null, tick, lockHide) {
        value = if (lockHide) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { UpNext.events(context, limit = 1).firstOrNull() }
    }
    if (!showMedia && config.upNext && !lockHide) {
        StandByWidgetCarousel(
            now = now,
            status = status,
            next = next,
            ink = ink,
            soft = soft,
            cardBg = cardBg,
            accentColor = if (night) soft else standByClockColor(config.clockColor),
            night = night,
        )
    }

    if (showMedia && media != null) {
        val openMediaAction: (() -> Unit)? = if (!locked && onDismiss != null) {
            {
                IslandListenerService.open(context, media)
                onDismiss()
            }
        } else null
        when (config.playerStyle) {
            StandByPlayerStyle.CARD -> StandByCleanPlayerCard(media, ink, soft, cardBg, night, config.nightColor, openMediaAction)
            StandByPlayerStyle.COMPACT -> StandByCompactPlayerRow(media, ink, soft, cardBg, night, config.nightColor, openMediaAction)
            StandByPlayerStyle.LEGACY_SLAB -> StandByLegacyMediaSlab(media, ink, soft, cardBg, night)
            StandByPlayerStyle.OFF -> Unit
        }
    }

    if (notifSummary.totalCount > 0) {
        StandByNotifRow(notifSummary, soft, night)
    }
}

@Composable
private fun StandByWidgetCarousel(
    now: LocalDateTime,
    status: DeviceStatus,
    next: UpNextEvent?,
    ink: Color,
    soft: Color,
    cardBg: Color,
    accentColor: Color,
    night: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    var widgetIndex by remember { mutableIntStateOf(0) }
    var swipeDirection by remember { mutableIntStateOf(1) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 24.dp.toPx() }

    Row(
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onDragEnd = {
                        if (dragAccumulator < -threshold) {
                            // Swiped up -> advance to next card
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            swipeDirection = 1
                            widgetIndex = (widgetIndex + 1) % 3
                        } else if (dragAccumulator > threshold) {
                            // Swiped down -> previous card
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            swipeDirection = -1
                            widgetIndex = (widgetIndex - 1 + 3) % 3
                        }
                        dragAccumulator = 0f
                    },
                    onDragCancel = { dragAccumulator = 0f }
                ) { change, dragAmount ->
                    change.consume()
                    dragAccumulator += dragAmount
                }
            }
            .clickable(remember { MutableInteractionSource() }, null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                swipeDirection = 1
                widgetIndex = (widgetIndex + 1) % 3
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            androidx.compose.animation.AnimatedContent(
                targetState = widgetIndex,
                transitionSpec = {
                    if (swipeDirection > 0) {
                        (androidx.compose.animation.slideInVertically(animationSpec = FolioMotion.spring(FolioMotion.Quick)) { it / 2 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)))
                            .togetherWith(androidx.compose.animation.slideOutVertically(animationSpec = FolioMotion.spring(FolioMotion.Quick)) { -it / 2 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)))
                    } else {
                        (androidx.compose.animation.slideInVertically(animationSpec = FolioMotion.spring(FolioMotion.Quick)) { -it / 2 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)))
                            .togetherWith(androidx.compose.animation.slideOutVertically(animationSpec = FolioMotion.spring(FolioMotion.Quick)) { it / 2 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)))
                    }
                },
                label = "standby carousel card" // english-only
            ) { index ->
                when (index) {
                    1 -> StandByMonthWidget(now, ink, soft, cardBg, accentColor, night)
                    2 -> StandByVitalsWidget(status, ink, soft, cardBg, accentColor, night)
                    else -> StandByUpNextCard(next, ink, soft, cardBg, accentColor, night)
                }
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (i in 0 until 3) {
                val isSelected = i == widgetIndex
                Box(
                    modifier = Modifier
                        .size(4.dp, if (isSelected) 12.dp else 4.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) (if (night) soft else accentColor) else soft.copy(alpha = 0.25f))
                        .clickable {
                            if (widgetIndex != i) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                swipeDirection = if (i > widgetIndex) 1 else -1
                                widgetIndex = i
                            }
                        }
                )
            }
        }
    }
}

@Composable
private fun StandByUpNextCard(
    next: UpNextEvent?,
    ink: Color,
    soft: Color,
    cardBg: Color,
    accentColor: Color,
    night: Boolean,
) {
    val context = LocalContext.current
    val cardRadius = RoundedCornerShape(FolioRadius.SHEET_TOP.dp)
    val barRadius = RoundedCornerShape(FolioSpace.HAIR.dp)

    if (next != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardRadius)
                .background(cardBg)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(barRadius)
                    .background(if (night) soft else next.color?.let { Color(it) } ?: accentColor)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(next.title, color = ink, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val begin = LocalDateTime.ofInstant(Instant.ofEpochMilli(next.begin), ZoneId.systemDefault())
                val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "EEE HH:mm" else "EEE h:mm a" // english-only
                Text(if (next.allDay) stringResource(R.string.all_day) else begin.format(DateTimeFormatter.ofPattern(pattern)), color = soft, fontSize = FolioType.COMPACT.sp)
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardRadius)
                .background(cardBg)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = if (night) soft else accentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.standby2_no_events), color = ink, fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(stringResource(R.string.standby2_widget_calendar), color = soft, fontSize = FolioType.COMPACT.sp)
            }
        }
    }
}

@Composable
private fun StandByMonthWidget(
    now: LocalDateTime,
    ink: Color,
    soft: Color,
    cardBg: Color,
    accentColor: Color,
    night: Boolean,
) {
    val yearMonth = YearMonth.of(now.year, now.month)
    val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek.value % 7
    val lengthOfMonth = yearMonth.lengthOfMonth()
    val currentDay = now.dayOfMonth
    val dayNames = listOf("S", "M", "T", "W", "T", "F", "S")
    val monthWidgetShape = RoundedCornerShape(FolioRadius.SHEET_TOP.dp)
    val dayHeaderSize = 10.sp
    val dayGridSize = 9.sp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(monthWidgetShape)
            .background(cardBg)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                now.format(DateTimeFormatter.ofPattern(stringResource(R.string.standby2_month_year_pattern))).uppercase(),
                color = if (night) soft else accentColor,
                fontSize = FolioType.CAPTION.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = soft, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            dayNames.forEach { name ->
                Text(name, color = soft.copy(alpha = 0.6f), fontSize = dayHeaderSize, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(20.dp), textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(4.dp))
        val totalCells = ((firstDayOfWeek + lengthOfMonth + 6) / 7) * 7
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (row in 0 until (totalCells / 7)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    for (col in 0 until 7) {
                        val cellIdx = row * 7 + col
                        val dayNum = cellIdx - firstDayOfWeek + 1
                        val isValid = dayNum in 1..lengthOfMonth
                        val isToday = isValid && dayNum == currentDay
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .then(if (isToday) Modifier.background(if (night) ink else accentColor, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isValid) {
                                Text(
                                    dayNum.toString(),
                                    color = if (isToday) (if (night) Color.Black else Color.White) else (if (col == 0 || col == 6) soft.copy(alpha = 0.5f) else ink),
                                    fontSize = dayGridSize,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StandByVitalsWidget(
    status: DeviceStatus,
    ink: Color,
    soft: Color,
    cardBg: Color,
    accentColor: Color,
    night: Boolean,
) {
    val context = LocalContext.current
    val batteryPct = status.battery ?: 100
    val memoryInfo = remember {
        val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        mi
    }
    val availGb = remember(memoryInfo) {
        memoryInfo?.availMem?.let { String.format("%.1f GB", it / (1024.0 * 1024.0 * 1024.0)) } ?: context.getString(R.string.standby2_vitals_free)
    }
    val vitalsShape = RoundedCornerShape(FolioRadius.SHEET_TOP.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(vitalsShape)
            .background(cardBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.standby2_widget_vitals), color = if (night) soft else accentColor, fontSize = FolioType.CAPTION.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Icon(
                imageVector = if (status.charging) Icons.Rounded.Bolt else Icons.Rounded.Favorite,
                contentDescription = null,
                tint = if (status.charging) FolioColors.GreenLight else FolioColors.RedLight,
                modifier = Modifier.size(18.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val strokeWidth = 5.dp.toPx()
                    val pad = strokeWidth / 2f
                    drawArc(
                        color = Color.White.copy(alpha = 0.15f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(strokeWidth, cap = StrokeCap.Round),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                        topLeft = Offset(pad, pad)
                    )
                    drawArc(
                        color = if (status.charging) FolioColors.GreenLight else accentColor,
                        startAngle = -90f,
                        sweepAngle = 360f * (batteryPct / 100f),
                        useCenter = false,
                        style = Stroke(strokeWidth, cap = StrokeCap.Round),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                        topLeft = Offset(pad, pad)
                    )
                }
                Text("$batteryPct%", color = ink, fontSize = FolioType.CAPTION.sp, fontWeight = FontWeight.Bold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(if (status.charging) R.string.standby2_vitals_charging else R.string.standby2_vitals_battery), color = ink, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.standby2_ram_avail, availGb), color = soft, fontSize = FolioType.CAPTION.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StandByNotifRow(summary: StandByNotifSummary, soft: Color, night: Boolean) {
    FlowRow(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(FolioSpace.TINY.dp),
    ) {
        if (summary.dotColors.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                summary.dotColors.forEach { argb ->
                    val dotColor = if (night) soft else Color(argb)
                    Box(Modifier.size(FolioSpace.MEDIUM.dp).clip(CircleShape).background(dotColor))
                }
                if (summary.overflowApps > 0) {
                    Text(
                        stringResource(R.string.standby2_more_apps, summary.overflowApps),
                        color = soft,
                        fontSize = FolioType.COMPACT.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Text(
            stringResource(R.string.standby2_new_count, summary.totalCount),
            color = soft,
            fontSize = FolioType.SUBHEAD.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StandByEdgeGlow(
    summary: StandByNotifSummary,
    mode: StandByNotifMode,
    night: Boolean,
) {
    val reduceMotion = LocalReduceMotion.current
    val glowAlpha = remember { Animatable(0f) }
    var glowColor by remember { mutableStateOf(FolioColors.Blue) }
    var lastGlowElapsedMs by remember { mutableLongStateOf(-1L) }
    var initialized by remember { mutableStateOf(false) }
    var lastSeenKey by remember { mutableStateOf<String?>(null) }
    var glowTriggerId by remember { mutableIntStateOf(0) }

    LaunchedEffect(summary.newestGlowKey, mode) {
        if (!initialized) {
            initialized = true
            lastSeenKey = summary.newestGlowKey
            return@LaunchedEffect
        }
        val key = summary.newestGlowKey
        if (key == null || key == lastSeenKey) return@LaunchedEffect
        lastSeenKey = key
        val nowElapsed = android.os.SystemClock.elapsedRealtime()
        if (!shouldTriggerStandByGlow(mode, reduceMotion, summary.newestGlowSilenced, nowElapsed, lastGlowElapsedMs)) {
            return@LaunchedEffect
        }
        lastGlowElapsedMs = nowElapsed
        glowColor = if (night) FolioColors.StandByNight else Color(summary.newestGlowColor ?: FolioColors.Value.Blue.toInt())
        glowTriggerId++
    }

    LaunchedEffect(glowTriggerId, reduceMotion) {
        if (reduceMotion || glowTriggerId == 0) {
            glowAlpha.snapTo(0f)
            return@LaunchedEffect
        }
        glowAlpha.snapTo(0f)
        glowAlpha.animateTo(StandBySafetyCaps.MAX_GLOW_ALPHA, tween(StandBySafetyCaps.GLOW_IN_MS))
        delay(StandBySafetyCaps.GLOW_HOLD_MS.toLong())
        glowAlpha.animateTo(0f, tween(StandBySafetyCaps.GLOW_OUT_MS))
    }

    val currentAlpha = glowAlpha.value
    if (currentAlpha > 0f && !reduceMotion) {
        val activeColor = (if (night) FolioColors.StandByNight else glowColor).copy(alpha = currentAlpha)
        Canvas(Modifier.fillMaxSize()) {
            val band = 36.dp.toPx()
            // Top edge inset glow
            drawRect(
                brush = Brush.verticalGradient(listOf(activeColor, Color.Transparent), startY = 0f, endY = band),
                size = size.copy(height = band),
            )
            // Bottom edge inset glow
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, activeColor), startY = size.height - band, endY = size.height),
                topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - band),
                size = size.copy(height = band),
            )
            // Left edge inset glow
            drawRect(
                brush = Brush.horizontalGradient(listOf(activeColor, Color.Transparent), startX = 0f, endX = band),
                size = size.copy(width = band),
            )
            // Right edge inset glow
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, activeColor), startX = size.width - band, endX = size.width),
                topLeft = androidx.compose.ui.geometry.Offset(size.width - band, 0f),
                size = size.copy(width = band),
            )
        }
    }
}

@Composable
private fun StandByTimerCard(timer: IslandActivity.Timer, ink: Color, soft: Color, cardBg: Color) {
    val nowMs by produceState(System.currentTimeMillis(), timer.base, timer.countDown) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val formatted = formatStandByTimer(timer.base, nowMs, timer.countDown)
    Row(
        Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(FolioRadius.CARD.dp))
            .background(cardBg)
            .padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.MEDIUM.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Timer, null, tint = soft, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(FolioSpace.MEDIUM.dp))
        Text(
            timer.title.ifBlank { stringResource(R.string.standby2_timer) },
            color = soft,
            fontSize = FolioType.BODY.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(FolioSpace.MEDIUM.dp))
        Text(
            formatted,
            color = ink,
            fontSize = FolioType.TITLE3.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
    }
}

/**
 * StandBy 2 cleaner Now Playing card (`docs/plan-standby-2.md` §5 Phase 3 & `2026-10-01-standby-now-playing-and-next.md` §2.2):
 * - Fixed `64.dp` art slot (`FolioRadius.CARD.dp` = `14.dp` corner radius) that never shifts whether art is present or absent;
 *   when `media.art` is null, centers `media.icon` at `40.dp` inside the `64.dp` tile (or `MusicNote` at `28.dp`).
 * - No slab background; at night album art is filtered through [nightArtColorMatrix] into StandBy hue.
 * - `2.dp` (`FolioSpace.HAIR.dp`) progress line during the day when `media.durationMs > 0L` (hidden at night).
 * - Three `48.dp` (`FolioTouch.MIN`) transport slots (`32.dp` Play/Pause glyph, `28.dp` skip glyphs at `0.7f` alpha) where
 *   Previous and Next enter the accessibility/touch tree only when offered by `PlaybackState.getActions()`.
 */
@Composable
private fun StandByCleanPlayerCard(
    media: IslandActivity.Media,
    ink: Color,
    soft: Color,
    cardBg: Color,
    night: Boolean,
    nightColor: String = "red",
    onOpenMedia: (() -> Unit)? = null,
) {
    val artFilter = remember(night, nightColor) {
        if (night) androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(nightArtColorMatrix(nightColor)))
        else null
    }
    val headerClickModifier = if (onOpenMedia != null) Modifier.clickable(onClick = onOpenMedia) else Modifier
    Column(
        Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.then(headerClickModifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(FolioRadius.CARD.dp))
                    .background(cardBg),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    media.art != null -> Image(
                        bitmap = media.art.asImageBitmap(),
                        contentDescription = null,
                        colorFilter = artFilter,
                        modifier = Modifier.fillMaxSize(),
                    )
                    media.icon != null -> Image(
                        bitmap = media.icon.asImageBitmap(),
                        contentDescription = null,
                        colorFilter = artFilter,
                        modifier = Modifier.size(40.dp),
                    )
                    else -> Icon(Icons.Rounded.MusicNote, null, tint = soft, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(FolioSpace.LARGE.dp))
            Column(Modifier.weight(1f)) {
                Text(media.title, color = ink, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                media.subtitle?.let { Text(it, color = soft, fontSize = FolioType.SUBHEAD.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        if (!night && media.durationMs > 0L) {
            val currentPosMs by produceState(media.positionMs, media.token, media.playing, media.positionMs) {
                while (media.playing) {
                    value = (runCatching { media.controller.playbackState?.position }.getOrNull() ?: value).coerceAtLeast(0L)
                    delay(1_000L)
                }
            }
            val progress = (currentPosMs.toFloat() / media.durationMs.toFloat()).coerceIn(0f, 1f)
            val barRadius = FolioSpace.HAIR.dp
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(FolioSpace.HAIR.dp)
                    .clip(RoundedCornerShape(barRadius))
                    .background(Color.White.copy(alpha = 0.16f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(ink),
                )
            }
        }
        StandByTransportRow(media, ink, soft, night)
    }
}

@Composable
private fun StandByCompactPlayerRow(
    media: IslandActivity.Media,
    ink: Color,
    soft: Color,
    cardBg: Color,
    night: Boolean,
    nightColor: String = "red",
    onOpenMedia: (() -> Unit)? = null,
) {
    val artBitmap = media.art ?: media.icon
    val artFilter = remember(night, nightColor) {
        if (night) androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(nightArtColorMatrix(nightColor)))
        else null
    }
    val t = runCatching { media.controller.transportControls }.getOrNull()
    val compactArtRadius = FolioSpace.SNUG.dp
    val openModifier = if (onOpenMedia != null) Modifier.clickable(onClick = onOpenMedia) else Modifier
    Row(
        Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).semantics(mergeDescendants = true) {}.then(openModifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(FolioSpace.XXL.dp)
                    .clip(RoundedCornerShape(compactArtRadius))
                    .background(cardBg),
                contentAlignment = Alignment.Center,
            ) {
                if (artBitmap != null) {
                    Image(artBitmap.asImageBitmap(), null, colorFilter = artFilter, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Rounded.MusicNote, null, tint = soft, modifier = Modifier.size(FolioSpace.COMFY.dp))
                }
            }
            Spacer(Modifier.width(FolioSpace.MEDIUM.dp))
            Column(Modifier.weight(1f)) {
                Text(media.title, color = ink, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                media.subtitle?.let { Text(it, color = soft, fontSize = FolioType.SUBHEAD.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        Box(
            Modifier
                .minimumInteractiveComponentSize()
                .size(FolioTouch.MIN.dp)
                .clip(CircleShape)
                .clickable { if (media.playing) t?.pause() else t?.play() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (media.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                stringResource(if (media.playing) R.string.pause else R.string.play),
                tint = ink,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun StandByLegacyMediaSlab(media: IslandActivity.Media, ink: Color, soft: Color, cardBg: Color, night: Boolean) {
    BoxWithConstraints(Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
        .background(cardBg).padding(16.dp)) {
        // On a narrow card (the cover's half of the screen) the controls go under the title, so the title has the
        // card's width instead of a third of it and breaks between words rather than inside one.
        val stacked = maxWidth < 360.dp
        val art: @Composable () -> Unit = {
            media.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))) }
            Spacer(Modifier.width(14.dp))
        }
        val words: @Composable (Modifier) -> Unit = { m ->
            Column(m) {
                Text(media.title, color = ink, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                media.subtitle?.let { Text(it, color = soft, fontSize = FolioType.COMPACT.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { art(); words(Modifier.weight(1f)) }
            StandByTransportRow(media, ink, soft, night)
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            art()
            words(Modifier.weight(1f))
            StandByTransportControls(media, ink, soft, night)
        }
    }
}

@Composable
private fun StandByTransportRow(media: IslandActivity.Media, ink: Color, soft: Color = ink, night: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StandByTransportControls(media, ink, soft, night)
    }
}

@Composable
private fun StandByTransportControls(media: IslandActivity.Media, ink: Color, soft: Color = ink, night: Boolean = false) {
    val t = runCatching { media.controller.transportControls }.getOrNull()
    val skipTint = if (night) soft else ink.copy(alpha = 0.7f)
    if (media.canSkipPrevious) {
        Box(
            Modifier
                .minimumInteractiveComponentSize()
                .size(FolioTouch.MIN.dp)
                .clip(CircleShape)
                .clickable { t?.skipToPrevious() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.SkipPrevious,
                stringResource(R.string.previous_track),
                tint = skipTint,
                modifier = Modifier.size(28.dp),
            )
        }
    } else {
        Spacer(Modifier.size(FolioTouch.MIN.dp))
    }
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .size(FolioTouch.MIN.dp)
            .clip(CircleShape)
            .clickable { if (media.playing) t?.pause() else t?.play() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (media.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            stringResource(if (media.playing) R.string.pause else R.string.play),
            tint = ink,
            modifier = Modifier.size(FolioSpace.HUGE.dp),
        )
    }
    if (media.canSkipNext) {
        Box(
            Modifier
                .minimumInteractiveComponentSize()
                .size(FolioTouch.MIN.dp)
                .clip(CircleShape)
                .clickable { t?.skipToNext() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.SkipNext,
                stringResource(R.string.next_track),
                tint = skipTint,
                modifier = Modifier.size(28.dp),
            )
        }
    } else {
        Spacer(Modifier.size(FolioTouch.MIN.dp))
    }
}

@Composable
private fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, ink: Color, soft: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = soft, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = ink, fontSize = FolioType.TITLE3.sp, fontWeight = FontWeight.Medium)
    }
}

private const val ENTER_DELAY_MS = 2_500L
private const val TOUCH_WAKE_HOLD_MS = 8_000L

/** m/s² of gravity along the phone's x axis that counts as on its side: up to about 60° of lean back on a stand. */
private const val SIDEWAYS_GRAVITY = 5f
