package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Folio's own background drifts as Home pages move, the way Android's wallpaper does when the system is moving it.
 *
 * Two things have to hold for that to be free: it is a translation and nothing else, so the drawn background stays
 * cached in its layer, and the background is wide enough that sliding it can never show a strip of nothing.
 */
class BackgroundDriftTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private fun source(name: String) = File(root, "app/src/main/java/com/mccal/folio/$name").readText()

    @Test fun `a single page cannot drift, and costs nothing`() {
        assertEquals(0f, BackgroundDrift.spare(1), 0f)
        assertEquals(0, BackgroundDrift.spareWidth(1080, 1))
        assertEquals(0f, BackgroundDrift.slide(0f, 1, 1080f), 0f)
        // A pager Folio hasn't filled yet behaves the same way rather than dividing by zero.
        assertEquals(0f, BackgroundDrift.slide(0f, 0, 1080f), 0f)
    }

    @Test fun `two and three pages drift by three tenths of a page each`() {
        for (pages in listOf(2, 3)) {
            val width = 1000f
            val background = width + BackgroundDrift.spareWidth(width.toInt(), pages)
            val steps = pages - 1
            val perPage = BackgroundDrift.slide(0f, pages, background) - BackgroundDrift.slide(1f, pages, background)
            assertEquals("$pages pages", BackgroundDrift.PER_PAGE * width, perPage, 1f)
            // And it is the same for every step, so no page feels different from the next.
            for (step in 1 until steps) {
                val here = BackgroundDrift.slide(step.toFloat(), pages, background) -
                    BackgroundDrift.slide(step + 1f, pages, background)
                assertEquals(perPage, here, .01f)
            }
        }
    }

    @Test fun `more pages compress the drift instead of widening the background without end`() {
        assertEquals(BackgroundDrift.MOST, BackgroundDrift.spare(5), 0f)
        assertEquals(BackgroundDrift.MOST, BackgroundDrift.spare(40), 0f)
        val width = 1000f
        val background = width + BackgroundDrift.spareWidth(width.toInt(), 40)
        val perPage = BackgroundDrift.slide(0f, 40, background) - BackgroundDrift.slide(1f, 40, background)
        assertTrue("40 pages would slide $perPage px a page", perPage < BackgroundDrift.PER_PAGE * width)
    }

    @Test fun `the drift is centred, so neither end shows more of the background than the other`() {
        val background = 1000f + BackgroundDrift.spareWidth(1000, 3)
        val first = BackgroundDrift.slide(0f, 3, background)
        val last = BackgroundDrift.slide(2f, 3, background)
        assertEquals(0f, first + last, .001f)
        assertEquals(0f, BackgroundDrift.slide(1f, 3, background), .001f)
        assertTrue("it should slide the same way pages do", first > 0f)
    }

    @Test fun `no window and no page ever uncovers an edge`() {
        for (windowWidth in listOf(1, 320, 411, 1080, 1768, 2208, 2999)) {
            for (pages in 1..12) {
                val extra = BackgroundDrift.spareWidth(windowWidth, pages)
                assertTrue("odd spare cannot be halved evenly", extra % 2 == 0)
                val background = (windowWidth + extra).toFloat()
                val room = extra / 2f
                // Past the ends too: a pager can overscroll past page zero and past the last page.
                for (tenth in -5..(pages - 1) * 10 + 5) {
                    val slide = BackgroundDrift.slide(tenth / 10f, pages, background)
                    assertTrue("$windowWidth px, $pages pages, at ${tenth / 10f}: slid $slide with $room to spare",
                        kotlin.math.abs(slide) <= room + .01f)
                }
            }
        }
    }

    @Test fun `the background is measured wider than the window, without widening the window`() {
        assertEquals(0, BackgroundDrift.spareWidth(1000, 1))
        // What the layout does with that spare width is checked in BackgroundDriftLayoutTest, which needs a renderer.
        assertEquals(600, BackgroundDrift.spareWidth(1000, 3))
        assertEquals(300, BackgroundDrift.spareWidth(1000, 2))
    }

    @Test fun `the drift is a translation on the cached layer and nothing else`() {
        val wallpaper = source("DuneWallpaper.kt")
        val block = wallpaper.substring(wallpaper.indexOf("internal fun DuneWallpaper("), wallpaper.indexOf("internal fun DrawScope.drawLauncherBackground"))
        assertTrue("the dunes must stay cached in an offscreen layer", "CompositingStrategy.Offscreen" in block)
        assertTrue("the background must slide with translationX", "translationX = BackgroundDrift.slide" in block)
        for (costly in listOf("scaleX", "scaleY", "rotationY", "rotationX", "rotationZ", "alpha =", "clip =", "renderEffect")) {
            assertTrue("$costly would stop the layer being a plain matrix offset", costly !in block)
        }
        // Read in the layer block, never as a parameter: a value read in composition recomposes every frame (PRF-7).
        val layer = block.substring(block.indexOf(".graphicsLayer {"))
        assertTrue("the pager position belongs in the layer block", "currentPageOffsetFraction" in layer)
        val beforeLayer = block.substring(0, block.indexOf(".graphicsLayer {"))
        assertTrue("the position must not be read in composition", "currentPageOffsetFraction" !in beforeLayer)
    }

    @Test fun `one switch moves whichever background is showing, and Reduce Motion stops it`() {
        val settings = source("CustomizationSheet.kt")
        val row = settings.lines().first { "wallpaper-motion-switch" in it }
        assertTrue("the switch must not be hidden when Folio's own background is showing: $row",
            "if (state.systemWallpaper)" !in row)
        assertTrue("the switch keeps the setting it always had", "state.wallpaperMotion" in row)

        val screen = source("LauncherScreen.kt")
        assertTrue("Reduce Motion must leave both backgrounds still",
            "state.wallpaperMotion && !LocalReduceMotion.current" in screen)
        assertTrue("Folio's own background has to be given the pager",
            "DuneWallpaper(drift = nativePager.takeIf { backgroundMoves }" in screen)
        assertTrue("Android's wallpaper still moves through the system",
            "if (backgroundMoves) SystemWallpaperParallax(nativePager)" in screen)
    }

    @Test fun `the setting people already chose is the one that is read`() {
        // Renaming the saved key would silently turn the switch back on for everyone who had turned it off.
        val model = source("LauncherModel.kt")
        assertTrue("wallpaperMotion" in model)
        assertTrue("""put("wallpaperMotion", s.wallpaperMotion)""" in model)
        assertTrue("""j.optBoolean("wallpaperMotion", true)""" in model)
        assertTrue(LauncherState().wallpaperMotion)
    }
}
