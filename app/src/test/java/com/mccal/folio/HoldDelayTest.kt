package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Touch and hold on Home: Standard is Android's delay untouched, the others scale it within a sane range. */
class HoldDelayTest {
    @Test fun `standard is the platform delay, whatever it is`() {
        for (base in listOf(100L, 400L, 500L, 1500L)) assertEquals(base, HoldDelay.STANDARD.millis(base))
    }

    @Test fun `shorter and longer scale it`() {
        assertEquals(350L, HoldDelay.SHORTER.millis(500))
        assertEquals(800L, HoldDelay.LONGER.millis(500))
    }

    @Test fun `a hold never fires like a tap or takes over a second`() {
        assertEquals(HoldDelay.MIN_MS, HoldDelay.SHORTER.millis(100))
        assertEquals(HoldDelay.MAX_MS, HoldDelay.LONGER.millis(2000))
        assertTrue(HoldDelay.entries.all { it.millis(500) in HoldDelay.MIN_MS..HoldDelay.MAX_MS })
    }

    @Test fun `a save from before the setting, or a damaged one, is Standard`() {
        assertEquals(HoldDelay.STANDARD, decodeLauncherState("{}", legacyRaw = null).holdDelay)
        assertEquals(HoldDelay.STANDARD, decodeLauncherState("""{"holdDelay":"sideways"}""", legacyRaw = null).holdDelay)
        assertEquals(HoldDelay.LONGER, decodeLauncherState("""{"holdDelay":"LONGER"}""", legacyRaw = null).holdDelay)
    }
}
