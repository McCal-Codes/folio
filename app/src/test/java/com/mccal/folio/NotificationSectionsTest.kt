package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationSectionsTest {
    private fun bucket(ongoing: Boolean = false, clearable: Boolean = true, importance: Int = 3, conversation: Boolean = false, urgent: Boolean = false) =
        notificationBucket(ongoing, clearable, importance, conversation, urgent)

    @Test fun `silent channels are Quiet, loud ones are not`() {
        assertEquals(NotificationBucket.QUIET, bucket(importance = IMPORTANCE_LOW))
        assertEquals(NotificationBucket.QUIET, bucket(importance = IMPORTANCE_MIN))
        assertEquals(NotificationBucket.MAIN, bucket(importance = 3))
        assertEquals(NotificationBucket.MAIN, bucket(importance = 4))
    }

    @Test fun `a notification Android has not ranked stays in the main pile, never Quiet`() {
        assertEquals(NotificationBucket.MAIN, bucket(importance = IMPORTANCE_UNKNOWN))
    }

    @Test fun `urgent and conversations are never put in Quiet whatever the channel says`() {
        assertEquals(NotificationBucket.MAIN, bucket(importance = IMPORTANCE_MIN, urgent = true))
        assertEquals(NotificationBucket.CONVERSATIONS, bucket(importance = IMPORTANCE_LOW, conversation = true))
        assertEquals(NotificationBucket.CONVERSATIONS, bucket(importance = IMPORTANCE_LOW, conversation = true, urgent = true))
    }

    @Test fun `what is ongoing and can't be cleared is Now, and only there`() {
        assertEquals(NotificationBucket.NOW, bucket(ongoing = true, clearable = false))
        assertEquals(NotificationBucket.NOW, bucket(ongoing = true, clearable = false, importance = IMPORTANCE_LOW, urgent = true))
        // An ongoing notification the person can clear stays in the pile like any other.
        assertEquals(NotificationBucket.MAIN, bucket(ongoing = true, clearable = true))
    }

    private fun item(key: String, pkg: String, time: Long, importance: Int = 3, conversation: Boolean = false) =
        NotificationItem(key, pkg, pkg, null, "t", "x", time, true, null, importance = importance, conversation = conversation)

    @Test fun `sections off is one unlabelled list by app, newest first`() {
        val out = sectionNotifications(listOf(item("a", "p1", 1), item("b", "p2", 5), item("c", "p1", 9, importance = IMPORTANCE_MIN)), sections = false)
        assertEquals(1, out.size)
        assertEquals(null, out[0].first)
        assertEquals(listOf("p1", "p2"), out[0].second.map { it.first().packageName })
        assertEquals(3, out[0].second.sumOf { it.size })
    }

    @Test fun `sections on keep every notification exactly once, in the order Now, Conversations, main, Quiet`() {
        val items = listOf(item("q", "p1", 1, importance = IMPORTANCE_LOW), item("m", "p2", 2), item("c", "p3", 3, conversation = true),
            item("m2", "p1", 4))
        val out = sectionNotifications(items, sections = true)
        assertEquals(listOf(NotificationBucket.CONVERSATIONS, NotificationBucket.MAIN, NotificationBucket.QUIET), out.map { it.first })
        assertEquals(items.map { it.key }.toSet(), out.flatMap { it.second.flatten() }.map { it.key }.toSet())
        assertEquals(items.size, out.sumOf { section -> section.second.sumOf { it.size } })
    }
}
