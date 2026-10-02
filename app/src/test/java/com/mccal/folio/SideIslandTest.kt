package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SideIslandTest {
    @Test fun `upright island only for a camera on a side edge`() {
        // Cover portrait: camera top-center.
        assertNull(cameraSideEdge(600, 0, 648, 104, 1248, 1972))
        // Cover turned sideways: camera on the right or left edge, vertically centered.
        assertEquals(1, cameraSideEdge(1868, 600, 1972, 648, 1972, 1248))
        assertEquals(-1, cameraSideEdge(0, 600, 104, 648, 1972, 1248))
        // Inner portrait: hidden camera near the right edge, low down.
        assertEquals(1, cameraSideEdge(1752, 1823, 1830, 1901, 1848, 2448))
        assertNull(cameraSideEdge(0, 0, 10, 10, 0, 0))
    }

    @Test fun `a saved island position stays inside the window with the pill visible`() {
        // Fold8 cover: 1248 x 1972 px at 2.625 density, a 36 dp pill.
        val bottom = IslandPosition(xFraction = .5f, topDp = 900f).clamped(1248, 1972, 2.625f, pillHDp = 36f)
        assertTrue("top ${bottom.topDp} leaves the pill off the bottom", bottom.topDp + 36f <= 1972 / 2.625f)
        assertEquals(1972 / 2.625f - 36f - ISLAND_EDGE_GAP, bottom.topDp, .01f)

        val above = IslandPosition(xFraction = 1.4f, topDp = -20f).clamped(1248, 1972, 2.625f, pillHDp = 36f)
        assertEquals(1f, above.xFraction, 0f)
        assertEquals(ISLAND_EDGE_GAP, above.topDp, 0f)

        // A sane position is left exactly as it is.
        val fine = IslandPosition(xFraction = .3f, topDp = 120f)
        assertEquals(fine, fine.clamped(1248, 1972, 2.625f, pillHDp = 36f))
    }
}
