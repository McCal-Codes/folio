package com.mccal.folio

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A phone's date can be wrong both ways. Winding it back must not hand back time already used; a clock that comes
 * up years ahead after a flat battery must not expire a code for good, since nothing in the app could undo that.
 */
class SupporterClockLeapTest {
    private val floor = LocalDate.of(2026, 1, 1)
    private val seen = LocalDate.of(2026, 9, 20)

    @Test fun `an ordinary day forward is believed`() {
        assertEquals(seen.plusDays(1), supporterClock(seen, seen.plusDays(1), floor))
        assertEquals(seen.plusDays(60), supporterClock(seen, seen.plusDays(60), floor))
    }

    @Test fun `a leap of years is not, and the phone keeps the day it had`() {
        assertEquals(seen, supporterClock(seen, LocalDate.of(2030, 1, 1), floor))
        assertEquals(seen, supporterClock(seen, seen.plusDays(MAX_CLOCK_LEAP + 1), floor))
    }

    @Test fun `a clock wound back still can't take time away`() {
        assertEquals(seen, supporterClock(seen, seen.minusMonths(6), floor))
        assertEquals(seen, supporterClock(seen, LocalDate.of(2001, 1, 1), floor))
    }

    @Test fun `a phone that has never seen a real date takes what it is given, or nothing`() {
        assertEquals(LocalDate.of(2026, 6, 1), supporterClock(null, LocalDate.of(2026, 6, 1), floor))
        assertEquals(null, supporterClock(null, LocalDate.of(2001, 1, 1), floor))
    }
}
