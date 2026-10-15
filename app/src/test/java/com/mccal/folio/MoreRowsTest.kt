package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** More rows: 36-cell pages, migrations from 24-cell ones, the shared row count and automatic placement. */
class MoreRowsTest {
    private fun legacyIndex(page: Int, local: Int) = page * LEGACY_HOME_CELLS + local
    /** Where an app sits on screen: page, column and row. */
    private fun position(index: Int) = Triple(homeCellPage(index), homeCellLocal(index) % GRID_COLUMNS, homeCellLocal(index) / GRID_COLUMNS)

    @Test fun `pages store two widget rows and up to seven app rows, and old cells keep their local index`() {
        assertEquals(9, GRID_ROWS); assertEquals(36, HOME_CELLS); assertEquals(4, BASE_APP_ROWS); assertEquals(7, MAX_APP_ROWS)
        assertEquals(6, visibleHomeRows(4)); assertEquals(9, visibleHomeRows(7))
        assertEquals(6, visibleHomeRows(1)); assertEquals(9, visibleHomeRows(12))
    }

    @Test fun `old 24-cell home slots keep every app at the same page, column and row`() {
        val old = MutableList<String?>(3 * LEGACY_HOME_CELLS) { null }.apply {
            this[legacyIndex(0, 8)] = "a"; this[legacyIndex(0, 23)] = "b"; this[legacyIndex(1, 0)] = "c"
            this[legacyIndex(1, 13)] = "d"; this[legacyIndex(2, 23)] = "e"
        }
        val migrated = migrateLegacyHomeSlots(old)
        old.forEachIndexed { index, id ->
            if (id == null) return@forEachIndexed
            val oldPosition = Triple(index / LEGACY_HOME_CELLS, index % LEGACY_HOME_CELLS % 4, index % LEGACY_HOME_CELLS / 4)
            assertEquals(id, oldPosition, position(migrated.indexOf(id)))
        }
        assertEquals(old.filterNotNull(), migrated.filterNotNull())
        assertEquals(3, homePageCount(migrated.size))
        assertEquals(emptyList<String?>(), migrateLegacyHomeSlots(emptyList()))
        // Rows 6–8 of every page start empty.
        assertTrue((0 until 3).all { page -> (24 until HOME_CELLS).all { migrated.getOrNull(homeCellIndex(page, it)) == null } })
    }

    @Test fun `old unfolded-only page is padded and the retained overflow widget moves under the new grid`() {
        val leading = List(LEGACY_HOME_CELLS) { if (it == 23) "z" else null }
        val padded = migrateLegacyLeadingSlots(leading)
        assertEquals(HOME_CELLS, padded.size); assertEquals("z", padded[23])
        assertEquals(padded, migrateLegacyLeadingSlots(padded))
        val overflow = WidgetPlacement(5, 202, 1, 0, LEGACY_GRID_ROWS, 4, 4)
        val moved = migrateLegacyWidgetPlacement(overflow)
        assertEquals(overflow.copy(row = GRID_ROWS), moved)
        // Still the retained special: it rebinds in place and covers no cells.
        assertTrue(moved.coveredIndices().isEmpty())
        val layout = HomeLayout(emptyList(), emptyList(), listOf(moved))
        assertEquals(303, placeWidget(layout, moved.copy(id = 303)).placement(5)?.id)
        val normal = WidgetPlacement(0, 101, 0, 2, 3, 2, 2)
        assertEquals(normal, migrateLegacyWidgetPlacement(normal))
    }

    @Test fun `layout history snapshots from before more rows load at the same positions`() {
        val old = org.json.JSONObject()
            .put("slots", org.json.JSONArray(List(LEGACY_HOME_CELLS + 2) { if (it == 9) "a" else if (it == LEGACY_HOME_CELLS + 1) "b" else null }
                .map { it ?: org.json.JSONObject.NULL }))
            .put("leadingSlots", org.json.JSONArray(List(LEGACY_HOME_CELLS) { if (it == 4) "l" else org.json.JSONObject.NULL }))
            .put("dock", org.json.JSONArray(List(4) { org.json.JSONObject.NULL }))
            .put("widgets", org.json.JSONArray().put(org.json.JSONObject().put("slot", 5).put("id", 7).put("page", 1).put("column", 0)
                .put("row", LEGACY_GRID_ROWS).put("spanX", 4).put("spanY", 4)))
        val raw = org.json.JSONArray().put(org.json.JSONObject().put("time", 1L).put("reason", "old").put("layout", old)).toString()
        val layout = LayoutHistory.decode(raw).single().layout
        assertEquals(Triple(0, 1, 2), position(layout.slots.indexOf("a")))
        assertEquals(Triple(1, 1, 0), position(layout.slots.indexOf("b")))
        assertEquals("l", layout.slotAt(homeCellIndex(-1, 4)))
        assertEquals(HOME_CELLS, layout.leadingSlots.size)
        assertEquals(GRID_ROWS, layout.placement(5)?.row)
        // New snapshots aren't moved again.
        assertEquals(layout, LayoutHistory.decodeLayout(LayoutHistory.encodeLayout(layout)))
    }

    @Test fun `automatic rows are the fewest any measured screen fits, and fixed 4 is always 4`() {
        assertEquals(4, effectiveHomeRows(0, 0, 0))
        assertEquals(5, effectiveHomeRows(0, 5, 0))
        assertEquals(6, effectiveHomeRows(0, 0, 6))
        assertEquals(5, effectiveHomeRows(0, 7, 5))
        assertEquals(5, effectiveHomeRows(0, 5, 7))
        assertEquals(4, effectiveHomeRows(4, 7, 7))
        assertEquals(7, effectiveHomeRows(0, 9, 9))
        assertEquals(5, LauncherState(homeFitCompact = 5, homeFitExpanded = 6).homeAppRows)
        assertEquals(4, LauncherState(homeRows = 4, homeFitCompact = 5, homeFitExpanded = 6).homeAppRows)
    }

