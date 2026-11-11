package com.mccal.folio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.format.DateTimeFormatter

/**
 * The heads-up that a supporter code is about to end, and the note that it has. Run from the daily update check and
 * whenever Folio opens, so Manual update mode doesn't silence it. Each code gets each reminder once, remembered by its serial, so the
 * daily check never repeats itself; redeeming a new code starts fresh. It says nothing about a code that does not
 * end, a phone with no code, or one that has turned notifications off.
 */
internal object SupporterReminder {
    private const val PREFS = "folio"
    private const val CHANNEL = "supporter_reminder"
    private const val NOTIFICATION_ID = 4202

    private fun sentKey(serial: Long) = "supporter_reminded_$serial"

    /** Whether [stage] still needs sending for a code whose last sent stage is [sent] (-1 for none): each is sent in order, once. */
    internal fun due(stage: SupporterReminderStage?, sent: Int): Boolean = stage != null && stage.ordinal > sent

    fun check(context: Context) {
        val state = runCatching { Supporter.state(context) }.getOrNull() ?: return
        val serial = when (state) { is SupporterState.Active -> state.serial; is SupporterState.Expired -> state.serial; SupporterState.None -> return }
        val stage = supporterReminderStage(state)
        val prefs = context.getSharedPreferences(PREFS, 0)
        if (!due(stage, prefs.getInt(sentKey(serial), -1))) return
        if (!SoftwareUpdate.canPostNotifications(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.supporter_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.supporter_reminder_channel_description) })
        val date = (state as? SupporterState.Active)?.ends?.format(DateTimeFormatter.ofPattern(context.getString(R.string.d_mmm_yyyy)))
        val (title, text) = when (stage) {
            SupporterReminderStage.SOON -> context.getString(R.string.supporter_soon_title) to context.getString(R.string.supporter_soon_text, date ?: "")
            else -> context.getString(R.string.supporter_ended_title) to context.getString(R.string.supporter_ended_text)
        }
        val open = PendingIntent.getActivity(context, NOTIFICATION_ID, Intent(Intent.ACTION_VIEW, android.net.Uri.parse(KOFI_URL)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val sent = runCatching {
            manager.notify(NOTIFICATION_ID, Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(open).setAutoCancel(true).build())
        }.isSuccess
        if (sent) prefs.edit().putInt(sentKey(serial), stage!!.ordinal).apply()
    }
}
