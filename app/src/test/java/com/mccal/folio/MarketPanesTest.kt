package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the Market shares a wide window: the list beside an open package only when both stay readable, otherwise the
 * package pushes over the list, and with nothing open the list takes the pane in two columns.
 */
class MarketPanesTest {
    @Test fun `with the tabs in a sidebar, a package pushes over the list below 1200 dp`() {
        assertFalse(marketListBeside(932f, sidebar = true))    // Fold8 inner
        assertFalse(marketListBeside(1199f, sidebar = true))
        assertTrue(marketListBeside(1200f, sidebar = true))
        assertTrue(marketListBeside(1280f, sidebar = true))    // tablet, landscape
    }

    @Test fun `with the tabs along the bottom, the list and the package are the two panes`() {
        assertTrue(marketListBeside(704f, sidebar = false))    // Fold8 inner, held upright
    }

    @Test fun `a list with the pane to itself uses two columns once each gets about 320 dp`() {
        assertEquals(1, marketListColumns(360f))
        assertEquals(1, marketListColumns(639f))
        assertEquals(2, marketListColumns(704f))
        assertEquals(2, marketListColumns(760f))                // Fold8 inner, beside the sidebar
    }

    @Test fun `Settings inside the Market leaves the sidebar its pane`() {
        assertEquals(1, settingsColumnsBesideSidebar(932f))    // Fold8 inner: Market sidebar + one Settings page
        assertEquals(2, settingsColumnsBesideSidebar(1280f))   // list and page beside the sidebar
    }
}
