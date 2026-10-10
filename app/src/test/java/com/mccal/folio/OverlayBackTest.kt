package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayBackTest {
    @Test fun `a back preview eases an open overlay up to a third of the way to closed`() {
        assertEquals(1f, OverlayBack.shown(1f, swipe = 0f), 0f)
        assertEquals(.825f, OverlayBack.shown(1f, swipe = .5f), .0001f)
        assertEquals(.65f, OverlayBack.shown(1f, swipe = 1f), .0001f)
        assertEquals(.65f, OverlayBack.shown(1f, swipe = 1.4f), .0001f)
    }

    @Test fun `the close runs from where the preview left it, so there is no jump`() {
        // The swipe keeps its value after release; the overlay's own value then falls from 1 to 0.
        assertEquals(.65f, OverlayBack.shown(1f, swipe = 1f), .0001f)
        assertEquals(.325f, OverlayBack.shown(.5f, swipe = 1f), .0001f)
        assertEquals(0f, OverlayBack.shown(0f, swipe = 1f), 0f)
    }

    @Test fun `opacity ignores the preview and only follows the close`() {
        assertEquals(1f, OverlayBack.unpreviewed(OverlayBack.shown(1f, .6f), swipe = .6f), .0001f)
        assertEquals(.5f, OverlayBack.unpreviewed(OverlayBack.shown(.5f, 1f), swipe = 1f), .0001f)
        assertEquals(0f, OverlayBack.unpreviewed(0f, swipe = .3f), 0f)
    }
}
