package com.mccal.folio

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val UP_NEXT_WIDGET = -6
const val SUGGESTIONS_WIDGET = -7
const val BIG_CLOCK_WIDGET = -8

/** The size of a Home icon in dp, for widgets that show apps and must never draw them bigger than Home does. */
internal val LocalHomeIconSize = staticCompositionLocalOf { 66f }

/** How many icons the Suggestions widget shows, in how many columns and rows, and how big (dp). */
internal data class SuggestionsLayout(val columns: Int, val rows: Int, val icon: Float)

/**
 * Suggestions for a box of [width] x [height] dp: as many icons as fit at the Home icon size ([homeIcon]), up to 4 across
 * and 4 down, and smaller than that only when even one will not fit. Icons are never drawn bigger than Home's.
 */
internal fun suggestionsLayout(width: Float, height: Float, homeIcon: Float): SuggestionsLayout {
    val gap = 12f
    val wanted = homeIcon.coerceAtLeast(28f)
    // n icons need n - 1 gaps between them, so one gap is added to the box before dividing.
    val columns = ((width + gap) / (wanted + gap)).toInt().coerceIn(1, 4)
    val rows = ((height + gap) / (wanted + gap)).toInt().coerceIn(1, 4)
    val icon = minOf(wanted, (width + gap) / columns - gap, (height + gap) / rows - gap).coerceAtLeast(28f)
    return SuggestionsLayout(columns, rows, icon)
}

/** Apps and launching for built-in widgets that show apps (provided by Home). */
internal class HomeApps(val apps: List<AppEntry>, val launch: (AppEntry) -> Unit)
internal val LocalHomeApps = staticCompositionLocalOf { HomeApps(emptyList()) {} }

/** iOS Calendar "Up Next": the day, then the next events with their calendar color, or the next alarm when the day is clear. */
@Composable
internal fun UpNextCard(onClick: () -> Unit) {
    val context = LocalContext.current
    val ink = LocalHomeInk.current
    val tick by rememberMinuteTick()
    var allowed by remember { mutableStateOf(UpNext.hasCalendar(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    val events by produceState(emptyList<UpNextEvent>(), tick, allowed) { value = withContext(Dispatchers.IO) { UpNext.events(context) } }
    val alarm = remember(tick) { UpNext.nextAlarm(context) }
    val today = remember(tick) { LocalDate.now() }
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    fun time(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).let { t ->
        (if (t.toLocalDate() != today) t.format(DateTimeFormatter.ofPattern("EEE ")) else "") + t.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a"))
    }
    GlassCard(onClick = onClick) {
        Column {
            Text(today.format(DateTimeFormatter.ofPattern("EEEE")).uppercase(), color = FolioColors.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp)
            Text(today.dayOfMonth.toString(), color = ink.primary, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp)
        }
        when {
            !allowed -> Column(Modifier.clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).clickable { ask.launch(Manifest.permission.READ_CALENDAR) }) {
                Icon(Icons.Rounded.CalendarToday, null, tint = ink.secondary, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.show_up_next), color = ink.primary, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.allow_calendar_access), color = LocalAccent.current.ink, fontSize = FolioType.GROUP_LABEL.sp)
            }
            events.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(FolioSpace.SNUG.dp)) {
                events.take(2).forEach { e ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { UpNext.openEvent(context, e) }, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(e.color?.let { Color(it) } ?: LocalAccent.current.fill))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(e.title, color = ink.primary, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (e.allDay) stringResource(R.string.all_day) else time(e.begin), color = ink.secondary, fontSize = FolioType.GROUP_LABEL.sp, maxLines = 1)
                        }
                    }
                }
            }
            alarm != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Alarm, null, tint = FolioColors.Orange, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(time(alarm), color = ink.primary, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold)
            }
            else -> Text(stringResource(R.string.no_more_events_today), color = ink.secondary, fontSize = FolioType.FOOTNOTE.sp)
        }
    }
}

/**
 * Big Clock, like the iPhone Lock Screen: a large time straight on the wallpaper, with the date and what's next (the next
 * event today, or the next alarm) underneath. Calendar details only show once calendar access is allowed.
 *
 * Once customized ([BigClockStyle], from the widget's Customize row) it also takes a color, weight, size, typeface,
 * shadow, date format and alignment. Reading the picture under the clock needs Folio's own picture behind Home (Android
 * gives no API to read the system wallpaper's pixels), so Wallpaper and Custom fall back to the whole-wallpaper ink
 * without one. A clock that was never customized draws exactly what it always did.
 */
