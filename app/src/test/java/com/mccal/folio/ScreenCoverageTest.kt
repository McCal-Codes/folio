package com.mccal.folio

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Screen Coverage v2: Folio supports Android windows, not a list of devices. Named devices live in [ScreenMatrixTest];
 * this sweeps the whole phone-to-tablet range and the breakpoint edges, checking the rules every Home layout keeps.
 */
class ScreenCoverageTest {
    private val probe = System.getenv("FOLIO_COVERAGE_PROBE") != null

    /** The rules for one window, with four app rows and with as many as fit (More rows). Returns what broke. */
    private fun problems(w: Float, h: Float, preset: LayoutPreset = LayoutPreset(), labels: Boolean = true,
        labelHeight: Float = 20f, status: Float = 160f, dockSlots: Int = MIN_DOCK_SLOTS, statusRail: Boolean = true): List<String> {
        val base = homeGeometry(w, h, preset, labels, statusHeight = status, labelHeight = labelHeight, dockSlots = dockSlots, statusRail = statusRail)
        val fit = homeGeometry(w, h, preset, labels, statusHeight = status, labelHeight = labelHeight, appRows = base.fitAppRows,
            fillSpace = true, dockSlots = dockSlots, statusRail = statusRail)
        if (base.fitAppRows !in BASE_APP_ROWS..MAX_APP_ROWS) return listOf("fit rows ${base.fitAppRows}")
        // Fewer rows re-center the page but never change the columns or icon size.
        if (fit.gridWidth != base.gridWidth || fit.iconSize != base.iconSize)
            return listOf("rows change the columns or icons")
        return (rules(w, h, preset, base, labels, labelHeight) + rules(w, h, preset, fit, labels, labelHeight)).distinct()
    }

    private fun rules(w: Float, h: Float, preset: LayoutPreset, g: HomeGeometry, labels: Boolean, labelHeight: Float): List<String> {
        val out = mutableListOf<String>()
        val numbers = listOf(g.homeWidth, g.gridWidth, g.iconSize, g.rowHeight, g.widgetHeight, g.contentTop, g.dockTop,
            g.dockHeight, g.dockRowHeight, g.cellWidth, g.statusTop)
        if (numbers.any { it.isNaN() || it.isInfinite() || it < 0f }) out += "invalid number"
        if (g.iconSize < 32f) out += "icon ${g.iconSize}"
        if (g.rowHeight < 48f) out += "row ${g.rowHeight}"
        if (g.dockRowHeight < 48f) out += "dock row ${g.dockRowHeight}"
        if (g.homeWidth > w + .5f) out += "home wider than window"
        // Breathing room between apps: at least a fifth of each column (and 8 dp).
        val space = g.cellWidth - g.iconSize
        if (space < maxOf(8f, g.cellWidth * .19f)) out += "apps only ${space} dp apart"
        val side = if (g.horizontalDock && !g.dockBesideRail) 0f else preset.sanitized().dockWidth + 28f
        if (g.gridWidth + side > (if (g.expanded) g.homeWidth + 16f else w) + .5f) out += "grid ${g.gridWidth} too wide"
        val bottom = 44f + if (g.horizontalDock) g.dockBarHeight + 16f else 0f
        val page = if (g.splitColumns) maxOf(g.widgetHeight + 18f + 2f * g.rowHeight, 3f * g.rowHeight)
            else g.widgetHeight + 18f + g.appRows * g.rowHeight
        // Extra rows only tighten the space under labels down to 4 dp, never into the labels.
        val labelSpace = if (labels) maxOf(20f, labelHeight) else 20f
        if (g.rowGap > 0f && g.rowGap < preset.sanitized().rowGap && g.rowHeight - g.iconSize - labelSpace < 4f - .01f) out += "labels crowded"
        // Four dock apps always fit. With more, the bar is held to the room it has and scrolls (dockBarRoom).
        if (g.horizontalDock && 4f * g.dockPitch + 16f > (if (g.dockBesideRail) w - preset.sanitized().dockWidth - 12f else w) + .5f) out += "dock bar too wide"
        if (g.horizontalDock && g.dockBarRoom > (if (g.dockBesideRail) w - preset.sanitized().dockWidth - 12f else w) + .5f) out += "dock bar room over the window"
        // Tier A (480 dp or taller, text up to 1.3×): the whole page fits. Shorter windows and larger text scroll the
        // page instead (HomeWorkspace), like Android asks for, so only the touch-target rules apply there.
        val mustFit = h >= 480f && labelHeight <= 26f
        if (mustFit && g.contentTop + page > h - bottom + .5f) out += "page ${g.contentTop + page} under controls at ${h - bottom}"
        if (!g.horizontalDock && g.dockTop + g.dockHeight > h + .5f) out += "dock off screen"
        if (g.dockTop < 8f) out += "dock above the top"
        // Two Home panels need a landscape or square window at least 650 dp wide.
        if (g.expanded && !(w >= 650f && w >= h)) out += "expanded in a narrow window"
        return out
    }

