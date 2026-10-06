package com.mccal.folio

import org.junit.Assert.assertTrue
import org.junit.Test

/** Two review findings on the Focus and island fixes: the no-Home path records a dismissal, and a destroyed island service stops watching. */
class FocusReviewFixesTest {
    private val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
    private fun source(path: String) = java.io.File(root, "app/src/main/java/com/mccal/folio/$path").readText()

    @Test fun `turning a scheduled Focus off by hand with Home not running is still a dismissal`() {
        val noHome = source("Focus.kt").substringAfter("fun setActive(").substringBefore("fun toggle(")
        assertTrue(noHome.indexOf("FocusDismissals.record(") in 0 until noHome.indexOf("apply()"))
        assertTrue(noHome.contains("if (byHand)"))
    }

    @Test fun `the island service refuses to register media callbacks once it is destroyed`() {
        val island = source("Island.kt")
        assertTrue(island.substringAfter("override fun onDestroy()").take(120).contains("destroyed = true"))
        val watch = island.substringAfter("private fun watch(").substringBefore("private fun clearControllerCallbacks")
        assertTrue(watch.contains("if (destroyed) return"))
    }
}
