package com.mccal.folio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * #61: on the Galaxy Z Fold8 cover the island was far taller than the camera. Android reports the punch hole as a rect
 * from the screen edge down past the lens (600, 0, 648, 104 px), and the island wrapped all of it. The cutout's own
 * outline is the hole, so the island wraps that when it fits inside the rect.
 */
class CameraRectTest {
    private val fold8Cover = intArrayOf(600, 0, 648, 104)
    // A 48 px lens at the bottom of that rect, as an example outline.
    private val lens = intArrayOf(600, 56, 648, 104)

    @Test fun `an outline inside a taller rect is the camera`() {
        assertArrayEquals(lens, cameraRect(fold8Cover, lens))
    }

    @Test fun `without a usable outline the reported rect is kept`() {
        assertArrayEquals(fold8Cover, cameraRect(fold8Cover, null))
        assertArrayEquals(fold8Cover, cameraRect(fold8Cover, intArrayOf(620, 50, 620, 50)))
        // As tall as the rect: nothing to gain, keep the rect.
        assertArrayEquals(fold8Cover, cameraRect(fold8Cover, fold8Cover.copyOf()))
    }

    @Test fun `an outline that also covers another cutout, or sits elsewhere, is not trusted`() {
        // The path holds every cutout on the display; its bounds then run past this rect.
        assertArrayEquals(fold8Cover, cameraRect(fold8Cover, intArrayOf(600, 56, 1248, 1972)))
        assertArrayEquals(fold8Cover, cameraRect(fold8Cover, intArrayOf(10, 10, 58, 58)))
        // Rounding of a pixel or two still counts as inside.
        assertArrayEquals(intArrayOf(599, 55, 649, 105), cameraRect(fold8Cover, intArrayOf(599, 55, 649, 105)))
    }

    @Test fun `the island at the Fold8 cover camera is a normal pill around the lens`() {
        val density = 2.625f
        val before = islandGeometry(fold8Cover, 1248, density)
        val after = islandGeometry(cameraRect(fold8Cover, lens), 1248, density)
        assertEquals("the old pill wrapped the whole rect", 104 / density + 5f, before.pillH, .01f)
        assertEquals("now the usual height", 34f, after.pillH, .01f)
        // Still wraps the lens: starts above it and ends below it.
        assertEquals(true, after.top < 56 / density && after.top + after.pillH > 104 / density)
        assertEquals(before.centerXPx, after.centerXPx, 0f)
    }
}
