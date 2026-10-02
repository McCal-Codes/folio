package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

/** StandBy on any phone: the fold's ways only with a hinge, the charger's once its gate is open. */
class StandByPhonesTest {
    @Test fun `a phone without a fold is offered charging on its side only`() {
        assertEquals(listOf(StandByWay.CHARGING), standByWaysOffered(hinge = false, chargingOpen = true))
    }

    @Test fun `a fold is offered all three, in Settings order`() {
        assertEquals(listOf(StandByWay.HALF_OPEN, StandByWay.CHARGING, StandByWay.TENT), standByWaysOffered(hinge = true, chargingOpen = true))
    }

    @Test fun `before the gate opens, a fold keeps the laptop pose and a phone without one has nothing to show`() {
        assertEquals(listOf(StandByWay.HALF_OPEN), standByWaysOffered(hinge = true, chargingOpen = false))
        assertEquals(emptyList<StandByWay>(), standByWaysOffered(hinge = false, chargingOpen = false))
    }
}
