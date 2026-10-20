package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A widget placed over apps pushes them along in order, on to the next page when this one runs out (#329). */
class WidgetPushTest {
    private val rows = BASE_APP_ROWS + 2
    private val widget = WidgetPlacement(0, BIG_CLOCK_WIDGET, 0, 0, 0, 4, 2)

    /** Cell [i] of page [page] holds "p{page}a{i}" for each i in [cells]. */
    private fun apps(page: Int, cells: IntRange) = cells.associate { homeCellIndex(page, it) to "p${page}a$it" }
    private fun layout(cells: Map<Int, String>, vararg widgets: WidgetPlacement) = HomeLayout(
        slots = List((cells.keys.maxOrNull() ?: -1) + 1) { cells[it] }, dock = listOf(null, null, null, null),
        widgetPlacements = widgets.toList())
    private fun HomeLayout.order() = slots.filterNotNull()
    private fun HomeLayout.cellOf(id: String) = slots.indexOf(id)
    private fun HomeLayout.appUnderAWidget() = widgetPlacements.any { w -> w.coveredIndices().any { slotAt(it) != null } }

    @Test fun `nothing in the way places the widget and moves nothing`() {
        val before = layout(apps(0, 8..15))
        val push = placeWidgetPushingApps(before, widget)!!
        assertEquals(placeWidget(before, widget), push.layout)
        assertEquals(0, push.moved); assertEquals(0, push.pagesAdded)
    }

    @Test fun `apps under the widget move down just far enough and keep their order`() {
        // A 4 x 2 widget at the top of a page whose first two rows are full of apps.
        val before = layout(apps(0, 0..15))
        val push = placeWidgetPushingApps(before, widget, rows)!!
        assertEquals(before.order(), push.layout.order())
        assertTrue(!push.layout.appUnderAWidget())
        assertEquals(homeCellIndex(0, 8), push.layout.cellOf("p0a0"))
        assertEquals(homeCellIndex(0, 23), push.layout.cellOf("p0a15"))
        assertEquals(16, push.moved)
    }

    @Test fun `an empty cell after the apps takes up the slack so later apps stay put`() {
        val cells = apps(0, 0..3) + apps(0, 8..11)  // row 0 apps, row 1 empty, row 2 apps
        val push = placeWidgetPushingApps(layout(cells), widget.copy(spanY = 1), rows)!!
        assertEquals(homeCellIndex(0, 4), push.layout.cellOf("p0a0"))
        assertEquals(homeCellIndex(0, 8), push.layout.cellOf("p0a8"))
        assertEquals(4, push.moved)
    }

    @Test fun `a full page continues on the next one and a page is made when there isn't one`() {
        val full = layout(apps(0, 0 until HOME_CELLS))
        val push = placeWidgetPushingApps(full, widget, rows)!!
        assertEquals(full.order(), push.layout.order())
        assertEquals(2, push.layout.contentPageCount)
        assertEquals(1, push.pagesAdded)
        assertTrue(!push.layout.appUnderAWidget())
    }

    @Test fun `when page two is full too the overflow makes page three`() {
        // Pages fill their shown rows (4 app rows + the 2 top rows = 24 cells), so two full pages overflow onto a third.
        val shown = 6 * GRID_COLUMNS
        val twoPages = layout(apps(0, 0 until shown) + apps(1, 0 until shown))
        val push = placeWidgetPushingApps(twoPages, widget, BASE_APP_ROWS)!!
        assertEquals(twoPages.order(), push.layout.order())
        assertEquals(3, push.layout.contentPageCount)
        assertEquals(1, push.pagesAdded)
        assertTrue(push.layout.slots.indices.all { push.layout.slotAt(it) == null || homeCellShown(it, BASE_APP_ROWS) })
    }

    @Test fun `apps skip rows a page doesn't show and cells other widgets hold`() {
        val other = WidgetPlacement(1, DATE_WIDGET, 0, 0, 3, 2, 1)
        val before = layout(apps(0, 0..7), other)
        val push = placeWidgetPushingApps(before, widget, BASE_APP_ROWS)!!
        assertTrue(!push.layout.appUnderAWidget())
        assertEquals(before.order(), push.layout.order())
        assertEquals(other, push.layout.placement(1))
    }

    @Test fun `a widget never moves another widget, and an overlap is refused`() {
        val other = WidgetPlacement(1, DATE_WIDGET, 0, 0, 0, 4, 2)
        assertNull(placeWidgetPushingApps(layout(emptyMap(), other), widget.copy(slot = 2, row = 1), rows))
    }

    @Test fun `the unfolded-only page and invalid sizes are refused`() {
        assertNull(placeWidgetPushingApps(layout(apps(0, 0..3)), widget.copy(page = -1), rows))
        assertNull(placeWidgetPushingApps(layout(apps(0, 0..3)), widget.copy(spanX = 5), rows))
        assertNull(placeWidgetPushingApps(layout(apps(0, 0..3)), widget.copy(row = GRID_ROWS - 1, spanY = 2), rows))
    }

    @Test fun `moving a widget down pushes the apps it lands on and frees where it was`() {
        val before = layout(apps(0, 8..23), widget)
        val push = moveWidgetPushingApps(before, 0, homeCellIndex(0, 8), rows)!!
        assertEquals(before.order(), push.layout.order())
        assertEquals(2, push.layout.placement(0)!!.row)
        assertTrue(!push.layout.appUnderAWidget())
    }

    @Test fun `resizing a widget taller pushes the row below it`() {
        val before = layout(apps(0, 8..15), widget.copy(spanY = 2))
        val push = resizeWidgetPushingApps(before, 0, 4, 3, rows)!!
        assertEquals(3, push.layout.placement(0)!!.spanY)
        assertEquals(before.order(), push.layout.order())
        assertTrue(!push.layout.appUnderAWidget())
        assertNull(resizeWidgetPushingApps(before, 9, 4, 3, rows))
    }

    @Test fun `making room is moving and resizing as before wherever nothing can be pushed`() {
        // The unfolded-only page has nothing to flow into, so a move or resize there is refused exactly as it was.
        val leading = HomeLayout(slots = emptyList(), dock = listOf(null, null, null, null),
            leadingSlots = List(HOME_CELLS) { if (it == 8) "app" else null }, widgetPlacements = listOf(widget.copy(page = -1)))
        val to = homeCellIndex(-1, 8)
        assertEquals(moveWidget(leading, 0, to), moveWidgetMakingRoom(leading, 0, to, rows))
        assertEquals(resizeWidget(leading, 0, 4, 4), resizeWidgetMakingRoom(leading, 0, 4, 4, rows))
        val free = layout(apps(0, 8..11), widget)
        assertEquals(moveWidget(free, 0, homeCellIndex(0, 16)), moveWidgetMakingRoom(free, 0, homeCellIndex(0, 16), rows))
        assertEquals(free, moveWidgetMakingRoom(free, 7, homeCellIndex(0, 16), rows))
    }

    @Test fun `every app survives wherever the widget lands`() {
        val before = layout(apps(0, 0 until 24) + apps(1, 0 until 12))
        for (row in 0..GRID_ROWS - 2) for (column in 0..GRID_COLUMNS - 2) for (page in 0..1) {
            val push = placeWidgetPushingApps(before, widget.copy(page = page, column = column, row = row, spanX = 2, spanY = 2), rows)
            assertNotNull("$page $column $row", push)
            assertEquals(before.order(), push!!.layout.order())
            assertTrue("$page $column $row", !push.layout.appUnderAWidget())
            assertEquals(before.order().toSet().size, push.layout.slots.filterNotNull().toSet().size)
        }
    }
}
