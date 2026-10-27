package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Control Center should never need scrolling and nothing in it should overlap (McCal 25 Oct 2026: "you shouldn't have to scroll in it",
 * "don't overlap things"; PNL-1 and PNL-2 in docs/standards/panels.md). For every screen in the shared matrix, in both orientations and
 * as a split half, at each size and with the controls Folio starts with and with all of them: the controls are split over pages so each
 * page fits the height the panel has, and the header and page strip fit beside each other and the pages.
 */
class ControlCenterFitTest {
    private data class Screen(val name: String, val w: Float, val h: Float)

    private val screens: List<Screen> = org.json.JSONObject(javaClass.getResource("/screen-matrix.json")!!.readText()).getJSONArray("devices").let { list ->
        (0 until list.length()).map { list.getJSONObject(it) }.flatMap { d ->
            val w = d.getDouble("width").toFloat(); val h = d.getDouble("height").toFloat(); val n = d.getString("name")
            listOf(Screen(n, w, h), Screen("$n rotated", h, w)) +
                (if (maxOf(w, h) >= 800f) listOf(Screen("$n split half", maxOf(w, h) / 2f, minOf(w, h))) else emptyList())
        }
    }

    /** The height the panel has: the window less the top inset and gap, the bottom gap and the navigation bar, rounded up to stay honest. */
    private fun available(s: Screen) = s.h - (24f + 24f + 12f + 24f + 16f)

    private fun check(s: Screen, size: Float, n: Int, focus: Boolean, problems: MutableList<String>) {
        val panelHeight = available(s)
        val g = controlCenterGrid(s.w - 32f, panelHeight, n, focus, size)
        val chrome = 48f + g.gapDp + 2 * CC_PANEL_PAD_DP + (if (g.stripAtEdge || g.stripInHeader) 0f else g.stripDp)
        val pageHeight = panelHeight - chrome
        val rows = ccRowsThatFit(pageHeight, g.cellDp, g.gapDp)
        val plan = ccPlanControls(g.columns, g.cellDp, g.gapDp, pageHeight, n, focus)
        val tag = "${s.name} at %.0f x %.0f, size %.0f, %d controls, focus %s (%d columns, cell %.0f, %d rows)".format(s.w, s.h, size, n, focus, g.columns, g.cellDp, rows)
        if (plan.perPage.sum() != n) problems += "$tag: ${plan.perPage.sum()} of $n controls placed"
        if (rows < 2) problems += "$tag: not even the modules fit"
        plan.perPage.indices.forEach { i ->
            val used = ccPlanRows(plan, i, g.columns, focus)
            if (used > rows) problems += "$tag: page ${i + 1} needs $used rows"
        }
        if (n > 0 && plan.perPage.any { it == 0 } && plan.perPage.size > 1 && plan.perPage.drop(1).any { it == 0 }) problems += "$tag: an empty page"
        // The header's three buttons (edit, close, power) side by side fit the width of the grid they sit above; with the strip in the header its own check below covers them.
        if (!g.stripInHeader) {
            val gridWidth = g.columns * g.cellDp + (g.columns - 1) * g.gapDp
            val needed = 3 * 48f + 2 * 8f
            if (gridWidth < needed) problems += "$tag: header buttons need %.0f of the grid's %.0f".format(needed, gridWidth)
        }
        // The strip in the header: the two buttons and a dot for each page fit the panel's width (the header may be wider than a narrow grid).
        if (g.stripInHeader) {
            val width = ccHeaderMinWidth(plan.perPage.size + 2)
            if (width > s.w - 32f) problems += "$tag: header needs %.0f of %.0f".format(width, s.w - 32f)
        }
    }

    @Test fun `every screen fits every Controls page without scrolling`() {
        val problems = mutableListOf<String>()
        for (s in screens) for (size in listOf(50f, 58f, 66f, 76f, 88f)) for (n in listOf(0, 8, 15)) for (focus in listOf(false, true)) check(s, size, n, focus, problems)
        assertTrue("These windows would scroll or overlap (${problems.size}):\n" + problems.take(40).joinToString("\n"), problems.isEmpty())
    }

    @Test fun `with room for everything the plan is one page, as before`() {
        val plan = ccPlanControls(4, 66f, 12f, 700f, 8, true)
        assertEquals(listOf(8), plan.perPage)
        assertTrue(plan.slidersOnFirst)
        assertEquals(listOf(6), ccPlanControls(6, 66f, 12f, null, 6, false).perPage)
    }

    @Test fun `controls that do not fit spill onto a second page and the sliders stay for the Media page`() {
        // 200 dp for the pages at 44 dp cells: four rows. Four columns, Focus on: two rows of modules, one of Focus, one left: no room for the 2 by 2 and sliders.
        val short = ccPlanControls(4, 44f, 8f, 200f, 15, true)
        assertEquals(4, ccRowsThatFit(200f, 44f, 8f))
        assertEquals(false, short.slidersOnFirst)
        assertEquals(15, short.perPage.sum())
        assertTrue(short.perPage.size >= 2)
        assertEquals(4, short.perPage[0])
    }

    @Test fun `Focus moves to the Media page when the first page has only the two module rows`() {
        // About 290 dp tall window: two rows fit. The modules fill them, so Focus cannot sit under them (it was cut off by 12 dp).
        val tight = ccPlanControls(4, 50f, 9f, 110f, 8, true)
        assertEquals(2, ccRowsThatFit(110f, 50f, 9f))
        assertEquals(false, tight.focusOnFirst)
        assertEquals(2, ccPlanRows(tight, 0, 4, true))
        assertEquals(4, ccMediaRows(tight, true))
        assertEquals(8, tight.perPage.sum())
        // With the row to spare it stays where it was, and without Focus nothing moves.
        assertEquals(true, ccPlanControls(4, 50f, 9f, 170f, 8, true).focusOnFirst)
        assertEquals(3, ccMediaRows(ccPlanControls(4, 50f, 9f, 110f, 8, false), false))
    }

    @Test fun `header and strip icons grow with the font size but stay inside their targets`() {
        assertEquals(1f, ccIconScale(.85f), 0f)
        assertEquals(1f, ccIconScale(1f), 0f)
        assertEquals(4f / 3f, ccIconScale(2f), 0f)
        // The largest drawn circle (34 dp) and strip icon (22 dp) at the largest growth fit the 48 dp target.
        assertTrue(34 * ccIconScale(3f) <= 48f && 22 * ccIconScale(3f) <= 48f)
    }
}
