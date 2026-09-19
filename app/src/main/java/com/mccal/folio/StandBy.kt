package com.mccal.folio

import android.app.Activity
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

/**
 * iPhone-style StandBy for a phone set down half-open: a big clock, date, next alarm, battery and
 * now playing. Dim red at night. Tap anywhere or open the phone flat to leave.
 */
@Composable
internal fun StandByOverlay(pose: FoldingFeature.Orientation?, enabled: Boolean, blocked: Boolean, status: DeviceStatus) {
    var active by remember { mutableStateOf(false) }
    var dismissedForPose by remember { mutableStateOf(false) }
    LaunchedEffect(pose, enabled, blocked) {
        if (pose == null) { active = false; dismissedForPose = false; return@LaunchedEffect }
        if (!enabled || blocked || dismissedForPose) return@LaunchedEffect
        delay(ENTER_DELAY_MS) // only after the phone has been set down, not while folding through
        active = true
    }
    val view = LocalView.current
    DisposableEffect(active) { view.keepScreenOn = active; onDispose { view.keepScreenOn = false } }
    BackHandler(active) { active = false; dismissedForPose = true }

    AnimatedVisibility(active, enter = fadeIn(tween(500)), exit = fadeOut(tween(300))) {
        val tick by rememberMinuteTick()
        val now = displayNow(tick)
        val night = now.hour >= 22 || now.hour < 6
        val ink = if (night) Color(0xFFB3261E) else Color.White
        val soft = ink.copy(alpha = if (night) .75f else .6f)
        Box(Modifier.fillMaxSize().background(Color.Black)
            .clickable(remember { MutableInteractionSource() }, null) { active = false; dismissedForPose = true }) {
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
                Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(if (night) soft else e.color?.let { Color(it) } ?: FolioColors.Blue))
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
