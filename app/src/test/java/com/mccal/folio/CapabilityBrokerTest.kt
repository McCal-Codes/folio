package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeProvider(
    override val tier: PrivilegeTier,
    override val capabilities: Set<FolioCapability>,
    var next: () -> BackendStatus,
) : CapabilityProvider {
    override fun status() = next()
}

private fun fake(tier: PrivilegeTier, vararg c: FolioCapability, status: BackendStatus = BackendStatus.AVAILABLE) =
    FakeProvider(tier, c.toSet()) { status }

class CapabilityBrokerTest {
    @Test fun `a capability with no provider is unavailable and says why`() {
        val s = CapabilityBroker({ emptyList() }).state(FolioCapability.SYSTEM_STATUS_MODULES)
        assertFalse(s.available); assertNull(s.via)
        assertEquals(UnavailableReason.NO_PROVIDER, s.reason)
        assertFalse(s.setupRequired)
    }

    @Test fun `the lowest tier that can do it wins`() {
        val broker = CapabilityBroker({
            listOf(fake(PrivilegeTier.ROOT, FolioCapability.HINGE_ANGLE), fake(PrivilegeTier.STANDARD, FolioCapability.HINGE_ANGLE))
        })
        assertEquals(PrivilegeTier.STANDARD, broker.state(FolioCapability.HINGE_ANGLE).via)
    }

    @Test fun `a higher tier steps in when the lower one is not granted`() {
        val broker = CapabilityBroker({
            listOf(
                fake(PrivilegeTier.STANDARD, FolioCapability.HINGE_ANGLE, status = BackendStatus.UNSUPPORTED),
                fake(PrivilegeTier.SHIZUKU, FolioCapability.HINGE_ANGLE),
            )
        })
        assertEquals(PrivilegeTier.SHIZUKU, broker.state(FolioCapability.HINGE_ANGLE).via)
    }

    @Test fun `not granted is a setup step the person can take`() {
        val s = CapabilityBroker({ listOf(fake(PrivilegeTier.NOTIFICATIONS, FolioCapability.NOTIFICATIONS_READ, status = BackendStatus.NOT_GRANTED)) })
            .state(FolioCapability.NOTIFICATIONS_READ)
        assertEquals(UnavailableReason.NOT_GRANTED, s.reason)
        assertTrue(s.setupRequired)
    }

    @Test fun `a fixable reason beats unsupported when several providers disagree`() {
        val s = CapabilityBroker({
            listOf(
                fake(PrivilegeTier.STANDARD, FolioCapability.HINGE_ANGLE, status = BackendStatus.UNSUPPORTED),
                fake(PrivilegeTier.SHIZUKU, FolioCapability.HINGE_ANGLE, status = BackendStatus.NOT_GRANTED),
            )
        }).state(FolioCapability.HINGE_ANGLE)
        assertEquals(UnavailableReason.NOT_GRANTED, s.reason)
    }

    @Test fun `every capability has an id that round trips and a unique one`() {
        assertEquals(FolioCapability.entries.size, FolioCapability.entries.map { it.id }.toSet().size)
        FolioCapability.entries.forEach { assertEquals(it, FolioCapability.fromId(it.id)) }
        assertNull(FolioCapability.fromId("nope"))
    }

    @Test fun `states lists every capability`() {
        assertEquals(FolioCapability.entries, CapabilityBroker({ emptyList() }).states().map { it.capability })
    }
}

/** A privileged call that breaks, or is revoked mid-run, ends in the standard behavior. */
class BridgeUnavailableFallbackTest {
    @Test fun `perform uses the fallback when nothing is available`() {
        val events = mutableListOf<AuditEvent>()
        val r = CapabilityBroker({ emptyList() }, audit = events::add)
            .perform(FolioCapability.SYSTEM_ACTIONS, fallback = { "standard" }, op = { "privileged" })
        assertEquals("standard", r)
        assertEquals(AuditEvent.Outcome.FELL_BACK, events.single().outcome)
    }

    @Test fun `perform runs on the chosen tier`() {
        val events = mutableListOf<AuditEvent>()
        val r = CapabilityBroker({ listOf(fake(PrivilegeTier.SHIZUKU, FolioCapability.SYSTEM_ACTIONS)) }, audit = events::add)
            .perform(FolioCapability.SYSTEM_ACTIONS, fallback = { "standard" }, op = { "ran on ${it.code}" })
        assertEquals("ran on A3", r)
        assertEquals(AuditEvent(FolioCapability.SYSTEM_ACTIONS, PrivilegeTier.SHIZUKU, AuditEvent.Outcome.RAN), events.single())
    }