    private fun sweep(label: String, cases: Sequence<Triple<Float, Float, () -> List<String>>>) {
        val failures = cases.mapNotNull { (w, h, check) -> check().takeIf { it.isNotEmpty() }?.let { "${w.toInt()}×${h.toInt()}: ${it.joinToString()}" } }.toList()
        if (probe) {
            println("[$label] ${failures.size} failures")
            val parsed = failures.flatMap { f ->
                val (size, rest) = f.split(": ", limit = 2)
                val (fw, fh) = size.split("×").map { it.toInt() }
                rest.split(", ").map { Triple(it.takeWhile { c -> !c.isDigit() }.trim(), fw, fh) }
            }
            parsed.groupBy { it.first }.forEach { (k, v) ->
                println("  ${v.size}× '$k' w ${v.minOf { it.second }}..${v.maxOf { it.second }} h ${v.minOf { it.third }}..${v.maxOf { it.third }}")
                v.groupBy { it.third / 40 * 40 }.toSortedMap().forEach { (hb, vv) -> println("      h≈$hb: w ${vv.minOf { it.second }}..${vv.maxOf { it.second }} (${vv.size})") }
            }
        }
        assertTrue("$label: ${failures.size} windows broke, e.g.\n${failures.take(12).joinToString("\n")}", failures.isEmpty())
    }

    /** Tier A and B: every window at least 320 dp wide and tall, from small phones to large tablets. */
    @Test fun `sweep of phone to tablet windows keeps every layout rule`() = sweep("sweep", sequence {
        for (w in 320..1600 step 8) for (h in 320..1200 step 8) yield(Triple(w.toFloat(), h.toFloat()) { problems(w.toFloat(), h.toFloat()) })
    })

    @Test fun `options and large text keep the rules across the sweep`() = sweep("options", sequence {
        val presets = listOf(LayoutPreset(dockPlacement = DockPlacement.BOTTOM), LayoutPreset(dockPlacement = DockPlacement.SIDE),
            LayoutPreset(statusAlignToGrid = false, statusPosition = 1f), LayoutPreset(pageTop = true), LayoutPreset(dockAlignToGrid = false, dockPosition = 1f),
            LayoutPreset(iconSize = 40f, rowGap = 28f), LayoutPreset(iconSize = 68f, rowGap = 28f, dockWidth = 84f),
            // Space between columns, Widget size and Space between dock apps at both ends.
            LayoutPreset(columnGap = 8f), LayoutPreset(columnGap = 40f), LayoutPreset(widgetScale = .8f), LayoutPreset(widgetScale = 1.25f),
            LayoutPreset(dockSpacing = 24f), LayoutPreset(dockSpacing = 24f, dockAlignToGrid = false, dockPosition = 1f),
            LayoutPreset(dockSpacing = 24f, dockPlacement = DockPlacement.BOTTOM, iconSize = 68f, columnGap = 8f, widgetScale = 1.25f))
        for (w in 320..1600 step 24) for (h in 320..1200 step 24) {
            for (p in presets) yield(Triple(w.toFloat(), h.toFloat()) { problems(w.toFloat(), h.toFloat(), p) })
            // Hidden labels, and label text at 1.3×, 1.5× and 2× font scale.
            for ((labels, lh) in listOf(false to 20f, true to 26f, true to 30f, true to 40f))
                yield(Triple(w.toFloat(), h.toFloat()) { problems(w.toFloat(), h.toFloat(), labels = labels, labelHeight = lh) })
            yield(Triple(w.toFloat(), h.toFloat()) { problems(w.toFloat(), h.toFloat(), status = 0f) })
            // Six dock apps, and Full-width Home (no status Side Bar), alone and together, under each dock placement.
            for (p in presets.take(2)) for (slots in listOf(4, 6)) for (rail in listOf(true, false))
                yield(Triple(w.toFloat(), h.toFloat()) { problems(w.toFloat(), h.toFloat(), p, dockSlots = slots, statusRail = rail) })
        }
    })

    /** Both sides of each breakpoint, where a one-dp change flips the layout family. */
    @Test fun `breakpoint edges keep the rules`() = sweep("edges", sequence {
        val widths = listOf(319f, 320f, 359f, 360f, 599f, 600f, 601f, 649f, 650f, 651f, 839f, 840f, 841f, 1199f, 1200f, 1201f)
        val heights = listOf(479f, 480f, 481f, 559f, 560f, 561f, 899f, 900f, 901f)
        for (w in widths) for (h in heights) {
            yield(Triple(w, h) { problems(w, h) }); yield(Triple(h, w) { problems(h, w) })
        }
    })

