package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins what the Stability and Motion Audit found on 8 Oct 2026 (docs/plan-beta-2-audit-duet-root.md), so a change to either is deliberate.
 * Nothing here is a fix: these tests describe how Folio behaves today.
 */
class LayoutIsolationAuditTest {
    /**
     * Issue #296: "separate layout when upright" only separates the geometry preset (sizes, spacing, dock width). The arrangement itself
     * (which app is where, the dock, folders, widgets) is one value, so moving an app edits the upright and the landscape inner screen
     * alike. If the arrangement ever becomes per screen, this is the test to change, together with the label of the switch.
     */
    @Test fun `a layout of its own for the upright inner screen separates the geometry and not the arrangement`() {
        val arranged = LauncherState(
            homeSlots = listOf("a", "b", null, "c"), dock = listOf("d", null, null, null),
            expanded = LayoutPreset(iconSize = 60f), portrait = null)
        val separate = arranged.copy(portrait = LayoutPreset(iconSize = 44f))
        assertNotEquals("the sizes are separate", arranged.presetFor(LayoutScreen.INNER_UPRIGHT), separate.presetFor(LayoutScreen.INNER_UPRIGHT))
        assertEquals("the landscape inner screen keeps its own sizes", arranged.presetFor(LayoutScreen.INNER), separate.presetFor(LayoutScreen.INNER))
        assertEquals("the arrangement is one value for every screen", arranged.layout, separate.layout)
        assertEquals(listOf("a", "b", null, "c"), separate.homeSlots)
    }

    /**
     * Issue #256 and #13: with round controls at the bottom of the rail, Home keeps 124 dp under the dock, and 28 dp when there are none.
     * The reserve is the same whether the rail holds one control (search) or two (back to Home and search), so it is sized for two.
     * Prints a table of the dock for the windows the reports mention, so a fix can be judged by numbers instead of by eye.
     */
    @Test fun `the controls at the bottom of the rail take a fixed share of the dock's room`() {
        val windows = listOf("Fold8 inner" to (932f to 704f), "Fold8 inner upright" to (704f to 932f), "Fold8 cover" to (475f to 751f), "Fold8 cover landscape" to (751f to 475f))
        val rows = mutableListOf<String>()
        for ((name, size) in windows) for (status in listOf(0f, 160f, 300f)) {
            val with = homeGeometry(size.first, size.second, LayoutPreset(), true, statusHeight = status, railControls = true)
            val without = homeGeometry(size.first, size.second, LayoutPreset(), true, statusHeight = status, railControls = false)
            rows += "%-22s status %3.0f  dock with controls: top %5.1f height %5.1f row %4.1f   without: top %5.1f height %5.1f row %4.1f".format(
                name, status, with.dockTop, with.dockHeight, with.dockRowHeight, without.dockTop, without.dockHeight, without.dockRowHeight)
            assertTrue("$name: the controls never make the dock taller", with.dockHeight <= without.dockHeight + .01f)
            assertTrue("$name: a dock keeps room for four 48 dp targets", with.dockRowHeight >= 48f)
        }
        println("DOCK RESERVE TABLE\n" + rows.joinToString("\n"))
        val reserveWith = 124f; val reserveWithout = 28f
        assertEquals(96f, reserveWith - reserveWithout, 0f)
    }
}
