package com.mccal.folio

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * When a months code's window begins. A supporter's early access hangs on this, so the awkward days — a phone that
 * doesn't know the date, and one that thought it was 2030 — are the ones worth pinning down.
 */
class SupporterWindowTest {
    private val floor = LocalDate.of(2026, 1, 1)
    private val today = LocalDate.of(2026, 9, 19)

    @Test fun `a code without months has no window`() {
        assertEquals(null, supporterWindowStart(null, today, months = 0, floor = floor))
        assertEquals(null, supporterWindowStart(LocalDate.of(2026, 5, 1), today, months = 0, floor = floor))
    }

    @Test fun `the first redemption starts today`() {
        assertEquals(today, supporterWindowStart(null, today, months = 1, floor = floor))
    }

    @Test fun `a window already started is kept, so re-pasting a code doesn't restart it`() {
        val began = LocalDate.of(2026, 9, 1)
        assertEquals(began, supporterWindowStart(began, today, months = 1, floor = floor))
        // Even long after it ran out: the code is expired, not renewed.
        assertEquals(began, supporterWindowStart(began, LocalDate.of(2027, 3, 1), months = 1, floor = floor))
    }

    @Test fun `a clock that hasn't been set starts nobody's month`() {
        assertEquals(null, supporterWindowStart(null, LocalDate.of(1970, 1, 1), months = 2, floor = floor))
    }

    @Test fun `a window started on a wrong clock begins again once the date is real`() {
        // The phone said 2030 when the code was redeemed. Keeping that would leave the code refused for good.
        assertEquals(today, supporterWindowStart(LocalDate.of(2030, 1, 1), today, months = 2, floor = floor))
    }
}
