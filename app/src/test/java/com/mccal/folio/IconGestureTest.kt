package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The icon swipe rules, as numbers. The defaults are what iconSwipes did before Icon Actions, so nothing changes for anyone who sets nothing. */
class IconGestureTest {
    private val t = 100f
    private fun swipe(dx: Float, dy: Float, up: Boolean = true, down: Boolean = true, start: Boolean = true) =
        IconGesture.classify(dx, dy, t, up, down, start)

    @Test fun `a short move is still pending`() {
        assertEquals(IconSwipe.PENDING, swipe(0f, -50f))
        assertEquals(IconSwipe.PENDING, swipe(40f, 40f))
    }

    @Test fun `a straight swipe up or down past the threshold fires`() {
        assertEquals(IconSwipe.UP, swipe(0f, -101f))
        assertEquals(IconSwipe.DOWN, swipe(0f, 101f))
    }

    @Test fun `a swipe within the cone fires and one outside it does not`() {
        // 0.6 of the vertical travel is the most sideways a swipe may be.
        assertEquals(IconSwipe.UP, swipe(70f, -120f))
        assertEquals(IconSwipe.CANCEL, swipe(80f, -120f))
        assertEquals(IconSwipe.DOWN, swipe(-70f, 120f))
        assertEquals(IconSwipe.CANCEL, swipe(-80f, 120f))
    }

    @Test fun `a sideways move is a page swipe, not an icon swipe`() {
        assertEquals(IconSwipe.CANCEL, swipe(150f, 10f))
        assertEquals(IconSwipe.CANCEL, swipe(-150f, -10f))
    }

    @Test fun `a direction the icon does not have never fires, and ends the watch as before`() {
        assertEquals(IconSwipe.CANCEL, swipe(0f, -150f, up = false))
        assertEquals(IconSwipe.CANCEL, swipe(0f, 150f, down = false))
        assertEquals(IconSwipe.UP, swipe(0f, -150f, down = false))
    }

    @Test fun `a touch that began in the gesture strip never fires`() {
        assertEquals(IconSwipe.CANCEL, swipe(0f, -150f, start = false))
        assertEquals(IconSwipe.CANCEL, swipe(0f, 0f, start = false))
    }

    @Test fun `the start is above the strip by the inset`() {
        assertTrue(IconGesture.startsAboveInset(startY = 2000f, windowHeight = 2400f, bottomInset = 100f))
        assertFalse(IconGesture.startsAboveInset(startY = 2350f, windowHeight = 2400f, bottomInset = 100f))
        assertFalse("exactly on the edge is inside the strip", IconGesture.startsAboveInset(2300f, 2400f, 100f))
        assertTrue("three-button navigation has no strip", IconGesture.startsAboveInset(2399f, 2400f, 0f))
    }

    @Test fun `today's values are kept`() {
        assertEquals(0.6f, IconGesture.CONE)
        assertEquals(28f, IconGesture.THRESHOLD_DP)
    }
}
