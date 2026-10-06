package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SystemBridgeTest {
    private fun state(available: Boolean, reason: UnavailableReason) = CapabilityState(FolioCapability.SYSTEM_ACTIONS, available, null, reason)

    @Test fun `every reason has words of its own except none`() {
        val labels = UnavailableReason.entries.filter { it != UnavailableReason.NONE }.map { SystemBridge.stateLabel(state(false, it)) }
        assertEquals(labels.size, labels.toSet().size)
        assertEquals(R.string.bridge_state_available, SystemBridge.stateLabel(state(true, UnavailableReason.NONE)))
    }

    @Test fun `ways above standard say safe mode before off before not yet`() {
        assertEquals(R.string.bridge_state_safe, SystemBridge.wayLabel(off = true, safe = true))
        assertEquals(R.string.bridge_state_off, SystemBridge.wayLabel(off = true, safe = false))
        assertEquals(R.string.bridge_state_not_yet, SystemBridge.wayLabel(off = false, safe = false))
    }

    @Test fun `every capability and outcome has a name`() {
        FolioCapability.entries.forEach { assertNotEquals(0, SystemBridge.capabilityName(it)) }
        AuditEvent.Outcome.entries.forEach { assertNotEquals(0, SystemBridge.outcomeLabel(it)) }
    }
}
