package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dragging in Control Center's editor: where a held control lands, how the page scrolls at its edges, and Undo. All pure. */
class CcDragTest {
    private val cols = 4
    private val rows = 4

    private fun grid(vararg slots: CcSlot) = slots.toList()
    private fun cell(id: String, col: Int, row: Int) = CcSlot(id, col, row, 1, 1)

    @Test fun `a drag into a free cell lands on the nearest cell`() {
        val slots = grid(cell("a", 0, 0))
        val preview = ccPreviewDrop(slots, "a", 1.2f, .1f, cols, rows)
        assertEquals(cell("a", 1, 0), preview.landing)
        assertEquals(grid(cell("a", 1, 0)), preview.slots)
    }

    @Test fun `a drag that has not gone halfway to the next cell lands where it started`() {
        val slots = grid(cell("a", 0, 0))
        val preview = ccPreviewDrop(slots, "a", .3f, .2f, cols, rows)
        assertEquals(cell("a", 0, 0), preview.landing)
        assertEquals(slots, preview.slots)
    }

    @Test fun `a control dragged onto a neighbor trades places and the preview shows both`() {
        val slots = grid(cell("a", 0, 0), cell("b", 1, 0))
        val preview = ccPreviewDrop(slots, "a", .9f, 0f, cols, rows)
        assertEquals(cell("a", 1, 0), preview.landing)
        assertEquals(cell("b", 0, 0), preview.slots.first { it.id == "b" })
    }

    @Test fun `a drop that cannot work returns home instead of landing somewhere odd`() {
        // A one-cell control cannot trade with a 2 by 2 module that would not fit in its cell.
        val slots = grid(cell("a", 0, 0), CcSlot(CC_MEDIA, 1, 0, 2, 2))
        val preview = ccPreviewDrop(slots, "a", 1f, 0f, cols, rows)
        assertEquals(cell("a", 0, 0), preview.landing)
        assertEquals(slots, preview.slots)
    }

    @Test fun `a drag far off the grid is a cancel`() {
        val slots = grid(cell("a", 0, 0), cell("b", 3, 3))
        assertEquals(slots, ccPreviewDrop(slots, "a", 9f, 2f, cols, rows).slots)
        assertEquals(slots, ccPreviewDrop(slots, "a", -6f, -6f, cols, rows).slots)
        assertEquals(cell("a", 0, 0), ccPreviewDrop(slots, "a", 9f, 2f, cols, rows).landing)
    }

    @Test fun `a tall control stays inside the grid when dragged past its edge`() {
        val slots = grid(CcSlot(CC_BRIGHTNESS, 3, 0, 1, 2))
        val preview = ccPreviewDrop(slots, "brightness", 3f, 1.6f, 4, 3)
        assertEquals(CcSlot(CC_BRIGHTNESS, 3, 1, 1, 2), preview.landing)
        assertTrue(preview.slots.all { ccFits(preview.slots, it, 4, 3) })
    }

    @Test fun `an unknown control changes nothing`() {
        val slots = grid(cell("a", 0, 0))
        val preview = ccPreviewDrop(slots, "zzz", 1f, 1f, cols, rows)
        assertEquals(slots, preview.slots)
        assertNull(preview.landing)
    }

    @Test fun `whatever the finger does the preview is a layout that fits with every control kept`() {
        val slots = ccDefaultLayout(4, listOf("WALLET", "FLASHLIGHT", "TIMER", "CAMERA"), true)
        val ids = slots.map { it.id }.toSet()
        var col = -2f
        while (col <= 6f) {
            var row = -2f
            while (row <= 8f) {
                slots.forEach { moving ->
                    val preview = ccPreviewDrop(slots, moving.id, col, row, 4, 8)
                    assertEquals(ids, preview.slots.map { it.id }.toSet())
                    assertTrue("overlap or out of the grid for ${moving.id} at $col,$row", preview.slots.all { ccFits(preview.slots, it, 4, 8) })
                }
                row += .37f
            }
            col += .41f
        }
    }

