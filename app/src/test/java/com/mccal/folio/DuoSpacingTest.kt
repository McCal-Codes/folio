package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DUO_SPACING: the row pitch and dock icon changes are judged by window-size classes and free height in dp, never by a
 * device, so every window in screen-matrix.json (phones, foldables, tablets, both ways up, split halves) is checked.
 */
class DuoSpacingTest {
    private data class Screen(val name: String, val width: Float, val height: Float)

    private val devices = org.json.JSONObject(javaClass.getResource("/screen-matrix.json")!!.readText()).getJSONArray("devices").let { list ->
        (0 until list.length()).map { i -> list.getJSONObject(i).let { Screen(it.getString("name"), it.getDouble("width").toFloat(), it.getDouble("height").toFloat()) } }
    }
    private val screens = devices.flatMap { d ->
        listOf(d, Screen("${d.name} rotated", d.height, d.width)) +
            (if (maxOf(d.width, d.height) >= 800f) listOf(Screen("${d.name} split half", maxOf(d.width, d.height) / 2f, minOf(d.width, d.height))) else emptyList())
    }

    private fun geo(w: Float, h: Float, duo: Boolean, labels: Boolean = true, preset: LayoutPreset = LayoutPreset(), rows: Int = BASE_APP_ROWS,
        fill: Boolean = false, auto: Boolean = false) =
        homeGeometry(w, h, preset, labels, statusHeight = if (fitsRegularHomeLayout(w, h)) 180f else 160f, appRows = rows,
            widgetsFillRows = fill, fillSpace = auto, duoSpacing = duo)

    private fun forEachWindow(block: (String, Float, Float) -> Unit) {
        for (s in screens) {
            val scale = uiScale(s.width, s.height)
            block("${s.name} (${(s.width / scale).toInt()}x${(s.height / scale).toInt()})", s.width / scale, s.height / scale)
        }
    }

    @Test fun `the gate closed is exactly today's geometry`() {
        forEachWindow { tag, w, h ->
            for (fill in listOf(false, true)) {
                val off = geo(w, h, false, fill = fill)
                assertEquals(tag, dockIconSize(off.iconSize), off.dockIcon, 0.001f)
                assertEquals(tag, off.copy(), geo(w, h, false, fill = fill))
            }
        }
    }

    @Test fun `rows gain only on phone-sized windows, only with free height, never past the cap`() {
        forEachWindow { tag, w, h ->
            for (fill in listOf(false, true)) for (auto in listOf(false, true)) {
                val off = geo(w, h, false, fill = fill, auto = auto)
                val on = geo(w, h, true, fill = fill, auto = auto)
                assertTrue("$tag never tighter", on.rowHeight >= off.rowHeight - 0.001f)
                assertTrue("$tag capped ${on.rowHeight} vs ${off.rowHeight}", on.rowHeight <= off.rowHeight * (1f + DUO_ROW_GAIN_CAP) + 0.01f)
                assertEquals("$tag icon size is never changed", off.iconSize, on.iconSize, 0.001f)
                val eligible = !fitsRegularHomeLayout(w, h) && !off.splitColumns
                if (!eligible) {
                    // The unfolded screens, tablets and two-column pages keep today's rows exactly.
                    assertEquals("$tag rows", off.rowHeight, on.rowHeight, 0f)
                    assertEquals("$tag widget", off.widgetHeight, on.widgetHeight, 0f)
                    assertEquals("$tag top", off.contentTop, on.contentTop, 0f)
                    assertEquals("$tag dock", off.dockTop, on.dockTop, 0f)
                    assertEquals("$tag dock height", off.dockHeight, on.dockHeight, 0f)
                }
                // Free height only: the page still ends above the bottom space, as it did before.
                val pageEnd = on.contentTop + (if (fill) on.widgetHeight else on.widgetHeight) + 18f + 4f * on.rowHeight
                val offEnd = off.contentTop + off.widgetHeight + 18f + 4f * off.rowHeight
                assertTrue("$tag page end $pageEnd (was $offEnd) inside $h", pageEnd <= maxOf(offEnd, h - 44f) + 0.5f)
                assertTrue("$tag dock inside the window", on.dockTop + on.dockHeight <= maxOf(h, off.dockTop + off.dockHeight) + 0.01f)
                assertTrue("$tag rows >= 48", on.rowHeight >= 48f && on.dockRowHeight >= 48f)
            }
        }
    }

