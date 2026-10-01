package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** In split screen Folio shows a Home page, not the App Library or Today it was left on. */
class SplitViewHomeTest {
    @Test fun `on the App Library or Today it goes to the last Home page`() {
        assertEquals(2, splitViewHomePage(inSplitScreen = true, currentPage = 4, homePages = 4, lastHomePage = 2))
        assertEquals(0, splitViewHomePage(inSplitScreen = true, currentPage = -1, homePages = 4, lastHomePage = 0))
    }

    @Test fun `on a Home page it stays where it is`() {
        assertNull(splitViewHomePage(true, currentPage = 1, homePages = 4, lastHomePage = 3))
    }

    @Test fun `outside split screen nothing moves`() {
        assertNull(splitViewHomePage(false, currentPage = 4, homePages = 4, lastHomePage = 2))
    }

    @Test fun `a stale last page is kept inside the pages that exist`() {
        assertEquals(1, splitViewHomePage(true, currentPage = 2, homePages = 2, lastHomePage = 9))
        assertNull(splitViewHomePage(true, currentPage = 0, homePages = 0, lastHomePage = 0))
    }
}
