package com.mccal.folio

import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsTest {
    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour

    @Test fun `apps used around this time of day rank above apps used at other times`() {
        val now = 30 * day + 8 * hour // 8 AM (UTC for the test)
        // Mail opened most mornings; games opened more often, but in the evening.
        val uses = (1..10).map { "mail" to now - it * day } + (1..14).map { "game" to now - it * day + 12 * hour }
        val scores = Suggestions.scores(uses, now, zoneOffsetMs = 0)
        assertTrue(scores.getValue("mail") > scores.getValue("game"))
    }

    @Test fun `recent habits outweigh old ones and the curve wraps past midnight`() {
        val now = 60 * day + 23 * hour + 30 * 60 * 1000L // 11:30 PM
        val recentNight = listOf("reader" to now - day + hour) // 12:30 AM yesterday: an hour away across midnight
        val oldNight = List(3) { "old" to now - 50 * day }
        val scores = Suggestions.scores(recentNight + oldNight, now, zoneOffsetMs = 0)
        assertTrue(scores.getValue("reader") > .5)
        assertTrue(scores.getValue("reader") > scores.getValue("old"))
    }

    @Test fun `smart rotate only moves to a clearly more relevant card`() {
        org.junit.Assert.assertEquals(2, Suggestions.smartStackPick(listOf(.1, .2, 1.0), 0))
        org.junit.Assert.assertNull(Suggestions.smartStackPick(listOf(.8, 1.0), 0)) // not half again as relevant
        org.junit.Assert.assertNull(Suggestions.smartStackPick(listOf(0.0, .2), 0)) // below the floor
        org.junit.Assert.assertNull(Suggestions.smartStackPick(listOf(.1, .9), 1)) // already showing the best
    }

    @Test fun `smart rotate counts time away from Home toward its wait`() {
        val interval = Suggestions.SMART_ROTATE_INTERVAL_MS
        val last = 1_000_000L
        // Just looked: the full wait.
        org.junit.Assert.assertEquals(interval, Suggestions.smartRotateWait(last, last))
        // Back after 10 minutes in another app: only the rest of the wait, not 15 minutes over.
        org.junit.Assert.assertEquals(5 * 60_000L, Suggestions.smartRotateWait(last + 10 * 60_000L, last))
        // Away longer than the interval: look right away.
        org.junit.Assert.assertEquals(0L, Suggestions.smartRotateWait(last + 2 * interval, last))
        // A clock that reads earlier than the last look never waits past one interval.
        org.junit.Assert.assertEquals(interval, Suggestions.smartRotateWait(last - 60_000L, last))
    }

    @Test fun `a stack keeps its last check when its page leaves and comes back`() {
        SmartRotateClock.clearForTest()
        org.junit.Assert.assertEquals("a new stack starts its wait now", 1_000L, SmartRotateClock.lastCheck(7, 1_000L))
        SmartRotateClock.record(7, 5_000L)
        org.junit.Assert.assertEquals("coming back later finds the old check, not a new one", 5_000L, SmartRotateClock.lastCheck(7, 900_000L))
        org.junit.Assert.assertEquals("another stack is separate", 900_000L, SmartRotateClock.lastCheck(8, 900_000L))
        SmartRotateClock.forget(7)
        org.junit.Assert.assertEquals(2_000_000L, SmartRotateClock.lastCheck(7, 2_000_000L))
    }
}
