package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockGrabberTest {
    private fun geometry(position: Float) =
        homeGeometry(475f, 700f, LayoutPreset(dockAlignToGrid = false, dockPosition = position), true)

    @Test fun `dragging the dock's middle to a place saves the position that puts it there`() {
        val start = geometry(.56f)
        for (target in listOf(300f, 360f, 420f)) {
            val saved = dockPositionForCenter(target, 700f)
            val g = geometry(saved)
            assertEquals("middle at $target", target, g.dockTop + g.dockHeight / 2f, 1.5f)
        }
        assertTrue(start.dockHeight > 0f)
    }

    @Test fun `a drag past the edges ends somewhere safe`() {
        val top = geometry(dockPositionForCenter(-500f, 700f))
        val bottom = geometry(dockPositionForCenter(5000f, 700f))
        assertEquals(0f, dockPositionForCenter(-500f, 700f), 0f)
        assertEquals(1f, dockPositionForCenter(5000f, 700f), 0f)
        // The geometry still keeps the whole dock inside the window.
        assertTrue(top.dockTop >= 0f)
        assertTrue(bottom.dockTop + bottom.dockHeight <= 700f)
    }

    @Test fun `no window height falls back to the usual position`() {
        assertEquals(LayoutPreset().dockPosition, dockPositionForCenter(100f, 0f), 0f)
    }

    @Test fun `TalkBack steps move the position by five percent and stop at the ends`() {
        assertEquals(.61f, dockPositionStep(.56f, 1), .001f)
        assertEquals(.51f, dockPositionStep(.56f, -1), .001f)
        assertEquals(0f, dockPositionStep(.02f, -1), 0f)
        assertEquals(1f, dockPositionStep(.98f, 1), 0f)
    }
}
