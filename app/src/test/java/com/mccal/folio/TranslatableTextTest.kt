package com.mccal.folio

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Text people read has to reach translators whole. A sentence half in strings.xml and half in Kotlin can't be
 * translated properly — moving strings out of the code in batches makes exactly that mistake easy, so it's caught here.
 */
class TranslatableTextTest {
    private val sources: List<File> = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "CHANGELOG.md").exists() }
        .let { File(it, "app/src/main/java/com/mccal/folio") }.listFiles()?.filter { it.extension == "kt" }.orEmpty()

    @Test fun `no sentence is half a resource and half a literal`() {
        val glued = Regex("""R\.string\.\w+\)\s*\+\s*"""")
        val bad = sources.filter { glued.containsMatchIn(it.readText()) }.map { it.name }
        assertTrue("A string resource is glued to a literal in $bad. Put the whole sentence in strings.xml, or use a " +
            "placeholder like %1\$s so the translation can reorder it.", bad.isEmpty())
    }

    @Test fun `strings meant for people don't hide in enum constructors`() {
        // An enum is built before any Context exists, so an English label written there can never be translated.
        // The exceptions hold names that stay as they are in every language: search engines, assistants, and the
        // iPhone apps Arrange Like iPhone matches by name.
        val namesNotTranslated = setOf("WebSearch.kt", "AssistPickerActivity.kt", "IPhoneHome.kt", "FolioVoiceInteraction.kt")
        val enumLabel = Regex("""enum class \w+\([^)]*val (label|title): String""")
        val bad = sources.filter { it.name !in namesNotTranslated && enumLabel.containsMatchIn(it.readText()) }.map { it.name }
        assertTrue("An enum in $bad holds a label as String; hold a string resource id instead.", bad.isEmpty())
    }

    @Test fun `dates and on-off states come from the translations`() {
        // A date pattern written in Kotlin keeps English word order in every language: Korean read "금요일, 10월 2"
        // instead of "10월 2일 금요일". The day and month names alone are fine; it's a month and a day together.
        val pattern = Regex(""""([^"\n]*MMM[^"\n]*)"""")  // any text with a month name in it, chosen by an if or not
        val dayOfMonth = Regex("""(^|[^d])d($|[^d])""")
        val dates = sources.flatMap { file ->
            pattern.findAll(file.readText()).map { it.groupValues[1] }
                .filter { "MMM" in it && dayOfMonth.containsMatchIn(it) }.map { "${file.name}: $it" }.toList()
        }
        assertTrue("Date patterns in code: $dates. Use a pattern from strings.xml, like R.string.eeee_mmmm_d, so " +
            "each language puts the month and day in its own order.", dates.isEmpty())
        val onOff = sources.filter { """"On" else "Off"""" in it.readText() }.map { it.name }
        assertTrue("\"On\"/\"Off\" written in $onOff stays English. Use R.string.state_on and R.string.state_off.", onOff.isEmpty())
    }
}
