package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
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

    @Test fun `the Root row follows the last root test, and the switch and Safe Mode still win`() {
        assertEquals(R.string.bridge_state_available, SystemBridge.rootWayLabel(RootState.READY, off = false, safe = false))
        assertEquals(R.string.bridge_state_not_tested, SystemBridge.rootWayLabel(RootState.UNKNOWN, off = false, safe = false))
        assertEquals(R.string.bridge_state_not_allowed, SystemBridge.rootWayLabel(RootState.DENIED, off = false, safe = false))
        assertEquals(R.string.bridge_state_no_root, SystemBridge.rootWayLabel(RootState.NO_ROOT, off = false, safe = false))
        assertEquals(R.string.bridge_state_unsupported, SystemBridge.rootWayLabel(RootState.NO_SENSOR, off = false, safe = false))
        assertEquals(R.string.bridge_state_lost, SystemBridge.rootWayLabel(RootState.LOST, off = false, safe = false))
        assertEquals(R.string.bridge_state_off, SystemBridge.rootWayLabel(RootState.READY, off = true, safe = false))
        assertEquals(R.string.bridge_state_safe, SystemBridge.rootWayLabel(RootState.READY, off = true, safe = true))
    }

    @Test fun `rows that say not available yet fold into one, and everything else stays in order`() {
        val labels = listOf(R.string.bridge_state_always, R.string.bridge_state_not_yet, R.string.bridge_state_not_allowed, R.string.bridge_state_not_yet)
        val (now, later) = SystemBridge.splitNotYet(labels.indices.toList()) { labels[it] }
        assertEquals(listOf(0, 2), now); assertEquals(listOf(1, 3), later)
    }

    @Test fun `when access is turned off the rows say so and are not folded away`() {
        // wayLabel gives "Turned off" or "Off in Safe Mode" for the gated rows, so they stay visible with that word.
        val gated = listOf(SystemBridge.wayLabel(off = true, safe = false), SystemBridge.wayLabel(off = false, safe = true))
        val (now, later) = SystemBridge.splitNotYet(gated) { it }
        assertEquals(gated, now); assertTrue(later.isEmpty())
        assertEquals(R.string.bridge_state_not_yet, SystemBridge.wayLabel(off = false, safe = false))
    }

    @Test fun `an advanced card says it is paused only while access is off or Safe Mode is on`() {
        assertNull(SystemBridge.pausedNote(off = false, safe = false))
        assertEquals(R.string.bridge_paused_off, SystemBridge.pausedNote(off = true, safe = false))
        assertEquals(R.string.bridge_paused_safe, SystemBridge.pausedNote(off = false, safe = true))
        assertEquals("Safe Mode is the stronger reason", R.string.bridge_paused_safe, SystemBridge.pausedNote(off = true, safe = true))
    }
}
