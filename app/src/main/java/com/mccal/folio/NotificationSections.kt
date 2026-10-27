package com.mccal.folio

/** Where a notification sits in Notification Center when sections are on (Settings › Notifications › Panels). */
internal enum class NotificationBucket { NOW, CONVERSATIONS, MAIN, QUIET }

/** Android's own values (NotificationManager.IMPORTANCE_*), kept here so the rule below runs without a phone. */
internal const val IMPORTANCE_UNKNOWN = -1000
internal const val IMPORTANCE_MIN = 1
internal const val IMPORTANCE_LOW = 2

/**
 * One rule for where a notification goes. Quiet is closed by default, so only what is safe to miss lands there:
 *  - a notification Android hasn't ranked yet ([IMPORTANCE_UNKNOWN]), a conversation, and anything [urgent] (calls, alarms, timers,
 *    a channel that bypasses Do Not Disturb) stay in the main pile or Conversations, never in Quiet;
 *  - ongoing notifications that can't be cleared (music, a timer running) are Now, and are not in the pile as well.
 */
internal fun notificationBucket(ongoing: Boolean, clearable: Boolean, importance: Int, conversation: Boolean, urgent: Boolean): NotificationBucket = when {
    ongoing && !clearable -> NotificationBucket.NOW
    conversation -> NotificationBucket.CONVERSATIONS
    urgent -> NotificationBucket.MAIN
    importance in IMPORTANCE_MIN..IMPORTANCE_LOW -> NotificationBucket.QUIET
    else -> NotificationBucket.MAIN
}

internal val NotificationItem.bucket: NotificationBucket
    get() = notificationBucket(ongoing, clearable, importance, conversation, urgent)

/**
 * The notifications split into their sections, in the order they're drawn (empty ones left out), each as one list per app,
 * newest app first. With sections off it is one unlabelled section, which is how Notification Center always looked.
 */
internal fun sectionNotifications(items: List<NotificationItem>, sections: Boolean): List<Pair<NotificationBucket?, List<List<NotificationItem>>>> {
    fun byApp(list: List<NotificationItem>) = list.groupBy { it.packageName }.values.sortedByDescending { g -> g.maxOf { it.postTime } }
    if (!sections) return listOf(null to byApp(items))
    return NotificationBucket.entries.mapNotNull { bucket -> items.filter { it.bucket == bucket }.takeIf { it.isNotEmpty() }?.let { bucket to byApp(it) } }
}