    @Test fun `Rows 5 to 7 and a pinned page top keep today's spacing`() {
        forEachWindow { tag, w, h ->
            for (rows in 5..7) for (shrink in listOf(false, true)) {
                val off = homeGeometry(w, h, LayoutPreset(), true, statusHeight = 160f, appRows = rows, shrinkIconsForRows = shrink)
                val on = homeGeometry(w, h, LayoutPreset(), true, statusHeight = 160f, appRows = rows, shrinkIconsForRows = shrink, duoSpacing = true)
                assertEquals("$tag rows=$rows", off.rowHeight, on.rowHeight, 0f)
                assertEquals("$tag rows=$rows gap", off.rowGap, on.rowGap, 0f)
            }
            val top = LayoutPreset(pageTop = true)
            assertEquals(tag, geo(w, h, false, preset = top).rowHeight, geo(w, h, true, preset = top).rowHeight, 0f)
        }
    }

    @Test fun `a squeezed short window keeps its tightened rows`() {
        for (h in listOf(420f, 480f, 520f)) {
            val off = geo(411f, h, false); val on = geo(411f, h, true)
            assertEquals("h=$h", off.rowHeight, on.rowHeight, 0f)
        }
    }

    @Test fun `dock icons grow toward 80 percent where they fit and never shrink`() {
        forEachWindow { tag, w, h ->
            for (dockWidth in listOf(56f, 68f, 84f)) for (icon in listOf(40f, 56f, 66f, 68f)) for (slots in listOf(4, 6)) for (labels in listOf(true, false)) {
                val p = LayoutPreset(iconSize = icon, dockWidth = dockWidth)
                for (placement in DockPlacement.entries) {
                    val pr = p.copy(dockPlacement = placement)
                    val off = homeGeometry(w, h, pr, labels, statusHeight = 160f, dockSlots = slots)
                    val on = homeGeometry(w, h, pr, labels, statusHeight = 160f, dockSlots = slots, duoSpacing = true)
                    val t = "$tag icon=$icon dockW=$dockWidth slots=$slots $placement"
                    assertTrue("$t never smaller", on.dockIcon >= dockIconSize(on.iconSize) - 0.001f)
                    assertTrue("$t at most 80%", on.dockIcon <= on.iconSize * DUO_DOCK_RATIO + 0.001f)
                    if (!on.horizontalDock) {
                        assertTrue("$t fits its row", on.dockIcon <= maxOf(dockIconSize(on.iconSize), on.dockRowHeight - 4f) + 0.001f)
                        assertTrue("$t fits its pill", on.dockIcon <= maxOf(dockIconSize(on.iconSize), dockWidth - 12f) + 0.001f)
                    } else {
                        assertTrue("$t fits its bar", on.dockIcon + 22f <= maxOf(dockIconSize(on.iconSize) + 22f, on.dockBarHeight - 0f) + 0.001f)
                        // A bar that held its apps still holds them, and a bar that scrolled is no wider than before per app by more than the growth.
                        if (slots * (dockIconSize(on.iconSize) + 22f) + 16f <= on.dockBarRoom - 32f)
                            assertTrue("$t still fits", slots * (on.dockIcon + 22f) + 16f <= on.dockBarRoom - 32f + 0.01f)
                    }
                    assertTrue("$t touch targets", on.dockRowHeight >= 48f)
                }
            }
        }
    }

