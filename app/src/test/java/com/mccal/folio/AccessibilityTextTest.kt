package com.mccal.folio

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What TalkBack reads comes from resources, so it follows the language. A literal English description, icon label or
 * click label in code is read in English on a Korean or Chinese phone (the mix a user reported on 3 Oct 2026).
 */
class AccessibilityTextTest {
    private val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
    private val literal = Regex("""(contentDescription = |onClickLabel = |Icon\([A-Za-z.]+, )"[A-Za-z]""")
    /** Diagnostics are written for the developer, not read aloud in the UI. */
    private val exempt = setOf("Diagnostics.kt", "CrashLog.kt")

    @Test fun `no accessibility text is a literal English string`() {
        val offenders = java.io.File(root, "app/src/main/java/com/mccal/folio").walkTopDown()
            .filter { it.extension == "kt" && it.name !in exempt }
            .flatMap { file -> file.readLines().mapIndexedNotNull { i, line -> if (literal.containsMatchIn(line)) "${file.name}:${i + 1}" else null } }
            .toList()
        assertTrue("Move these to strings.xml: $offenders", offenders.isEmpty())
    }
}
