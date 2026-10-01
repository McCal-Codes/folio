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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
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
internal data class StandByWays(val halfOpen: Boolean, val charging: Boolean, val tent: Boolean)

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
)

/**
 * Whether StandBy should be on, and why, or null.
 *
 * iPhone's own rule, charging, on its side and still, is the one that works in any pose. On the Fold8 the posture a
 * tent really is (TENT) isn't open to apps, and a narrow tent reads 0° on the hinge, the same as closed (measured on
 * the phone, 1 Oct 2026). So the laptop pose comes from WindowManager on the inner screen, as before, and a tent off
 * the charger needs the hinge at its middle step with Folio on the cover. Pass `still = true` to ask whether a way is
 * ready apart from the phone settling: dismissing StandBy holds until that changes, not until the next tap jiggles it.
 */
internal fun standByWay(ways: StandByWays, s: StandBySignals): StandByWay? = when {
    ways.halfOpen && s.halfOpen -> StandByWay.HALF_OPEN
    ways.charging && s.plugged && s.sideways && s.still -> StandByWay.CHARGING
    ways.tent && s.onCover && s.hingeHalfway && s.sideways && s.still -> StandByWay.TENT
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
internal fun rememberStandBySignals(activity: Activity, ways: StandByWays, plugged: Boolean, halfOpen: Boolean): StandBySignals {
    val onCover = !LocalConfiguration.current.fitsRegularHomeLayout()
    val started = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.STARTED)
    val wantMotion = started && ((ways.charging && plugged) || (ways.tent && onCover))
    val wantHinge = started && ways.tent && onCover
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
        hingeHalfway = hinge?.let { it in 30f..150f } == true, onCover = onCover)
}

/**
 * iPhone-style StandBy for a phone set down half-open: a big clock, date, next alarm, battery and
 * now playing. Dim red at night. Tap anywhere or open the phone flat to leave.
 */
@Composable
internal fun StandByOverlay(way: StandByWay?, ready: Boolean, pose: FoldingFeature.Orientation?, blocked: Boolean, status: DeviceStatus) {
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
    val view = LocalView.current
    // The screen stays on while charging, as on iPhone, and in the laptop pose as before; a tent off the charger
    // follows the screen timeout rather than draining the battery.
    val awake = active && (shown == StandByWay.HALF_OPEN || status.charging)
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

    AnimatedVisibility(active, enter = fadeIn(tween(500)), exit = fadeOut(tween(300))) {
        val tick by rememberMinuteTick()
        val now = displayNow(tick)
        val night = now.hour >= 22 || now.hour < 6
        val ink = if (night) Color(0xFFB3261E) else Color.White
        val soft = ink.copy(alpha = if (night) .75f else .6f)
        Box(Modifier.fillMaxSize().background(Color.Black)
            .clickable(remember { MutableInteractionSource() }, null) { active = false; dismissed = true }) {
            val clock: @Composable (Modifier) -> Unit = { m -> BigClock(now, ink, soft, m) }
            val info: @Composable (Modifier) -> Unit = { m -> StandByInfo(status, ink, soft, night, m) }
            if (pose == FoldingFeature.Orientation.HORIZONTAL) Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                clock(Modifier.weight(1f).fillMaxWidth()); info(Modifier.weight(1f).fillMaxWidth())
            } else Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                clock(Modifier.weight(1f).fillMaxHeight()); info(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun BigClock(now: LocalDateTime, ink: Color, soft: Color, modifier: Modifier) {
    val context = LocalContext.current
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(now.format(DateTimeFormatter.ofPattern(pattern)), color = ink, fontSize = 120.sp, fontWeight = FontWeight.Thin,
            lineHeight = 124.sp, style = TextStyle(fontFeatureSettings = "tnum"))
        Text(now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), color = soft, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StandByInfo(status: DeviceStatus, ink: Color, soft: Color, night: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val tick by rememberMinuteTick()
    val alarm = remember(tick) { UpNext.nextAlarm(context) }
    val media = IslandListenerService.activity.collectAsState().value as? IslandActivity.Media
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            status.battery?.let { level ->
                InfoChip(if (status.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd, "$level%", ink, soft)
            }
            alarm?.let {
                val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
                val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "EEE HH:mm" else "EEE h:mm a"
                InfoChip(Icons.Rounded.Alarm, t.format(DateTimeFormatter.ofPattern(pattern)), ink, soft)
            }
        }
        // Up Next while nothing is playing: the next event, in the calendar's color (not tinted red at night).
        val next by produceState<UpNextEvent?>(null, tick) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { UpNext.events(context, limit = 1).firstOrNull() } }
        if (media == null) next?.let { e ->
            Row(Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(if (night) Color(0xFF1A0605) else FolioColors.SecondaryBackground)
                .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(if (night) soft else e.color?.let { Color(it) } ?: LocalAccent.current.fill))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val begin = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.begin), ZoneId.systemDefault())
                    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "EEE HH:mm" else "EEE h:mm a"
                    Text(if (e.allDay) stringResource(R.string.all_day) else begin.format(DateTimeFormatter.ofPattern(pattern)), color = soft, fontSize = 14.sp)
                }
            }
        }
        if (media != null) Row(Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(if (night) Color(0xFF1A0605) else FolioColors.SecondaryBackground).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            media.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(media.title, color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                media.subtitle?.let { Text(it, color = soft, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            val t = media.controller.transportControls
            Icon(Icons.Rounded.SkipPrevious, "Previous", tint = ink, modifier = Modifier.minimumInteractiveComponentSize().size(36.dp).clip(CircleShape).clickable { t.skipToPrevious() })
            Icon(if (media.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (media.playing) R.string.pause else R.string.play), tint = ink,
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable { if (media.playing) t.pause() else t.play() })
            Icon(Icons.Rounded.SkipNext, "Next", tint = ink, modifier = Modifier.minimumInteractiveComponentSize().size(36.dp).clip(CircleShape).clickable { t.skipToNext() })
        }
    }
}

@Composable
private fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, ink: Color, soft: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = soft, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

private const val ENTER_DELAY_MS = 2_500L

/** m/s² of gravity along the phone's x axis that counts as on its side: up to about 60° of lean back on a stand. */
private const val SIDEWAYS_GRAVITY = 5f
