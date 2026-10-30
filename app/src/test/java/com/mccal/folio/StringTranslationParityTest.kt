package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Korean and Chinese carry every English string, so lint's MissingTranslation never has to be the first to say so. */
class StringTranslationParityTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }

    private fun names(dir: String, translatable: Boolean = false): Set<String> =
        Regex("""<string\s+name="([^"]+)"([^>]*)>""").findAll(File(root, "app/src/main/res/$dir/strings.xml").readText())
            .filter { !translatable || "translatable=\"false\"" !in it.groupValues[2] }.map { it.groupValues[1] }.toSet()

    /** Missing today. This list should only shrink: translate a string, then delete its name here. */
    private val knownMissing = mapOf(
        "values-b+ko" to setOf<String>(),
        "values-b+zh+Hans" to setOf<String>(),
    )

    @Test
    fun `translations carry every English string except the known gaps`() {
        val english = names("values", translatable = true)
        for ((dir, allowed) in knownMissing) {
            val missing = english - names(dir)
            assertEquals("$dir is missing strings; translate them (do not grow the allow-list): ${missing - allowed}", emptySet<String>(), missing - allowed)
            assertTrue("$dir allow-list names now translated; delete them: ${allowed - missing}", (allowed - missing).isEmpty())
        }
    }

    @Test
    fun `translations have no string English lacks`() {
        val english = names("values")
        for (dir in knownMissing.keys) assertEquals("$dir has strings not in values/strings.xml", emptySet<String>(), names(dir) - english)
    }
}