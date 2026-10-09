package com.mccal.folio

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt

/** The 0.6.9 motion tokens: what they are, how far they overshoot, and that the old numbers come back when the pass is off. */
class FolioMotionTest {
    @After fun reset() { FolioMotion.use(false) }

    private fun overshootPercent(damping: Float): Double =
        if (damping >= 1f) 0.0 else 100 * exp(-PI * damping / sqrt(1.0 - damping * damping))

    @Test fun `the proposed springs are the ones that were shown and agreed`() {
        assertEquals(.9f to 380f, FolioMotion.Sheet)
        assertEquals(.78f to 650f, FolioMotion.Menu)
        assertEquals(.7f to 480f, FolioMotion.Bounce)
    }

    @Test fun `no spring is livelier than the overshoot rule allows`() {
        // Damping 0.7 is about 4.6% overshoot, the most a settle that starts with the finger's speed should have.
        for ((name, spring) in listOf("Settle" to FolioMotion.Settle, "Quick" to FolioMotion.Quick, "Firm" to FolioMotion.Firm,
            "Sheet" to FolioMotion.Sheet, "Menu" to FolioMotion.Menu, "Bounce" to FolioMotion.Bounce)) {
            assertTrue("$name damps to ${spring.first}", spring.first >= .7f)
            assertTrue("$name overshoots ${overshootPercent(spring.first)}%", overshootPercent(spring.first) <= 4.7)
        }
        assertTrue("a sheet does not visibly overshoot", overshootPercent(FolioMotion.Sheet.first) < .5)
        assertEquals("a dismissal does not overshoot", 0.0, overshootPercent(FolioMotion.Firm.first), 0.0)
    }

    @Test fun `the old numbers are used until the pass is on`() {
        val old = 1f to 500f
        FolioMotion.use(false)
        assertEquals(old, FolioMotion.pick(old, FolioMotion.Sheet))
        FolioMotion.use(true)
        assertEquals(FolioMotion.Sheet, FolioMotion.pick(old, FolioMotion.Sheet))
    }

    @Test fun `the icon fan keeps its old spring until the pass is on`() {
        FolioMotion.use(false)
        assertEquals(.68f to 520f, fanSpring())
        FolioMotion.use(true)
        assertEquals(FolioMotion.Bounce, fanSpring())
    }

    @Test fun `the switch overrides the gate, and with no switch the gate decides`() {
        assertEquals(true, FolioMotion.resolve(override = null, gateOpen = true))
        assertEquals(false, FolioMotion.resolve(override = null, gateOpen = false))
        assertEquals("a tester can turn it off to compare", false, FolioMotion.resolve(override = false, gateOpen = true))
        assertEquals("or on, where the gate is shut", true, FolioMotion.resolve(override = true, gateOpen = false))
    }

    @Test fun `the motion pass is a gate that opens in 0_6_9`() {
        assertEquals("0.6.9", FeatureGate.MOTION_V2.opensIn)
    }
}
