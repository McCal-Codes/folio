package com.mccal.folio

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The nudge back to unfinished setup, like iPhone's "Finish Setting Up": not on the day you set Folio up, then at most
 * every few days when you say Not Now, and never again once setup has been finished.
 */
internal object SetupReminder {
    private const val PREFS = "setup_experience"
    private const val FIRST_SEEN = "reminderFirstSeen"
    private const val SNOOZED_UNTIL = "reminderSnoozedUntil"
    private const val FINISHED = "setupFinished"
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    const val GRACE_MS = DAY_MS
    const val NOT_NOW_MS = 3 * DAY_MS
    const val CONTINUE_MS = DAY_MS

    fun due(stepsLeft: Int, finished: Boolean, firstSeen: Long, snoozedUntil: Long, now: Long): Boolean =
        stepsLeft > 0 && !finished && firstSeen > 0 && now - firstSeen >= GRACE_MS && now >= snoozedUntil

    /** Records what it learns (first time seen, setup finished) and answers whether to show the card now. */
    fun shouldShow(context: Context, stepsLeft: Int, now: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (stepsLeft == 0) { prefs.edit().putBoolean(FINISHED, true).apply(); return false }
        val firstSeen = prefs.getLong(FIRST_SEEN, 0L).takeIf { it > 0 } ?: now.also { prefs.edit().putLong(FIRST_SEEN, it).apply() }
        return due(stepsLeft, prefs.getBoolean(FINISHED, false), firstSeen, prefs.getLong(SNOOZED_UNTIL, 0L), now)
    }

    fun snooze(context: Context, forMs: Long, now: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(SNOOZED_UNTIL, now + forMs).apply()
    }
}

/** A progress ring with "done/total" inside (Settings' Finish Setting Up row and the Home card). */
@Composable
internal fun SetupRing(done: Int, total: Int, size: Dp, track: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(size).semantics { contentDescription = "$done of $total done" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.toPx() * .1f
            val inset = stroke / 2
            val arc = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            if (total > 0) drawArc(FolioColors.Green, -90f, 360f * done / total, false, Offset(inset, inset), arc,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("$done/$total", fontSize = (size.value * .26f).sp, fontWeight = FontWeight.SemiBold, color = FolioColors.Green)
    }
}

/** The Home card that brings you back to unfinished setup. */
@Composable
internal fun SetupReminderCard(isDefaultHome: Boolean, blocked: Boolean, onMakeDefault: () -> Unit, onShadeSetup: () -> Unit,
    onContinue: () -> Unit) {
    val context = LocalContext.current
    val required = rememberSetupSteps(isDefaultHome, onMakeDefault, onShadeSetup).filter { it.required }
    val left = required.count { !it.done }
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(left) { show = SetupReminder.shouldShow(context, left) }
    val visible = show && !blocked
    androidx.activity.compose.BackHandler(visible) { show = false; SetupReminder.snooze(context, SetupReminder.CONTINUE_MS) }
    val dark = LocalDuoPalette.current.dark
    val background = if (dark) FolioColors.SecondaryBackground else Color(0xFFF2F2F7)
    val primary = if (dark) Color.White else Color.Black
    val secondary = if (dark) Color(0xFF98989F) else Color(0xFF6C6C70)
    val reduceMotion = LocalReduceMotion.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible,
            enter = if (reduceMotion) fadeIn() else slideInVertically(FolioMotion.spring(FolioMotion.Settle)) { it } + fadeIn(),
            exit = if (reduceMotion) fadeOut() else slideOutVertically { it } + fadeOut()) {
            Column(Modifier.navigationBarsPadding().padding(12.dp).widthIn(max = 400.dp).fillMaxWidth()
                .clip(RoundedCornerShape(28.dp)).background(background).padding(18.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }.testTag("setup-reminder"),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SetupRing(required.size - left, required.size, 52.dp, secondary.copy(alpha = .25f))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.finish_setting_up_folio), color = primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(required.filterNot { it.done }.joinToString(", ") { it.title }, color = secondary, fontSize = 14.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReminderButton("Not Now", secondary.copy(alpha = .18f), primary, Modifier.weight(1f)) {
                        show = false; SetupReminder.snooze(context, SetupReminder.NOT_NOW_MS)
                    }
                    ReminderButton("Continue", FolioColors.Blue, Color.White, Modifier.weight(1f)) {
                        show = false; SetupReminder.snooze(context, SetupReminder.CONTINUE_MS); onContinue()
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderButton(text: String, background: Color, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(46.dp).clip(RoundedCornerShape(14.dp)).background(background).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(text, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
