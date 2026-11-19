package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Each screen keeps its own resting Home page through a fold or an unfold. */
class DisplayPagesTest {
    @Test fun `a screen that was used before gets its own page back`() {
        assertEquals(0, pageAfterScreenChange(settledPage = 3, homePages = 5, remembered = 0))
        assertEquals(4, pageAfterScreenChange(settledPage = 1, homePages = 5, remembered = 4))
    }

    @Test fun `first time on a screen keeps the page it is on`() {
        assertNull(pageAfterScreenChange(settledPage = 2, homePages = 5, remembered = null))
    }

    @Test fun `already on that page nothing moves`() {
        assertNull(pageAfterScreenChange(settledPage = 2, homePages = 5, remembered = 2))
    }

    @Test fun `a page that is gone is kept inside the pages there are`() {
        assertEquals(2, pageAfterScreenChange(settledPage = 0, homePages = 3, remembered = 9))
    }

    @Test fun `the App Library stays the App Library`() {
        assertNull(pageAfterScreenChange(settledPage = 5, homePages = 5, remembered = 1))
    }

    @Test fun `the page left of Home and a temporary page stay put`() {
        assertNull(pageAfterScreenChange(settledPage = -1, homePages = 5, remembered = 3))
        assertNull(pageAfterScreenChange(settledPage = 5, homePages = 5, remembered = 0))
    }

    @Test fun `no Home pages leaves everything alone`() {
        assertNull(pageAfterScreenChange(settledPage = 0, homePages = 0, remembered = 0))
    }

    @Test fun `memory is kept per screen and starts empty`() {
        val empty = HomePageMemory()
        assertNull(empty.page(FolioScreen.COVER))
        assertNull(empty.page(FolioScreen.INNER))
        val used = empty.with(FolioScreen.COVER, 0).with(FolioScreen.INNER, 3)
        assertEquals(0, used.page(FolioScreen.COVER))
        assertEquals(3, used.page(FolioScreen.INNER))
        assertEquals(1, used.with(FolioScreen.INNER, 1).page(FolioScreen.INNER))
        assertEquals(0, used.with(FolioScreen.INNER, 1).page(FolioScreen.COVER))
    }

    @Test fun `the library place starts at the top of Personal and clears`() {
        val place = LibraryPlace()
        assertFalse(place.work)
        assertNull(place.category)
        place.work = true; place.category = LibraryCategory.OTHER; place.index = 4; place.offset = 12; place.scrollKey = "k"
        place.clear()
        assertFalse(place.work)
        assertNull(place.category)
        assertEquals(0, place.index); assertEquals(0, place.offset); assertNull(place.scrollKey)
    }
}
