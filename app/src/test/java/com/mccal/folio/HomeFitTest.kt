package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Home's placed bounds on every window in screen-matrix.json (rotated, and split in half where it is big enough), judged
 * from window size and dp only, never from a device. Where the usual four app rows sit, where the page controls begin,
 * and how much free height is left above and below the page.
 *
 * Set FOLIO_HOME_FIT_TABLE=<file> to write the table the pull request quotes.
 */
class HomeFitTest {
    private data class Window(val name: String, val width: Float, val height: Float)

    private val windows: List<Window> = org.json.JSONObject(javaClass.getResource("/screen-matrix.json")!!.readText()).getJSONArray("devices").let { list ->
        (0 until list.length()).map { i -> list.getJSONObject(i).let { Window(it.getString("name"), it.getDouble("width").toFloat(), it.getDouble("height").toFloat()) } }
    }.flatMap { d ->
        listOf(d, Window("${d.name} rotated", d.height, d.width)) +
            (if (maxOf(d.width, d.height) >= 800f) listOf(Window("${d.name} split half", maxOf(d.width, d.height) / 2f, minOf(d.width, d.height))) else emptyList())
    }

    /** The window as Home sees it: after the big-screen scale, like the other geometry tests. */
    private fun forEachWindow(block: (String, Float, Float) -> Unit) {
        for (s in windows) {
            val scale = uiScale(s.width, s.height)
            block("${s.name} (${(s.width / scale).toInt()}x${(s.height / scale).toInt()})", s.width / scale, s.height / scale)
        }
    }

    /** Home as the launcher asks for it: the base bottom space, and the controls' real space (Home app, or the Preview bar). */
    private fun geo(w: Float, h: Float, duo: Boolean, labels: Boolean = true, rows: Int = BASE_APP_ROWS, automatic: Boolean = false, preview: Boolean = false,
        fill: Boolean = false, preset: LayoutPreset = LayoutPreset(), real: Boolean = false): HomeGeometry {
        fun make(appRows: Int, fillSpace: Boolean) = homeGeometry(w, h, preset, labels, statusHeight = if (fitsRegularHomeLayout(w, h)) 180f else 160f,
            homeBottomSpace = if (real) homeControlsSpace(!preview, 0f) else if (preview) 88f else 44f, controlsSpace = homeControlsSpace(!preview, 0f), appRows = appRows, fillSpace = fillSpace,
            widgetsFillRows = fill, duoSpacing = duo, shrinkIconsForRows = rows > BASE_APP_ROWS)
        return if (automatic) make(make(BASE_APP_ROWS, false).fitAppRows, true) else make(rows, false)
    }

    /** What the page really leaves for itself: under the status controls, and under the bottom dock bar when there is one. */
    private fun limit(g: HomeGeometry, h: Float, preview: Boolean) =
        h - homeControlsSpace(!preview, 0f) - (if (g.horizontalDock) g.dockBarHeight + 16f else 0f)

    private class Placed(val rows: Int, val end: Float, val topSlack: Float, val bottomSlack: Float, val reachableRows: Int, val cells: HomeCellLayout)

    private fun place(g: HomeGeometry, h: Float, preview: Boolean, rows: Int): Placed {
        val cells = HomeCellLayout.forPage(g, listOf(0 to 2))
        // The widget row is rows 0 and 1; app rows follow. Rows the page draws, under its top inset.
        // The last row's own space under its label may sit over the controls' padding: the visible page ends before it.
        val end = g.contentTop + cells.height(2 + rows) - g.rowGap
        val limit = limit(g, h, preview)
        val reachable = (0 until rows).count { r -> g.contentTop + cells.y(2 + r) + cells.spanHeight(2 + r, 1) - g.rowGap <= limit + 0.5f }
        return Placed(rows, end, g.contentTop, limit - end, reachable, cells)
    }

