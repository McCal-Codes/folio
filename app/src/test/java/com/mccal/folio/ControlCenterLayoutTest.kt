package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlCenterLayoutTest {
    private val controls = CcControl.entries.map { it.name }

    private fun noOverlap(slots: List<CcSlot>, cols: Int): Boolean =
        slots.all { s -> ccFits(slots, s, cols, ccRowsUsed(slots)) }

    @Test fun `the default layout is the page Control Center draws today, at every width and control count`() {
        for (cols in listOf(4, 6, 8, 10, 12)) for (n in listOf(0, 3, 4, 5, 8, 13, 15)) for (focus in listOf(false, true)) {
            val chosen = controls.take(n)
            val slots = ccDefaultLayout(cols, chosen, focus)
            assertTrue("overlap or outside at $cols columns, $n controls", noOverlap(slots, cols))
            // controlCenterRows counts one more row than the grid has (the page strip's), so the grid has one less.
            assertEquals("rows at $cols columns, $n controls, focus $focus", controlCenterRows(cols, n, focus) - 1, ccRowsUsed(slots))
            assertEquals("every control is placed once", chosen.toSet(), slots.map { it.id }.toSet().intersect(chosen.toSet()))
            assertEquals(slots.size, slots.map { it.id }.toSet().size)
        }
    }

    @Test fun `four columns put the first four small controls beside the sliders, six or more put the sliders at the end of the top row`() {
        val four = ccDefaultLayout(4, controls.take(6), focus = true).associateBy { it.id }
        assertEquals(CcSlot(CC_FOCUS, 0, 2, 4, 1), four[CC_FOCUS])
        assertEquals(CcSlot(CC_BRIGHTNESS, 2, 3, 1, 2), four[CC_BRIGHTNESS])
        assertEquals(CcSlot(controls[0], 0, 3, 1, 1), four[controls[0]])
        assertEquals(CcSlot(controls[3], 1, 4, 1, 1), four[controls[3]])
        assertEquals(CcSlot(controls[4], 0, 5, 1, 1), four[controls[4]])
        val six = ccDefaultLayout(6, controls.take(6), focus = false).associateBy { it.id }
        assertEquals(CcSlot(CC_MEDIA, 2, 0, 2, 2), six[CC_MEDIA])
        assertEquals(CcSlot(CC_VOLUME, 5, 0, 1, 2), six[CC_VOLUME])
        assertEquals(CcSlot(controls[0], 0, 2, 1, 1), six[controls[0]])
    }

    @Test fun `a control moves to a free place and not onto another control or off the grid`() {
        val slots = ccDefaultLayout(4, controls.take(6), focus = true)
        val rows = 8
        val moved = ccMove(slots, controls[0], 2, 7, 4, rows)
        assertEquals(CcSlot(controls[0], 2, 7, 1, 1), moved.first { it.id == controls[0] })
        assertEquals(slots.size, moved.size)
        // onto the brightness slider, off the right edge, below the bottom, and an unknown id: nothing changes
        assertSame(slots, ccMove(slots, controls[0], 2, 3, 4, rows))
        assertSame(slots, ccMove(slots, controls[0], 4, 7, 4, rows))
        assertSame(slots, ccMove(slots, controls[0], 0, 8, 4, rows))
        assertSame(slots, ccMove(slots, "nothing", 0, 7, 4, rows))
        // staying put is fine
        assertEquals(slots, ccMove(slots, controls[0], 0, 3, 4, rows))
    }

    @Test fun `a control takes only the sizes it can, and only where they fit`() {
        val slots = ccDefaultLayout(4, controls.take(4), focus = false) + CcSlot("x", 0, 6, 1, 1)
        val rows = 8
        // a shortcut can be wide: its right neighbor is the other 2 by 2 control, so it cannot at (1, 3), but can on a free row
        assertSame(slots, ccResize(slots, controls[1], 2, 1, 4, rows))
        assertEquals(2, ccResize(slots, "x", 2, 1, 4, rows).first { it.id == "x" }.w)
        // not a size a shortcut has
        assertSame(slots, ccResize(slots, "x", 3, 1, 4, rows))
        assertSame(slots, ccResize(slots, "x", 1, 2, 4, rows))
        // a slider can be taller if the room below is free
        assertEquals(3, ccResize(slots, CC_BRIGHTNESS, 1, 3, 4, rows).first { it.id == CC_BRIGHTNESS }.h)
        assertSame(slots, ccResize(slots, CC_BRIGHTNESS, 1, 3, 4, 4))
        // at four columns the media module cannot be wider than the grid
        assertTrue(ccAllowedSizes(CC_MEDIA, 4).all { it.first <= 4 })
        assertTrue((6 to 2) in ccAllowedSizes(CC_MEDIA, 6))
    }

    @Test fun `a corner drag lands on the nearest size that fits`() {
        val slots = ccDefaultLayout(4, controls.take(4), focus = false) + CcSlot("x", 0, 6, 1, 1)
        // dragged to about two cells wide on a free row: wide
        assertEquals(2 to 1, ccSnapSize(slots, "x", 1.8f, 1.1f, 4, 8))
        // dragged little: it stays one cell
        assertEquals(1 to 1, ccSnapSize(slots, "x", 1.2f, 1.0f, 4, 8))
        // where the wide size is taken, it stays small however far it is dragged
        assertEquals(1 to 1, ccSnapSize(slots, controls[1], 2f, 1f, 4, 8))
        assertNull(ccSnapSize(slots, "nothing", 1f, 1f, 4, 8))
    }

    @Test fun `adding takes the first free place and says no when there is none`() {
        val slots = ccDefaultLayout(4, controls.take(2), focus = false)
        val added = ccAdd(slots, "extra", 4, 6)!!
        // the first free single cell, reading left to right, top to bottom, is under the two small controls
        assertEquals(CcSlot("extra", 0, 3, 1, 1), added.first { it.id == "extra" })
        // already there: nothing changes
        assertSame(added, ccAdd(added, "extra", 4, 6))
        // a grid with no free cell
        val full = listOf(CcSlot("a", 0, 0, 1, 1), CcSlot("b", 1, 0, 1, 1))
        assertNull(ccAdd(full, "c", 2, 1))
        // Now Playing starts at 2 by 2, which one taken cell in a 2 by 2 grid does not leave room for
        val gap = listOf(CcSlot("a", 0, 0, 1, 1))
        assertNull(ccAdd(gap, CC_MEDIA, 2, 2))
        assertEquals(CcSlot(CC_MEDIA, 0, 0, 2, 2), ccAdd(emptyList(), CC_MEDIA, 2, 2)!!.single())
    }

    @Test fun `removing frees the places and the free cells add up`() {
        val slots = ccDefaultLayout(4, controls.take(6), focus = true)
        val rows = 8
        val area = slots.sumOf { it.w * it.h }
        assertEquals(4 * rows - area, ccFreeCells(slots, 4, rows).size)
        val without = ccRemove(slots, controls[0])
        assertEquals(slots.size - 1, without.size)
        assertEquals(ccFreeCells(slots, 4, rows).size + 1, ccFreeCells(without, 4, rows).size)
        assertTrue(ccFreeCells(without, 4, rows).contains(0 to 3))
    }
}
