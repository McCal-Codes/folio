package com.mccal.folio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Settings' Back: what it says, and where it lands. It used to know four parents by name, so a page under General said
 * "‹ Folio" and went to General, and in two columns the pages under General, Support Folio and Themes had no Back at
 * all. And one scroll served every page, reset on any move, so Back always landed at the top of the list.
 */
class SettingsNavigationTest {
    @Before fun forget() = SettingsMemory.pageScroll.clear()

    @Test fun `Back names the page it returns to`() {
        assertEquals(R.string.general, settingsBackLabel(CustomizationPage.SOFTWARE_UPDATE, columns = 1))
        assertEquals(R.string.support_folio, settingsBackLabel(CustomizationPage.SUPPORTERS, columns = 1))
        assertEquals(R.string.wallpaper_appearance, settingsBackLabel(CustomizationPage.THEMES, columns = 1))
        assertEquals(R.string.tweak_library, settingsBackLabel(CustomizationPage.LIBRARY_TWEAK, columns = 1))
        assertEquals(R.string.folio, settingsBackLabel(CustomizationPage.GENERAL, columns = 1))
        assertNull("the list has nowhere to go back to", settingsBackLabel(CustomizationPage.OVERVIEW, columns = 1))
    }

    @Test fun `in two columns every page under another gets a Back, and a top-level page none`() {
        for (page in CustomizationPage.entries.filter { it.parent != CustomizationPage.OVERVIEW })
            assertEquals("$page", page.parent.title, settingsBackLabel(page, columns = 2))
        // The list beside it is what Back would return to, and it's already on screen.
        assertNull(settingsBackLabel(CustomizationPage.GENERAL, columns = 2))
        assertNull(settingsBackLabel(CustomizationPage.OVERVIEW, columns = 2))
    }

    @Test fun `in three columns the page Back would return to is the middle one, so there is no Back`() {
        assertNull(settingsBackLabel(CustomizationPage.SOFTWARE_UPDATE, columns = 3))
        assertNull(settingsBackLabel(CustomizationPage.TWEAK, columns = 3))
    }

    @Test fun `Back lands where the page was left, and a page opened again starts at its top`() = runBlocking {
        val scroll = SettingsScroll(CustomizationPage.OVERVIEW, 0)
        scroll.state.scrollTo(900)                                      // down the list to General
        scroll.show(CustomizationPage.GENERAL).scrollTo(240)            // and down General to Software Update
        assertEquals(0, scroll.show(CustomizationPage.SOFTWARE_UPDATE).value)
        assertEquals(240, scroll.show(CustomizationPage.GENERAL).value)  // Back
        assertEquals(900, scroll.show(CustomizationPage.OVERVIEW).value) // Back again
        assertEquals(0, scroll.show(CustomizationPage.GENERAL).value)    // opened anew from the list
    }

    @Test fun `a page reached any other way starts at its top`() = runBlocking {
        val scroll = SettingsScroll(CustomizationPage.TWEAKS, 0)
        scroll.state.scrollTo(500)
        assertEquals(0, scroll.show(CustomizationPage.HOME).value)      // from the sidebar, beside the list
        assertEquals(0, scroll.show(CustomizationPage.TWEAKS).value)    // and back to it the same way: not Back
    }
}