    @Test fun `the preview matches what ccDrop does at the cell it names`() {
        val slots = grid(cell("a", 0, 0), cell("b", 1, 0), cell("c", 2, 1))
        val preview = ccPreviewDrop(slots, "a", 2.1f, .9f, cols, rows)
        val landed = preview.landing!!
        assertEquals(ccDrop(slots, "a", landed.col, landed.row, cols, rows), preview.slots)
    }

    @Test fun `the page does not scroll with the finger in the middle`() {
        assertEquals(0f, ccAutoScrollStep(500f, 100f, 900f, 80f, 14f), 0f)
    }

    @Test fun `near the top it scrolls up and near the bottom down, faster the deeper the finger is`() {
        val shallow = ccAutoScrollStep(150f, 100f, 900f, 80f, 14f)
        val deep = ccAutoScrollStep(110f, 100f, 900f, 80f, 14f)
        assertTrue(shallow < 0f && deep < shallow)
        val low = ccAutoScrollStep(840f, 100f, 900f, 80f, 14f)
        val lower = ccAutoScrollStep(890f, 100f, 900f, 80f, 14f)
        assertTrue(low > 0f && lower > low)
    }

    @Test fun `past the edge of the page it scrolls at full speed and never faster`() {
        assertEquals(-14f, ccAutoScrollStep(-300f, 100f, 900f, 80f, 14f), 0f)
        assertEquals(14f, ccAutoScrollStep(2000f, 100f, 900f, 80f, 14f), 0f)
        assertEquals(-14f, ccAutoScrollStep(100f, 100f, 900f, 80f, 14f), 0f)
    }

    @Test fun `a page too short for two edges still has a quiet middle and no bad input scrolls`() {
        assertEquals(0f, ccAutoScrollStep(150f, 100f, 200f, 80f, 14f), 0f)
        assertEquals(0f, ccAutoScrollStep(150f, 200f, 100f, 80f, 14f), 0f)
        assertEquals(0f, ccAutoScrollStep(150f, 100f, 900f, 0f, 14f), 0f)
        assertEquals(0f, ccAutoScrollStep(150f, 100f, 900f, 80f, 0f), 0f)
    }

    private fun state(vararg controls: String) = CcEditState(controls.toList(), null)

    @Test fun `undo takes the changes back newest first`() {
        var history = CcEditHistory()
        history = history.push(state("a"))
        history = history.push(state("a", "b"))
        val (first, rest) = history.pop()!!
        assertEquals(state("a", "b"), first)
        assertEquals(state("a"), rest.pop()!!.first)
        assertNull(rest.pop()!!.second.pop())
    }

    @Test fun `an empty history has nothing to undo`() {
        assertFalse(CcEditHistory().canUndo)
        assertNull(CcEditHistory().pop())
        assertTrue(CcEditHistory().push(state("a")).canUndo)
    }

    @Test fun `a step that changes nothing is not kept`() {
        val history = CcEditHistory().push(state("a"))
        assertEquals(history, history.push(state("a")))
        assertEquals(CcEditHistory(), CcEditHistory().push(state("a"), after = state("a")))
        assertNotEquals(history, history.push(state("b")))
    }

    @Test fun `the history keeps only the last steps`() {
        var history = CcEditHistory()
        repeat(CC_UNDO_DEPTH + 10) { history = history.push(state("c$it")) }
        assertEquals(CC_UNDO_DEPTH, history.steps.size)
        assertEquals(state("c${CC_UNDO_DEPTH + 9}"), history.pop()!!.first)
        assertEquals(state("c10"), history.steps.first())
    }

    @Test fun `a saved layout is part of the step so a layout change can be undone`() {
        val before = CcEditState(listOf("a"), CcSavedLayout(4, listOf(cell("a", 0, 0)), false))
        val history = CcEditHistory().push(before)
        assertEquals(before, history.pop()!!.first)
    }
}
