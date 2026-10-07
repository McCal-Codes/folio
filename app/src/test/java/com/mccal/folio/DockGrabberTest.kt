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

    @Test fun `TalkBack steps move five percent of the window from where the dock is drawn, and stop at the ends`() {
        // A 700 dp window: 5% is 35 dp.
        assertEquals((392f + 35f) / 700f, dockPositionStepFrom(392f, 700f, 1), .001f)
        assertEquals((392f - 35f) / 700f, dockPositionStepFrom(392f, 700f, -1), .001f)
        assertEquals(0f, dockPositionStepFrom(10f, 700f, -1), 0f)
        assertEquals(1f, dockPositionStepFrom(690f, 700f, 1), 0f)
    }

    @Test fun `a step from the dock's edge moves it at once, even after a drag saved exactly 0 or 1`() {
        // The dock is drawn with its middle at 132 dp (the safe edge, 19% of 700) though 0 was saved.
        val saved = dockPositionStepFrom(132f, 700f, 1)
        assertEquals((132f + 35f) / 700f, saved, .001f)
        assertEquals("one step is already a visible move", true, saved * 700f > 132f + 30f)
    }
}
