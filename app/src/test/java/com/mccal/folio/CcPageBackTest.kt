package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A swipe up in the empty space under Control Center's controls goes back a page from Media and Connectivity, and closes only from the first. */
class CcPageBackTest {
    @Test fun `a swipe up goes back one page from every page after the first`() {
        assertEquals(0, ccBackTarget(1))
        assertEquals(1, ccBackTarget(2))
    }

    @Test fun `the first page has no page to go back to, so the swipe closes the panel`() {
        assertNull(ccBackTarget(0))
    }

    @Test fun `the scrim closes the panel when Control Center is not there to take the swipe`() {
        assertFalse(CcPageBack().goBack())
    }

    @Test fun `the scrim lets Control Center take the swipe when it has a page to go back to`() {
        val pages = CcPageBack()
        var shown = 2
        pages.back = { ccBackTarget(shown)?.let { shown = it; true } ?: false }
        assertTrue(pages.goBack()); assertEquals(1, shown)
        assertTrue(pages.goBack()); assertEquals(0, shown)
        assertFalse(pages.goBack()) // on the first page the same swipe closes
        assertEquals(0, shown)
    }

    @Test fun `a swipe up while editing ends the editing first, whatever page it is on`() {
        assertEquals(CcSwipeUp.StopEditing, ccSwipeUp(editing = true, current = 0))
        assertEquals(CcSwipeUp.StopEditing, ccSwipeUp(editing = true, current = 2))
    }

    @Test fun `not editing, a swipe up goes back a page and only the first page closes the panel`() {
        assertEquals(CcSwipeUp.GoBack(1), ccSwipeUp(editing = false, current = 2))
        assertEquals(CcSwipeUp.GoBack(0), ccSwipeUp(editing = false, current = 1))
        assertEquals(CcSwipeUp.Close, ccSwipeUp(editing = false, current = 0))
    }
}
