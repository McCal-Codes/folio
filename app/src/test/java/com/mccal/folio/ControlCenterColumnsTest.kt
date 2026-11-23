package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Control Center fills its window with pairs of columns of cells near the size chosen, and puts its page strip at the edge or under the grid. */
class ControlCenterColumnsTest {
    private val size = 66f
    private fun grid(w: Float, h: Float?, controls: Int = 8, focus: Boolean = false, s: Float = size) = controlCenterGrid(w, h, controls, focus, s)

    @Test fun `a phone upright keeps four columns and its cells fill the width`() {
        val g = grid(379f, 700f)
        assertEquals(4, g.columns)
        // The page strip stands beside the grid from a phone's width, as on the iPhone and the Duo: the grid has 379 less 32 of padding less the strip.
        assertEquals(true, g.stripAtEdge)
        assertEquals(379f - 32f - g.stripDp, g.cellDp * 4 + g.gapDp * 3, 0.5f)
        assertTrue(g.cellDp >= CC_MIN_CELL_DP && g.cellDp <= size * CC_FILL_MAX)
    }

    @Test fun `a wider window takes more columns instead of giant tiles`() {
        assertEquals(6, grid(672f, 848f).columns)   // the unfolded screen upright
        assertEquals(8, grid(880f, 620f).columns)   // the unfolded screen wide
        assertEquals(8, grid(1248f, 760f).columns)  // a tablet: no more than the controls can use
        for ((w, h) in listOf(379f to 700f, 672f to 848f, 880f to 620f, 1248f to 760f)) assertTrue(grid(w, h).cellDp <= size * CC_FILL_MAX + 0.01f)
    }

    @Test fun `columns never outnumber what the controls can use, and grow with more controls`() {
        assertEquals(4, grid(1248f, 760f, controls = 4).columns)
        assertEquals(8, grid(1248f, 760f, controls = 8).columns)
        assertTrue(grid(1600f, 900f, controls = 15).columns in 10..12)
    }

    @Test fun `the page strip stands at the edge only when the panel is wide enough for it to cost little`() {
        assertEquals(false, grid(280f, 700f).stripAtEdge)
        assertEquals(true, grid(379f, 700f).stripAtEdge)
        assertEquals(true, grid(700f, 700f).stripAtEdge)
        // The strip is one touch target and a gap wide, so the rule follows the size chosen, not a fixed width.
        val g = grid(700f, 700f)
        assertEquals(48f + g.gapDp, g.stripDp, 0.01f)
    }

    @Test fun `a phone on its side is wide and short, so it takes eight columns and fits`() {
        val g = grid(880f, 327f)
        assertEquals(8, g.columns)
        // What has to stand on the first page (the sliders and the modules) fits; the other controls go to another page.
        val rows = controlCenterRows(8, 0, false)
        assertTrue("the rows fit: ${g.cellDp}", g.cellDp * rows + g.gapDp * (rows - 1) + 48f + g.gapDp <= 327f + 0.5f)
    }

    @Test fun `adding controls never makes a tile smaller`() {
        // McCal, 17 Nov 2026: "the cc settings shouldn't get smaller". The same window and size give the same cell for any number of controls
        // (the controls that do not fit go to another page), and only the columns a window can use depend on how many there are.
        for ((w, h) in listOf(379f to 700f, 379f to 520f, 672f to 848f, 880f to 620f)) for (focus in listOf(false, true)) {
            val few = grid(w, h, controls = 4, focus = focus); val many = grid(w, h, controls = 15, focus = focus)
            if (few.columns == many.columns) assertEquals("${w}x$h focus $focus", few.cellDp, many.cellDp, 0.01f)
        }
        for (n in 0..16) assertEquals("$n controls", grid(379f, 700f, controls = 4).cellDp, grid(379f, 700f, controls = n).cellDp, 0.01f)
    }

    @Test fun `a panel with no height limit is as wide as its width allows`() {
        assertEquals(6, grid(672f, null).columns)
    }

    @Test fun `the gap and the cell follow the size chosen`() {
        val compact = grid(672f, 848f, s = 58f); val large = grid(672f, 848f, s = 76f)
        assertTrue(large.gapDp > compact.gapDp)
        assertTrue(large.cellDp > compact.cellDp || large.columns < compact.columns)
    }

    @Test fun `the grid always fits inside the panel less its padding and strip`() {
        for (s in listOf(58f, 66f, 76f)) for (w in listOf(320f, 379f, 520f, 560f, 672f, 880f, 1248f)) for (h in listOf(300f, 400f, 500f, 700f, 900f)) {
            val g = grid(w, h, s = s)
            val used = g.cellDp * g.columns + g.gapDp * (g.columns - 1) + 32f + if (g.stripAtEdge) g.stripDp else 0f
            assertTrue("size $s panel ${w}x$h: ${g.columns} columns of ${g.cellDp} use $used", g.cellDp <= CC_MIN_CELL_DP + 0.01f || used <= w + 0.01f)
        }
    }

    @Test fun `more columns need fewer rows for the same controls`() {
        assertEquals(6, controlCenterRows(4, 8, false))
        assertEquals(5, controlCenterRows(6, 8, false))
        assertEquals(4, controlCenterRows(8, 8, false))
        assertEquals(5, controlCenterRows(8, 8, true))
        assertEquals(3, controlCenterRows(8, 0, false))
        assertEquals(5, controlCenterRows(4, 0, false))
    }
    @Test fun `a narrower pane can keep the columns a wider window had, and the cells follow the width`() {
        val wide = grid(672f, 848f)
        val kept = controlCenterGrid(422f, 848f, 8, false, size, minColumns = wide.columns)
        assertEquals(wide.columns, kept.columns)
        assertTrue("narrower cells, not overflow", kept.cellDp < wide.cellDp)
        assertEquals(4, controlCenterGrid(422f, 848f, 8, false, size).columns) // without it the narrower width takes fewer
    }

    @Test fun `a kept column count never goes past what the controls can use or below four`() {
        assertEquals(4, controlCenterGrid(1248f, 760f, 4, false, size, minColumns = 8).columns)
        assertEquals(4, controlCenterGrid(379f, 700f, 8, false, size, minColumns = 1).columns)
        assertEquals(6, controlCenterGrid(700f, 800f, 12, false, size, minColumns = 5).columns)
    }

    @Test fun theLeastPanelForSixColumnsKeepsEveryCellAtTheTouchSize() {
        val width = controlCenterMinPanelDp(6, 90f)
        val grid = controlCenterGrid(width, null, 12, false, 90f, 48f, 32f, true, minColumns = 6)
        assertEquals(6, grid.columns)
        assertTrue("cells are ${grid.cellDp} dp", grid.cellDp >= 48f - .01f)
    }
}
