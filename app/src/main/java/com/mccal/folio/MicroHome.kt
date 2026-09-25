package com.mccal.folio

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tier C: tiny, square-ish windows (flip-phone cover screens) where a full Home would shrink below tappable sizes. */
fun isMicroWindow(widthDp: Float, heightDp: Float) = minOf(widthDp, heightDp) <= 320f && maxOf(widthDp, heightDp) < 480f

/** How many 48 dp app icons fit in a row of [widthDp], with [MICRO_SIDE] margins and [MICRO_GAP] between them. */
fun microAppCount(widthDp: Float): Int =
    ((widthDp - 2 * MICRO_SIDE + MICRO_GAP) / (MICRO_ICON + MICRO_GAP)).toInt().coerceIn(0, 6)

private const val MICRO_ICON = 48f
private const val MICRO_GAP = 10f
private const val MICRO_SIDE = 18f

/**
 * A focused Home for tiny cover screens: the time, your first dock apps, what's playing, and ways into Notification
 * Center and Spotlight. Same apps and look as the full Home, just fewer things at once.
 */
@Composable
internal fun MicroHome(apps: List<AppEntry>, status: DeviceStatus, width: Dp, height: Dp,
    onLaunch: (AppEntry) -> Unit, onNotifications: () -> Unit, onSearch: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    // The shared minute tick rather than a loop of its own (DYN-14).
    val now by rememberMinuteTick()
    val roomy = minOf(width, height) >= 300.dp
    val media = IslandListenerService.activity.collectAsStateWithLifecycle().value as? IslandActivity.Media
    val notifications = IslandListenerService.notifications.collectAsStateWithLifecycle().value.size
    // Like the iOS Lock Screen: no AM/PM, so the time always fits one line.
    val time = android.text.format.DateFormat.format(
        if (android.text.format.DateFormat.is24HourFormat(context)) "H:mm" else "h:mm", now).toString()
    val timeSize = minOf(if (roomy) 52f else 44f, (width.value - 2 * MICRO_SIDE) / 3.2f)
    val date = android.text.format.DateFormat.format("EEE, MMM d", now).toString() +
        (status.battery?.let { " · $it%" } ?: "")
    val shown = apps.take(microAppCount(width.value))
    Column(Modifier.fillMaxSize().padding(horizontal = MICRO_SIDE.dp, vertical = FolioSpace.COMFY.dp).testTagMicro(),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        // Tap the time for Notification Center; hold it for Folio's settings.
        Column(Modifier.clip(RoundedCornerShape(FolioRadius.GROUP.dp))
            .combinedClickable(onClickLabel = "Notification Center", onLongClickLabel = "Folio Settings",
                onLongClick = onSettings, onClick = onNotifications)
            .padding(horizontal = FolioSpace.COMPACT.dp, vertical = FolioSpace.HAIR.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(time, color = Color.White, fontSize = timeSize.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, softWrap = false, modifier = Modifier.semantics { heading() })
            Text(date, color = Color.White.copy(alpha = .75f), fontSize = FolioType.FOOTNOTE.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (shown.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(MICRO_GAP.dp)) {
            shown.forEach { app ->
                Box(Modifier.size(MICRO_ICON.dp).clickable(onClickLabel = "Open ${app.label}") { onLaunch(app) }) {
                    AppIcon(app, app.label, Modifier.size(MICRO_ICON.dp), shape = RoundedCornerShape(12.dp))
                }
            }
        }
        media?.let { MicroNowPlaying(it) }
        if (roomy) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MicroChip(if (notifications == 1) "1 notification" else "$notifications notifications", null, onNotifications)
            MicroChip("Search", Icons.Rounded.Search, onSearch)
        }
    }
}

@Composable
private fun MicroNowPlaying(media: IslandActivity.Media) {
    val controls = runCatching { media.controller.transportControls }.getOrNull()
    val open = runCatching { media.controller.sessionActivity }.getOrNull()
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(FolioRadius.PANEL.dp)).background(Color.White.copy(alpha = .14f))
        .clickable(enabled = open != null, onClickLabel = "Open ${media.title}") { runCatching { open?.send() } }
        .padding(start = FolioSpace.SMALL.dp, end = FolioSpace.TINY.dp), verticalAlignment = Alignment.CenterVertically) {
        val art = media.art ?: media.icon
        if (art != null) Image(art.asImageBitmap(), null, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Text(media.title, color = Color.White, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            media.subtitle?.let { Text(it, color = Color.White.copy(alpha = .65f), fontSize = FolioType.GROUP_LABEL.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = .9f))
            .clickable(onClickLabel = if (media.playing) "Pause" else "Play") { if (media.playing) controls?.pause() else controls?.play() },
            contentAlignment = Alignment.Center) {
            Icon(if (media.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun MicroChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit) {
    Row(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(FolioRadius.PANEL.dp)).clickable(onClick = onClick)
        .padding(horizontal = FolioSpace.TINY.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clip(RoundedCornerShape(FolioRadius.CARD.dp)).background(Color.White.copy(alpha = .14f)).padding(horizontal = FolioSpace.COMPACT.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, null, tint = Color.White, modifier = Modifier.size(14.dp)); Spacer(Modifier.size(4.dp)) }
            Text(label, color = Color.White, fontSize = FolioType.GROUP_LABEL.sp, maxLines = 1)
        }
    }
}

private fun Modifier.testTagMicro() = then(Modifier.testTag("micro-home"))
