package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

/** A swipe along Control Center's page strip: which page it lands on. */
class CcStripSwipeTest {
    @Test fun `a swipe down goes to the next page and a swipe up goes back`() {
        assertEquals(1, ccStripSwipeTarget(0, 3, 80f, 24f))
        assertEquals(0, ccStripSwipeTarget(1, 3, -80f, 24f))
    }

    @Test fun `a short drag stays on the page`() {
        assertEquals(1, ccStripSwipeTarget(1, 3, 10f, 24f))
        assertEquals(1, ccStripSwipeTarget(1, 3, -23f, 24f))
    }

    @Test fun `a swipe never goes past either end`() {
        assertEquals(2, ccStripSwipeTarget(2, 3, 200f, 24f))
        assertEquals(0, ccStripSwipeTarget(0, 3, -200f, 24f))
    }
}
