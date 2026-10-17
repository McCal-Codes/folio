package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PagerFeelTest {
    private val width = 1000f

    @Test fun `the rubber band gives less and less and never a whole page`() {
        val small = PagerFeel.rubberBand(50f, width)
        val big = PagerFeel.rubberBand(500f, width)
        assertTrue("some give", small > 0f && small < 50f)
        assertTrue("less give per pixel further out", big / 500f < small / 50f)
        assertTrue(PagerFeel.rubberBand(1_000_000f, width) < width)
        assertEquals(0f, PagerFeel.rubberBand(0f, width), 0f)
        assertEquals(0f, PagerFeel.rubberBand(100f, 0f), 0f)
    }

    @Test fun `the rubber band uses iOS's curve and keeps its sign`() {
        // UIScrollView: (1 - 1 / (x * .55 / d + 1)) * d
        assertEquals((1f - 1f / (200f * .55f / width + 1f)) * width, PagerFeel.rubberBand(200f, width), .001f)
        assertEquals(-PagerFeel.rubberBand(200f, width), PagerFeel.rubberBand(-200f, width), .001f)
    }

    @Test fun `a caught stretch maps back to the finger travel that made it`() {
        listOf(-400f, -20f, 3f, 120f, 900f).forEach { excess ->
            assertEquals(excess, PagerFeel.excessFor(PagerFeel.rubberBand(excess, width), width), .05f)
        }
        assertEquals(0f, PagerFeel.excessFor(0f, width), 0f)
    }

    @Test fun `only the true first and last page stretch, not the one-page-per-swipe bound`() {
        // Five pages. Past the last page: stretch.
        assertEquals(1, PagerFeel.stretchSide(4.2f, 4f, count = 5, minPage = 0, leading = true))
        // Before the first page: stretch.
        assertEquals(-1, PagerFeel.stretchSide(-.3f, 0f, count = 5, minPage = 0, leading = true))
        // Swiping from page 1 past page 2 meets the one-page bound: a hard stop, as today.
        assertEquals(0, PagerFeel.stretchSide(2.4f, 2f, count = 5, minPage = 0, leading = true))
        assertEquals(0, PagerFeel.stretchSide(.6f, 1f, count = 5, minPage = 0, leading = true))
        // Inside: nothing.
        assertEquals(0, PagerFeel.stretchSide(2.5f, 2.5f, count = 5, minPage = 0, leading = true))
    }

    @Test fun `Discover keeps the first page's stop, and Today beside Home moves the first page`() {
        assertEquals(0, PagerFeel.stretchSide(-.3f, 0f, count = 5, minPage = 0, leading = false))
        assertEquals(1, PagerFeel.stretchSide(4.3f, 4f, count = 5, minPage = 0, leading = false))
        assertEquals(-1, PagerFeel.stretchSide(.7f, 1f, count = 5, minPage = 1, leading = true))
        assertEquals(1, PagerFeel.firstPage(count = 5, minPage = 1))
        assertEquals(4, PagerFeel.lastPage(count = 5))
    }

    @Test fun `a flick passes a middle page by at most the overshoot and eases back`() {
        // From 1.3 toward page 2 of 5: the spring's raw value passes the page by .3.
        val shown = PagerFeel.settlePosition(2.3f, start = 1.3f, target = 2, count = 5, minPage = 0)
        assertTrue(shown > 2f && shown < 2f + PagerFeel.OVERSHOOT)
        // A small overshoot is nearly untouched.
        assertEquals(2.01f, PagerFeel.settlePosition(2.01f, start = 1.3f, target = 2, count = 5, minPage = 0), .001f)
        // On the way to the page nothing changes.
        assertEquals(1.7f, PagerFeel.settlePosition(1.7f, start = 1.3f, target = 2, count = 5, minPage = 0), 0f)
        // Going the other way, the same.
        val back = PagerFeel.settlePosition(.7f, start = 1.6f, target = 1, count = 5, minPage = 0)
        assertTrue(back < 1f && back > 1f - PagerFeel.OVERSHOOT)
    }

    @Test fun `past a true end the overshoot is left for the rubber band`() {
        assertEquals(4.06f, PagerFeel.settlePosition(4.06f, start = 3.4f, target = 4, count = 5, minPage = 0), 0f)
        assertEquals(-.05f, PagerFeel.settlePosition(-.05f, start = .5f, target = 0, count = 5, minPage = 0), 0f)
    }

    @Test fun `a flick lands on Bounce, a stretch springs back on Settle, anything else keeps today's settle`() {
        assertEquals(FolioMotion.Bounce, PagerFeel.settleSpring(velocity = -900f, flick = 500f, stretched = false, reduce = false))
        assertEquals(FolioMotion.Bounce, PagerFeel.settleSpring(velocity = 900f, flick = 500f, stretched = true, reduce = false))
        assertEquals(FolioMotion.Settle, PagerFeel.settleSpring(velocity = 100f, flick = 500f, stretched = true, reduce = false))
        assertNull(PagerFeel.settleSpring(velocity = 100f, flick = 500f, stretched = false, reduce = false))
    }

    @Test fun `Reduce Motion keeps today's settle, never the overshoot`() {
        assertNull(PagerFeel.settleSpring(velocity = 9000f, flick = 500f, stretched = true, reduce = true))
    }

    @Test fun `a touch catches pages that are still moving, not ones that have visibly landed`() {
        assertTrue(PagerFeel.catches(settling = true, position = 1.4f, target = 2, stretch = 0f, stretchSlack = 4f))
        assertTrue(PagerFeel.catches(settling = true, position = 4f, target = 4, stretch = 30f, stretchSlack = 4f))
        assertFalse(PagerFeel.catches(settling = true, position = 1.995f, target = 2, stretch = 0f, stretchSlack = 4f))
        assertFalse(PagerFeel.catches(settling = false, position = 1.4f, target = 2, stretch = 0f, stretchSlack = 4f))
    }

    @Test fun `a caught page let go goes to the nearest page inside the ends`() {
        assertEquals(1, PagerFeel.caughtPage(1.4f, count = 5, minPage = 0))
        assertEquals(2, PagerFeel.caughtPage(1.6f, count = 5, minPage = 0))
        assertEquals(1, PagerFeel.caughtPage(.4f, count = 5, minPage = 1))
        assertEquals(4, PagerFeel.caughtPage(4.4f, count = 5, minPage = 0))
    }
}
