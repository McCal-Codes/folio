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

    private fun place(extra: Float = 0f, custom: IslandPosition? = null) =
        islandPlacement(camera, custom, window.first, window.second, 2f, extra)

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
        assertTrue(huge.widthFor(event) <= auto.maxW + .001f || huge.widthFor(event) <= huge.wrapW + huge.pillH + .001f)
        assertEquals(0f, place(extra = -8f).extraW)
        assertEquals(0f, place(extra = Float.NaN).extraW)
    }

    @Test fun `the size travels with a dragged position`() {
        val moved = place(extra = 8f, custom = IslandPosition(.3f, 40f))
        assertEquals(8f, moved.extraW)
        assertEquals(600f, moved.centerXPx)
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
