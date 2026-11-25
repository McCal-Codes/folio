package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rail beside Control Center's grid: the status at the top (battery ring, then the Wi-Fi and cellular marks with their words) and the page
 * buttons under it. On the Fold's inner screen the buttons stood in the middle of the rail and the first one sat under the cellular mark, against
 * the Wi-Fi name (McCal 24 Nov 2026, beta 6 device test: "looks like control center icons are overlapping"). For every screen in the shared
 * matrix, turned and as a split half, at larger display sizes and every system text size: the buttons never start above the status block's
 * bottom and a gap, and whenever the marks are shown the whole rail fits the panel.
 */
class CcRailTest {
    private data class Screen(val name: String, val w: Float, val h: Float)

    /** Larger display size settings leave fewer dp on the same screen (Android's largest is about 1.3 times the default). */
    private val displaySizes = listOf(1f, 1.15f, 1.3f)

    /** System text sizes, up to Android 14's largest (200%). */
    private val fontScales = listOf(.85f, 1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f)

    private val screens: List<Screen> = org.json.JSONObject(javaClass.getResource("/screen-matrix.json")!!.readText()).getJSONArray("devices").let { list ->
        (0 until list.length()).map { list.getJSONObject(it) }.flatMap { d ->
            val w = d.getDouble("width").toFloat(); val h = d.getDouble("height").toFloat(); val n = d.getString("name")
            displaySizes.flatMap { z ->
                val zw = w / z; val zh = h / z; val tag = if (z == 1f) n else "$n at display size %.2f".format(z)
                listOf(Screen(tag, zw, zh), Screen("$tag rotated", zh, zw)) +
                    (if (maxOf(zw, zh) >= 800f) listOf(Screen("$tag split half", maxOf(zw, zh) / 2f, minOf(zw, zh))) else emptyList())
            }
        }
    }

    /** The height the panel has, as in ControlCenterFitTest: the window less the top inset and gap, the bottom gap and the navigation bar. */
    private fun available(s: Screen) = s.h - (24f + 24f + 12f + 24f + 16f)

    private val gap = FolioSpace.SMALL.toFloat()

    /** The page buttons' column as the rail draws it: 48 dp each, [FolioSpace.TINY] apart on a roomy rail and [gap] apart on a short one. */
    private fun pagesHeight(pages: Int, roomy: Boolean) = pages * 48f + (pages - 1) * (if (roomy) FolioSpace.TINY.toFloat() else gap)

    @Test fun `status and page buttons never overlap and the rail fits the panel on every screen`() {
        val problems = mutableListOf<String>()
        var withMarks = 0
        var pushedDown = 0
        for (s in screens) for (fontScale in fontScales) for (pages in 1..7) for (apart in listOf(false, true)) {
            val tall = available(s)
            val column = tall - 2 * CC_PANEL_PAD_DP
            val roomy = tall >= 560f
            val ring = tall >= 360f
            val marks = ring && tall >= 520f && ccRailShowsReadouts(column, pages, gap, apart, fontScale)
            if (marks) withMarks++
            // The status block as the rail stacks it: the ring (and its extra gap when apart), then the marks a gap under it.
            val status = (if (ring) 48f + (if (apart) CC_BATTERY_APART_DP else 0f) else 0f) + (if (marks) gap + ccRailReadoutsHeight(fontScale) else 0f)
            val buttons = pagesHeight(pages, roomy)
            // The rail is at least as tall as the grid beside it, which is anywhere from a little to all of the panel.
            for (share in listOf(.25f, .5f, .75f, 1f)) {
                val rail = if (roomy) column * share else 0f
                val top = ccRailPagesTop(rail, status, buttons, gap, roomy)
                val tag = "${s.name} at %.0f x %.0f, text %.2f, %d pages, apart %s, rail %.0f".format(s.w, s.h, fontScale, pages, apart, rail)
                if (status > 0f && top < status + gap) problems += "$tag: the page buttons start at %.0f, inside the status block (%.0f)".format(top, status)
                if (roomy && (rail - buttons) / 2f >= status + gap && top != (rail - buttons) / 2f) problems += "$tag: the page buttons left the middle with room to stay"
                if (roomy && top > (rail - buttons) / 2f) pushedDown++
                if (marks && maxOf(rail, top + buttons) > column) problems += "$tag: the rail needs %.0f of %.0f".format(maxOf(rail, top + buttons), column)
            }
        }
        assertTrue("the matrix never shows the connection marks", withMarks > 0)
        assertTrue("the matrix never reaches a rail where the middle would overlap the status", pushedDown > 0)
        assertTrue("The rail overlaps or overflows on these windows (${problems.size}): " + problems.take(40).joinToString(" | "), problems.isEmpty())
    }

    @Test fun `the reported Fold rail puts the first page under the Wi-Fi and cellular marks`() {
        // The beta 6 screenshot: ring, Wi-Fi with its name, cellular, then pages 1 and 2, Media and Connectivity, on a rail about as tall as
        // a three-row grid. The middle of that rail is above the status block's bottom, so the buttons move down under it.
        val status = 48f + CC_BATTERY_APART_DP + gap + ccRailReadoutsHeight(1f)
        val buttons = pagesHeight(4, roomy = true)
        val rail = 420f
        assertTrue("the case should be one where the middle overlaps", (rail - buttons) / 2f < status + gap)
        assertEquals(status + gap, ccRailPagesTop(rail, status, buttons, gap, center = true), 0f)
    }

    @Test fun `a tall rail keeps the page buttons in its middle`() {
        assertEquals(300f, ccRailPagesTop(800f, 150f, 200f, gap, center = true), 0f)
    }

    @Test fun `without a status block the page buttons start at the top or in the middle`() {
        assertEquals(0f, ccRailPagesTop(0f, 0f, 200f, gap, center = false), 0f)
        assertEquals(100f, ccRailPagesTop(400f, 0f, 200f, gap, center = true), 0f)
    }

    @Test fun `the marks are left out when the panel cannot hold them and every page button`() {
        // 2.0 text on a short column with seven pages: the ring and the buttons fit, the marks do not.
        val column = ccEdgeStripHeight(7, battery = true, gapDp = gap, apart = true) + 40f
        assertFalse(ccRailShowsReadouts(column, 7, gap, apart = true, fontScale = 2f))
        assertTrue(ccRailShowsReadouts(column + ccRailReadoutsHeight(2f), 7, gap, apart = true, fontScale = 2f))
    }

    @Test fun `the marks grow with the text size`() {
        assertTrue(ccRailReadoutsHeight(2f) > ccRailReadoutsHeight(1f))
        assertEquals(ccRailReadoutsHeight(1f), ccRailReadoutsHeight(.85f), 0f)
    }
}
