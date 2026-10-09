package com.mccal.folio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** One newer version a refresh found: what it is called and the version on offer. */
internal data class PendingUpdate(val id: String, val name: String, val version: String)

/**
 * "2 updates in the Market": one notice per set of new versions, never the same set twice. The wording and the
 * deciding are plain functions; [post] is the only part that touches Android.
 */
internal object MarketUpdateNotice {
    private const val CHANNEL = "market_updates"
    private const val NOTIFICATION_ID = 4105
    private const val MAX_NAMED = 3

    /** What the set is, for remembering it: the same ids at the same versions give the same signature, in any order. */
    fun signature(updates: List<PendingUpdate>): String = updates.map { "${it.id}@${it.version}" }.sorted().joinToString(",")

    /** A notice is worth posting when there is something to say that was not already said. */
    fun shouldNotify(updates: List<PendingUpdate>, lastSignature: String?): Boolean =
        updates.isNotEmpty() && signature(updates) != lastSignature

    /** "Keyd 0.4.1, Duet 1.2": the first few by name and version, then how many more. */
    fun names(updates: List<PendingUpdate>, andMore: (Int) -> String): String {
        val shown = updates.take(MAX_NAMED).joinToString(", ") { "${it.name} ${it.version}" }
        return if (updates.size > MAX_NAMED) shown + ", " + andMore(updates.size - MAX_NAMED) else shown
    }

    /** Posts the notice, when notifications are allowed, and says whether it did: a set that was not announced is not remembered as announced. Tapping it opens the Market's updates. */
    fun post(context: Context, updates: List<PendingUpdate>): Boolean {
        if (updates.isEmpty() || !SoftwareUpdate.canPostNotifications(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.market_updates_channel), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.market_updates_channel_detail) })
        val open = PendingIntent.getActivity(context, 3, Intent(Intent.ACTION_VIEW, "folio://market/updates".toUri(), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = context.resources.getQuantityString(R.plurals.market_updates_notice, updates.size, updates.size)
        val text = names(updates) { context.getString(R.string.market_updates_and_more, it) }
        return runCatching {
            manager.notify(NOTIFICATION_ID, Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title).setContentText(text).setContentIntent(open).setAutoCancel(true).build())
        }.isSuccess
    }

    /**
     * The newer versions the sources now list for what is installed: packages by version, apps by what Android has.
     * Read-only; nothing is installed.
     */
    fun pending(context: Context, session: MarketSession): List<PendingUpdate> {
        val installed = session.installed().associateBy { it.id }
        val entries = session.entries()
        // The same rules as an automatic update: never announce one the Market would refuse (pulled, clashing, needing another).
        val packages = entries.filter { e -> e.revokedReason == null && e.clash == null && e.entry.needs.isEmpty() && installed[e.id]?.let { e.entry.version > it.version } == true }
        val apps = entries.filter { MarketAppUpdate.offered(context, it) != null }
        val locale = listOf(java.util.Locale.getDefault().toLanguageTag())
        return (packages + apps).distinctBy { it.id }.map { PendingUpdate(it.id, it.entry.manifest?.name?.resolve(locale) ?: it.id, it.entry.version.text) }
    }
}
