package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitiesInspectorTest {
    @Test fun `Standard is always on and the others follow what was allowed`() {
        val off = Capabilities.rows(notificationAccess = false, accessibilityConnected = false)
        assertEquals(listOf(true, false, false), off.map { it.on })
        assertEquals(listOf(CapabilityTier.NOTIFICATIONS, CapabilityTier.ACCESSIBILITY), Capabilities.needsPermission(off))
        val on = Capabilities.rows(true, true)
        assertTrue(Capabilities.needsPermission(on).isEmpty())
        assertEquals(listOf("A0", "A1", "A2"), on.map { it.tier.code })
    }

    @Test fun `the trail is read newest first and failures are marked`() {
        val trail = """
            10:00:01.000  Folio started
            10:00:02.500  Market refresh failed: IOException
            10:00:03.100  Work turned on
        """.trimIndent()
        val e = Inspector.entries(trail)
        assertEquals(listOf("Work turned on", "Market refresh failed: IOException", "Folio started"), e.map { it.text })
        assertEquals("10:00:03.100", e.first().time)
        assertEquals(listOf(false, true, false), e.map { it.failed })
    }

    @Test fun `failed-only keeps just the failures`() {
        val e = Inspector.entries("10:00:01.000  a\n10:00:02.000  b failed: X (×3)\n")
        assertEquals(1, Inspector.filter(e, failedOnly = true).size)
        assertEquals(2, Inspector.filter(e, failedOnly = false).size)
    }

    @Test fun `a line that is not a trail line is kept without a time`() {
        val e = Inspector.entries("something odd")
        assertEquals("", e.single().time)
        assertEquals("something odd", e.single().text)
        assertFalse(e.single().failed)
    }

    @Test fun `an empty trail has no entries`() {
        assertTrue(Inspector.entries("").isEmpty())
    }
}
