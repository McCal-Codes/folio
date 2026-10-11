package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Android drops a plain space at either end of a string resource, so text joined in code from two strings
 * ("...over other apps. " + "It can't...") came out as "apps.It can't". A space meant to stay is written  .
 */
class StringEdgeSpacesTest {
    private val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }

    private fun edgeSpaces(xml: String) = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        .findAll(xml).filter { m -> m.groupValues[2].let { it.isNotEmpty() && (it.first() == ' ' || it.last() == ' ') } }
        .map { it.groupValues[1] }.toList()

    @Test fun `no string resource starts or ends with a space Android would drop`() {
        val found = java.io.File(root, "app/src/main/res").listFiles { f -> f.name.startsWith("values") }.orEmpty()
            .mapNotNull { java.io.File(it, "strings.xml").takeIf(java.io.File::isFile) }
            .flatMap { file -> edgeSpaces(file.readText()).map { "${file.parentFile.name}/$it" } }
        assertEquals("Write a space that must stay as \\u0020", emptyList<String>(), found)
    }

    @Test fun `an escaped space counts as kept and a plain one does not`() {
        val kept = "\\" + "u0020" // spelled out: Kotlin would turn a literal one into a space before the test runs
        assertEquals(listOf("a"), edgeSpaces("<string name=\"a\">Ends here. </string><string name=\"b\">Ends here.$kept</string>"))
    }

    @Test fun `the gestures note and the backup note keep their space`() {
        assertEquals(" ", EnglishStrings.get(R.string.folio_uses_it_to_open_notification_cente).takeLast(1))
        assertEquals(" ", EnglishStrings.get(R.string.save_the_current_home_layout_folders_wid).takeLast(1))
        assertEquals(" ", EnglishStrings.get(R.string.save_the_current_home_layout_folders_wid_2).takeLast(1))
    }
}
