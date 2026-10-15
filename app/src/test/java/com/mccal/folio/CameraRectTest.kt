package com.mccal.folio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #61: on the Galaxy Z Fold8 cover the island was far taller than the camera, because Android reports the camera as a
 * strip from the top edge down past the lens and the island wrapped all of it. When the cutout's own outline is a
 * smaller hole inside that rect, the island wraps the outline. When the outline is just the strip, as on the real
 * Fold8, the island keeps the usual pill height on the strip.
 */
class CameraRectTest {
    private val density = 2.625f
    // Measured on McCal's Fold8 cover (1248 x 1972) on 14 Oct 2026: the rect, and an outline that is the same
    // rectangle (spec "M 0,0 H -13.333 V 39.619 H 13.333 V 0 H 0 Z @dp").
    private val fold8Cover = intArrayOf(589, 0, 659, 104)
    // The PR first assumed 600,0 - 648,104 (from SideIslandTest) with a 48 px lens at its bottom as the outline. No
    // device has shown that outline yet; the case stays to cover a smaller hole inside a strip.
    private val assumedStrip = intArrayOf(600, 0, 648, 104)
    private val lens = intArrayOf(600, 56, 648, 104)

    @Test fun `an outline inside a taller rect is the camera`() {
        assertArrayEquals(lens, cameraRect(assumedStrip, lens, density))
    }

    @Test fun `without a usable outline the reported rect is kept`() {
        assertArrayEquals(fold8Cover, cameraRect(fold8Cover, null, density))
        assertArrayEquals(fold8Cover, cameraRect(fold8Cover, intArrayOf(620, 50, 620, 50), density))
        // A punch hole off the edge whose outline is as tall as its rect: nothing to gain, keep the rect.
        val hole = intArrayOf(600, 30, 648, 78)
        assertArrayEquals(hole, cameraRect(hole, hole.copyOf(), density))
    }

    @Test fun `an outline that also covers another cutout, or sits elsewhere, is not trusted`() {
        // The path holds every cutout on the display; its bounds then run past this rect.
        assertArrayEquals(assumedStrip, cameraRect(assumedStrip, intArrayOf(600, 56, 1248, 1972), density))
        assertArrayEquals(assumedStrip, cameraRect(assumedStrip, intArrayOf(10, 10, 58, 58), density))
        // Rounding of a pixel or two still counts as inside.
        assertArrayEquals(intArrayOf(599, 55, 649, 105), cameraRect(assumedStrip, intArrayOf(599, 55, 649, 105), density))
    }

    @Test fun `the island at the Fold8 cover camera is a normal pill around the lens`() {
        val before = islandGeometry(assumedStrip, 1248, density)
        val after = islandGeometry(cameraRect(assumedStrip, lens, density), 1248, density)
        assertEquals("the old pill wrapped the whole rect", 104 / density + 5f - 8f, before.pillH, .01f)
        assertEquals("now the usual height", 34f, after.pillH, .01f)
        // Still wraps the lens: starts above it and ends below it.
        assertTrue(after.top < 56 / density && after.top + after.pillH > 104 / density)
        assertEquals(before.centerXPx, after.centerXPx, 0f)
    }

    @Test fun `the real Fold8 strip counts as a strip`() {
        assertTrue(isCameraStrip(fold8Cover, fold8Cover, density))
        // A pixel of rounding in the outline still does.
        assertTrue(isCameraStrip(fold8Cover, intArrayOf(588, 0, 660, 105), density))
    }

    @Test fun `the real Fold8 strip gets the usual pill on the strip`() {
        val before = islandGeometry(fold8Cover, 1248, density)
        val after = islandGeometry(cameraRect(fold8Cover, fold8Cover.copyOf(), density), 1248, density)
        assertEquals("before: wrapped the whole strip", 104 / density + 5f - 8f, before.pillH, .01f)
        assertEquals("the usual height", 34f, after.pillH, .01f)
        // Same horizontal center as the strip, and as wide around it.
        assertEquals(624f, after.centerXPx, 0f)
        assertEquals(70 / density, after.camW, .01f)
        // On the strip: the usual gap from the edge (Home holds it there anyway), then down past the strip's bottom.
        assertEquals(8f, after.top, .01f)
        assertTrue(after.top + after.pillH > 104 / density)
        // Its center is the strip's, moved down only as far as the edge gap needs.
        assertEquals(maxOf(52 / density, 8f + 34f / 2), after.centerY, .01f)
    }

    @Test fun `a taller strip is centered on itself`() {
        val tall = intArrayOf(589, 0, 659, 160)
        val g = islandGeometry(cameraRect(tall, tall.copyOf(), density), 1248, density)
        assertEquals(34f, g.pillH, .5f)
        assertEquals(80 / density, g.centerY, .5f)
    }

    @Test fun `notches, punch holes and short strips are not strips`() {
        // A notch: wider than tall.
        val notch = intArrayOf(400, 0, 848, 90)
        assertFalse(isCameraStrip(notch, notch, density))
        assertArrayEquals(notch, cameraRect(notch, notch.copyOf(), density))
        // A punch hole off the edge.
        val hole = intArrayOf(600, 30, 648, 130)
        assertFalse(isCameraStrip(hole, hole, density))
        // A strip no taller than the usual pill.
        val short = intArrayOf(600, 0, 648, 80)
        assertFalse(isCameraStrip(short, short, density))
        // An outline smaller than the strip is a hole, which still wins.
        assertFalse(isCameraStrip(fold8Cover, intArrayOf(589, 40, 659, 104), density))
        // A camera on a side edge (cover turned sideways) is left alone.
        val side = intArrayOf(0, 589, 104, 659)
        assertArrayEquals(side, cameraRect(side, side.copyOf(), density))
    }
}
