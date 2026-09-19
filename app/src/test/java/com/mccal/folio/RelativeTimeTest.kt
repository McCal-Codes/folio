package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

/** How long ago a notification arrived, counted in whole minutes. */
class RelativeTimeTest {
    private val now = 1_800_000_000_000L

    @Test fun `a notification from a moment ago reads as no minutes`() {
        assertEquals(0L, relativeMinutes(now - 59_000, now))
    }

    @Test fun `whole minutes, rounded down`() {
        assertEquals(1L, relativeMinutes(now - 60_000, now))
        assertEquals(59L, relativeMinutes(now - 59 * 60_000 - 59_000, now))
        assertEquals(90L, relativeMinutes(now - 90 * 60_000, now))
    }

    @Test fun `a clock correction can't make a notification arrive in the future`() {
        // The phone's clock jumps back an hour; a notification posted before it now looks like it's from ahead.
        assertEquals(0L, relativeMinutes(now + 60 * 60_000, now))
    }
}