@Composable
internal fun BigClockCard(onClick: () -> Unit, slot: Int = -1, home: Boolean = false) {
    val context = LocalContext.current
    val ink = LocalHomeInk.current
    val tick by rememberMinuteTick()
    val screenshot by ScreenshotMode.on.collectAsStateWithLifecycle()
    val now = displayNow(tick)
    val systemIs24 = android.text.format.DateFormat.is24HourFormat(context)
    val allowed = remember(tick) { UpNext.hasCalendar(context) }
    val event by produceState<UpNextEvent?>(null, tick, allowed, screenshot) {
        value = if (!allowed || screenshot) null else withContext(Dispatchers.IO) { UpNext.events(context, limit = 1).firstOrNull() }
    }
    val alarm = remember(tick, screenshot) { if (screenshot) null else UpNext.nextAlarm(context) }
    val stored = LocalBigClockStyles.current.bySlot[slot]
    val style = stored.orDefault()
    val is24 = when (style.hours) { "24" -> true; "12" -> false; else -> systemIs24 }
    val today = now.toLocalDate()
    fun time(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).let { t ->
        (if (t.toLocalDate() != today) t.format(DateTimeFormatter.ofPattern("EEE ")) else "") + t.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a"))
    }
    var boxInWindow by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val window = LocalWindowInfo.current.containerSize
    val fraction = remember(boxInWindow, window) {
        if (boxInWindow.isEmpty || window.width <= 0 || window.height <= 0) androidx.compose.ui.geometry.Rect.Zero
        else androidx.compose.ui.geometry.Rect(boxInWindow.left / window.width, boxInWindow.top / window.height,
            boxInWindow.right / window.width, boxInWindow.bottom / window.height)
    }
    // Only a customized clock reads the picture; an untouched one keeps the whole-wallpaper ink it always had.
    val sample = rememberClockInkSample(fraction, enabled = stored != null && launcherBackgroundEnabled(context))
    val resolved = if (stored == null) null else resolveClockInk(style, sample, fallbackDark = ink.dark)
    // Everything about the clock eases to a new value instead of jumping, so the weight slider and a Look change glide.
    val textColor by animateColorAsState(resolved?.color ?: ink.primary, clockMotion(), label = "clock ink")
    val secondaryColor = if (resolved != null) textColor.copy(alpha = .75f) else ink.secondary
    val animatedWeight by animateFloatAsState(style.weight.toFloat(), clockMotion(), label = "clock weight")
    val animatedSize by animateFloatAsState(style.size, clockMotion(), label = "clock size")
    val shadeAlpha by animateFloatAsState(if (resolved?.shade == true) .4f else 0f, clockMotion(), label = "clock shade")
    val lightInk = if (resolved != null) resolved.color == Color.White else !ink.dark
    val shadow: androidx.compose.ui.graphics.Shadow? = when (style.shadow) {
        "OFF" -> null
        "GLOW" -> androidx.compose.ui.graphics.Shadow(Color.White.copy(alpha = if (lightInk) .6f else .75f), androidx.compose.ui.geometry.Offset.Zero, 28f)
        else -> androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = if (lightInk) .25f else 0f), blurRadius = 8f)
    }
    val timeFamily = remember(style.face, style.weight) { clockFontFamily(style.face, style.weight) }
    val dateFamily = remember(style.face) { clockFontFamily(style.face, 600) }
    val align = when (style.align) { "LEFT" -> Alignment.Start; "RIGHT" -> Alignment.End; else -> Alignment.CenterHorizontally }
    if (home && slot >= 0) {
        LaunchedEffect(boxInWindow) { if (!boxInWindow.isEmpty) BigClockBounds.bySlot[slot] = boxInWindow }
        androidx.compose.runtime.DisposableEffect(slot) { onDispose { BigClockBounds.bySlot.remove(slot) } }
    }
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(FolioRadius.PANEL.dp)).widgetTap(onClick)
        .onGloballyPositioned { boxInWindow = it.boundsInWindow() }
        .semantics(mergeDescendants = true) {}, contentAlignment = Alignment.Center) {
        val boxHeight = maxHeight.value
        val boxWidth = maxWidth.value
        val big = (maxHeight.value * .46f * animatedSize).coerceAtMost(maxWidth.value * .34f).sp
        if (shadeAlpha > 0f) Box(Modifier.fillMaxSize().background(
            androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color.Black.copy(alpha = shadeAlpha), Color.Transparent))))
        Column(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.SMALL.dp), horizontalAlignment = align) {
            if (style.date != "OFF") Text(now.format(DateTimeFormatter.ofPattern(
                stringResource(if (style.date == "SHORT") R.string.eee_mmm_d_2 else R.string.eeee_mmmm_d))), color = textColor,
                fontSize = (big.value * .2f).coerceIn(13f, 20f).sp, fontWeight = FontWeight.SemiBold, fontFamily = dateFamily,
                style = androidx.compose.ui.text.TextStyle(shadow = shadow))
            val clockStyle = androidx.compose.ui.text.TextStyle(shadow = shadow, fontFeatureSettings = "tnum")
            val weight = FontWeight(animatedWeight.toInt().coerceIn(100, 900))
            val suffix = if (!is24 && style.ampm) now.format(DateTimeFormatter.ofPattern("a")) else ""
            if (style.stacked) {
                // Two lines plus the date and the next event have to fit the widget's height, so size from the height, not the one-line size.
                val stackedSize = minOf(boxHeight * .27f * animatedSize, boxWidth * .3f).sp
                Text(now.format(DateTimeFormatter.ofPattern(if (is24) "HH" else "h")), color = textColor, fontSize = stackedSize, fontWeight = weight,
                    fontFamily = timeFamily, lineHeight = stackedSize * .9f, maxLines = 1, style = clockStyle)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(now.format(DateTimeFormatter.ofPattern("mm")), color = textColor, fontSize = stackedSize, fontWeight = weight,
                        fontFamily = timeFamily, lineHeight = stackedSize * .9f, maxLines = 1, style = clockStyle)
                    if (suffix.isNotEmpty()) Text(suffix, color = secondaryColor, fontSize = (stackedSize.value * .28f).sp, fontWeight = FontWeight.SemiBold,
                        fontFamily = dateFamily, modifier = Modifier.padding(start = 3.dp, bottom = 6.dp), style = androidx.compose.ui.text.TextStyle(shadow = shadow))
                }
            } else Row(verticalAlignment = Alignment.Bottom) {
                Text(now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm")), color = textColor, fontSize = big, fontWeight = weight,
                    fontFamily = timeFamily, lineHeight = big * 1.02f, maxLines = 1, style = clockStyle)
                if (suffix.isNotEmpty()) Text(suffix, color = secondaryColor, fontSize = (big.value * .28f).sp, fontWeight = FontWeight.SemiBold,
                    fontFamily = dateFamily, modifier = Modifier.padding(start = 3.dp, bottom = (big.value * .12f).dp), style = androidx.compose.ui.text.TextStyle(shadow = shadow))
            }
            val allDay = stringResource(R.string.all_day)
            val next = event?.let { e -> (if (e.allDay) allDay else time(e.begin)) + " · " + e.title }
                ?: alarm?.let { "Alarm · " + time(it) }
            if (next != null && style.showNext) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (event != null) Icons.Rounded.CalendarToday else Icons.Rounded.Alarm, null, tint = secondaryColor, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(next, color = secondaryColor, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = dateFamily,
                    style = androidx.compose.ui.text.TextStyle(shadow = shadow))
            }
        }
    }
}

