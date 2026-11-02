package com.mccal.folio

import android.app.Activity
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
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
    val tick by rememberMinuteTick()
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
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_LIGHT && event.values.isNotEmpty()) {
                    val nowElapsedMs = android.os.SystemClock.elapsedRealtime()
                    roomDark = tracker.onReading(event.values[0], nowElapsedMs)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose {
            sensors.unregisterListener(listener)
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
    // At night the big clock stays a dim color, which is enough at its size; the smaller text uses brighter hues that
    // reach the 4.5:1 small text needs (it was 2.2:1), so the date, chips and cards stay readable in the dark.
    val clockInk = if (night) palette.clock else Color.White
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
        when (posture) {
            StandByPosture.TABLETOP -> Column(dimmedModifier) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(bottom = creasePadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BigClock(now, clockInk, soft, config.clockLook, night, cardBg, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(FolioSpace.MEDIUM.dp))
                    StandByStatusChips(status, ink, soft, config.showChargingLine)
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
                    StandByCards(ink, soft, cardBg, night, config, notifSummary, locked, lockScreenShowNotifs, onDismiss)
                }
            }
            StandByPosture.BOOK, StandByPosture.TENT, StandByPosture.LANDSCAPE -> Row(dimmedModifier) {
                BigClock(now, clockInk, soft, config.clockLook, night, cardBg, Modifier.weight(1f).fillMaxHeight().padding(end = creasePadding))
                StandByInfo(status, ink, soft, cardBg, night, config, notifSummary, locked, lockScreenShowNotifs, onDismiss, Modifier.weight(1f).fillMaxHeight().padding(start = creasePadding))
            }
            StandByPosture.PORTRAIT -> Column(dimmedModifier) {
                BigClock(now, clockInk, soft, config.clockLook, night, cardBg, Modifier.weight(1f).fillMaxWidth())
                StandByInfo(status, ink, soft, cardBg, night, config, notifSummary, locked, lockScreenShowNotifs, onDismiss, Modifier.weight(1f).fillMaxWidth())
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
) {
    when (clockLook) {
        StandByClockLook.ANALOG -> AnalogClock(now, ink, soft, night, modifier)
        StandByClockLook.NUMERALS -> NumeralsClock(now, ink, soft, night, modifier)
        StandByClockLook.FLIP -> FlipClock(now, ink, soft, night, cardBg, modifier)
        StandByClockLook.WORLD -> WorldClock(now, ink, soft, night, modifier)
        StandByClockLook.STACK -> StackClock(now, ink, soft, modifier)
        StandByClockLook.WORDS -> WordsClock(now, ink, soft, modifier)
        StandByClockLook.NEON -> NeonClock(now, ink, soft, night, cardBg, modifier)
        StandByClockLook.SOLAR -> SolarClock(now, ink, soft, night, modifier)
        StandByClockLook.BOLD, StandByClockLook.THIN -> {
            val context = LocalContext.current
            val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
            val weight = if (clockLook == StandByClockLook.BOLD) FontWeight.Bold else FontWeight.Thin
            val clockSp = (if (clockLook == StandByClockLook.BOLD) 108 else 120).sp
            Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(now.format(DateTimeFormatter.ofPattern(pattern)), color = ink, fontSize = clockSp, fontWeight = weight,
                    lineHeight = 124.sp, style = TextStyle(fontFeatureSettings = "tnum"))
                Text(now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))), color = soft, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun AnalogClock(now: LocalDateTime, ink: Color, soft: Color, night: Boolean, modifier: Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val tick = if (!night && !reduceMotion) rememberSecondTick().value else null
    val sec = if (tick != null) LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(tick), java.time.ZoneId.systemDefault()).second else 0
    val min = now.minute
    val hour = now.hour % 12
    val hourAngle = (hour + min / 60f) * 30f
    val minAngle = (min + sec / 60f) * 6f
    val secAngle = sec * 6f

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
        Text(now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))), color = soft, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FlipCard(text: String, ink: Color, night: Boolean, cardBg: Color) {
    Box(
        Modifier
            .size(width = 100.dp, height = 110.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (night) cardBg else Color(0xFF1E1E22)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = ink,
            fontSize = 68.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
        // Crease / split line
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.Black.copy(alpha = 0.65f))
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

    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FlipCard(hr, ink, night, cardBg)
            Text(":", color = soft.copy(alpha = 0.7f), fontSize = 44.sp, fontWeight = FontWeight.Bold)
            FlipCard(mn, ink, night, cardBg)
        }
        Spacer(Modifier.height(FolioSpace.MEDIUM.dp))
        Text(now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))), color = soft, fontSize = 20.sp, fontWeight = FontWeight.Medium)
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

    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            now.format(DateTimeFormatter.ofPattern(pattern)),
            color = ink,
            fontSize = 76.sp,
            fontWeight = FontWeight.Medium,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
        val amPmPrefix = if (!is24) now.format(DateTimeFormatter.ofPattern("a")) + " · " else ""
        Text(
            amPmPrefix + "${localZone.id.substringAfterLast('/')} · " + now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))),
            color = soft,
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(FolioSpace.COMFY.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (night) soft.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("UTC", color = soft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(utcTime.format(DateTimeFormatter.ofPattern("HH:mm")), color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, style = TextStyle(fontFeatureSettings = "tnum"))
            }
            Box(Modifier.width(1.dp).height(24.dp).background(soft.copy(alpha = 0.3f)))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TOKYO", color = soft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(tokyoTime.format(DateTimeFormatter.ofPattern("HH:mm")), color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, style = TextStyle(fontFeatureSettings = "tnum"))
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
                            Color(0xFFFFB74D).copy(alpha = glowAlpha),
                            Color(0xFFFF6F00).copy(alpha = glowAlpha * 0.7f),
                            Color(0xFF8B2500).copy(alpha = glowAlpha * 0.35f),
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
    val tick = if (!night && !reduceMotion) rememberSecondTick().value else null
    val sec = if (tick != null) LocalDateTime.ofInstant(Instant.ofEpochMilli(tick), ZoneId.systemDefault()).second else 0
    val min = now.minute
    val hour = now.hour % 12
    val hourAngle = (hour + min / 60f) * 30f
    val minAngle = (min + sec / 60f) * 6f
    val secAngle = sec * 6f
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
                            fontSize = 15.sp,
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
        Text(now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))), color = soft, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StackClock(now: LocalDateTime, ink: Color, soft: Color, modifier: Modifier) {
    val context = LocalContext.current
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val hourVal = if (is24) now.hour else (if (now.hour % 12 == 0) 12 else now.hour % 12)
    val hr = String.format("%02d", hourVal)
    val mn = String.format("%02d", now.minute)

    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            hr,
            color = ink,
            fontSize = 92.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 84.sp,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
        Text(
            mn,
            color = ink,
            fontSize = 92.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 84.sp,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
        Spacer(Modifier.height(FolioSpace.SMALL.dp))
        Text(
            now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))),
            color = soft,
            fontSize = 18.sp,
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
    Column(
        modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "IT IS",
            color = soft,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 4.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            line1,
            color = ink,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            line2,
            color = ink,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(FolioSpace.MEDIUM.dp))
        Text(
            now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))),
            color = soft,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun NeonClock(now: LocalDateTime, ink: Color, soft: Color, night: Boolean, cardBg: Color, modifier: Modifier) {
    val context = LocalContext.current
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val glowColor = if (night) ink else Color(0xFF00E5FF)
    val textGlow = glowColor.copy(alpha = if (night) 0.8f else 0.95f)

    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(if (night) cardBg else Color(0xFF0D1117))
                .border(
                    width = 2.dp,
                    color = textGlow.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = now.format(DateTimeFormatter.ofPattern(pattern)),
                color = if (night) ink else Color(0xFFE0F7FA),
                fontSize = 86.sp,
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
            fontSize = 20.sp,
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
                color = if (isDay) (if (night) ink else Color(0xFFFFB74D)).copy(alpha = 0.6f) else soft.copy(alpha = 0.25f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            val t = sunProgress.coerceIn(0f, 1f)
            val p0 = Offset(24.dp.toPx(), horizonY)
            val p1 = Offset(w / 2f, arcTopY - 10.dp.toPx())
            val p2 = Offset(w - 24.dp.toPx(), horizonY)
            val bx = (1 - t) * (1 - t) * p0.x + 2 * (1 - t) * t * p1.x + t * t * p2.x
            val by = (1 - t) * (1 - t) * p0.y + 2 * (1 - t) * t * p1.y + t * t * p2.y

            val sunColor = if (night) ink else (if (isDay) Color(0xFFFFB74D) else Color(0xFF90CAF9))
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

        Spacer(Modifier.height(FolioSpace.SMALL.dp))
        Text(
            text = now.format(DateTimeFormatter.ofPattern(timePattern)),
            color = ink,
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
        Text(
            now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))),
            color = soft,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StandByInfo(
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
        StandByCards(ink, soft, cardBg, night, config, notifSummary, locked, lockScreenShowNotifs, onDismiss)
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

    // Up Next while nothing is playing: the next event, in the calendar's color (not tinted red at night).
    val next by produceState<UpNextEvent?>(null, tick, lockHide) {
        value = if (lockHide) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { UpNext.events(context, limit = 1).firstOrNull() }
    }
    if (!showMedia && config.upNext && !lockHide) next?.let { e ->
        Row(Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cardBg)
            .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(if (night) soft else e.color?.let { Color(it) } ?: LocalAccent.current.fill))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val begin = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.begin), ZoneId.systemDefault())
                val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "EEE HH:mm" else "EEE h:mm a" // english-only
                Text(if (e.allDay) stringResource(R.string.all_day) else begin.format(DateTimeFormatter.ofPattern(pattern)), color = soft, fontSize = FolioType.COMPACT.sp)
            }
        }
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
