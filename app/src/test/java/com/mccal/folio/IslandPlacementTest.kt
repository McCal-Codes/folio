package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One placement for the island on Home and over other apps ([islandPlacement]). Home used to floor the top at the
 * edge gap while the overlay did not, so the two sat at different heights (#394), and over apps a camera on a side
 * edge put the pill mid-edge and partly off screen.
 */
class IslandPlacementTest {
    private data class Case(
        val name: String, val camera: IntArray?, val widthPx: Int, val heightPx: Int, val density: Float,
        val centerXPx: Float, val top: Float, val pillH: Float, val camW: Float,
    )

    // Measured on McCal's Fold8 cover (1248 x 1972 at 2.625) on 14 Oct 2026, see CameraRectTest.
    private val fold8Strip = intArrayOf(589, 0, 659, 104)
    // A hidden inner camera at the top of a landscape window, and the same camera with the screen turned.
    private val innerTop = intArrayOf(1823, 18, 1901, 96)
    private val innerTurned90 = intArrayOf(18, 2448 - 1901, 96, 2448 - 1823)
    private val innerTurned180 = intArrayOf(2448 - 1901, 1848 - 96, 2448 - 1823, 1848 - 18)

    private val cases = listOf(
        Case("cover strip, portrait", cameraRect(fold8Strip, fold8Strip.copyOf(), 2.625f), 1248, 1972, 2.625f,
            centerXPx = 624f, top = 8f, pillH = 34f, camW = 70 / 2.625f),
        Case("cover strip, landscape: camera on the left edge", intArrayOf(0, 589, 104, 659), 1972, 1248, 2.625f,
            centerXPx = 986f, top = 8f, pillH = 34f, camW = 0f),
        Case("camera on the right edge", intArrayOf(1868, 589, 1972, 659), 1972, 1248, 2.625f,
            centerXPx = 986f, top = 8f, pillH = 34f, camW = 0f),
        Case("inner screen, no cutout", null, 2184, 1968, 2.625f,
            centerXPx = 1092f, top = 8f, pillH = 34f, camW = 0f),
        Case("inner camera at the top", innerTop, 2448, 1848, 2.625f,
            centerXPx = 1862f, top = 18 / 2.625f - 2f, pillH = 96 / 2.625f + 5f - (18 / 2.625f - 2f), camW = 78 / 2.625f),
        Case("inner turned 90 degrees: camera on the left edge", innerTurned90, 1848, 2448, 2.625f,
            centerXPx = 924f, top = 8f, pillH = 34f, camW = 0f),
        Case("inner turned upside down: camera on the bottom edge", innerTurned180, 2448, 1848, 2.625f,
            centerXPx = 1224f, top = 8f, pillH = 34f, camW = 0f),
        // A punch hole closer to the top than the usual gap: the pill starts above the lens, with no 8 dp floor.
        Case("punch hole near the top", intArrayOf(900, 6, 940, 46), 2000, 1800, 2f,
            centerXPx = 920f, top = 1f, pillH = 34f, camW = 20f),
        // A notch starts at the edge itself, so the pill keeps the usual gap from the edge instead.
        Case("notch at the edge", intArrayOf(400, 0, 848, 90), 1248, 1972, 2.625f,
            centerXPx = 624f, top = 8f, pillH = 90 / 2.625f + 5f - 8f, camW = 448 / 2.625f),
    )

    @Test fun `every screen places the island where expected`() {
        for (c in cases) {
            val g = islandPlacement(c.camera, null, c.widthPx, c.heightPx, c.density)
            assertEquals(c.name + ": center", c.centerXPx, g.centerXPx, .5f)
            assertEquals(c.name + ": top", c.top, g.top, .01f)
            assertEquals(c.name + ": height", maxOf(c.pillH, 34f), g.pillH, .01f)
            assertEquals(c.name + ": camera width", c.camW, g.camW, .01f)
        }
    }

    @Test fun `the island is always fully on screen`() {
        val content = IslandContent.Event(IslandEvent.Silent(true))
        for (c in cases) {
            val g = islandPlacement(c.camera, null, c.widthPx, c.heightPx, c.density)
            val widthPx = g.widthFor(content) * c.density
            val left = islandLeftPx(g.centerXPx, widthPx, c.widthPx, c.density)
            assertTrue(c.name + ": left $left", left >= 0f)
            assertTrue(c.name + ": right ${left + widthPx}", left + widthPx <= c.widthPx)
            assertTrue(c.name + ": top", g.top >= 0f)
            assertTrue(c.name + ": bottom", (g.top + g.pillH) * c.density <= c.heightPx)
        }
    }

    @Test fun `a camera on the top edge is still wrapped`() {
        for (c in cases.filter { it.camW > 0f }) {
            val g = islandPlacement(c.camera, null, c.widthPx, c.heightPx, c.density)
            val camTop = c.camera!![1] / c.density; val camBottom = c.camera[3] / c.density
            if (c.camera[1] > 2) assertTrue(c.name + ": starts above the lens", g.top < camTop)
            assertTrue(c.name + ": ends below the lens", g.top + g.pillH > camBottom)
        }
    }

    @Test fun `a dragged spot is kept on screen over apps as on Home`() {
        val strip = cameraRect(fold8Strip, fold8Strip.copyOf(), 2.625f)
        val offBottom = islandPlacement(strip, IslandPosition(.5f, 900f), 1248, 1972, 2.625f)
        assertEquals(1972 / 2.625f - 34f - 8f, offBottom.top, .01f)
        val offTop = islandPlacement(strip, IslandPosition(1.4f, -20f), 1248, 1972, 2.625f)
        assertEquals(8f, offTop.top, .01f)
        assertEquals(1248f, offTop.centerXPx, .01f)
        // A pill pushed against the right edge still keeps the side margin.
        val left = islandLeftPx(offTop.centerXPx, 200 * 2.625f, 1248, 2.625f)
        assertEquals(1248 - 200 * 2.625f - 12 * 2.625f, left, .01f)
    }

    @Test fun `with a side camera a drag snaps to the real camera, not to top center`() {
        val camera = innerTurned90
        val w = 1848; val h = 2448; val d = 2.625f
        val real = islandGeometry(camera, w, d)
        val fallback = islandCameraGeometry(camera, w, h, d)
        // The pill is drawn at top center, but the snap target is still the physical camera on the left edge.
        assertEquals(w / 2f, fallback.centerXPx, .5f)
        assertTrue(islandSnapsToCamera(real.centerXPx, real.top, real, d))
        assertTrue(!islandSnapsToCamera(fallback.centerXPx, fallback.top, real, d))
    }

    @Test fun `Home and the overlay use the same saved spot key`() {
        // Cover landscape is 751 dp wide but short; it used to count as wide on Home and not over apps.
        assertTrue(islandWideWindow(1972, 2.625f, 1f))
        assertTrue(!islandWideWindow(1248, 2.625f, 1f))
    }
}
