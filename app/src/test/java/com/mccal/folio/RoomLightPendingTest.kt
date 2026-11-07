package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A day/night change waiting out its delay has to finish even if the light sensor sends nothing more. */
class RoomLightPendingTest {
    @Test fun nothingIsPendingUntilAReadingCrossesTheLine() {
        val tracker = RoomLightTracker(initialNight = false)
        assertNull(tracker.pendingRemainingMs(0L))
        tracker.onReading(50f, 0L)
        assertNull(tracker.pendingRemainingMs(10L))
    }

    @Test fun aDarkReadingSaysHowLongIsLeftAndAskingAgainCommitsIt() {
        val tracker = RoomLightTracker(initialNight = false)
        assertEquals(false, tracker.onReading(0.5f, 1_000L))
        assertEquals(RoomLightTracker.ENTER_DARK_DELAY_MS, tracker.pendingRemainingMs(1_000L))
        assertEquals(RoomLightTracker.ENTER_DARK_DELAY_MS - 500L, tracker.pendingRemainingMs(1_500L))
        // The same reading, asked again once the delay is over, with no new sensor event in between.
        assertEquals(true, tracker.onReading(0.5f, 1_000L + RoomLightTracker.ENTER_DARK_DELAY_MS))
        assertNull(tracker.pendingRemainingMs(1_000L + RoomLightTracker.ENTER_DARK_DELAY_MS))
    }

    @Test fun theWayBackToDayWaitsItsOwnLongerDelay() {
        val tracker = RoomLightTracker(initialNight = true)
        tracker.onReading(200f, 0L)
        assertEquals(RoomLightTracker.EXIT_DARK_DELAY_MS, tracker.pendingRemainingMs(0L))
        assertTrue(tracker.pendingRemainingMs(1_000L)!! <= RoomLightTracker.EXIT_DARK_DELAY_MS - 1_000L)
        assertEquals(false, tracker.onReading(200f, RoomLightTracker.EXIT_DARK_DELAY_MS))
    }
}
