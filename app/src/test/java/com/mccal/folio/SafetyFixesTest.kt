package com.mccal.folio

import org.junit.Assert.assertTrue
import org.junit.Test

/** Findings from the 5 Oct invariant audit: Safe Mode pauses background Market work, and a failed action says why. */
class SafetyFixesTest {
    private val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
    private fun source(path: String) = java.io.File(root, "app/src/main/java/com/mccal/folio/$path").readText()

    @Test fun `the background Market refresh checks Safe Mode before it goes online`() {
        val job = source("MarketRefreshJob.kt")
        assertTrue(job.indexOf("SafeMode.isOn") in 0 until job.indexOf("refreshSources(applicationContext)"))
    }

    @Test fun `actions that need the accessibility service or Do Not Disturb access say so`() {
        // The refusals are said by the action runner, which every action goes through.
        val actions = source("ActionRegistry.kt")
        assertTrue(actions.contains("needs_accessibility_service"))
        assertTrue(actions.contains("needs_dnd_access"))
        assertTrue(source("TopPanels.kt").contains("needs_accessibility_service"))
    }
}