    @Test fun `the four app rows are inside the window and above the page controls, in every window`() {
        forEachWindow { tag, w, h ->
            for (preview in listOf(false, true)) for (labels in listOf(true, false)) for (duo in listOf(false, true)) {
                val g = geo(w, h, duo, labels, preview = preview)
                val p = place(g, h, preview, BASE_APP_ROWS)
                val name = "$tag preview=$preview labels=$labels duo=$duo"
                // Preview adds a bar that a window under about 400 dp has no room for beside four rows; it only needs to keep its own floors.
                val room = h - 16f - limitShortfall(g, h, preview)
                assertTrue("$name rows ${g.rowHeight} icon ${g.iconSize}", g.rowHeight >= 48f && g.iconSize >= LAST_RESORT_ICON)
                if (room >= floorHeight(g)) {
                    assertEquals("$name reachable rows (page ends ${p.end}, limit ${limit(g, h, preview)})", BASE_APP_ROWS, p.reachableRows)
                }
                // Every cell is inside the grid's own width, and the grid keeps off the Side Bar.
                for (r in 2 until 2 + BASE_APP_ROWS) for (c in 0 until GRID_COLUMNS) {
                    val x = p.cells.x(c, r)
                    assertTrue("$name cell x $x", x >= -0.01f && x + g.cellWidth <= g.gridWidth + 0.5f + g.columnsInset * 2f)
                }
                if (!g.horizontalDock) assertTrue("$name grid ${g.gridWidth} beside the Side Bar", g.gridWidth + 16f + LayoutPreset().dockWidth <= w + 0.01f)
                assertTrue("$name dock inside", g.dockTop >= 8f && g.dockTop + g.dockHeight <= h + 0.01f)
            }
        }
    }

    /** Height the usual four rows need at the smallest sizes Home goes to: widget, gap, four rows. */
    private fun floorHeight(g: HomeGeometry) = if (g.splitColumns) MIN_WIDGET_HEIGHT + 18f + 2f * 52f else MIN_WIDGET_HEIGHT + 18f + 4f * 52f

    private fun limitShortfall(g: HomeGeometry, h: Float, preview: Boolean) = h - limit(g, h, preview)

    @Test fun `rows do not overlap each other, the widget or the other half`() {
        forEachWindow { tag, w, h ->
            for (duo in listOf(false, true)) {
                val g = geo(w, h, duo)
                val cells = HomeCellLayout.forPage(g, listOf(0 to 2))
                for (r in 0 until 5) {
                    if (cells.splitRow != null && r + 1 == cells.splitRow) continue
                    assertTrue("$tag row $r then ${r + 1}", cells.y(r + 1) >= cells.y(r) + cells.pitch(r) - 0.01f)
                }
                // The widget (rows 0 and 1) ends where the first app row under it starts.
                assertEquals("$tag widget block", cells.spanHeight(0, 2), cells.y(2), 0.01f)
                if (cells.splitRow != null) {
                    val left = cells.x(0, 2); val right = cells.x(0, cells.splitRow)
                    assertEquals("$tag halves are one half apart", 4f * g.cellWidth + g.zoneGap, right - left, 0.01f)
                    // Balanced: the two halves have the same width and together fill the grid.
                    assertEquals("$tag halves fill the grid", g.gridWidth, 8f * g.cellWidth + g.zoneGap, 0.01f)
                }
            }
        }
    }

    @Test fun `a short window splits into two halves rather than stacking rows under the controls`() {
        for ((w, h) in listOf(800f to 360f, 640f to 360f, 640f to 384f, 923f to 411f, 997f to 448f)) {
            for (preview in listOf(false, true)) {
                val g = geo(w, h, false, preview = preview)
                assertTrue("${w}x$h splits", g.splitColumns)
                val p = place(g, h, preview, BASE_APP_ROWS)
                assertEquals("${w}x$h preview=$preview rows reachable (ends ${p.end})", BASE_APP_ROWS, p.reachableRows)
            }
        }
    }

