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
        assertEquals(false, g.stripAtEdge)
        // 379 less 32 of padding: four cells and three gaps use all of it, and the cell is a little bigger than the 66 chosen.
        assertEquals(379f - 32f, g.cellDp * 4 + g.gapDp * 3, 0.5f)
        assertTrue(g.cellDp > size)
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
        assertEquals(false, grid(500f, 700f).stripAtEdge)
        assertEquals(true, grid(700f, 700f).stripAtEdge)
        // The strip is one touch target and a gap wide, so the rule follows the size chosen, not a fixed width.
        val g = grid(700f, 700f)
        assertEquals(48f + g.gapDp, g.stripDp, 0.01f)
    }

    @Test fun `a phone on its side is wide and short, so it takes eight columns and fits`() {
        val g = grid(880f, 327f)
        assertEquals(8, g.columns)
        val rows = controlCenterRows(8, 8, false)
        assertTrue("the rows fit: ${g.cellDp}", g.cellDp * rows + g.gapDp * (rows - 1) + 48f + g.gapDp <= 327f + 0.5f)
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
}
