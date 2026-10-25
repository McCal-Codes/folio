package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Control Center fills its window with 4, 6 or 8 columns of cells near the size chosen, and puts its page strip at the edge or under the grid. */
class ControlCenterColumnsTest {
    private val size = 66f
    private fun grid(w: Float, h: Float?, controls: Int = 8, focus: Boolean = false, s: Float = size) = controlCenterGrid(w, h, controls, focus, s)

    @Test fun `a phone upright keeps four columns and its cells fill the width`() {
        val g = grid(379f, 700f)
        assertEquals(4, g.columns)
        assertEquals(false, g.stripAtEdge)
        // 379 less 32 of padding, four cells and three gaps: the whole width is used, and the cell is a little bigger than the 66 chosen.
        assertEquals(379f - 32f, g.cellDp * 4 + CC_GAP_DP * 3, 0.5f)
        assertTrue(g.cellDp > size)
    }

    @Test fun `a wider window takes more columns instead of giant tiles`() {
        assertEquals(6, grid(672f, 848f).columns)   // the unfolded screen upright
        assertEquals(8, grid(880f, 620f).columns)   // the unfolded screen wide
        assertEquals(8, grid(1248f, 760f).columns)  // a tablet: never more than eight
        for ((w, h) in listOf(379f to 700f, 672f to 848f, 880f to 620f, 1248f to 760f)) assertTrue(grid(w, h).cellDp <= size * CC_FILL_MAX + 0.01f)
    }

    @Test fun `the page strip is at the edge on a wide panel and under the grid on a narrow one`() {
        assertEquals(false, grid(CC_EDGE_STRIP_MIN_DP - 1, 700f).stripAtEdge)
        assertEquals(true, grid(CC_EDGE_STRIP_MIN_DP, 700f).stripAtEdge)
    }

    @Test fun `a phone on its side is wide and short, so it takes eight columns and fits`() {
        val g = grid(880f, 327f)
        assertEquals(8, g.columns)
        val rows = controlCenterRows(8, 8, false)
        assertTrue("the rows fit: ${g.cellDp}", g.cellDp * rows + CC_GAP_DP * (rows - 1) + 60f <= 327f + 0.5f)
    }

    @Test fun `a window that is wide enough but only barely short stays at the narrowest grid`() {
        val tall = grid(672f, 2000f)
        assertEquals(6, tall.columns)
        assertEquals(6, grid(672f, null).columns)
    }

    @Test fun `the grid always fits inside the panel less its padding and strip`() {
        for (s in listOf(58f, 66f, 76f)) for (w in listOf(320f, 379f, 520f, 560f, 672f, 880f, 1248f)) for (h in listOf(300f, 400f, 500f, 700f, 900f)) {
            val g = grid(w, h, s = s)
            val used = g.cellDp * g.columns + CC_GAP_DP * (g.columns - 1) + CC_SIDE_PADDING_DP + if (g.stripAtEdge) CC_STRIP_DP else 0f
            assertTrue("size $s panel ${w}x$h: ${g.columns} columns of ${g.cellDp} use $used", g.cellDp <= CC_MIN_CELL_DP + 0.01f || used <= w + 0.01f)
        }
    }

    @Test fun `eight columns need fewer rows for the same controls`() {
        assertEquals(6, controlCenterRows(4, 8, false))
        assertEquals(5, controlCenterRows(6, 8, false))
        assertEquals(4, controlCenterRows(8, 8, false))
        assertEquals(5, controlCenterRows(8, 8, true))
        assertEquals(3, controlCenterRows(8, 0, false))
        assertEquals(5, controlCenterRows(4, 0, false))
    }
}
