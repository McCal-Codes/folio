package com.mccal.folio

import org.junit.Assert.assertTrue
import org.junit.Test

/** Findings from the 5 Oct invariant audit: an automatic update waits in Safe Mode and shares the one-at-a-time install slot. */
class MarketAutoUpdateSafetyTest {
    private val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
    private val source = java.io.File(root, "app/src/main/java/com/mccal/folio/MarketAutoUpdate.kt").readText()
    private val apply = source.substringAfter("suspend fun applyStaged(").substringBefore("private suspend fun applyAll")

    @Test fun `staged updates are not applied in Safe Mode`() {
        assertTrue(apply.contains("SafeMode.isOn(context)"))
    }

    @Test fun `staged updates use the same one-at-a-time slot as the Get button`() {
        assertTrue(apply.contains("MarketWork.exclusive("))
    }
}
