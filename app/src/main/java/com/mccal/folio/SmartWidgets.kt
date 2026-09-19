package com.mccal.folio

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

/** Apps and launching for built-in widgets that show apps (provided by Home). */
internal class HomeApps(val apps: List<AppEntry>, val launch: (AppEntry) -> Unit)
internal val LocalHomeApps = staticCompositionLocalOf { HomeApps(emptyList()) {} }

/** iOS Calendar "Up Next": the day, then the next events with their calendar color, or the next alarm when the day is clear. */
@Composable
internal fun UpNextCard(onEdit: () -> Unit) {
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
    GlassCard(onClick = onEdit) {
        Column {
            Text(today.format(DateTimeFormatter.ofPattern("EEEE")).uppercase(), color = FolioColors.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp)
            Text(today.dayOfMonth.toString(), color = ink.primary, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp)
        }
        when {
            !allowed -> Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable { ask.launch(Manifest.permission.READ_CALENDAR) }) {
                Icon(Icons.Rounded.CalendarToday, null, tint = ink.secondary, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.show_up_next), color = ink.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.allow_calendar_access), color = FolioColors.Blue, fontSize = 12.sp)
            }
            events.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                events.take(2).forEach { e ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { UpNext.openEvent(context, e) }, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(e.color?.let { Color(it) } ?: FolioColors.Blue))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(e.title, color = ink.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (e.allDay) stringResource(R.string.all_day) else time(e.begin), color = ink.secondary, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
            alarm != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Alarm, null, tint = FolioColors.Orange, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(time(alarm), color = ink.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            else -> Text(stringResource(R.string.no_more_events_today), color = ink.secondary, fontSize = 13.sp)
        }
    }
}

/**
 * Big Clock, like the iPhone Lock Screen: a large time straight on the wallpaper, with the date and what's next (the next
 * event today, or the next alarm) underneath. Calendar details only show once calendar access is allowed.
 */
@Composable
internal fun BigClockCard(onEdit: () -> Unit) {
    val context = LocalContext.current
    val ink = LocalHomeInk.current
    val tick by rememberMinuteTick()
    val screenshot by ScreenshotMode.on.collectAsState()
    val now = displayNow(tick)
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val allowed = remember(tick) { UpNext.hasCalendar(context) }
    val event by produceState<UpNextEvent?>(null, tick, allowed, screenshot) {
        value = if (!allowed || screenshot) null else withContext(Dispatchers.IO) { UpNext.events(context, limit = 1).firstOrNull() }
    }
    val alarm = remember(tick, screenshot) { if (screenshot) null else UpNext.nextAlarm(context) }
    val today = now.toLocalDate()
    fun time(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).let { t ->
        (if (t.toLocalDate() != today) t.format(DateTimeFormatter.ofPattern("EEE ")) else "") + t.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a"))
    }
    val shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = if (ink.dark) 0f else .25f), blurRadius = 8f)
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).clickable(onClick = onEdit)
        .semantics(mergeDescendants = true) {}, contentAlignment = Alignment.Center) {
        val big = (maxHeight.value * .46f).coerceAtMost(maxWidth.value * .3f).sp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), color = ink.primary, fontSize = (big.value * .2f).coerceIn(13f, 20f).sp,
                fontWeight = FontWeight.SemiBold, style = androidx.compose.ui.text.TextStyle(shadow = shadow))
            Text(now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm")), color = ink.primary, fontSize = big,
                fontWeight = FontWeight.SemiBold, lineHeight = big * 1.02f, maxLines = 1,
                style = androidx.compose.ui.text.TextStyle(shadow = shadow, fontFeatureSettings = "tnum"))
            val allDay = stringResource(R.string.all_day)
            val next = event?.let { e -> (if (e.allDay) allDay else time(e.begin)) + " · " + e.title }
                ?: alarm?.let { "Alarm · " + time(it) }
            if (next != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (event != null) Icons.Rounded.CalendarToday else Icons.Rounded.Alarm, null, tint = ink.secondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(next, color = ink.secondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
    val apps by produceState(emptyList<AppEntry>(), home.apps, quarter) { value = withContext(Dispatchers.IO) { Suggestions.forNow(context, home.apps) } }
    GlassCard(onClick = onEdit) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = if (maxWidth > maxHeight * 1.5f) 4 else 2
            val rows = if (maxHeight > 140.dp) 2 else if (columns == 4) 1 else 2
            val icon = minOf(maxWidth / columns - 12.dp, maxHeight / rows - 12.dp).coerceAtLeast(28.dp)
            if (apps.isEmpty()) Text(stringResource(R.string.suggestions_appear_as_you_use_your_apps), color = LocalHomeInk.current.secondary, fontSize = 13.sp,
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
