package com.mccal.folio

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DragCancelTest {
    private val removeAt = Offset(50f, 20f)
    private val otherCellAt = Offset(250f, 250f)

    /** An app carried from Home cell 3 on page 0, past slop, with Remove at the top and cell 7 elsewhere. */
    private fun carrying(over: Offset) = HomeDragState().apply {
        register("remove", DragRegion(DropTarget.Remove, Rect(0f, 0f, 100f, 40f), null, null))
        register("from", DragRegion(DropTarget.Home(3), Rect(0f, 100f, 100f, 200f), "app", 0))
        register("to", DragRegion(DropTarget.Home(7), Rect(200f, 200f, 300f, 300f), null, 0))
        source = regions.getValue(DropTarget.Home(3))
        origin = Offset(50f, 150f)
        pointer = over
        moved = true
    }

    private fun release(at: Offset, consumed: Boolean) = PointerInputChange(PointerId(0), 100, at, false, 90, at, true, consumed)

    /** What a drag drops on for this release, the way homeDragInput hands it to finishDrag. */
    private fun HomeDragState.dropOn(change: PointerInputChange) = dropTarget(dragEnding(change, false)!!, setOf(0))

    @Test fun `a finger that lifts drops and a system cancel does not`() {
        assertEquals(false, dragEnding(release(removeAt, consumed = false), false))
        assertEquals(true, dragEnding(release(removeAt, consumed = true), false))
        assertNull(dragEnding(PointerInputChange(PointerId(0), 100, removeAt, true, 90, removeAt, true, false), false))
        assertEquals(true, dragEnding(null, false))
        assertEquals(true, dragEnding(release(removeAt, consumed = false), true))
    }

    @Test fun `a cancel over Remove leaves the app in place`() {
        assertNull(carrying(removeAt).dropOn(release(removeAt, consumed = true)))
    }

    @Test fun `a cancel over another cell does not move the app`() {
        assertNull(carrying(otherCellAt).dropOn(release(otherCellAt, consumed = true)))
    }

    @Test fun `a normal release still drops where the finger lifts`() {
        assertEquals(DropTarget.Home(7), carrying(otherCellAt).dropOn(release(otherCellAt, consumed = false)))
        assertEquals(DropTarget.Remove, carrying(removeAt).dropOn(release(removeAt, consumed = false)))
    }
}
