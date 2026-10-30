package com.mccal.folio

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Moving controls in Control Center: the drop rule is pure, so no tile ever moves by itself while you edit. */
class CcReorderTest {
    private val names = listOf("A", "B", "C", "D", "E")

    @Test fun `a control dropped on another takes its place and the rest keep their order`() {
        assertEquals(listOf("A", "C", "D", "B", "E"), ccMoveControl(names, "B", "D"))
        assertEquals(listOf("C", "A", "B", "D", "E"), ccMoveControl(names, "C", "A"))
        assertEquals(listOf("A", "B", "C", "E", "D"), ccMoveControl(names, "D", "E"))
    }

    @Test fun `nothing moves when the drop is on itself or on a name that is not there`() {
        assertEquals(names, ccMoveControl(names, "C", "C"))
        assertEquals(names, ccMoveControl(names, "C", "Z"))
        assertEquals(names, ccMoveControl(names, "Z", "C"))
    }

    @Test fun `moving never loses or doubles a control`() {
        names.forEach { moving -> names.forEach { target ->
            val out = ccMoveControl(names, moving, target)
            assertEquals(names.sorted(), out.sorted())
        } }
    }

    @Test fun `TalkBack's earlier and later move one place and stop at the ends`() {
        assertEquals(listOf("A", "C", "B", "D", "E"), ccMoveControlBy(names, "B", 1))
        assertEquals(listOf("B", "A", "C", "D", "E"), ccMoveControlBy(names, "B", -1))
        assertEquals(names, ccMoveControlBy(names, "A", -1))
        assertEquals(names, ccMoveControlBy(names, "E", 1))
        assertEquals(names, ccMoveControlBy(names, "Z", 1))
    }

    @Test fun `the drop target is the control under the finger, never the one being held`() {
        val bounds = mapOf("A" to Rect(0f, 0f, 100f, 100f), "B" to Rect(120f, 0f, 220f, 100f))
        assertEquals("B", ccDropTarget(bounds, "A", Offset(150f, 50f)))
        assertNull(ccDropTarget(bounds, "A", Offset(50f, 50f))) // over itself
        assertNull(ccDropTarget(bounds, "A", Offset(110f, 50f))) // in the gap
        assertNull(ccDropTarget(bounds, "A", Offset(500f, 500f)))
    }

    @Test fun `a drag lifts the held control, follows the finger and drops on the one under it`() {
        val state = CcReorderState()
        state.bounds["A"] = Rect(0f, 0f, 100f, 100f)
        state.bounds["B"] = Rect(120f, 0f, 220f, 100f)
        state.start("A", Offset(50f, 50f))
        assertEquals("A", state.moving)
        state.drag(Offset(100f, 0f))
        assertEquals(Offset(100f, 0f), state.lift)
        assertEquals("B", state.hover)
        var dropped: Pair<String, String>? = null
        state.end { m, t -> dropped = m to t }
        assertEquals("A" to "B", dropped)
        assertNull(state.moving)
        assertEquals(Offset.Zero, state.lift)
    }

    @Test fun `letting go over nothing, or cancelling, drops nothing`() {
        val state = CcReorderState()
        state.bounds["A"] = Rect(0f, 0f, 100f, 100f)
        state.start("A", Offset(50f, 50f))
        state.drag(Offset(400f, 400f))
        var called = false
        state.end { _, _ -> called = true }
        assertTrue("nothing under the finger", !called)
        state.start("A", Offset(50f, 50f)); state.cancel()
        assertNull(state.moving)
    }

    @Test fun `a control that left the layout is no longer found by a drop`() {
        val state = CcReorderState()
        state.bounds["A"] = Rect(0f, 0f, 100f, 100f)
        state.bounds["B"] = Rect(120f, 0f, 220f, 100f)
        state.bounds["C"] = Rect(120f, 0f, 220f, 100f) // C moved into the cell B was removed from, while B's old box was still there
        state.forget("B")
        state.start("A", Offset(50f, 50f))
        state.drag(Offset(100f, 0f))
        assertEquals("C", state.hover)
    }
}
