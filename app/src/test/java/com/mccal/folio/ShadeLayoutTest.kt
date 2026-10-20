package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

/** The pull-down's shape per layout and screen, and the move from the old switch (the unfolded Notification Center layout choice). */
class ShadeLayoutTest {
    private val nc = ShadePanel.NOTIFICATIONS
    private val cc = ShadePanel.QUICK_SETTINGS
    private fun shape(choice: NcLayout, wide: Boolean = true, landscape: Boolean = true, width: Int = 932, hinged: Boolean = false, panel: ShadePanel = nc) =
        shadeShape(choice, wide, landscape, width, hinged, panel)

    @Test fun `a phone keeps one panel at a time whatever is chosen`() {
        NcLayout.entries.forEach { assertEquals(ShadeShape.SINGLE_TOP, shape(it, wide = false, width = 475)) }
    }

    @Test fun `full screen is how it has been`() {
        assertEquals(ShadeShape.SPLIT_CLOCK, shape(NcLayout.FULL_SCREEN))
        assertEquals(ShadeShape.SINGLE_TOP, shape(NcLayout.FULL_SCREEN, landscape = false))
        assertEquals(ShadeShape.SINGLE_TOP, shape(NcLayout.FULL_SCREEN, panel = cc))
    }

    @Test fun `card is a card for either panel, on any wide screen`() {
        listOf(nc, cc).forEach { assertEquals(ShadeShape.CARD, shape(NcLayout.CARD, panel = it)) }
        assertEquals(ShadeShape.CARD, shape(NcLayout.CARD, landscape = false, width = 704))
    }

    @Test fun `side by side needs room for two and no fold in the way`() {
        assertEquals(ShadeShape.BOTH, shape(NcLayout.SIDE_BY_SIDE))
        assertEquals(ShadeShape.BOTH, shape(NcLayout.SIDE_BY_SIDE, panel = cc))
        assertEquals(ShadeShape.BOTH, shape(NcLayout.SIDE_BY_SIDE, width = SIDE_BY_SIDE_MIN_DP))
        assertEquals(ShadeShape.CARD, shape(NcLayout.SIDE_BY_SIDE, width = SIDE_BY_SIDE_MIN_DP - 1))
        assertEquals(ShadeShape.CARD, shape(NcLayout.SIDE_BY_SIDE, hinged = true))
    }

    @Test fun `Folio's panel over another app needs everything to agree`() {
        assertEquals(true, shadeOverAppsWanted(true, true, false, false, nc))
        assertEquals(true, shadeOverAppsWanted(true, true, false, false, cc))
        assertEquals(false, shadeOverAppsWanted(false, true, false, false, nc))   // the switch is off: the phone's shade
        assertEquals(false, shadeOverAppsWanted(true, false, false, false, nc))   // Folio's panels are off altogether
        assertEquals(false, shadeOverAppsWanted(true, true, true, false, nc))     // Safe Mode
        assertEquals(false, shadeOverAppsWanted(true, true, false, true, nc))     // locked: never over the lock screen
        assertEquals(false, shadeOverAppsWanted(true, true, false, false, ShadePanel.SEARCH))
    }

    @Test fun `a saved layout wins over the old switch`() {
        assertEquals(NcLayout.SIDE_BY_SIDE, ncLayoutFrom("SIDE_BY_SIDE", true))
        assertEquals(NcLayout.CARD, ncLayoutFrom("CARD", true))
        assertEquals(NcLayout.FULL_SCREEN, ncLayoutFrom("FULL_SCREEN", false))
    }

    @Test fun `settings from before the choice move to the nearest layout`() {
        assertEquals(NcLayout.FULL_SCREEN, ncLayoutFrom(null, true))
        assertEquals(NcLayout.CARD, ncLayoutFrom(null, false))
        assertEquals(NcLayout.FULL_SCREEN, ncLayoutFrom(null, null))
    }

    @Test fun `an unreadable saved layout falls back, and the old switch still decides`() {
        assertEquals(NcLayout.FULL_SCREEN, ncLayoutFrom("", null))
        assertEquals(NcLayout.FULL_SCREEN, ncLayoutFrom("TABLET", null))
        assertEquals(NcLayout.CARD, ncLayoutFrom("TABLET", false))
    }
}
