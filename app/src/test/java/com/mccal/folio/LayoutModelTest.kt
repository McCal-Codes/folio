package com.mccal.folio

import org.junit.Assert.*
import org.junit.Test

class LayoutModelTest {
    @Test fun `aligned dock spans first through third icon images on the physical Fold presets`() {
        val p = LayoutPreset(rowGap = 16.263264f, dockWidth = 73.46808f, dockPosition = .503011f)
        for (labels in listOf(true, false)) {
            val g = homeGeometry(475.43f, 696.38f, p, labels, statusHeight = 160f, labelHeight = 21.12f)
            val firstImageTop = g.contentTop + g.widgetHeight + 18f
            val thirdImageBottom = firstImageTop + 2f * g.rowHeight + g.iconSize
            assertEquals(firstImageTop, g.dockTop, .01f)
            assertEquals(thirdImageBottom, g.dockTop + g.dockHeight, .01f)
            assertEquals(g.dockHeight, 4f * g.dockRowHeight + 16f, .01f)
            val feed = homeGeometry(475.43f, 696.38f, p, labels, statusHeight = 160f, labelHeight = 21.12f, inLibrary = true)
            assertEquals(g.dockTop, feed.dockTop, .01f)
            assertEquals(g.dockHeight, feed.dockHeight, .01f)
        }
    }
    @Test fun `manual dock mode keeps saved position and alignment does not overwrite it`() {
        val p = LayoutPreset(dockPosition = .43f, dockAlignToGrid = false)
        val manual = homeGeometry(475f, 700f, p, true)
        assertEquals(700f * .43f - 128f, manual.dockTop, .01f)
        assertEquals(256f, manual.dockHeight, .01f)
        assertEquals(.43f, p.copy(dockAlignToGrid = true).sanitized().dockPosition)
    }
    @Test fun `search keeps four complete dock targets between status and keyboard`() {
        for (width in listOf(475f, 933f)) for (height in listOf(310f, 330f, 375f, 420f)) {
            for (position in listOf(.25f, .56f, .75f)) {
                val g = homeGeometry(width, height, LayoutPreset(dockPosition = position), true,
                    statusHeight = 80f, inLibrary = true)
                assertTrue(g.dockTop >= 80f)
                assertTrue(g.dockTop + g.dockHeight <= height - 12f + .01f)
                assertTrue(g.dockRowHeight >= 48f)
                assertTrue(4f * g.dockRowHeight + 16f <= g.dockHeight + .01f)
            }
        }
    }
    @Test fun `hiding labels preserves row rhythm and large text gains room`() {
        val regular = homeGeometry(475f, 700f, LayoutPreset(), true)
        val hidden = homeGeometry(475f, 700f, LayoutPreset(), false)
        val largeText = homeGeometry(475f, 700f, LayoutPreset(), true, labelHeight = 34f)
        assertEquals(regular.rowHeight, hidden.rowHeight)
        assertTrue(largeText.rowHeight >= regular.rowHeight + 14f)
        assertTrue(regular.iconSize / (regular.gridWidth / 4f) in .70f.. .77f)
    }
    @Test fun `new icon default preserves tuned presets and current schema values`() {
        assertEquals(66f, upgradePreset(LayoutPreset(iconSize = 60f), 2, false).iconSize)
        val custom = LayoutPreset(62f, 13f, 72f, .67f)
        assertEquals(custom, upgradePreset(custom, 2, true))
        assertEquals(LayoutPreset(iconSize = 60f), upgradePreset(LayoutPreset(iconSize = 60f), 3, false))
        assertEquals(LayoutPreset(), upgradePreset(LayoutPreset(54f, 12f, 64f), 1, false))
        assertEquals(LayoutPreset(), upgradePreset(LayoutPreset(58f, 12f, 64f), 1, true))
    }
    @Test fun `dock fits above controls at Fold cover inner and short landscape sizes`() {
        val sizes = listOf(475f to 700f, 933f to 650f, 850f to 840f, 360f to 620f, 740f to 280f)
        for ((width, height) in sizes) for (position in listOf(.25f, .56f, .75f)) {
            val p = LayoutPreset(dockPosition = position)
            val g = homeGeometry(width, height, p, true)
            assertTrue("$width x $height", g.dockTop >= 8f)
            assertTrue("Dock overlaps bottom controls at $width x $height", g.dockTop + g.dockHeight <= height - 124f + .01f)
            assertTrue(g.gridWidth + p.dockWidth + 24f <= g.homeWidth)
            assertTrue(g.iconSize + 8f <= g.gridWidth / 4f)
            if (g.dockHeight == 256f) {
                val library = homeGeometry(width, height, p, true, inLibrary = true)
                assertEquals("Paging must preserve even a low dock position", g.dockTop, library.dockTop, .01f)
            }
        }
    }
    @Test fun `short and landscape windows stay compact and fit a page without scrolling`() {
        // Cover in landscape, a small phone in landscape, and a split-screen half: compact size, whole page visible.
        for ((width, height) in listOf(751f to 475f, 751f to 431f, 640f to 360f, 460f to 700f)) {
            val g = homeGeometry(width, height, LayoutPreset(), true, labelHeight = 21f)
            assertFalse("$width x $height", g.expanded)
            val pageHeight = if (g.splitColumns) maxOf(g.widgetHeight + 18f, 4f * g.rowHeight) else g.widgetHeight + 18f + 4f * g.rowHeight
            if (height >= 430f) assertTrue("page overflows at $width x $height", pageHeight <= height - 16f - 44f + .01f)
            if (g.splitColumns) assertTrue("halves overflow the width", 8f * g.cellWidth + g.zoneGap <= width - 68f - 44f + .01f)
            assertTrue(g.rowHeight >= 48f)
        }
        assertTrue(isRegularSize(704f, 930f))
        assertTrue(isRegularSize(932f, 680f))
        assertFalse(isRegularSize(751f, 475f))
    }
    @Test fun `two-column pages keep widgets whole and rows aligned`() {
        val g = homeGeometry(751f, 459f, LayoutPreset(), true, labelHeight = 21f)
        assertTrue(g.splitColumns)
        // Widgets up top: the widget row and two app rows on the left, the other two app rows on the right, both from the top.
        val withWidgets = HomeCellLayout.forPage(g, listOf(0 to 2))
        assertEquals(4, withWidgets.splitRow)
        assertEquals(0f, withWidgets.y(4), .01f)
        assertEquals(g.widgetHeight, withWidgets.spanHeight(0, 2) - 18f, .01f)
        assertTrue(withWidgets.x(0, 4) >= withWidgets.x(3, 3) + g.cellWidth)
        assertTrue(withWidgets.height(6) <= 459f - 16f - 44f + .01f)
        // Apps only: three full rows per half.
        val appsOnly = HomeCellLayout.forPage(g, emptyList())
        assertEquals(3, appsOnly.splitRow)
        assertEquals(g.rowHeight * 3, appsOnly.height(6), .01f)
        // A widget that would be cut in half picks another split, or the page stays stacked.
        assertEquals(2, HomeCellLayout.forPage(g, listOf(2 to 3)).splitRow)
        assertEquals(3, HomeCellLayout.forPage(g, listOf(3 to 2)).splitRow)
        assertEquals(null, HomeCellLayout.forPage(g, listOf(1 to 2, 3 to 2, 2 to 2)).splitRow)
        // Stacked layouts are unchanged: rows 0-1 are half pitch, then app rows.
        val portrait = homeGeometry(475f, 700f, LayoutPreset(), true)
        val stacked = HomeCellLayout.forPage(portrait, listOf(0 to 2))
        assertEquals(null, stacked.splitRow)
        assertEquals(portrait.widgetHeight + 18f + portrait.rowHeight, stacked.y(3), .01f)
    }
    @Test fun `expanded pane appears from actual window width`() {
        assertFalse(homeGeometry(475f, 700f, LayoutPreset(), true).expanded)
        assertTrue(homeGeometry(933f, 650f, LayoutPreset(), true).expanded)
        assertTrue(homeGeometry(933f, 650f, LayoutPreset(), true).homeWidth <= 460f)
    }
    @Test fun `raising dock cannot overlap measured status or lower controls`() {
        for ((height, status) in listOf(700f to 124f, 650f to 124f, 280f to 80f)) {
            for (position in listOf(.25f, .56f, .75f)) {
                val g = homeGeometry(475f, height, LayoutPreset(dockPosition = position), true, status)
                assertTrue(g.dockTop >= status)
                assertTrue(g.dockTop + g.dockHeight <= height - 124f + .01f)
                assertTrue(g.dockHeight >= 68f)
            }
        }
    }
    @Test fun `reconciliation preserves custom order while handling installs removals duplicates`() {
        assertEquals(listOf("c", "a", "d"), reconcileOrder(listOf("c", "gone", "a", "c"), listOf("a", "c", "d")))
        assertEquals(emptyList<String>(), reconcileOrder(listOf("a"), emptyList()))
    }
    @Test fun `reordering across page boundary does not drop apps`() {
        val apps = (0..31).map { "app$it" }
        val moved = moveApp(apps, "app16", -1)
        assertEquals("app16", moved[15])
        assertEquals("app15", moved[16])
        assertEquals(apps.toSet(), moved.toSet())
        assertEquals("app31", moveApp(apps, "app31", -100).first())
        assertEquals(apps, moveApp(apps, "missing", 1))
    }
    @Test fun `out of range preferences are constrained before layout`() {
        val p = LayoutPreset(999f, -40f, 2f, 12f).sanitized()
        assertEquals(68f, p.iconSize); assertEquals(0f, p.rowGap)
        assertEquals(56f, p.dockWidth); assertEquals(.75f, p.dockPosition)
    }
    @Test fun `new installs and refresh do not pin apps and empty home stays empty`() {
        assertEquals(listOf("c", "a"), reconcilePins(listOf("c", "gone", "a", "c"), listOf("a", "c", "new")))
        assertEquals(emptyList<String>(), reconcilePins(emptyList(), listOf("new")))
    }
    @Test fun `migration uses suggestions for auto sorted legacy home but retains manual first page`() {
        val installed = (0..31).map { "app%02d".format(it) }
        assertEquals(listOf("app20", "app10"), migrateHomePins(installed, installed, listOf("app20", "app10", "gone")))
        val custom = listOf("app31") + installed.dropLast(1)
        assertEquals(custom.take(16), migrateHomePins(custom, installed, listOf("app20")))
    }
    @Test fun `home always has a page independently of the library`() {
        assertEquals(1, homePageCount(0)); assertEquals(1, homePageCount(24)); assertEquals(2, homePageCount(25))
    }
    @Test fun `app library tiles stay phone-sized on big screens`() {
        // Issue #9: an unfolded Fold8 in landscape showed two giant columns; it now shows more, phone-sized tiles.
        assertEquals(5, libraryColumns(780f))
        assertEquals(4, libraryColumns(606f))
        assertEquals(2, libraryColumns(340f))
        assertEquals(2, libraryColumns(360f))
        assertEquals(6, libraryColumns(1300f))
    }
    @Test fun `a phone-sized screen keeps the phone layout when Smallest width is raised`() {
        // Reported: a Galaxy Z Fold8 cover (1248px wide, 420dpi) set to 600dp got the unfolded layout.
        val scale = classScale(densityDpi = 1248 * 160 / 600, stableDpi = 420)
        assertFalse(isRegularSize(600f, 948f, scale))
        val cover = homeGeometry(600f, 948f, LayoutPreset(), labels = true, classScale = scale)
        assertFalse(cover.horizontalDock)
        assertFalse(cover.expanded)
        assertEquals(1f, uiScale(600f, 948f, scale))
        assertTrue(isRegularSize(932f, 704f, classScale(420, 420)))
        assertEquals(1f, classScale(0, 420))
    }
}
