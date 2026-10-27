package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Control Center's swipe-away pull: when letting go closes it, and that a quick reopen starts from rest. */
class CcSwipeAwayTest {
    private val threshold = 96f * 3f
    private val flick = 530f * 3f

    @Test fun `a pull past the threshold closes, a short slow one does not`() {
        assertTrue(ccSwipeAwayCloses(-threshold - 1f, 0f, threshold, flick))
        assertFalse(ccSwipeAwayCloses(-threshold + 1f, 0f, threshold, flick))
    }

    @Test fun `a quick flick up closes a short pull, a slow or downward one does not`() {
        assertTrue(ccSwipeAwayCloses(-10f, -flick - 1f, threshold, flick))
        assertFalse(ccSwipeAwayCloses(-10f, -flick + 1f, threshold, flick))
        assertFalse(ccSwipeAwayCloses(-10f, flick * 2f, threshold, flick))
    }

    @Test fun `a panel that was not pulled up never closes from a fling`() {
        assertFalse(ccSwipeAwayCloses(0f, -flick * 10f, threshold, flick))
    }

    @Test fun `the same flick closes at any density once the limit is converted for it`() {
        // 530 dp per second is 1060 px/s on a 2x screen and 1590 on a 3x one: a flick of 1200 px/s is a flick on the first, not on the second.
        assertTrue(ccSwipeAwayCloses(-5f, -1200f, 96f * 2f, 530f * 2f))
        assertFalse(ccSwipeAwayCloses(-5f, -1200f, 96f * 3f, 530f * 3f))
    }

    @Test fun `opening again after a swipe-away close starts from rest, closing leaves the offset`() {
        val afterClose = -250f
        assertEquals(afterClose, ccSwipeAwayOnOpenChange(open = false, offsetPx = afterClose), 0f)
        assertEquals(0f, ccSwipeAwayOnOpenChange(open = true, offsetPx = afterClose), 0f)
    }
}