    @Test fun `a window that already clears the controls is laid out exactly as before the controls were counted`() {
        forEachWindow { tag, w, h ->
            val plain = homeGeometry(w, h, LayoutPreset(), true, statusHeight = if (fitsRegularHomeLayout(w, h)) 180f else 160f)
            val counted = homeGeometry(w, h, LayoutPreset(), true, statusHeight = if (fitsRegularHomeLayout(w, h)) 180f else 160f,
                controlsSpace = homeControlsSpace(true, 0f))
            val cells = HomeCellLayout.forPage(plain, listOf(0 to 2))
            val end = plain.contentTop + cells.height(2 + BASE_APP_ROWS)
            val cleared = end <= h - homeControlsSpace(true, 0f) - (if (plain.horizontalDock) plain.dockBarHeight + 16f else 0f) + 0.5f
            if (cleared) assertEquals(tag, plain, counted)
        }
    }

    // ---- DUO_SPACING: rows added for a bigger window, pitch capped, free height shared above and below ----

    @Test fun `with DUO_SPACING the pitch never passes the cap and no window gets fewer rows or cells`() {
        forEachWindow { tag, w, h ->
            for (preview in listOf(false, true)) for (auto in listOf(false, true)) {
                val off = geo(w, h, false, automatic = auto, preview = preview)
                val on = geo(w, h, true, automatic = auto, preview = preview)
                // Only a marginal Automatic row that the controls would cover (off) can go; rows really reachable never do.
                assertTrue("$tag rows ${on.appRows} vs ${off.appRows}", on.appRows >= off.appRows - 1)
                // Spacing is shared out for the controls' real space, so it may be tighter by that extra space over the rows, never more.
                val extra = (homeControlsSpace(!preview, 0f) - (if (preview) 88f else 44f)) / (on.appRows + 1)
                assertTrue("$tag never tighter", on.rowHeight >= off.rowHeight - extra - 0.01f)
                assertTrue("$tag capped ${on.rowHeight} vs ${off.rowHeight}", on.rowHeight <= maxOf(off.rowHeight, minOf(off.rowHeight * (1f + DUO_PORTRAIT_ROW_GAIN_CAP), DUO_PORTRAIT_ROW_PITCH_MAX_DP)) + 0.01f)
                val pOff = place(off, h, preview, off.appRows); val pOn = place(on, h, preview, on.appRows)
                assertTrue("$tag auto=$auto preview=$preview cells never fewer: ${pOn.reachableRows}/${on.appRows} rows end ${pOn.end} top ${on.contentTop} gap ${on.rowGap} row ${on.rowHeight} vs ${pOff.reachableRows}/${off.appRows} end ${pOff.end} limit ${limit(on, h, preview)}", pOn.reachableRows >= pOff.reachableRows)
                // The page still ends above the controls wherever it did.
                if (pOff.bottomSlack >= -0.5f) assertTrue("$tag bottom slack ${pOn.bottomSlack}", pOn.bottomSlack >= -0.5f)
            }
        }
    }

    @Test fun `with DUO_SPACING the top and bottom bands are alike, and below a row on a window the rows can use`() {
        forEachWindow { tag, w, h ->
            for (fill in listOf(false, true)) {
                val auto = geo(w, h, true, automatic = true, fill = fill)
                if (auto.splitColumns) continue
                val p = place(auto, h, false, auto.appRows)
                val pitch = auto.rowHeight
                // Centered: the band above the first widget and the band above the controls differ by at most the 16 dp floor.
                assertTrue("$tag top ${p.topSlack} vs bottom ${p.bottomSlack}",
                    kotlin.math.abs(p.topSlack - p.bottomSlack) <= 1f || p.topSlack <= 16.5f)
                // Unless every row Home has is already shown (a window bigger than they can use), each band is under one row.
                if (auto.appRows < MAX_APP_ROWS) {
                    assertTrue("$tag top ${p.topSlack} under a row $pitch", p.topSlack <= pitch)
                    assertTrue("$tag bottom ${p.bottomSlack} under a row $pitch", p.bottomSlack <= pitch)
                }
            }
        }
    }

