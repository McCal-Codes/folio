package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerVocabularyTest {
    @Test fun `tiers follow ADR 0008 and only A0 to A2 are in use`() {
        assertEquals(listOf("A0", "A1", "A2", "A3", "A4", "A5"), PrivilegeTier.entries.map { it.code })
        assertEquals(listOf(true, true, true, false, false, false), PrivilegeTier.entries.map { it.inUse })
    }

    @Test fun `every capability tier maps onto a privilege tier with the same code`() {
        CapabilityTier.entries.forEach { assertEquals(it.code, it.privilege.code) }
        assertTrue(CapabilityTier.entries.all { it.privilege.inUse })
    }

    @Test fun `risk is ordered and says what needs care`() {
        assertTrue(OperationRisk.OBSERVE < OperationRisk.CRITICAL)
        assertEquals(listOf(false, false, false, true, true, true), OperationRisk.entries.map { it.confirmFirst })
        assertEquals(listOf(OperationRisk.CRITICAL), OperationRisk.entries.filter { it.timedKeep })
        assertFalse(OperationRisk.OBSERVE.needsRollback)
        assertTrue(OperationRisk.entries.filter { it != OperationRisk.OBSERVE }.all { it.needsRollback })
    }

    @Test fun `an unsupported or blocked operation never runs and experimental runs with a warning`() {
        assertEquals(listOf(true, true, true, false, false), CapabilityStatus.entries.map { it.runs })
        assertEquals(listOf(CapabilityStatus.EXPERIMENTAL), CapabilityStatus.entries.filter { it.warns })
    }

    @Test fun `a backend is available, not granted or unsupported`() {
        assertEquals(BackendStatus.AVAILABLE, BackendStatus.of(granted = true))
        assertEquals(BackendStatus.NOT_GRANTED, BackendStatus.of(granted = false))
        assertEquals(BackendStatus.UNSUPPORTED, BackendStatus.of(granted = true, supported = false))
    }

    @Test fun `the rows carry their status and risk`() {
        val rows = Capabilities.rows(notificationAccess = false, accessibilityConnected = true)
        assertEquals(listOf(BackendStatus.AVAILABLE, BackendStatus.NOT_GRANTED, BackendStatus.AVAILABLE), rows.map { it.status })
        assertEquals(listOf(OperationRisk.REVERSIBLE, OperationRisk.STATEFUL, OperationRisk.REVERSIBLE), rows.map { it.risk })
    }
}
