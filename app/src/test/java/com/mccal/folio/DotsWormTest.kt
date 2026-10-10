package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DotsWormTest {
    private fun span(pos: Float) = DotsWorm.span(pos)

    @Test fun `at rest the worm is one dot on the page`() {
        assertEquals(2f to 2f, span(2f))
        assertEquals(0f to 0f, span(0f))
    }

    @Test fun `the head runs ahead and the tail follows, so the middle of a swipe is stretched`() {
        assertEquals(1f to 0f, span(.5f))
        assertEquals(.5f to 0f, span(.25f))
        assertEquals(1f to .5f, span(.75f))
        assertEquals(3f to 2f, span(2.5f))
    }

    @Test fun `a finished swipe is one dot again, on the next page`() {
        assertEquals(1f to 1f, span(1f))
        assertEquals(3f to 3f, span(3f))
    }

    @Test fun `the head is never behind the tail, going either way`() {
        var pos = 0f
        while (pos < 4f) { val (head, tail) = span(pos); assertTrue("at $pos", head >= tail); pos += .05f }
    }

    @Test fun `swiping back works the same, measured from the page below`() {
        // Between page 1 and page 2, coming from page 2: pos = 1.75 is a quarter of the way back.
        val (head, tail) = span(1.75f)
        assertEquals(2f, head, 0f)
        assertEquals(1.5f, tail, 0f)
    }

    @Test fun `the worm fades as the pager leaves the last real page for the plus slot`() {
        assertEquals(1f, DotsWorm.alpha(0f, 3), 0f)
        assertEquals(1f, DotsWorm.alpha(2f, 3), 0f)
        assertEquals(.5f, DotsWorm.alpha(2.5f, 3), 0f)
        assertEquals(0f, DotsWorm.alpha(3f, 3), 0f)
        assertEquals(0f, DotsWorm.alpha(4f, 3), 0f)
    }
}
