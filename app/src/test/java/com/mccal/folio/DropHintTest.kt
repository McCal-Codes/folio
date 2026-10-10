package com.mccal.folio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DropHintTest {
    @Test fun `an empty cell shows a hint while an app is carried`() {
        assertTrue(showsDropHint(dragging = true, carryingApp = true, gap = false, previewId = null, underWidget = false))
        assertTrue(showsDropHint(dragging = true, carryingApp = true, gap = true, previewId = "maps", underWidget = false))
    }

    @Test fun `a cell under a widget shows no hint`() {
        assertFalse(showsDropHint(dragging = true, carryingApp = true, gap = false, previewId = null, underWidget = true))
    }

    @Test fun `no hint without a carried app or on a cell an app will fill`() {
        assertFalse(showsDropHint(dragging = false, carryingApp = true, gap = false, previewId = null, underWidget = false))
        assertFalse(showsDropHint(dragging = true, carryingApp = false, gap = false, previewId = null, underWidget = false))
        assertFalse(showsDropHint(dragging = true, carryingApp = true, gap = false, previewId = "maps", underWidget = false))
    }

    @Test fun `a widget covers the cells of its span on its page`() {
        val covered = WidgetPlacement(slot = 0, id = 7, page = 0, column = 0, row = 0, spanX = 2, spanY = 2).coveredIndices()
        assertTrue(homeCellIndex(0, 0) in covered && homeCellIndex(0, 1) in covered && homeCellIndex(0, GRID_COLUMNS + 1) in covered)
        assertFalse(homeCellIndex(0, 2) in covered)
    }
}