    @Test fun `with the default pill the cover and inner dock icons land near the Duo's 80 percent`() {
        for ((w, h) in listOf(475.43f to 751.2f, 932f to 704f, 411f to 923f, 360f to 640f)) {
            val g = geo(w, h, true)
            assertEquals("$w x $h", g.iconSize * DUO_DOCK_RATIO, g.dockIcon, 0.01f)
        }
        // 1 dp is 0.1558 mm on the Fold8 cover and 0.1651 mm on its inner screen.
        val cover = geo(475.43f, 751.2f, true); val inner = geo(932f, 704f, true)
        println("COVER icon=${cover.iconSize} dock=${cover.dockIcon} (${cover.dockIcon * .1558f} mm) row=${cover.rowHeight} (${cover.rowHeight * .1558f} mm)")
        println("INNER icon=${inner.iconSize} dock=${inner.dockIcon} (${inner.dockIcon * .1651f} mm) row=${inner.rowHeight} (${inner.rowHeight * .1651f} mm)")
        // The default icon (66 dp) puts the dock at 80% of it. The Fold8 photos measured Home icons of about 60 dp on both screens
        // (the setting, or art drawn a little inside its cell), where the same ratio gives these millimeters.
        val cover60 = geo(475.43f, 751.2f, true, preset = LayoutPreset(iconSize = 60f)); val inner60 = geo(932f, 704f, true, preset = LayoutPreset(iconSize = 60f))
        println("ICON60 cover dock=${cover60.dockIcon} (${cover60.dockIcon * .1558f} mm) row=${cover60.rowHeight} (${cover60.rowHeight * .1558f} mm) off=${geo(475.43f, 751.2f, false, preset = LayoutPreset(iconSize = 60f)).rowHeight}; inner dock=${inner60.dockIcon} (${inner60.dockIcon * .1651f} mm) row=${inner60.rowHeight}")
        assertTrue(cover.dockIcon * .1558f in 8.0f..8.4f)
        assertTrue(inner60.dockIcon * .1651f in 7.4f..8.2f)
        assertEquals(geo(932f, 704f, false).rowHeight, inner.rowHeight, 0f)
    }

    @Test fun `the cover gains rows toward the Duo's pitch and an ordinary phone gets a sensible result`() {
        val cover0 = geo(475.43f, 751.2f, false); val cover = geo(475.43f, 751.2f, true)
        assertTrue("${cover0.rowHeight} -> ${cover.rowHeight}", cover.rowHeight > cover0.rowHeight * 1.1f)
        assertTrue(cover.rowHeight <= cover0.rowHeight * 1.2f + .01f)
        for ((w, h) in listOf(360f to 640f, 393f to 851f, 411f to 923f, 448f to 997f)) {
            val off = geo(w, h, false); val on = geo(w, h, true)
            println("PHONE ${w.toInt()}x${h.toInt()} row ${off.rowHeight} -> ${on.rowHeight}, dock ${off.dockIcon} -> ${on.dockIcon}")
            assertTrue(on.rowHeight in off.rowHeight..off.rowHeight * 1.2f + .01f)
            assertFalse(on.splitColumns)
        }
    }

    private val postures = listOf(false, true)

    @Test fun `no size loses a row or a cell in either rotation, with the gate open or closed`() {
        forEachWindow { tag, w, h ->
            for (fold in postures) for (rows in BASE_APP_ROWS..MAX_APP_ROWS) for (shrink in listOf(false, true)) {
                fun g(duo: Boolean) = homeGeometry(w, h, LayoutPreset(), true, statusHeight = 160f, appRows = rows, foldAtCenter = fold,
                    shrinkIconsForRows = shrink, duoSpacing = duo)
                val off = g(false); val on = g(true)
                val t = "$tag fold=$fold rows=$rows shrink=$shrink"
                assertTrue("$t fit rows ${off.fitAppRows} -> ${on.fitAppRows}", on.fitAppRows >= off.fitAppRows)
                assertEquals("$t rows shown", off.appRows, on.appRows)
                assertEquals("$t columns", off.splitColumns, on.splitColumns)
                assertEquals("$t cell width", off.cellWidth, on.cellWidth, 0f)
                assertEquals("$t grid width", off.gridWidth, on.gridWidth, 0f)
                assertEquals("$t horizontal dock", off.horizontalDock, on.horizontalDock)
                assertTrue("$t icons never smaller", on.iconSize >= off.iconSize)
            }
        }
    }

