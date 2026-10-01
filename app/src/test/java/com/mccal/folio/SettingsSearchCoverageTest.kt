package com.mccal.folio

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every switch, menu and slider on a Settings page can be found by searching for its own name.
 *
 * The index in [SettingsEntries] is written by hand, so a setting added without an entry simply can't be found. This
 * fails and names it, the way LabSettingsParityTest does for the lab.
 */
class SettingsSearchCoverageTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private val sheet = File(root, "app/src/main/java/com/mccal/folio/CustomizationSheet.kt").readText()

    private fun english(res: Int) = EnglishStrings.get(res)

    private val indexed: List<Pair<String, String>> by lazy {
        SettingsEntries.map { (title, keywords) -> english(title) to keywords?.let(::english).orEmpty() }
    }

    /** Titles written as stringResource(R.string.x) on a control; ones built at run time are skipped. */
    private fun controlNames(): Set<String> =
        Regex("""\b(?:SettingsSwitch|IosMenuRow|CustomizationSlider|IosNavRow|IosActionRow|CardAction|TweakRow)\((?:Icons\.Rounded\.\w+, [\w.]+, )?stringResource\(R\.string\.(\w+)\)""").findAll(sheet).map { it.groupValues[1] }.toSet()

    /** Settings on a tweak's own page, which Settings search reaches through the tweak (searchableTweaks). */
    private val onTweakPages = setOf("enabled", "intensity", "preview", "duet_tilt")

    /** Shown only in the moment they apply (a dialog button, an undo, the Market's intro, the Set as home banner). */
    private val onDemand = setOf("cancel", "undo_last_layout_change", "undo_theme_change", "use_island", "set_as_home_app", "show_the_introduction_again", "duet_reset_style", "add_widget_to_this_page_2")

    @Test fun `every setting can be found by its own name`() {
        val xml = File(root, "app/src/main/res/values/strings.xml").readText()
        val missing = (controlNames() - onTweakPages - onDemand).mapNotNull { name ->
            val text = Regex("""<string name="$name"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)
                ?.replace("\\'", "'")?.replace("&amp;", "&") ?: return@mapNotNull null
            if (indexed.any { (title, keywords) -> settingsMatches(text, title, keywords) }) null else "$name: $text"
        }
        assertTrue("Settings search can't find: ${missing.sorted()}", missing.isEmpty())
    }

    @Test fun `a lone symbol in a query doesn't stop a page's own name from matching`() {
        assertTrue(settingsMatches("Fold & Displays", "Fold & Displays", ""))
        assertTrue(settingsMatches("fold - displays", "Fold & Displays", ""))
        assertTrue(!settingsMatches("&", "Fold", ""))
    }
}
