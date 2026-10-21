package com.mccal.folio

import android.app.NotificationManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Why the Dynamic Island is or is not showing over other apps, most important first. */
internal enum class IslandReason { SAFE_MODE, IN_EVERY_APP_OFF, GESTURES_SERVICE_OFF, NOTIFICATION_ACCESS_OFF, DO_NOT_DISTURB, ANDROID_POPUP, READY }

internal enum class IslandLevel { GOOD, WARNING, PROBLEM, INFO, OFF }

internal data class IslandStatusInputs(
    val safeMode: Boolean, val everywhere: Boolean, val gesturesService: Boolean, val notificationAccess: Boolean,
    val doNotDisturb: Boolean, val avoidDouble: Boolean,
    /** Message pop-ups or other-notification alerts are on, so there is something for Android's own pop-up to take over. */
    val popUpsOn: Boolean = true,
)

/** The headline reason and, when there is more than one, the others to mention under it. */
internal data class IslandStatus(val headline: IslandReason, val notes: List<IslandReason> = emptyList())

/**
 * What stops the island from showing over other apps, or holds back its message pop-ups. Found on the Fold8 (21 Oct 2026): music,
 * calls and timers always show, but message pop-ups are quiet under Do Not Disturb, and with "Don't double up with Android pop-ups" (on
 * to start with) every notification Android already pops up is left to Android, which is most messaging apps. Both are intended
 * and both look like "the island doesn't work", so the page says so.
 */
internal fun islandStatus(i: IslandStatusInputs): IslandStatus = when {
    i.safeMode -> IslandStatus(IslandReason.SAFE_MODE)
    !i.everywhere -> IslandStatus(IslandReason.IN_EVERY_APP_OFF)
    !i.gesturesService -> IslandStatus(IslandReason.GESTURES_SERVICE_OFF)
    !i.notificationAccess -> IslandStatus(IslandReason.NOTIFICATION_ACCESS_OFF)
    i.doNotDisturb -> IslandStatus(IslandReason.DO_NOT_DISTURB, if (i.avoidDouble && i.popUpsOn) listOf(IslandReason.ANDROID_POPUP) else emptyList())
    i.avoidDouble && i.popUpsOn -> IslandStatus(IslandReason.ANDROID_POPUP)
    else -> IslandStatus(IslandReason.READY)
}

internal fun IslandReason.level() = when (this) {
    IslandReason.READY -> IslandLevel.GOOD
    IslandReason.DO_NOT_DISTURB -> IslandLevel.WARNING
    IslandReason.ANDROID_POPUP -> IslandLevel.INFO
    IslandReason.GESTURES_SERVICE_OFF, IslandReason.NOTIFICATION_ACCESS_OFF -> IslandLevel.PROBLEM
    IslandReason.IN_EVERY_APP_OFF, IslandReason.SAFE_MODE -> IslandLevel.OFF
}

/** True when the reason has something the person can do from the card. */
internal fun IslandReason.hasAction() = this != IslandReason.READY && this != IslandReason.IN_EVERY_APP_OFF && this != IslandReason.SAFE_MODE

/** Reads the phone's state for [islandStatus]; call it again when the person comes back from Android's settings. */
internal fun readIslandStatusInputs(context: Context, state: LauncherState): IslandStatusInputs {
    val manager = context.getSystemService(NotificationManager::class.java)
    val filter = manager?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    return IslandStatusInputs(
        safeMode = SafeMode.active, everywhere = state.islandEverywhere,
        gesturesService = SystemShadeAccessibilityService.isConnected(),
        notificationAccess = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName),
        doNotDisturb = filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
        avoidDouble = state.messagesAvoidDouble,
        popUpsOn = "MESSAGE" !in state.islandEventsOff || state.islandAlerts,
    )
}

@Composable
private fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    // Do Not Disturb can change from Quick Settings without this page pausing, so its change refreshes the card too.
    val context = LocalContext.current
    androidx.compose.runtime.DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() { override fun onReceive(c: Context?, i: android.content.Intent?) { tick++ } }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver,
            android.content.IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return tick
}

