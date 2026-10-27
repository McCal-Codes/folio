package com.mccal.folio

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Folio's panels over other apps draw in a service window with no Back dispatcher, where a plain BackHandler throws. The
 * panel files use SafeBackHandler or OverlayBackHandler, never BackHandler directly.
 */
class PanelBackHandlerTest {
    private val plain = Regex("(?<![A-Za-z])BackHandler\\(")

    @Test fun panelsNeverUseAPlainBackHandler() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
        val panels = listOf("TopPanels.kt")
        val offenders = panels.flatMap { name ->
            File(root, "app/src/main/java/com/mccal/folio/$name").readLines().withIndex()
                .filter { (_, line) -> plain.containsMatchIn(line) && !line.trimStart().startsWith("//") && !line.trimStart().startsWith("*") }
                .map { (i, _) -> "$name:${i + 1}" }
        }
        assertTrue("Plain BackHandler in a panel that can draw over other apps: $offenders", offenders.isEmpty())
    }
}
