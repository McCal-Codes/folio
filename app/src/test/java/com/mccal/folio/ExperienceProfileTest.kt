package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The profiles are named bundles of existing settings, and today's defaults are the Folio one. */
class ExperienceProfileTest {
    @Test fun `a fresh state is the Folio profile, so skipping setup changes nothing`() {
        assertEquals(ExperienceProfile.FOLIO, LauncherState().profile())
        assertEquals(LauncherState(), LauncherState().withProfile(ExperienceProfile.FOLIO))
    }

    @Test fun `Android style turns off the island and Folio's panels and sends a swipe down to the shade`() {
        val android = LauncherState().withProfile(ExperienceProfile.ANDROID_STYLE)
        assertEquals(false, android.island)
        assertEquals(false, android.folioPanels)
        assertEquals("NOTIFICATIONS", android.swipeDownHome)
        assertEquals(ExperienceProfile.ANDROID_STYLE, android.profile())
    }

    @Test fun `choosing a profile leaves every other setting alone`() {
        val mine = LauncherState(haptics = false, minPages = 3, searchEngine = "DUCKDUCKGO")
        val after = mine.withProfile(ExperienceProfile.ANDROID_STYLE)
        assertEquals(mine.copy(island = false, folioPanels = false, swipeDownHome = "NOTIFICATIONS"), after)
    }

    @Test fun `any other mix is Custom, and Custom is never applied`() {
        val mixed = LauncherState(island = false)
        assertEquals(ExperienceProfile.CUSTOM, mixed.profile())
        assertEquals(mixed, mixed.withProfile(ExperienceProfile.CUSTOM))
    }

    @Test fun `switching there and back is lossless`() {
        val there = LauncherState().withProfile(ExperienceProfile.ANDROID_STYLE)
        assertNotEquals(LauncherState(), there)
        assertEquals(LauncherState(), there.withProfile(ExperienceProfile.FOLIO))
    }
}
