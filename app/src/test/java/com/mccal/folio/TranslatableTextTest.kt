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
}
