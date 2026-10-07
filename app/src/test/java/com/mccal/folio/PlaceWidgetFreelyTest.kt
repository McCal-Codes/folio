package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceWidgetFreelyTest {
    private val clock = WidgetPlacement(0, BIG_CLOCK_WIDGET, 0, 0, 0, 4, 2)
    private fun layout(vararg apps: Int) = HomeLayout(
        slots = MutableList<String?>(HOME_CELLS) { null }.also { cells -> apps.forEach { cells[it] = "app$it" } },
        dock = listOf(null, null, null, null), widgetPlacements = listOf(clock))

    @Test fun `a free spot keeps the nearest cells and draws the rest of the way`() {
        val moved = placeWidgetFreely(layout(), 0, 0f, 2.3f).placement(0)!!
        assertEquals(0, moved.column); assertEquals(2, moved.row)
        assertEquals(0f, moved.offsetX, 0f); assertEquals(.3f, moved.offsetY, .001f)
    }

    @Test fun `the offset never passes half a cell and the page edge keeps it inside`() {
        val half = placeWidgetFreely(layout(), 0, 0f, 3.5f).placement(0)!!
        assertTrue(half.offsetY in -MAX_WIDGET_OFFSET..MAX_WIDGET_OFFSET)
        val edge = placeWidgetFreely(layout(), 0, -.4f, -.4f).placement(0)!!
        assertEquals(0f, edge.offsetX, 0f); assertEquals(0f, edge.offsetY, 0f)
        val bottom = placeWidgetFreely(layout(), 0, 0f, (GRID_ROWS - 2) + .4f).placement(0)!!
        assertEquals(GRID_ROWS - 2, bottom.row); assertEquals(0f, bottom.offsetY, 0f)
    }

    @Test fun `cells with apps in them refuse the spot and leave the layout alone`() {
        val taken = layout(2 * GRID_COLUMNS)
        assertEquals(taken, placeWidgetFreely(taken, 0, 0f, 2.2f))
        assertEquals(taken, placeWidgetFreely(taken, 0, Float.NaN, 1f))
    }

    @Test fun `moving or resizing a widget puts it back on the grid`() {
        val free = layout().copy(widgetPlacements = listOf(clock.copy(offsetX = .3f, offsetY = -.2f)))
        assertEquals(0f, moveWidget(free, 0, homeCellIndex(0, 2 * GRID_COLUMNS)).placement(0)!!.offsetY, 0f)
        val smaller = resizeWidget(free, 0, 2, 2).placement(0)!!
        assertEquals(0f, smaller.offsetX, 0f); assertEquals(0f, smaller.offsetY, 0f)
    }

    @Test fun `an untouched placement is exactly on its cells`() {
        assertEquals(0f, clock.offsetX, 0f); assertEquals(0f, clock.offsetY, 0f)
        assertEquals(clock, WidgetPlacement(0, BIG_CLOCK_WIDGET, 0, 0, 0, 4, 2))
    }

    @Test fun `a placement with an offset is a different placement from the same cells without one`() {
        assertTrue(clock.copy(offsetY = .25f) != clock)
        assertEquals(clock, clock.copy(offsetX = 0f, offsetY = 0f))
    }

    @Test fun `an offset outside half a cell is not a valid placement and does not get saved`() {
        val outside = clock.copy(offsetY = .75f)
        val layout = layout()
        assertEquals(layout, placeWidget(layout, outside))
    }

    @Test fun `the half height top rows and the app rows below them are measured in their own pitch`() {
        val top = 100f; val app = 200f
        assertEquals(100f, placeRowToPx(1f, top, app), 0f)
        assertEquals(200f, placeRowToPx(2f, top, app), 0f)
        assertEquals(400f, placeRowToPx(3f, top, app), 0f)
        for (r in listOf(0f, .5f, 1.5f, 2f, 2.25f, 5f)) assertEquals(r, placePxToRow(placeRowToPx(r, top, app), top, app), .0001f)
        // 250 px down from the top of row 1 is the rest of row 1 (100) and then 150 px of an app row.
        assertEquals(2.75f, placePxToRow(placeRowToPx(1f, top, app) + 250f, top, app), .0001f)
    }
}