    @Test fun `a throwing operation never reaches the caller`() {
        val events = mutableListOf<AuditEvent>()
        val r = CapabilityBroker({ listOf(fake(PrivilegeTier.SHIZUKU, FolioCapability.SYSTEM_ACTIONS)) }, audit = events::add)
            .perform(FolioCapability.SYSTEM_ACTIONS, fallback = { "standard" }, op = { error("binder died") })
        assertEquals("standard", r)
        assertEquals(AuditEvent.Outcome.FAILED_THEN_FELL_BACK, events.single().outcome)
    }

    @Test fun `a provider whose status throws counts as lost`() {
        val p = FakeProvider(PrivilegeTier.SHIZUKU, setOf(FolioCapability.SYSTEM_ACTIONS)) { throw SecurityException("gone") }
        val s = CapabilityBroker({ listOf(p) }).state(FolioCapability.SYSTEM_ACTIONS)
        assertEquals(UnavailableReason.LOST, s.reason)
        assertTrue(s.setupRequired)
    }
}

class PrivilegeRevokedTest {
    @Test fun `revoking access mid-session turns a capability into lost then back`() {
        var granted = true
        val p = FakeProvider(PrivilegeTier.SHIZUKU, setOf(FolioCapability.SYSTEM_ACTIONS)) {
            if (granted) BackendStatus.AVAILABLE else BackendStatus.LOST
        }
        val broker = CapabilityBroker({ listOf(p) })
        assertTrue(broker.state(FolioCapability.SYSTEM_ACTIONS).available)
        granted = false
        assertEquals(UnavailableReason.LOST, broker.state(FolioCapability.SYSTEM_ACTIONS).reason)
        granted = true
        assertTrue(broker.state(FolioCapability.SYSTEM_ACTIONS).available)
    }

    @Test fun `standard tiers do not depend on the bridge at all`() {
        val broker = CapabilityBroker({ listOf(fake(PrivilegeTier.ACCESSIBILITY, FolioCapability.SHADE_OPEN)) }, integrationOff = { true }, safeMode = { true })
        assertTrue(broker.state(FolioCapability.SHADE_OPEN).available)
    }
}

class SafeModeBridgeDisableTest {
    private val shizuku = { listOf(fake(PrivilegeTier.SHIZUKU, FolioCapability.SYSTEM_ACTIONS)) }

    @Test fun `the kill switch turns off every tier above accessibility`() {
        val s = CapabilityBroker(shizuku, integrationOff = { true }).state(FolioCapability.SYSTEM_ACTIONS)
        assertFalse(s.available)
        assertEquals(UnavailableReason.DISABLED_BY_USER, s.reason)
    }

    @Test fun `safe mode does the same and is named as such`() {
        val s = CapabilityBroker(shizuku, safeMode = { true }, integrationOff = { true }).state(FolioCapability.SYSTEM_ACTIONS)
        assertEquals(UnavailableReason.SAFE_MODE, s.reason)
    }

    @Test fun `turning the switch back on restores the capability`() {
        var off = true
        val broker = CapabilityBroker(shizuku, integrationOff = { off })
        assertFalse(broker.state(FolioCapability.SYSTEM_ACTIONS).available)
        off = false
        assertTrue(broker.state(FolioCapability.SYSTEM_ACTIONS).available)
    }
}

class BridgeHandshakeTest {
    private fun hello(protocol: Int = 1, minFolio: Int = 1, ids: Set<String> = setOf("system.actions")) =
        BridgeHello(protocol, minFolio, "0.1.0", ids, androidApi = 36)

    @Test fun `a matching bridge is compatible`() {
        assertEquals(HandshakeResult.Compatible(setOf(FolioCapability.SYSTEM_ACTIONS)), BridgeProtocol.negotiate(hello()))
    }

    @Test fun `a bridge older than Folio accepts is refused`() {
        assertEquals(HandshakeResult.BridgeTooOld, BridgeProtocol.negotiate(hello(protocol = BridgeProtocol.OLDEST_ACCEPTED - 1)))
    }

    @Test fun `a bridge that needs a newer Folio is refused`() {
        assertEquals(HandshakeResult.FolioTooOld, BridgeProtocol.negotiate(hello(protocol = 9, minFolio = BridgeProtocol.CURRENT + 1)))
    }

    @Test fun `a newer bridge that still speaks our protocol works and unknown capabilities are ignored`() {
        val r = BridgeProtocol.negotiate(hello(protocol = 5, minFolio = 1, ids = setOf("system.actions", "future.thing")))
        assertEquals(HandshakeResult.Compatible(setOf(FolioCapability.SYSTEM_ACTIONS)), r)
    }

    @Test fun `a bridge offering nothing we know is a state not a crash`() {
        assertEquals(HandshakeResult.NothingUsable, BridgeProtocol.negotiate(hello(ids = setOf("future.thing"))))
    }
}