    @Test fun `nothing overlaps or leaves the window, and every dock slot keeps a touch target`() {
        forEachWindow { tag, w, h ->
            for (fold in postures) for (slots in listOf(4, 5, 6)) for (labels in listOf(true, false)) for (placement in DockPlacement.entries) {
                val p = LayoutPreset(dockPlacement = placement)
                val g = homeGeometry(w, h, p, labels, statusHeight = 160f, dockSlots = slots, foldAtCenter = fold, duoSpacing = true)
                val off = homeGeometry(w, h, p, labels, statusHeight = 160f, dockSlots = slots, foldAtCenter = fold)
                val t = "$tag fold=$fold slots=$slots labels=$labels $placement"
                fun end(x: HomeGeometry) = x.contentTop + (if (x.splitColumns) maxOf(x.widgetHeight + 18f + 2f * x.rowHeight, 3f * x.rowHeight)
                    else x.widgetHeight + 18f + x.appRows * x.rowHeight)
                assertTrue("$t page end ${end(g)} (was ${end(off)}) of $h", end(g) <= maxOf(h - 44f, end(off)) + 0.5f)
                assertTrue("$t dock inside", g.dockTop >= 8f)
                if (h >= 400f) assertTrue("$t dock bottom ${g.dockTop + g.dockHeight} of $h", g.dockTop + g.dockHeight <= h)
                assertTrue("$t row targets", g.dockRowHeight >= 48f)
                if (!g.horizontalDock) assertTrue("$t icon in its row", g.dockIcon <= maxOf(g.dockRowHeight, dockIconSize(g.iconSize)) + .01f)
                assertTrue("$t grid inside", g.gridWidth <= w && g.homeWidth <= w)
            }
        }
    }

    @Test fun `the extra pitch fades to nothing as height gets tight, with no jump`() {
        for (w in listOf(360f, 411f, 475.43f)) for (labels in listOf(true, false)) for (fill in listOf(false, true)) {
            var last: Float? = null
            var h = 400f
            while (h <= 1100f) {
                val off = homeGeometry(w, h, LayoutPreset(), labels, statusHeight = 160f, widgetsFillRows = fill)
                val on = homeGeometry(w, h, LayoutPreset(), labels, statusHeight = 160f, widgetsFillRows = fill, duoSpacing = true)
                val gain = on.rowHeight - off.rowHeight
                assertTrue("w=$w h=$h gain $gain", gain >= -0.001f)
                if (last != null) assertTrue("w=$w h=$h gain jumps from $last to $gain", kotlin.math.abs(gain - last) <= 1.5f)
                last = gain
                h += 1f
            }
        }
        // Short phone in landscape: the cover and an ordinary phone turned sideways keep today's spacing.
        for ((w, h) in listOf(751f to 475f, 800f to 360f, 640f to 360f)) {
            assertEquals("$w x $h", geo(w, h, false).rowHeight, geo(w, h, true).rowHeight, 0f)
        }
    }

    @Test fun `table of every matrix size and orientation`() {
        val out = StringBuilder("| window | fit rows (Rows 7) before | after | row pitch before | after | dock icon before | after |\n|---|---|---|---|---|---|---|\n")
        forEachWindow { tag, w, h ->
            val off = homeGeometry(w, h, LayoutPreset(), true, statusHeight = 160f, appRows = 7)
            val on = homeGeometry(w, h, LayoutPreset(), true, statusHeight = 160f, appRows = 7, duoSpacing = true)
            val o4 = geo(w, h, false); val n4 = geo(w, h, true)
            out.append("| $tag | ${off.fitAppRows} | ${on.fitAppRows} | ${"%.1f".format(o4.rowHeight)} | ${"%.1f".format(n4.rowHeight)} | ${"%.1f".format(o4.dockIcon)} | ${"%.1f".format(n4.dockIcon)} |\n")
        }
        java.io.File("build/duo-spacing-table.md").also { it.parentFile?.mkdirs() }.writeText(out.toString())
    }
}
