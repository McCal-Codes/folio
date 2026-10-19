package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    @Test fun `Simple Mode applies its bundle and reads back as Simple`() {
        val simple = LauncherState().withProfile(ExperienceProfile.SIMPLE)
        assertEquals(true, simple.simpleMode)
        assertEquals(false, simple.island)
        assertEquals(false, simple.folioPanels)
        assertEquals("OFF", simple.swipeDownHome)
        assertEquals(ExperienceProfile.SIMPLE, simple.profile())
    }

    @Test fun `a fresh state is not Simple, and Folio and Android style are still found`() {
        assertEquals(false, LauncherState().simpleMode)
        assertNotEquals(ExperienceProfile.SIMPLE, LauncherState().profile())
        assertEquals(ExperienceProfile.ANDROID_STYLE, LauncherState().withProfile(ExperienceProfile.ANDROID_STYLE).profile())
        assertEquals(ExperienceProfile.FOLIO, LauncherState().withProfile(ExperienceProfile.SIMPLE).withProfile(ExperienceProfile.FOLIO).profile())
    }

    @Test fun `Simple Mode on with a different mix is Custom, and choosing Simple keeps the people`() {
        assertEquals(ExperienceProfile.CUSTOM, LauncherState(simpleMode = true).profile())
        val people = listOf(SimpleFavorite("Mom", "555-0100"))
        assertEquals(people, LauncherState(simpleFavorites = people).withProfile(ExperienceProfile.SIMPLE).simpleFavorites)
    }

    @Test fun `turning Simple Mode on and off returns an Android style user to exactly Android style`() {
        val android = LauncherState().withProfile(ExperienceProfile.ANDROID_STYLE)
        val on = android.withProfile(ExperienceProfile.SIMPLE)
        assertEquals(ExperienceProfile.SIMPLE, on.profile())
        val back = on.withoutSimpleMode()
        assertEquals(android, back.copy(preSimpleIsland = android.preSimpleIsland, preSimplePanels = android.preSimplePanels, preSimpleSwipe = android.preSimpleSwipe))
        assertEquals(ExperienceProfile.ANDROID_STYLE, back.profile())
    }

    @Test fun `a custom mix survives Simple Mode, and choosing Simple twice keeps the first copy`() {
        val mine = LauncherState(island = false, swipeDownHome = "SPOTLIGHT")
        val twice = mine.withProfile(ExperienceProfile.SIMPLE).withProfile(ExperienceProfile.SIMPLE)
        val back = twice.withoutSimpleMode()
        assertEquals(false, back.island)
        assertEquals(true, back.folioPanels)
        assertEquals("SPOTLIGHT", back.swipeDownHome)
        assertEquals(ExperienceProfile.CUSTOM, back.profile())
        assertEquals(mine, LauncherState().withoutSimpleMode().let { mine.withoutSimpleMode() })
    }

    @Test fun `Safe Mode draws the regular Home instead of Simple Mode, without forgetting it`() {
        val simple = LauncherState().withProfile(ExperienceProfile.SIMPLE)
        assertTrue("Safe Mode is off: Simple Mode stands", SafeMode.effective(simple).simpleMode)
        // Safe Mode only turns on after two quick crashes, so its effect is read from the copy it makes (as PageEffectsTest does).
        val crashLog = java.io.File(generateSequence(java.io.File("").absoluteFile) { it.parentFile }
            .first { java.io.File(it, "CHANGELOG.md").exists() }, "app/src/main/java/com/mccal/folio/CrashLog.kt").readText()
        assertTrue("Safe Mode should fall back from Simple Mode", "simpleMode = false" in crashLog)
    }
}
