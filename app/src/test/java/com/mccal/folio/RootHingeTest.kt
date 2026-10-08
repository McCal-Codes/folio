package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootHingeTest {
    private fun broker(state: RootState, off: Boolean = false, safe: Boolean = false) =
        CapabilityBroker({ listOf(RootHingeProvider { state }) }, integrationOff = { off }, safeMode = { safe })

    @Test fun `root states map onto the broker's words`() {
        assertEquals(BackendStatus.AVAILABLE, RootHingeProvider { RootState.READY }.status())
        assertEquals(BackendStatus.LOST, RootHingeProvider { RootState.LOST }.status())
        assertEquals(BackendStatus.UNSUPPORTED, RootHingeProvider { RootState.NO_ROOT }.status())
        assertEquals(BackendStatus.NOT_GRANTED, RootHingeProvider { RootState.UNKNOWN }.status())
        assertEquals(BackendStatus.NOT_GRANTED, RootHingeProvider { RootState.DENIED }.status())
    }

    @Test fun `the continuous angle is a root capability and is never offered before the owner tests it`() {
        assertEquals(PrivilegeTier.ROOT, FolioCapability.HINGE_ANGLE_CONTINUOUS.minTier)
        val untested = broker(RootState.UNKNOWN).state(FolioCapability.HINGE_ANGLE_CONTINUOUS)
        assertFalse(untested.available); assertTrue(untested.setupRequired)
        assertEquals(UnavailableReason.NOT_GRANTED, untested.reason)
    }

    @Test fun `ready root is available and the source follows it`() {
        val b = broker(RootState.READY)
        assertEquals(PrivilegeTier.ROOT, b.state(FolioCapability.HINGE_ANGLE_CONTINUOUS).via)
        assertEquals(HingeSource.ROOT_HELPER, hingeSource(b))
    }

    @Test fun `the kill switch and safe mode send the fold animation back to the public sensor`() {
        assertEquals(HingeSource.PUBLIC_SENSOR, hingeSource(broker(RootState.READY, off = true)))
        assertEquals(HingeSource.PUBLIC_SENSOR, hingeSource(broker(RootState.READY, safe = true)))
        assertEquals(UnavailableReason.DISABLED_BY_USER, broker(RootState.READY, off = true).state(FolioCapability.HINGE_ANGLE_CONTINUOUS).reason)
        assertEquals(UnavailableReason.SAFE_MODE, broker(RootState.READY, safe = true).state(FolioCapability.HINGE_ANGLE_CONTINUOUS).reason)
    }

    @Test fun `losing root is a state and the source falls back at once`() {
        var state = RootState.READY
        val b = CapabilityBroker({ listOf(RootHingeProvider { state }) })
        assertEquals(HingeSource.ROOT_HELPER, hingeSource(b))
        state = RootState.LOST
        assertEquals(HingeSource.PUBLIC_SENSOR, hingeSource(b))
        assertEquals(UnavailableReason.LOST, b.state(FolioCapability.HINGE_ANGLE_CONTINUOUS).reason)
    }

    @Test fun `a call that needs root runs the standard way when root is not ready`() {
        val r = broker(RootState.DENIED).perform(FolioCapability.HINGE_ANGLE_CONTINUOUS, fallback = { "public sensor" }, op = { "root" })
        assertEquals("public sensor", r)
    }

    @Test fun `each source remembers what it learned under its own key`() {
        assertNotEquals(hingeCapabilityKey(HingeSource.PUBLIC_SENSOR), hingeCapabilityKey(HingeSource.ROOT_HELPER))
    }

    @Test fun `the helper's messages parse and anything else is ignored`() {
        assertEquals(RootHingeProtocol.Message.Ready, RootHingeProtocol.parse("R"))
        assertEquals(RootHingeProtocol.Message.Reading(HingeSample(142.5f, 123_456_789L)), RootHingeProtocol.parse("H 123456789 142.5"))
        assertEquals(RootHingeProtocol.Message.Stopped("denied"), RootHingeProtocol.parse("E denied"))
        listOf("", "H", "H 1", "H x 1.0", "H 1 x", "H -1 5", "H 1 181", "H 1 -0.5", "H 1 NaN", "H 1 Infinity", "R extra", "E", "E two words",
            "E " + "a".repeat(40), "E drop;table", "X 1 2", "H 1 2 3").forEach { assertNull("'$it'", RootHingeProtocol.parse(it)) }
    }

    @Test fun `the gate keeps readings in order and caps the rate`() {
        val gate = RootHingeProtocol.Gate()
        val ms = 1_000_000L
        assertTrue(gate.accept(HingeSample(10f, 100 * ms)))
        assertFalse("same time", gate.accept(HingeSample(11f, 100 * ms)))
        assertFalse("earlier", gate.accept(HingeSample(11f, 90 * ms)))
        assertFalse("too soon: 5 ms is faster than 100 a second", gate.accept(HingeSample(11f, 105 * ms)))
        assertTrue(gate.accept(HingeSample(12f, 110 * ms)))
    }
}
