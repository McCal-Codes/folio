package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The shade shortcuts name their panel in the manifest; only the two system panels count. */
class ShadeShortcutTest {
    @Test fun `the two system panels are read from their name`() {
        assertEquals(ShadePanel.NOTIFICATIONS, ShadePanel.fromShortcut("NOTIFICATIONS"))
        assertEquals(ShadePanel.QUICK_SETTINGS, ShadePanel.fromShortcut("QUICK_SETTINGS"))
    }

    @Test fun `Folio's own search, a stray value or nothing opens nothing`() {
        assertNull(ShadePanel.fromShortcut("SEARCH"))
        assertNull(ShadePanel.fromShortcut("notifications"))
        assertNull(ShadePanel.fromShortcut(""))
        assertNull(ShadePanel.fromShortcut(null))
    }
}
