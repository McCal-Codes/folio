package com.mccal.folio

import android.app.Notification
import android.app.Person
import android.os.Bundle
import androidx.core.os.BundleCompat

/**
 * What an app icon's badge shows (#561): the number of unread items when Android gives a reliable source, a dot when
 * Folio only knows a notification exists.
 *
 * A notification's own count is, in order:
 *  1. [Notification.number], when the app sets it above 0 (the app's own unread count);
 *  2. the messages of its MessagingStyle that came from someone else (your own replies are left out);
 *  3. otherwise unknown.
 *
 * Only counts are kept. The messages' text and senders are looked at while counting and never stored or logged.
 *
 * Android keeps at most 25 messages in a MessagingStyle, so a very busy chat tops out there; the number is "at least".
 */
internal object UnreadCount {
    /** The count for one notification, or 0 when there is no reliable source. */
    fun of(n: Notification): Int {
        if (n.number > 0) return n.number
        if (n.extras.getString(Notification.EXTRA_TEMPLATE)?.endsWith("MessagingStyle") != true) return 0
        val me = BundleCompat.getParcelable(n.extras, Notification.EXTRA_MESSAGING_PERSON, Person::class.java)
        val messages = BundleCompat.getParcelableArray(n.extras, Notification.EXTRA_MESSAGES, Bundle::class.java).orEmpty().filterIsInstance<Bundle>()
        return messages.count { !fromMe(it, me) }
    }

    private fun fromMe(message: Bundle, me: Person?): Boolean {
        val sender = BundleCompat.getParcelable(message, "sender_person", Person::class.java)
        if (sender == null) return message.getCharSequence("sender") == null // no sender at all means the user
        if (me == null) return false
        return if (sender.key != null && me.key != null) sender.key == me.key else sender.name == me.name
    }
}

/** A badge's number, and whether it is the whole story: false means some notification of the app could not be counted. */
internal data class BadgeTally(val count: Int, val exact: Boolean) {
    companion object {
        /**
         * The tally for one app's (and profile's) notifications that are still counted.
         *
         * The number is the sum of the reliable per-notification counts. A group summary is never listed, so one with
         * its own count stands for all of its children, once, instead of being added to them. The number is exact
         * (shown as a number) only when every notification that is an unread item has a reliable count; an ongoing one
         * (a download, a call, "running in the background") is not an unread item and never spoils it. With nothing
         * reliable at all the count is how many notifications there are, shown as a dot.
         */
        fun of(items: List<NotificationItem>): BadgeTally {
            val (grouped, single) = items.partition { it.group != null && it.groupUnread > 0 }
            val reliable = grouped.groupBy { it.group }.values.sumOf { it.first().groupUnread } +
                single.sumOf { it.unread.coerceAtLeast(0) }
            if (reliable <= 0) return BadgeTally(items.size, exact = false)
            val unknown = single.count { it.unread <= 0 && !it.ongoing }
            return BadgeTally(reliable, exact = unknown == 0)
        }
    }
}

/** The style an icon's badge is drawn in: a dot when [dot] says the number is not reliable, whatever the setting. */
internal fun badgeShape(style: BadgeStyle, dot: Boolean) = if (dot && style == BadgeStyle.COUNT) BadgeStyle.DOT else style
