package com.mccal.folio

import android.os.Process
import android.os.UserHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A Dual Messenger or work-profile copy has the same package name as the original, so badge counts are filed by
 * package and profile (#561). Before, both icons showed one combined count.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BadgeKeyTest {
    private val kakao = "com.kakao.talk"
    /** Dual Messenger runs as user 95 on Samsung; any other profile does the same job here. */
    private val clone: UserHandle = UserHandle.getUserHandleForUid(95 * 100_000 + 10_001)

    private fun note(key: String, user: UserHandle?) =
        NotificationItem(key, kakao, "KakaoTalk", null, "t", "x", 0L, true, null, user = user)

    @After fun reset() { BadgeClears.cleared.value = emptySet() }

    @Test fun `the profile Folio runs in keeps the bare package name`() {
        assertEquals(kakao, BadgeKey.of(kakao, null))
        assertEquals(kakao, BadgeKey.of(kakao, Process.myUserHandle()))
    }

    @Test fun `a copy in another profile is filed apart from the original`() {
        assertNotEquals(BadgeKey.of(kakao, null), BadgeKey.of(kakao, clone))
        assertTrue(BadgeKey.of(kakao, clone).startsWith("$kakao@"))
    }

    @Test fun `the original and its copy are counted separately`() {
        val items = listOf(note("a", null), note("b", null), note("c", clone))
        val counts = BadgeClears.counts(items, emptySet())
        assertEquals(2, counts[kakao])
        assertEquals(1, counts[BadgeKey.of(kakao, clone)])
    }

    @Test fun `clearing the copy's badge leaves the original's alone`() {
        val items = listOf(note("a", null), note("b", clone))
        BadgeClears.clear(kakao, items, clone)
        assertEquals(setOf("b"), BadgeClears.cleared.value)
        val counts = BadgeClears.counts(items, BadgeClears.cleared.value)
        assertEquals(1, counts[kakao])
        assertEquals(null, counts[BadgeKey.of(kakao, clone)])
    }

    @Test fun `clearing without a profile still clears the original, as before`() {
        val items = listOf(note("a", null), note("b", clone))
        BadgeClears.clear(kakao, items)
        assertEquals(setOf("a"), BadgeClears.cleared.value)
    }
}