    @Test fun `the upright inner screen records its own fit and Automatic takes the fewest of all three`() {
        val s = LauncherState(homeFitCompact = 6, homeFitExpanded = 6)
        assertEquals(6, s.homeAppRows)
        val measured = s.withHomeFit(LayoutScreen.INNER_UPRIGHT, 5)
        assertEquals(6, measured.homeFitExpanded) // upright no longer overwrites the sideways inner fit
        assertEquals(5, measured.homeFitFor(LayoutScreen.INNER_UPRIGHT))
        assertEquals(5, measured.homeAppRows)
        assertEquals(4, effectiveHomeRows(0, 6, 6, 4))
        assertEquals(6, effectiveHomeRows(0, 6, 6, 0)) // not measured yet doesn't count
    }

    @Test fun `new apps skip rows Home doesn't show and continue on the next page`() {
        val full = List(visibleHomeRows(4) * GRID_COLUMNS) { "a$it" }
        val pinned = pinHomeApp(full, "new", true, appRows = 4)
        assertEquals(homeCellIndex(1, 0), pinned.indexOf("new"))
        // With room for more rows, the same app goes under the others.
        assertEquals(homeCellIndex(0, full.size), pinHomeApp(full, "new", true, appRows = 5).indexOf("new"))
        // A gap in a hidden row isn't used either.
        val gapBelow = full + null + "x"
        assertEquals(homeCellIndex(1, 0), pinHomeApp(gapBelow, "new", true, appRows = 4).indexOf("new"))
    }

    @Test fun `pushing apps along skips hidden rows`() {
        val shown = visibleHomeRows(4) * GRID_COLUMNS
        val full = List(shown) { "a$it" }
        val next = dropApp(HomeLayout(full, emptyList()), "new", DropTarget.Home(shown - 1), appRows = 4)
        assertEquals("new", next.slotAt(shown - 1))
        assertEquals("a${shown - 1}", next.slotAt(homeCellIndex(1, 0)))
        assertTrue((shown until HOME_CELLS).all { next.slotAt(it) == null })
        // Everything can use all rows when the pages show them.
        assertEquals("a${shown - 1}", dropApp(HomeLayout(full, emptyList()), "new", DropTarget.Home(shown - 1)).slotAt(shown))
    }

    @Test fun `widget spots, candidates and arrange like iPhone use only shown rows`() {
        val shown = visibleHomeRows(4) * GRID_COLUMNS
        val layout = HomeLayout(List(shown) { "a$it" }, List(4) { null }, emptyList())
        assertEquals(1 to homeCellIndex(1, 0), firstFreeWidgetSpot(layout, 2, 2, appRows = 4))
        assertEquals(0 to shown, firstFreeWidgetSpot(layout, 2, 2, appRows = 6))
        assertNull(firstFreeWidgetSpot(layout, 2, 7, appRows = 4))
        assertNull(widgetCandidate(layout, 9, shown, 2, 2, rows = visibleHomeRows(4)))
        assertNotNull(widgetCandidate(layout, 9, shown, 2, 2))
        // Displaced apps move to page 2's shown cells.
        val first = HomeLayout(List(HOME_CELLS + shown) { if (it < 24) "old$it" else if (it >= HOME_CELLS) "p$it" else null }, List(4) { null })
        val arranged = arrangeLikeIPhone(first, mapOf(IPhoneApp.values().first() to "iphone"), appRows = 4)
        val displaced = arranged.slots.withIndex().filter { (i, id) -> id != null && first.slots.getOrNull(i) != id && id != "iphone" }
        assertTrue(displaced.isNotEmpty())
        assertTrue(displaced.all { (i, _) -> homeCellShown(i, 4) })
    }

    @Test fun `pages draw the shown rows, or more where something already sits lower`() {
        val cells = List<String?>(HOME_CELLS) { null }
        assertEquals(6, shownHomeRows(4, cells, emptyList()))
        assertEquals(7, shownHomeRows(5, cells, emptyList()))
        assertEquals(8, shownHomeRows(4, cells.toMutableList().apply { this[29] = "low" }, emptyList()))
        assertEquals(9, shownHomeRows(4, cells, listOf(WidgetPlacement(0, 1, 0, 0, 7, 2, 2))))
        // The retained overflow widget lives under the grid and draws after it.
        assertEquals(6, shownHomeRows(4, cells, listOf(WidgetPlacement(5, 1, 1, 0, GRID_ROWS, 4, 4))))
        assertFalse(homeCellShown(homeCellIndex(0, 24), 4))
        assertTrue(homeCellShown(homeCellIndex(0, 24), 5))
    }

    @Test fun `accessible moves cover every direction and only real pages`() {
        fun labels(page: Int) = homeMoveOffsets(page).associate { EnglishStrings.get(it.label) to it.offset }
        val first = labels(0)
        org.junit.Assert.assertEquals(-1, first["Move Left"]); org.junit.Assert.assertEquals(GRID_COLUMNS, first["Move Down"])
        org.junit.Assert.assertEquals(HOME_CELLS, first["Move to Next Page"])
        org.junit.Assert.assertFalse("Move to Previous Page" in first)
        org.junit.Assert.assertTrue("Move to Previous Page" in labels(2))
        org.junit.Assert.assertFalse(labels(-1).keys.any { "Page" in it })
        // TalkBack says where it went in a sentence of its own, not "Move Left" with the verb cut off.
        org.junit.Assert.assertEquals("Moved left", EnglishStrings.get(homeMoveOffsets(0).first().moved))
    }
}