/** Siri Suggestions-style widget: apps you usually open around now, as many as fit. */
@Composable
internal fun SuggestionsCard(onEdit: () -> Unit) {
    val context = LocalContext.current
    val home = LocalHomeApps.current
    val tick by rememberMinuteTick()
    // Re-rank every quarter hour, not every minute: suggestions shouldn't shuffle under a finger.
    val quarter = tick / 15
    val apps by produceState(emptyList<AppEntry>(), home.apps, quarter) { value = withContext(Dispatchers.IO) { Suggestions.forNow(context, home.apps, limit = 16) } }
    GlassCard(onClick = onEdit) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val layout = suggestionsLayout(maxWidth.value, maxHeight.value, LocalHomeIconSize.current)
            val columns = layout.columns
            val rows = layout.rows
            val icon = layout.icon.dp
            if (apps.isEmpty()) Text(stringResource(R.string.suggestions_appear_as_you_use_your_apps), color = LocalHomeInk.current.secondary, fontSize = FolioType.FOOTNOTE.sp,
                modifier = Modifier.align(Alignment.Center))
            else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                apps.take(columns * rows).chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { app ->
                            AppIcon(app, app.label, Modifier.size(icon).clip(RoundedCornerShape(icon * .24f)).clickable { home.launch(app) },
                                shape = RoundedCornerShape(icon * .24f), badge = false)
                        }
                        repeat(columns - row.size) { Spacer(Modifier.size(icon)) }
                    }
                }
            }
        }
    }
}
