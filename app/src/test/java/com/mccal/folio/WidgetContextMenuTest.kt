package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The widget hold menu's rows: what the first level lists, what waits behind More, and which size carries the checkmark. */
class WidgetContextMenuTest {
    @Test fun `the first level lists the six everyday rows with Remove last`() {
        assertEquals(
            listOf(WidgetMenuAction.EDIT_WIDGET, WidgetMenuAction.SIZE, WidgetMenuAction.REPLACE, WidgetMenuAction.STACK,
                WidgetMenuAction.EDIT_HOME, WidgetMenuAction.MORE, WidgetMenuAction.REMOVE),
            widgetMenuActions(canEdit = true))
    }

    @Test fun `a widget with nothing to edit has no Edit Widget row`() {
        val actions = widgetMenuActions(canEdit = false)
        assertFalse(WidgetMenuAction.EDIT_WIDGET in actions)
        assertEquals(WidgetMenuAction.REMOVE, actions.last())
        assertEquals(WidgetMenuAction.SIZE, actions.first())
    }

    @Test fun `custom size is always behind More and the rest appear only where they apply`() {
        assertEquals(listOf(WidgetMoreEntry.CUSTOM_SIZE),
            widgetMoreEntries(homePages = 1, canPlace = false, canExpand = false, canRemovePhoto = false, stacked = false))
        assertEquals(
            listOf(WidgetMoreEntry.CUSTOM_SIZE, WidgetMoreEntry.MOVE_TO_PAGE, WidgetMoreEntry.PLACE_FREELY, WidgetMoreEntry.EXPAND,
                WidgetMoreEntry.REMOVE_PHOTO, WidgetMoreEntry.EDIT_STACK),
            widgetMoreEntries(homePages = 3, canPlace = true, canExpand = true, canRemovePhoto = true, stacked = true))
    }

    @Test fun `pages appear only when Home has more than one`() {
        assertFalse(WidgetMoreEntry.MOVE_TO_PAGE in widgetMoreEntries(1, true, true, true, true))
        assertTrue(WidgetMoreEntry.MOVE_TO_PAGE in widgetMoreEntries(2, false, false, false, false))
    }

    private val everything: (Int, Int) -> Boolean = { _, _ -> true }

    @Test fun `the checkmark sits on the size the widget has now`() {
        val medium = widgetSizeChoices(4, 2, constraintsKnown = true, fits = everything)
        assertEquals(listOf(WidgetShape.MEDIUM), medium.filter { it.selected }.map { it.shape })
        val small = widgetSizeChoices(2, 2, constraintsKnown = true, fits = everything)
        assertEquals(listOf(WidgetShape.SMALL), small.filter { it.selected }.map { it.shape })
        val large = widgetSizeChoices(4, 4, constraintsKnown = true, fits = everything)
        assertEquals(listOf(WidgetShape.LARGE), large.filter { it.selected }.map { it.shape })
    }

    @Test fun `Small Medium and Large always show, dimmed when the widget cannot take them`() {
        val choices = widgetSizeChoices(2, 2, constraintsKnown = true) { w, h -> w == 2 && h == 2 }
        assertEquals(listOf(WidgetShape.SMALL, WidgetShape.MEDIUM, WidgetShape.LARGE), choices.take(3).map { it.shape })
        assertEquals(listOf(true, false, false), choices.take(3).map { it.enabled })
    }

    @Test fun `the current size is never dimmed even when it no longer fits`() {
        val choices = widgetSizeChoices(4, 4, constraintsKnown = true) { _, _ -> false }
        val large = choices.first { it.shape == WidgetShape.LARGE }
        assertTrue(large.selected)
        assertTrue(large.enabled)
    }

    @Test fun `the one-cell, row and column shapes show only where the limits allow them`() {
        val none = widgetSizeChoices(2, 2, constraintsKnown = true) { w, h -> w >= 2 && h in 2..4 }
        assertEquals(listOf(WidgetShape.SMALL, WidgetShape.MEDIUM, WidgetShape.LARGE), none.map { it.shape })
        val all = widgetSizeChoices(2, 2, constraintsKnown = true, fits = everything)
        assertEquals(listOf(WidgetShape.EXTRA_LARGE, WidgetShape.TINY, WidgetShape.ROW, WidgetShape.COLUMN), all.drop(3).map { it.shape })
        assertEquals(listOf(GRID_COLUMNS to 1), all.filter { it.shape == WidgetShape.ROW }.map { it.width to it.height })
    }

    @Test fun `Extra Large is 4 by 6, offered only where the widget and the page allow it`() {
        val tall = widgetSizeChoices(4, 4, constraintsKnown = true, fits = everything)
        assertEquals(listOf(4 to 6), tall.filter { it.shape == WidgetShape.EXTRA_LARGE }.map { it.width to it.height })
        val capped = widgetSizeChoices(4, 4, constraintsKnown = true) { _, h -> h <= 4 }
        assertFalse(capped.any { it.shape == WidgetShape.EXTRA_LARGE })
    }

    @Test fun `an Extra Large widget gets the checkmark even when it no longer fits`() {
        val choices = widgetSizeChoices(4, 6, constraintsKnown = true) { _, _ -> false }
        assertEquals(listOf(WidgetShape.EXTRA_LARGE), choices.filter { it.selected }.map { it.shape })
    }

    @Test fun `with no known limits the extra shapes stay hidden`() {
        val choices = widgetSizeChoices(2, 2, constraintsKnown = false, fits = everything)
        assertEquals(3, choices.size)
    }

    @Test fun `a size set by hand gets a checked row of its own`() {
        val choices = widgetSizeChoices(3, 2, constraintsKnown = true, fits = everything)
        val checked = choices.filter { it.selected }
        assertEquals(1, checked.size)
        assertEquals(WidgetShape.CURRENT, checked.single().shape)
        assertEquals(3 to 2, checked.single().width to checked.single().height)
    }

    @Test fun `a named size never gets a duplicate custom row`() {
        assertNull(widgetSizeChoices(2, 2, constraintsKnown = true, fits = everything).firstOrNull { it.shape == WidgetShape.CURRENT })
    }
}
