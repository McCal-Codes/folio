package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Today View, the page left of Home, scrolls under Home's page controls. Where its last widget comes to rest is what this
 * pins: above the strip and, before Folio is the Home app, above the Preview bar too (seen on a 748x720 window at 2.0 text:
 * the last card ran under "Use as Home").
 */
class TodayBottomPaddingTest {
    @Test
    fun `Home that is already the default keeps the 96 dp it always had`() {
        assertEquals(TODAY_BOTTOM_PADDING_DP, todayBottomPadding(isDefaultHome = true, measuredDp = 0f), 0f)
        // A taller measured strip still fits inside 96 dp less the gap.
        assertEquals(TODAY_BOTTOM_PADDING_DP, todayBottomPadding(isDefaultHome = true, measuredDp = 60f), 0f)
    }

    @Test
    fun `before Folio is Home the last widget stops above the Preview bar`() {
        val space = homeControlsSpace(isDefaultHome = false, measuredDp = 0f)
        val padding = todayBottomPadding(isDefaultHome = false, measuredDp = 0f)
        assertTrue("$padding must clear the $space dp the controls reserve", padding >= space + TODAY_CONTROLS_GAP_DP)
        assertTrue("the Preview bar needs more than the strip's 96 dp", padding > TODAY_BOTTOM_PADDING_DP)
    }

    @Test
    fun `a Preview bar taller than the guess at large text pushes the end up with it`() {
        val measured = 170f
        assertEquals(measured + 12f + TODAY_CONTROLS_GAP_DP, todayBottomPadding(isDefaultHome = false, measuredDp = measured), 0f)
    }

    @Test
    fun `a measured strip taller than 96 dp is cleared too`() {
        assertEquals(130f + TODAY_CONTROLS_GAP_DP, todayBottomPadding(isDefaultHome = true, measuredDp = 130f), 0f)
    }
}