    /** Flip phones, book-fold covers and inner screens, and tri-fold-sized windows, in both orientations. */
    @Test fun `flip, fold and tri-fold windows keep the rules`() = sweep("folds", sequence {
        val sizes = listOf(
            // Flip inner screens (tall and narrow).
            360f to 879f, 384f to 900f, 412f to 915f, 430f to 1000f,
            // Flip cover screens (square-ish, Tier C below 320).
            320f to 320f, 360f to 360f, 412f to 412f, 480f to 360f,
            // Book-fold covers.
            320f to 780f, 344f to 840f, 360f to 850f, 393f to 900f, 430f to 900f, 475f to 751f,
            // Book-fold inner screens across the 600 and 840 breakpoints.
            600f to 700f, 650f to 700f, 700f to 700f, 700f to 840f, 704f to 930f, 800f to 700f, 838f to 945f,
            840f to 700f, 841f to 701f, 852f to 883f, 932f to 704f, 1000f to 800f,
            // Tri-folds and other very large unfolded screens.
            1200f to 900f, 1350f to 900f, 1500f to 1000f,
            // Galaxy Z TriFold main and Z Fold8 Ultra inner, and their covers (estimated at 420 dpi; see ScreenMatrixTest).
            823f to 603f, 859f to 954f, 411f to 960f)
        for ((w, h) in sizes) for (labels in listOf(true, false)) {
            yield(Triple(w, h) { problems(w, h, labels = labels) }); yield(Triple(h, w) { problems(h, w, labels = labels) })
        }
    })

    /** Tier C: micro flip covers still produce a valid layout (touch targets kept, the page scrolls). */
    @Test fun `micro cover windows stay valid`() = sweep("micro", sequence {
        for ((w, h) in listOf(240f to 260f, 260f to 240f, 280f to 280f, 300f to 300f))
            yield(Triple(w, h) { problems(w, h).filter { it.startsWith("invalid") || it.startsWith("dock row") || it.startsWith("row") } })
    })

    @Test fun `micro cover mode covers only tiny windows and its apps always fit`() {
        for ((w, h) in listOf(240f to 260f, 260f to 260f, 280f to 280f, 300f to 300f, 320f to 320f, 300f to 420f))
            org.junit.Assert.assertTrue("${w}×$h", isMicroWindow(w, h))
        for ((w, h) in listOf(321f to 321f, 360f to 640f, 320f to 640f, 640f to 320f, 475f to 751f, 280f to 480f))
            org.junit.Assert.assertFalse("${w}×$h", isMicroWindow(w, h))
        for (w in 200..480) {
            val n = microAppCount(w.toFloat())
            org.junit.Assert.assertTrue("$w: $n apps", 2 * 18f + n * 48f + (n - 1).coerceAtLeast(0) * 10f <= w)
        }
        org.junit.Assert.assertEquals(4, microAppCount(260f))
        org.junit.Assert.assertEquals(5, microAppCount(320f))
    }

    /** Galaxy Z Fold8 (labels on, measured status, default settings): both screens have room for a fifth app row. */
    @Test fun `fold8 cover and inner screens fit five app rows`() {
        val cover = homeGeometry(475f, 751f, LayoutPreset(), true, statusHeight = 160f)
        val inner = homeGeometry(932f, 704f, LayoutPreset(), true, statusHeight = 160f)
        org.junit.Assert.assertEquals(5, cover.fitAppRows)
        org.junit.Assert.assertEquals(5, inner.fitAppRows)
        // The cover fits five at the usual spacing; the inner screen needs the space under labels tightened to 4 dp.
        org.junit.Assert.assertEquals(8f, homeGeometry(475f, 751f, LayoutPreset(), true, statusHeight = 160f, appRows = 5).rowGap)
        org.junit.Assert.assertEquals(4f, homeGeometry(932f, 704f, LayoutPreset(), true, statusHeight = 160f, appRows = 5).rowGap)
        // Four rows keep today's spacing.
        org.junit.Assert.assertEquals(8f, inner.rowGap)
        // Split screen halves and short landscape windows stay at four.
        org.junit.Assert.assertEquals(4, homeGeometry(751f, 475f, LayoutPreset(), true, statusHeight = 160f).fitAppRows)
    }

