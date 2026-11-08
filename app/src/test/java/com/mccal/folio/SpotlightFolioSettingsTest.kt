package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Spotlight offers Folio's own settings from the same index as Settings search, so the two can never disagree about what exists or what is held
 * back by a gate. This fails if either one grows its own copy of the gate list or its own match rule.
 */
class SpotlightFolioSettingsTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private val sheet = File(root, "app/src/main/java/com/mccal/folio/CustomizationSheet.kt").readText()
    private val spotlight = File(root, "app/src/main/java/com/mccal/folio/Spotlight.kt").readText()

    private fun body(name: String): String {
        val start = sheet.indexOf(name)
        assertTrue("$name is missing", start >= 0)
        return sheet.substring(start, minOf(sheet.length, start + 2500))
    }

    @Test fun `spotlight and settings search share one gated index`() {
        assertTrue(body("internal fun folioSettingHits(").contains("availableSettingsEntries(context)"))
        assertTrue(body("private fun SettingsSearchResults(").contains("availableSettingsEntries(context)"))
        // The gates live in one place: the gate for a held-back row appears once in the sheet.
        assertEquals(1, Regex("""it\.third != CustomizationPage\.STORAGE""").findAll(sheet).count())
    }

    @Test fun `spotlight matches with the same rule as settings search`() {
        assertTrue(body("internal fun folioSettingHits(").contains("settingsMatches("))
        assertTrue("Spotlight must not carry its own matcher", !spotlight.contains("fun settingsMatches"))
    }

    @Test fun `spotlight opens a setting through the page link Settings already reads`() {
        val main = File(root, "app/src/main/java/com/mccal/folio/MainActivity.kt").readText()
        assertTrue(main.contains("onOpenFolioSetting = { page, row -> SettingsFocus.label = row; SettingsLink.page = page; settingsRequests.intValue++ }"))
    }

    @Test fun `the keyboard Search action opens the first Folio setting before the phone's`() {
        val search = spotlight.substring(spotlight.indexOf("KeyboardActions(onSearch"), spotlight.indexOf("KeyboardActions(onSearch") + 900)
        assertTrue(search.indexOf("folioHits.isNotEmpty()") in 0 until search.indexOf("settingHits.isNotEmpty()"))
    }

    @Test fun `spotlight lists three rows and counts the rest`() {
        assertTrue(spotlight.contains("private const val FOLIO_SETTING_ROWS = 3"))
        assertTrue(spotlight.contains("R.string.more_in_settings"))
    }
}
