package com.mccal.folio

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * When a months code's window begins, and which day it is judged against. A supporter's early access hangs on
 * this, so the awkward days — a phone that doesn't know the date, one that thought it was 2030, and one whose
 * clock fell back to the day it was built — are the ones worth pinning down.
 */
class SupporterWindowTest {
    private val floor = LocalDate.of(2026, 1, 1)
    private val today = LocalDate.of(2026, 9, 19)

    private fun clock(seen: LocalDate?, now: LocalDate = today) = supporterClock(seen, now, floor)

    @Test fun `a code without months has no window`() {
        assertEquals(null, supporterWindowStart(null, clock(null), months = 0))
        assertEquals(null, supporterWindowStart(LocalDate.of(2026, 5, 1), clock(null), months = 0))
    }

    @Test fun `the first redemption starts today`() {
        assertEquals(today, supporterWindowStart(null, clock(null), months = 1))
    }

    @Test fun `a window already started is kept, so re-pasting a code doesn't restart it`() {
        val began = LocalDate.of(2026, 9, 1)
        assertEquals(began, supporterWindowStart(began, clock(null), months = 1))
        // Even long after it ran out: the code is expired, not renewed.
        assertEquals(began, supporterWindowStart(began, clock(null, LocalDate.of(2027, 3, 1)), months = 1))
    }

    @Test fun `a clock that hasn't been set starts nobody's month`() {
        assertEquals(null, clock(null, LocalDate.of(1970, 1, 1)))
        assertEquals(null, supporterWindowStart(null, clock(null, LocalDate.of(1970, 1, 1)), months = 2))
    }

    @Test fun `a window started on a wrong clock begins again once the date is real`() {
        // The phone said 2030 when the code was redeemed. Keeping that would leave the code refused for good.
        assertEquals(today, supporterWindowStart(LocalDate.of(2030, 1, 1), clock(null), months = 2))
    }

    @Test fun `the day to judge by only ever moves forward`() {
        assertEquals(today, clock(LocalDate.of(2026, 5, 1)))
        // A battery that died and left the phone on its build date, and a date set back by hand.
        assertEquals(today, clock(today, LocalDate.of(2026, 3, 1)))
        assertEquals(today, clock(today, LocalDate.of(2025, 12, 1)))
    }

    @Test fun `a clock that has fallen behind doesn't drag a window earlier`() {
        val began = LocalDate.of(2026, 5, 15)
        val fallenBack = clock(today, LocalDate.of(2026, 3, 1))
        assertEquals(began, supporterWindowStart(began, fallenBack, months = 1))
    }

    @Test fun `winding the clock back doesn't hand back a window that ran out`() {
        val began = LocalDate.of(2026, 5, 15)
        val backTo2025 = clock(today, LocalDate.of(2025, 12, 1))
        val start = supporterWindowStart(began, backTo2025, months = 1)
        // Judged against the latest day the phone has seen, not the one it was set to: over since 15 June.
        assertEquals(LocalDate.of(2026, 6, 15), BetaCodes.Code(emptySet(), 0, 0, 1L, months = 1).ends(start))
        assertEquals(true, BetaCodes.Code(emptySet(), 0, 0, 1L, months = 1).expired(backTo2025!!, start))
    }
}
