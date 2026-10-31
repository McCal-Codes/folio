package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherPagerTest {
    @Test fun `Today View appearing moves the pager one page so Home page 2 stays Home page 2`() {
        // Native page 2 with no left page is Home page 2; with a left page it is page 3 of 5.
        assertEquals(3, nativePageAfterFirstHomeChange(currentNativePage = 2, oldFirstHome = 0, newFirstHome = 1, nativePageCount = 5))
    }

    @Test fun `the left page going away moves the pager back one page`() {
        assertEquals(1, nativePageAfterFirstHomeChange(currentNativePage = 2, oldFirstHome = 1, newFirstHome = 0, nativePageCount = 4))
    }

    @Test fun `standing on the left page when it goes away stays on the first page`() {
        assertEquals(0, nativePageAfterFirstHomeChange(currentNativePage = 0, oldFirstHome = 1, newFirstHome = 0, nativePageCount = 4))
    }

    @Test fun `no change keeps the page and the result is never outside the pager`() {
        assertEquals(2, nativePageAfterFirstHomeChange(2, 1, 1, 5))
        assertEquals(3, nativePageAfterFirstHomeChange(currentNativePage = 9, oldFirstHome = 0, newFirstHome = 1, nativePageCount = 4))
        assertEquals(0, nativePageAfterFirstHomeChange(currentNativePage = 0, oldFirstHome = 0, newFirstHome = 0, nativePageCount = 0))
    }
}
