package com.mccal.folio

import android.app.Notification
import android.app.Person
import android.content.Context
import android.os.UserHandle
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Badges show a real unread number where Android gives a reliable source and a dot where Folio only knows a
 * notification exists (#561, second half).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BadgeCountsTest {
    private val kakao = "com.kakao.talk"
    private val clone: UserHandle = UserHandle.getUserHandleForUid(95 * 100_000 + 10_001)

    private fun note(key: String, unread: Int = 0, user: UserHandle? = null, ongoing: Boolean = false,
        group: String? = null, groupUnread: Int = 0, pkg: String = kakao) =
        NotificationItem(key, pkg, "App", null, "t", "x", 0L, true, null, ongoing = ongoing, user = user,
            unread = unread, group = group, groupUnread = groupUnread)

    @After fun reset() { BadgeClears.cleared.value = emptySet() }

    // What one notification says.

    private fun builder() = Notification.Builder(ApplicationProvider.getApplicationContext<Context>(), "c").setSmallIcon(android.R.drawable.ic_dialog_info)
    private val me = Person.Builder().setName("Me").setKey("me").build()
    private val friend = Person.Builder().setName("Friend").setKey("friend").build()

    private fun chat(vararg from: Person?, number: Int = 0): Notification {
        val style = Notification.MessagingStyle(me)
        from.forEachIndexed { i, who -> style.addMessage(Notification.MessagingStyle.Message("text $i", i.toLong(), who)) }
        return builder().setStyle(style).setNumber(number).build()
    }

    @Test fun `a notification number above zero is the count`() {
        assertEquals(7, UnreadCount.of(builder().setNumber(7).build()))
    }

    @Test fun `a plain notification has no reliable count`() {
        assertEquals(0, UnreadCount.of(builder().build()))
    }

    @Test fun `fifty messages in one chat count as the chat's messages, not one`() {
        assertEquals(25, UnreadCount.of(chat(*Array(25) { friend })))
    }

    @Test fun `your own replies in a chat are not unread`() {
        assertEquals(2, UnreadCount.of(chat(friend, me, friend, null)))
    }

    @Test fun `the number the app sets beats counting the messages`() {
        assertEquals(9, UnreadCount.of(chat(friend, friend, number = 9)))
    }

    // How a package's notifications add up.

    @Test fun `reliable counts are added up`() {
        assertEquals(BadgeTally(8, exact = true), BadgeTally.of(listOf(note("a", 5), note("b", 3))))
    }

    @Test fun `nothing reliable gives a dot, counting the notifications for the seen rule`() {
        assertEquals(BadgeTally(2, exact = false), BadgeTally.of(listOf(note("a"), note("b"))))
    }

    @Test fun `one notification that cannot be counted turns the number into a dot`() {
        val tally = BadgeTally.of(listOf(note("a", 5), note("b")))
        assertEquals(5, tally.count)
        assertFalse(tally.exact)
    }

    @Test fun `an ongoing notification does not spoil a number`() {
        assertEquals(BadgeTally(4, exact = true), BadgeTally.of(listOf(note("a", 4), note("b", ongoing = true))))
    }

    @Test fun `a group summary's count stands for its children once`() {
        val children = listOf(note("a", 2, group = "g", groupUnread = 5), note("b", 1, group = "g", groupUnread = 5), note("c", group = "g", groupUnread = 5))
        assertEquals(BadgeTally(5, exact = true), BadgeTally.of(children))
    }

    @Test fun `a group without a count uses its children's own counts`() {
        assertEquals(BadgeTally(3, exact = true), BadgeTally.of(listOf(note("a", 2, group = "g"), note("b", 1, group = "g"))))
    }

    @Test fun `two groups each count once`() {
        val items = listOf(note("a", group = "g", groupUnread = 4), note("b", group = "g", groupUnread = 4), note("c", group = "h", groupUnread = 2))
        assertEquals(BadgeTally(6, exact = true), BadgeTally.of(items))
    }

    // Badge keys, Clear Badge and the private view.

    @Test fun `cleared notifications are left out`() {
        val items = listOf(note("a", 5), note("b", 3))
        assertEquals(mapOf(kakao to 3), BadgeClears.counts(items, setOf("a")))
        assertTrue(BadgeClears.counts(items, setOf("a", "b")).isEmpty())
    }

    @Test fun `a copy in another profile is counted apart from the original`() {
        val items = listOf(note("a", 5), note("b", 40, user = clone))
        val counts = BadgeClears.counts(items, emptySet())
        assertEquals(5, counts[kakao])
        assertEquals(40, counts[BadgeKey.of(kakao, clone)])
    }

    @Test fun `only the keys with an unreliable count are dots`() {
        val items = listOf(note("a", 5), note("b", pkg = "com.example.mail"))
        assertEquals(setOf("com.example.mail"), BadgeClears.dots(items, emptySet()))
    }

    @Test fun `a dot stays a dot and a number is a number`() {
        assertEquals(BadgeStyle.DOT, badgeShape(BadgeStyle.COUNT, dot = true))
        assertEquals(BadgeStyle.COUNT, badgeShape(BadgeStyle.COUNT, dot = false))
        assertEquals(BadgeStyle.OFF, badgeShape(BadgeStyle.OFF, dot = true))
    }

    @Test fun `while recording a number is a dot too`() {
        // The private view turns the style to a dot first; an exact number must not turn it back.
        assertEquals(BadgeStyle.DOT, badgeShape(privateBadgeStyle(BadgeStyle.COUNT, private = true), dot = false))
        assertEquals(BadgeStyle.OFF, badgeShape(privateBadgeStyle(BadgeStyle.OFF, private = true), dot = false))
    }
}
