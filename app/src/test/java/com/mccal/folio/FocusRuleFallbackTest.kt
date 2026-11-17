package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 0.6.9.1 crash: Android 15 on a Samsung Z Flip 3 reports API 35 but its framework has no `AutomaticZenRule.Builder`, so building a Focus
 * rule threw NoSuchMethodError (an Error, not an Exception) on the main thread, and a schedule or charging could do it with no tap.
 */
class FocusRuleFallbackTest {
    private val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
    private fun source(path: String) = java.io.File(root, "app/src/main/java/com/mccal/folio/$path").readText()

    @Test fun `a modes builder that is missing falls back to the legacy rule`() {
        val rule = FocusController.modernOrLegacy({ throw NoSuchMethodError("AutomaticZenRule\$Builder.<init>") }, { "legacy" })
        assertEquals("legacy", rule)
    }

    @Test fun `any failure in the modes chain falls back, not only a missing method`() {
        assertEquals("legacy", FocusController.modernOrLegacy({ throw NoClassDefFoundError("ZenDeviceEffects\$Builder") }, { "legacy" }))
        assertEquals("legacy", FocusController.modernOrLegacy({ throw IllegalArgumentException("bad type") }, { "legacy" }))
    }

    @Test fun `a working modes builder is used and the legacy one is not built`() {
        var legacyBuilt = false
        val rule = FocusController.modernOrLegacy({ "modern" }, { legacyBuilt = true; "legacy" })
        assertEquals("modern", rule)
        assertFalse(legacyBuilt)
    }

    @Test fun `a Do Not Disturb failure never propagates, Errors included`() {
        FocusController.guardedZen { throw NoSuchMethodError("AutomaticZenRule\$Builder.<init>") }
        FocusController.guardedZen { throw SecurityException("no access") }
        var ran = false
        FocusController.guardedZen { ran = true }
        assertTrue(ran)
    }

    @Test fun `every modes API call sits inside the fallback, and each Focus applies inside the guard`() {
        val focus = source("Focus.kt")
        val build = focus.substringAfter("private fun buildRule(").substringBefore("/** Focus schedules")
        val guarded = build.substringAfter("modernOrLegacy(")
        listOf("AutomaticZenRule.Builder(", ".setType(", ".setDeviceEffects(", "ZenDeviceEffects.Builder()", ".setManualInvocationAllowed(")
            .forEach { assertTrue("$it must be inside modernOrLegacy", guarded.contains(it)) }
        assertFalse("the modes builder must not be built before the guard", build.substringBefore("modernOrLegacy(").contains("AutomaticZenRule.Builder("))
        val apply = focus.substringAfter("fun apply(").substringBefore("internal fun guardedZen")
        assertTrue(apply.contains("guardedZen {"))
        assertTrue("buildRule runs inside the guard", apply.indexOf("guardedZen {") < apply.indexOf("buildRule("))
    }
}
