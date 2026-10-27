package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adjust island: the width a person adds to the camera area the island wraps ([IslandSize], [IslandGeometry.extraW]).
 * A supporter on a smaller minimum width found the pill narrower than the camera area, so the size is a small, bounded,
 * per-screen number, and zero is the automatic fit everyone else keeps.
 */
class IslandSizeTest {
    // A 40 px wide camera (20 dp at density 2) near the middle of a 2000 x 1800 window.
    private val camera = intArrayOf(900, 20, 940, 60)
    private val window = 2000 to 1800
    private val event = IslandContent.Event(IslandEvent.Silent(true))

    private fun place(extra: Float = 0f, custom: IslandPosition? = null, nudge: IslandNudge = IslandNudge.None) =
        islandPlacement(camera, custom, window.first, window.second, 2f, extra, nudge)

    @Test fun `no extra is the automatic fit`() {
        val g = place()
        assertEquals(0f, g.extraW)
        assertEquals(g.camW, g.wrapW)
        assertEquals(islandPlacement(camera, null, window.first, window.second, 2f).widthFor(event), g.widthFor(event))
    }

    @Test fun `extra widens what the pill wraps and never narrows it`() {
        val auto = place()
        val wider = place(extra = 12f)
        assertEquals(auto.camW + 12f, wider.wrapW)
        assertTrue(wider.widthFor(event) >= auto.widthFor(event))
        // The pill always wraps the widened camera area with the pill height as its padding.
        assertTrue(wider.widthFor(event) >= wider.wrapW + wider.pillH)
        // The centre and the top do not move: the handle grows the pill from the middle.
        assertEquals(auto.centerXPx, wider.centerXPx)
        assertEquals(auto.top, wider.top)
    }

    @Test fun `extra is held inside the room beside the camera`() {
        val auto = place()
        assertTrue(auto.maxExtra > 0f)
        assertEquals(auto.maxW - (auto.camW + auto.pillH), auto.maxExtra)
        val huge = place(extra = 5000f)
        assertEquals(auto.maxExtra, huge.extraW)
        // The pill never asks for more than the room, or the camera plus its padding when even that is more.
        assertTrue(huge.widthFor(event) <= maxOf(auto.maxW, huge.wrapW + huge.pillH) + .001f)
        assertEquals(0f, place(extra = -8f).extraW)
        assertEquals(0f, place(extra = Float.NaN).extraW)
    }

    @Test fun `the size travels with a dragged position`() {
        val moved = place(extra = 8f, custom = IslandPosition(.3f, 40f))
        assertEquals(8f, moved.extraW)
        assertEquals(600f, moved.centerXPx)
    }

    @Test fun `a nudge shifts the pill and keeps what it wraps`() {
        val still = place(extra = 10f)
        val nudged = place(extra = 10f, nudge = IslandNudge(6f, 2f))
        assertEquals(still.wrapW, nudged.wrapW)
        assertEquals(still.pillH, nudged.pillH)
        assertEquals(still.camW, nudged.camW)
        assertEquals(still.centerXPx + 6f * 2f, nudged.centerXPx, .001f)
        assertEquals(still.top + 2f, nudged.top, .001f)
        assertEquals(still.centerY + 2f, nudged.centerY, .001f)
    }

    @Test fun `a nudge moves a dragged pill as it is and cannot push the top above the screen`() {
        val moved = place(extra = 0f, custom = IslandPosition(.3f, 40f), nudge = IslandNudge(-4f, 3f))
        assertEquals(600f - 8f, moved.centerXPx, .001f)
        assertEquals(43f, moved.top, .001f)
        val up = place(nudge = IslandNudge(0f, -500f))
        assertEquals(0f, up.top)
    }

    @Test fun `a nudge down never takes the pill off the bottom of the window`() {
        // The window is 1800 px tall at density 2 (900 dp); a pill dragged to the bottom is nudged down by the largest step.
        val low = place(custom = IslandPosition(.5f, 1700f), nudge = IslandNudge(0f, IslandNudge.NUDGE_LIMIT_DP))
        assertTrue("top ${low.top}", low.top + low.pillH <= 900f)
        // Sideways too: the centre stays inside the window.
        val edge = place(custom = IslandPosition(1f, 40f), nudge = IslandNudge(IslandNudge.NUDGE_LIMIT_DP, 0f))
        assertTrue(edge.centerXPx <= window.first.toFloat())
    }

    @Test fun `nudges add up and stay inside the limit`() {
        assertEquals(IslandNudge(4f, -2f), IslandNudge.None.plus(2f, -1f).plus(2f, -1f))
        val far = IslandNudge.None.plus(1000f, -1000f)
        assertEquals(IslandNudge.NUDGE_LIMIT_DP, far.dxDp)
        assertEquals(-IslandNudge.NUDGE_LIMIT_DP, far.dyDp)
    }

    @Test fun `fit to camera asks for the reported width only when it is wider than the lens`() {
        val g = place()
        assertEquals(10f, IslandSize.fitExtra(g.camW + 10f, g)!!, .001f)
        assertNull("already as wide as reported", IslandSize.fitExtra(g.camW, g))
        assertNull("narrower than the lens outline", IslandSize.fitExtra(g.camW - 5f, g))
        assertNull("nothing reported", IslandSize.fitExtra(null, g))
        assertNull("less than a dp is not worth a change", IslandSize.fitExtra(g.camW + .4f, g))
        assertEquals("never past the room", g.maxExtra, IslandSize.fitExtra(g.camW + 5000f, g)!!, .001f)
    }

    @Test fun `a nudge is a fixed small step`() {
        assertEquals(2f, IslandSize.STEP_DP)
    }
}