    /** Default Space between columns, Widget size and dock spacing lay the Fold8 out as before those settings. */
    @Test fun `new spacing settings at their defaults keep the fold8 layout`() {
        val cover = homeGeometry(475f, 751f, LayoutPreset(), true, statusHeight = 160f)
        org.junit.Assert.assertEquals(listOf(363f, 90.75f, 66f, 176f, 0f), listOf(cover.gridWidth, cover.cellWidth, cover.iconSize, cover.widgetHeight, cover.columnsInset))
        val inner = homeGeometry(932f, 704f, LayoutPreset(), true, statusHeight = 160f)
        org.junit.Assert.assertEquals(listOf(348f, 87f, 66f, 169f, 0f), listOf(inner.gridWidth, inner.cellWidth, inner.iconSize, inner.widgetHeight, inner.columnsInset))
        org.junit.Assert.assertEquals(dockIconSize(66f) + 22f, homeGeometry(704f, 932f, LayoutPreset(), true, statusHeight = 160f).dockPitch)
        // Closer columns narrow the cells (the grid area stays put); wider ones shrink the icons.
        val closer = homeGeometry(475f, 751f, LayoutPreset(columnGap = 8f), true, statusHeight = 160f)
        org.junit.Assert.assertEquals(listOf(363f, 82.75f, 66f), listOf(closer.gridWidth, closer.cellWidth, closer.iconSize))
        val apart = homeGeometry(475f, 751f, LayoutPreset(columnGap = 40f), true, statusHeight = 160f)
        org.junit.Assert.assertEquals(listOf(90.75f, 42f), listOf(apart.cellWidth, apart.iconSize))
        // Widget size scales the widget rows; dock spacing makes a free-standing dock taller.
        org.junit.Assert.assertEquals(140.8f, homeGeometry(475f, 751f, LayoutPreset(widgetScale = .8f), true, statusHeight = 160f).widgetHeight, .01f)
        val loose = LayoutPreset(dockAlignToGrid = false)
        org.junit.Assert.assertTrue(homeGeometry(475f, 751f, loose.copy(dockSpacing = 12f), true, statusHeight = 160f).dockHeight >
            homeGeometry(475f, 751f, loose, true, statusHeight = 160f).dockHeight)
    }

    /** Switching Rows between Automatic and 4 (or the other screen fitting fewer) never moves the dock or the apps. */
    @Test fun `more rows keep the columns, icons and dock size and only re-center the page`() {
        for ((w, h) in listOf(475f to 751f, 932f to 704f, 704f to 932f, 412f to 915f, 1200f to 900f, 800f to 1280f)) {
            val g = homeGeometry(w, h, LayoutPreset(), true, statusHeight = 160f)
            for (rows in BASE_APP_ROWS..g.fitAppRows) {
                val shown = homeGeometry(w, h, LayoutPreset(), true, statusHeight = 160f, appRows = rows)
                val tag = "${w}×$h $rows rows"
                org.junit.Assert.assertEquals(tag, g.gridWidth, shown.gridWidth)
                org.junit.Assert.assertEquals(tag, g.iconSize, shown.iconSize)
                org.junit.Assert.assertEquals(tag, g.dockWidthOrBar(), shown.dockWidthOrBar())
                // Every row that was shown keeps its spacing from the widgets (apps never change cells).
                val a = HomeCellLayout.forPage(g, listOf(0 to 2)); val b = HomeCellLayout.forPage(shown, listOf(0 to 2))
                org.junit.Assert.assertEquals(tag, a.y(3) - a.y(2), b.y(3) - b.y(2), 4.01f)
            }
        }
    }
    private fun HomeGeometry.dockWidthOrBar() = if (horizontalDock) dockBarHeight else dockRowHeight.coerceAtLeast(48f).let { 0f }

    /** A book-style hinge anywhere across a tall page: rows under it move below it, and a widget is never split. */
    @Test fun `a hinge anywhere across the page moves whole rows below it`() {
        for ((w, h) in listOf(704f to 930f, 600f to 900f, 852f to 1100f, 700f to 1200f)) {
            val g = homeGeometry(w, h, LayoutPreset(), true)
            val widgets = listOf(0 to 2)
            val cells = HomeCellLayout.forPage(g, widgets)
            val top = g.contentTop
            var hinge = top
            while (hinge < top + cells.height(GRID_ROWS)) {
                val result = foldDisplacement(cells, GRID_ROWS, top, hinge, hinge + 24f, widgets)
                if (result != null) {
                    val (row, shift) = result
                    assertTrue("${w}×$h hinge $hinge: row $row lands below", top + cells.y(row) + shift >= hinge + 24f - .5f)
                    assertTrue("${w}×$h hinge $hinge: widget split", row == 0 || row >= 2)
                }
                hinge += 6f
            }
        }
    }
}
