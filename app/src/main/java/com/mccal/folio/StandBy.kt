package com.mccal.folio

import android.app.Activity
import android.content.Context
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

/** The two ways into StandBy. One screen, so a phone that is both charging and half open shows it once. */
internal enum class StandByEntry {
    /** Set down half open: the hinge posture Folio has always shown StandBy for. */
    POSE,

    /** Plugged in and left alone, on any pose, so a phone that does not fold has StandBy too. */
    CHARGING,
}

/**
 * The one place that decides whether StandBy should be on screen, and which way in brought it there. Pure, so the
 * decision is a JVM test rather than a phone on a charger (TST-1).
 *
 * The hinge wins when the phone is both charging and half open: the posture is the more specific fact, and it is what
 * decides how the two halves are laid out either side of the fold.
 *
 * [chargingEnabled] is the user's switch **and** the gate ([standByChargingOn]); when it is false the charger is not
 * consulted at all, so a phone that cannot see the feature does no work for it (REL-4a).
 */
internal fun standByEntry(
    pose: FoldingFeature.Orientation?,
    poseEnabled: Boolean,
    charging: Boolean,
    chargingEnabled: Boolean,
): StandByEntry? = when {
    poseEnabled && pose != null -> StandByEntry.POSE
    chargingEnabled && charging -> StandByEntry.CHARGING
    else -> null
}

/**
 * Whether the charger may bring StandBy up on this phone: the user's switch, and the gate that keeps the feature with
 * supporters until 0.6.8. Asked where the work starts, not in the UI, so nothing watches the charger while it is shut.
 */
internal fun standByChargingOn(context: Context, setting: Boolean): Boolean =
    setting && FeatureGate.STANDBY_CHARGING.isOpen(context)

/**
 * How long the phone has to be left alone before StandBy appears. Half open it is the moment it takes to set the phone
 * down; on a charger it is a screen saver, so it waits long enough that plugging in while you are reading Home does
 * not take Home away from you. Any touch starts the wait again.
 */
internal fun standByEnterDelayMs(entry: StandByEntry): Long = when (entry) {
    StandByEntry.POSE -> ENTER_DELAY_MS
    StandByEntry.CHARGING -> CHARGING_ENTER_DELAY_MS
}

/**
 * iPhone-style StandBy: a big clock, date, next alarm, battery and now playing. Dim red at night.
 *
 * Two ways in ([standByEntry]): the phone set down half open, and, for supporters until 0.6.8, the phone left alone on
 * a charger on any pose. Tap anywhere or press Back to leave; it stays gone until nothing is asking for it any more,
 * so leaving it while plugged in doesn't fight you. Unplugging or opening the phone flat ends it on its own.
 *
 * Dynamic class: D5 Ambient (the charger and the hinge, no user action).
 * Source of truth: [DeviceStatus.charging] and the WindowManager pose, both owned elsewhere and only read here.
 * When not visible: nothing beyond the state Home already collects for its status bar.
 *
 * [interactions] counts touches and keys on Home, so the charger's wait restarts whenever the phone is used.
 */
@Composable
internal fun StandByOverlay(pose: FoldingFeature.Orientation?, enabled: Boolean, chargingEnabled: Boolean,
    blocked: Boolean, status: DeviceStatus, interactions: androidx.compose.runtime.IntState) {
    val context = LocalContext.current
    val chargingOn = remember(context, chargingEnabled) { standByChargingOn(context, chargingEnabled) }
    val entry = standByEntry(pose, poseEnabled = enabled, charging = status.charging, chargingEnabled = chargingOn)
    var active by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    // Read only while the charger is the way in, so a touch on Home costs nothing the rest of the time.
    val touches = if (entry == StandByEntry.CHARGING) interactions.intValue else 0
    LaunchedEffect(entry, blocked, touches) {
        if (entry == null) { active = false; dismissed = false; return@LaunchedEffect }
        if (blocked || dismissed) return@LaunchedEffect
        delay(standByEnterDelayMs(entry)) // only once the phone has been left alone, not while folding or plugging in
        active = true
    }
    val view = LocalView.current
    DisposableEffect(active) { view.keepScreenOn = active; onDispose { view.keepScreenOn = false } }
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
            // The hinge says which way the two halves sit when the posture brought StandBy up: clock above the fold
            // in tabletop, beside it in a book. On a charger there may be no hinge to ask, so the window's own shape
            // decides and a phone that does not fold still splits the right way (ADP-1, ADP-15).
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val wide = maxWidth > maxHeight
                val sideBySide = if (pose != null) pose == FoldingFeature.Orientation.VERTICAL else wide
                if (sideBySide) Row(Modifier.fillMaxSize()) {
                    clock(Modifier.weight(1f).fillMaxHeight()); info(Modifier.weight(1f).fillMaxHeight())
                } else Column(Modifier.fillMaxSize()) {
                    clock(Modifier.weight(1f).fillMaxWidth()); info(Modifier.weight(1f).fillMaxWidth())
                }
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
            Icon(if (media.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", tint = ink,
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

/**
 * Half a minute of not being touched before the charger brings StandBy up. Long enough that plugging in while
 * you are using Home doesn't take Home away, short enough to be a screen saver; Settings says the same number.
 */
private const val CHARGING_ENTER_DELAY_MS = 30_000L
