package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the row of page dots, Search and App Library sits. Folded it is centred on Home, which is the whole
 * workspace; unfolded, Home is only part of it — the rest is the extra page of widgets to its side — and the row
 * has to stay under Home rather than drift into that page's content.
 */
class PageRowPlacementTest {
    /** The Fold8's inner screen, open: a wide window where Home shares the room with the page beside it. */
    private fun geometry(width: Float, height: Float) = homeGeometry(width, height, LayoutPreset(), labels = true)

    @Test fun `unfolded, the row is centred on Home and clear of the page beside it`() {
        val width = 932.6f
        val g = geometry(width, 704f)
        assertTrue("the inner screen should lay out as two pages", g.expanded)
        val besideHome = width - g.homeWidth
        assertTrue("Home takes only part of the window", besideHome > 0f)
        // The row's middle, once the page beside Home is held out of it, sits inside Home's own half.
        val middle = besideHome + (width - besideHome) / 2f
        assertTrue("row middle $middle should be within Home", middle > besideHome && middle < width)
    }

    @Test fun `folded, nothing is held out and the row stays centred on the screen`() {
        val g = geometry(411f, 914f)
        assertEquals(false, g.expanded)
        assertEquals(411f, g.homeWidth, .001f)  // Home is the whole width, so besideHome is zero
    }
}