private fun IslandReason.title(): Int = when (this) {
    IslandReason.READY -> R.string.island_status_ready_title
    IslandReason.DO_NOT_DISTURB -> R.string.island_status_dnd_title
    IslandReason.ANDROID_POPUP -> R.string.island_status_popup_title
    IslandReason.GESTURES_SERVICE_OFF -> R.string.island_status_service_title
    IslandReason.NOTIFICATION_ACCESS_OFF -> R.string.island_status_access_title
    IslandReason.IN_EVERY_APP_OFF -> R.string.island_status_off_title
    IslandReason.SAFE_MODE -> R.string.island_status_safe_title
}

private fun IslandReason.body(): Int = when (this) {
    IslandReason.READY -> R.string.island_status_ready_body
    IslandReason.DO_NOT_DISTURB -> R.string.island_status_dnd_body
    IslandReason.ANDROID_POPUP -> R.string.island_status_popup_body
    IslandReason.GESTURES_SERVICE_OFF -> R.string.island_status_service_body
    IslandReason.NOTIFICATION_ACCESS_OFF -> R.string.island_status_access_body
    IslandReason.IN_EVERY_APP_OFF -> R.string.island_status_off_body
    IslandReason.SAFE_MODE -> R.string.island_status_safe_body
}

private fun IslandReason.action(): Int? = when (this) {
    IslandReason.DO_NOT_DISTURB -> R.string.island_status_dnd_action
    IslandReason.ANDROID_POPUP -> R.string.island_status_popup_action
    IslandReason.GESTURES_SERVICE_OFF -> R.string.island_status_service_action
    IslandReason.NOTIFICATION_ACCESS_OFF -> R.string.island_status_access_action
    else -> null
}

/** The status card at the top of Settings > Dynamic Island. [onAction] gets the headline reason when its button is tapped. */
@Composable
internal fun IslandStatusCard(state: LauncherState, onAction: (IslandReason) -> Unit) {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    val status = remember(tick, state.islandEverywhere, state.messagesAvoidDouble) { islandStatus(readIslandStatusInputs(context, state)) }
    val headline = status.headline
    val dot = when (headline.level()) {
        IslandLevel.GOOD -> FolioColors.Green
        IslandLevel.WARNING -> Color(FolioColors.Value.Orange)
        IslandLevel.PROBLEM -> FolioColors.Red
        IslandLevel.INFO -> Color(FolioColors.Value.Blue)
        IslandLevel.OFF -> Color.White.copy(alpha = .4f)
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.GROUP.dp)).background(Color.White.copy(alpha = .07f))
        .padding(FolioSpace.LARGE.dp).testTag("island-status"), horizontalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
        Box(Modifier.padding(top = 6.dp).size(12.dp).clip(CircleShape).background(dot))
        Column(Modifier.weight(1f)) {
            Text(stringResource(headline.title()), color = Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(headline.body()), color = Color.White.copy(alpha = .8f), fontSize = FolioType.SUBHEAD.sp, lineHeight = 21.sp,
                modifier = Modifier.padding(top = 2.dp))
            status.notes.forEach { note ->
                Text(stringResource(note.noteText()), color = Color.White.copy(alpha = .8f), fontSize = FolioType.SUBHEAD.sp, lineHeight = 21.sp,
                    modifier = Modifier.padding(top = FolioSpace.SMALL.dp))
            }
            headline.action()?.let { action ->
                Text(stringResource(action), color = LocalAccent.current.ink, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp).heightIn(min = 48.dp).clickable(role = Role.Button) { onAction(headline) }
                        .padding(vertical = FolioSpace.COMPACT.dp).semantics { role = Role.Button }.testTag("island-status-action"))
            }
        }
    }
}

/** A reason shown as a second line under the headline. Only Android's pop-up has wording for it today. */
private fun IslandReason.noteText(): Int = when (this) {
    IslandReason.ANDROID_POPUP -> R.string.island_status_popup_note
    else -> body()
}
