package com.mccal.folio

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The brief messages Folio shows — a toast, or the island's notice — are read by people just like the rest of the app,
 * so they have to come from strings.xml. They sit far from the settings screens that were translated in batches, which
 * is exactly how an English sentence survives in the code unnoticed.
 */
class UntranslatedNoticeTest {
    private val sources: List<File> = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "CHANGELOG.md").exists() }
        .let { File(it, "app/src/main/java/com/mccal/folio") }.listFiles()?.filter { it.extension == "kt" }.orEmpty()

    /**
     * Text() given an English sentence. Numbers, names and formats are what's left after the sentences moved to
     * strings.xml — a page count, a widget's size, the version beside Folio's name — so they're named here rather
     * than left to a pattern that would quietly let a new sentence through.
     */
    @Test fun `no screen text is written in English in the code`() {
        val sentence = Regex("""(?<![A-Za-z])Text\(\s*"[^"$]*[A-Za-z]{2}[^"]*"""")
        val named = setOf("CustomizationSheet.kt")  // "Folio <version>", a name and a number
        val bad = sources.filter { it.name !in named && sentence.containsMatchIn(it.readText()) }.map { it.name }
        assertTrue("Screen text in $bad is an English literal; use stringResource so it can be translated.", bad.isEmpty())
    }

    @Test fun `no toast or notice is written in English in the code`() {
        // A literal as the message: Toast.makeText(context, "…" or IslandEvents.notice(context, "…".
        val literal = Regex("""(Toast\.makeText|notice)\([^,()]+,\s*"""")
        val bad = sources.filter { literal.containsMatchIn(it.readText()) }.map { it.name }
        assertTrue("A message in $bad is an English literal; pass a string resource so it can be translated.", bad.isEmpty())
    }
}
