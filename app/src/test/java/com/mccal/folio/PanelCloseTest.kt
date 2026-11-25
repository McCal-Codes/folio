package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A panel that closes notes why in the bug-report trail (beta 6: Control Center closed about a second after a fast swipe open on the
 * Fold, and no report could say which path closed it). The line names the panel and the reason, and nothing is written when no panel was open.
 */
class PanelCloseTest {
    @Test fun `the trail line names the panel and the reason`() {
        assertEquals("Control Center closed by a tap under the controls", panelClosedLine(ShadePanel.QUICK_SETTINGS, PanelClose.TAP_BELOW))
        assertEquals("Notification Center closed by Back", panelClosedLine(ShadePanel.NOTIFICATIONS, PanelClose.BACK))
        assertEquals("Control Center closed by the window changing size (904x704 to 412x884 dp)",
            panelClosedLine(ShadePanel.QUICK_SETTINGS, PanelClose.WINDOW_RESIZED, "904x704 to 412x884 dp"))
    }

    @Test fun `every reason has its own words`() {
        val words = PanelClose.entries.map { it.words }
        assertEquals(words.size, words.toSet().size)
        assertTrue(words.none { it.isBlank() })
    }

    @Test fun `a close is written to the trail only when a panel was open`() {
        Diagnostics.panelClosed(ShadePanel.QUICK_SETTINGS, PanelClose.HOME_STOPPED)
        assertTrue(Diagnostics.trailText().lines().last().endsWith("Control Center closed by Home going to the background"))
        val before = Diagnostics.trailText()
        Diagnostics.panelClosed(null, PanelClose.HOME_STOPPED)
        assertEquals(before, Diagnostics.trailText())
        assertFalse(Diagnostics.trailText().lines().last().contains("null"))
    }
}
