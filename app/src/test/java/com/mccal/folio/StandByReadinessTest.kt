package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandByReadinessTest {
    private val bools = listOf(false, true)

    /** Every switch and signal combination the rules can see, so the diagnostic can never drift from [standByWay]. */
    private fun allCases(): Sequence<Pair<StandByWays, StandBySignals>> = sequence {
        for (h in bools) for (c in bools) for (t in bools) for (o in bools) {
            val ways = StandByWays(halfOpen = h, charging = c, tent = t, offCharger = o)
            for (halfOpen in bools) for (plugged in bools) for (sideways in bools) for (still in bools)
                for (cover in bools) for (tent in listOf(null, false, true)) for (hinge in bools) for (battery in listOf(null, 10, 20, 80))
                    yield(ways to StandBySignals(halfOpen, plugged, sideways, still, hinge, cover, tent, battery))
        }
    }

    @Test
    fun `a route is ready exactly when standByWay would start StandBy`() {
        for ((ways, signals) in allCases()) {
            val ready = standByReadiness(ways, signals).any { it.ready }
            assertEquals("$ways $signals", standByWay(ways, signals) != null, ready)
        }
    }

    @Test
    fun `only routes that are switched on are listed`() {
        assertTrue(standByReadiness(StandByWays(false, false, false), StandBySignals()).isEmpty())
        val listed = standByReadiness(StandByWays(halfOpen = true, charging = false, tent = true), StandBySignals()).map { it.route }
        assertEquals(listOf(StandByRoute.HALF_OPEN, StandByRoute.TENT), listed)
    }

    @Test
    fun `a charger that is not plugged in is the first thing charging StandBy names`() {
        val ways = StandByWays(halfOpen = false, charging = true, tent = false)
        val still = StandBySignals(plugged = false, sideways = true, still = true)
        assertEquals(StandByRequirement.PLUGGED, standByNextStep(standByReadiness(ways, still)))
    }

    @Test
    fun `off the charger asks for an unplugged phone and enough battery`() {
        val ways = StandByWays(halfOpen = false, charging = false, tent = false, offCharger = true)
        val plugged = standByReadiness(ways, StandBySignals(plugged = true, sideways = true, still = true, batteryLevel = 90))
        assertEquals(listOf(StandByRequirement.UNPLUGGED), plugged.single().missing)
        val low = standByReadiness(ways, StandBySignals(plugged = false, sideways = true, still = true, batteryLevel = 19))
        assertEquals(listOf(StandByRequirement.BATTERY), low.single().missing)
    }

    @Test
    fun `no next step when a route is ready or nothing is switched on`() {
        val ways = StandByWays(halfOpen = true, charging = false, tent = false)
        assertNull(standByNextStep(standByReadiness(ways, StandBySignals(halfOpen = true))))
        assertNull(standByNextStep(emptyList()))
    }

    @Test
    fun `the route with the fewest missing requirements names the next step`() {
        val ways = StandByWays(halfOpen = true, charging = true, tent = false)
        // Plugged and sideways but still moving: charging is one step away, half-open is one step away too; half-open is listed first.
        val signals = StandBySignals(plugged = true, sideways = true, still = false)
        assertEquals(StandByRequirement.HALF_OPEN, standByNextStep(standByReadiness(ways, signals)))
        // Half-open is not met and charging needs only stillness once the phone is not half-open: ties go to the earlier route, so use charging alone.
        val chargingOnly = StandByWays(halfOpen = false, charging = true, tent = false)
        assertEquals(StandByRequirement.STILL, standByNextStep(standByReadiness(chargingOnly, signals)))
    }
}