    @Test fun `a row count picked by hand is kept, and the free height goes to pitch and the insets`() {
        forEachWindow { tag, w, h ->
            for (rows in 4..7) {
                val on = geo(w, h, true, rows = rows)
                assertEquals("$tag rows", rows, on.appRows)
                val off = geo(w, h, false, rows = rows)
                assertTrue("$tag pitch", on.rowHeight in off.rowHeight - 0.001f..off.rowHeight * (1f + DUO_PORTRAIT_ROW_GAIN_CAP) + 0.01f)
                if (!on.splitColumns) {
                    val p = place(on, h, false, rows)
                    assertTrue("$tag centered", kotlin.math.abs(p.topSlack - p.bottomSlack) <= 1f || p.topSlack <= 16.5f || p.bottomSlack < 0f)
                }
            }
        }
    }

    @Test fun `windows under the wide threshold keep their page width, wider ones use their share`() {
        forEachWindow { tag, w, h ->
            val off = geo(w, h, false); val on = geo(w, h, true)
            if (w < WIDE_HOME_MIN_WIDTH_DP || !on.expanded) {
                // The Fold8 sizes (cover 475x751, inner 932x704 and upright), phones and every other window below the threshold.
                assertEquals("$tag home width", off.homeWidth, on.homeWidth, 0f)
                assertEquals("$tag grid width", off.gridWidth, on.gridWidth, 0f)
                assertEquals("$tag cell", off.cellWidth, on.cellWidth, 0f)
                assertEquals("$tag icon", off.iconSize, on.iconSize, 0f)
            } else {
                assertEquals("$tag page share", w * 0.56f, on.homeWidth, 0.01f)
                assertTrue("$tag wider than before", on.homeWidth > off.homeWidth)
                // What stays beside the page is the other pane (Today), under half the window; and the page's grid and Side Bar fit in it.
                assertTrue("$tag bare width ${w - on.homeWidth}", w - on.homeWidth <= w * 0.44f + 0.01f)
                assertTrue("$tag grid + dock in the page", on.gridWidth + 16f + LayoutPreset().dockWidth <= on.homeWidth + 0.01f)
                assertTrue("$tag icon in range", on.iconSize in 40f..68f)
            }
        }
        // The Fold8's own screens, by their measured sizes.
        for ((w, h) in listOf(475.43f to 751.2f, 932f to 704f, 704f to 932f)) {
            assertEquals("$w x $h", geo(w, h, false).homeWidth, geo(w, h, true).homeWidth, 0f)
        }
    }

    @Test fun `write the table`() {
        val file = System.getenv("FOLIO_HOME_FIT_TABLE") ?: return
        val out = StringBuilder("window | duo | rows | icon | row | top slack | bottom slack | ratio | pct top | pct bottom | reachable cells\n")
        forEachWindow { tag, w, h ->
            for (duo in listOf(false, true)) {
                val g = geo(w, h, duo, automatic = true)
                val p = place(g, h, false, g.appRows)
                val ratio = if (p.bottomSlack > 0.5f && p.topSlack > 0.5f) minOf(p.topSlack, p.bottomSlack) / maxOf(p.topSlack, p.bottomSlack) else 0f
                out.appendLine("$tag | $duo | ${g.appRows} | ${"%.1f".format(g.iconSize)} | ${"%.1f".format(g.rowHeight)} | ${"%.0f".format(p.topSlack)} | ${"%.0f".format(p.bottomSlack)} | ${"%.2f".format(ratio)} | " +
                    "${"%.0f".format(100f * p.topSlack / h)} | ${"%.0f".format(100f * p.bottomSlack / h)} | ${4 * p.reachableRows}${if (g.splitColumns) " split" else ""}")
            }
        }
        File(file).writeText(out.toString())
    }
}
