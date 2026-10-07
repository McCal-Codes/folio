package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandByWaysTest {
    private val all = StandByWays(halfOpen = true, charging = true, tent = true)
    private val onStand = StandBySignals(plugged = true, sideways = true, still = true, onCover = true)

    @Test fun `half-open comes on by itself, as before`() {
        assertEquals(StandByWay.HALF_OPEN, standByWay(all, StandBySignals(halfOpen = true)))
        assertNull(standByWay(all.copy(halfOpen = false), StandBySignals(halfOpen = true)))
    }

    // iPhone's rule: on the charger, on its side, and set down.
    @Test fun `charging needs the cable, the side and stillness`() {
        assertEquals(StandByWay.CHARGING, standByWay(all, onStand))
        assertNull("unplugged", standByWay(all.copy(tent = false), onStand.copy(plugged = false)))
        assertNull("held upright", standByWay(all, onStand.copy(sideways = false)))
        assertNull("still moving", standByWay(all, onStand.copy(still = false)))
        assertNull("switched off", standByWay(all.copy(charging = false, tent = false), onStand))
    }

    // A narrow tent reads 0° on the Fold8's public hinge sensor, the same as closed, so only a wide one counts.
    @Test fun `a tent off the charger needs the hinge at its middle step, on the cover`() {
        val tent = StandBySignals(sideways = true, still = true, hingeHalfway = true, onCover = true)
        assertEquals(StandByWay.TENT, standByWay(all, tent))
        assertNull("a narrow tent", standByWay(all, tent.copy(hingeHalfway = false)))
        assertNull("on the inner screen", standByWay(all, tent.copy(onCover = false)))
        assertNull("off by default", standByWay(all.copy(tent = false), tent))
    }

    // The Fold8 reports the pose by name through Android's device state (measured 6 Oct 2026), including the narrow tent the hinge misses.
    @Test fun `when the phone says tent, that decides, not the hinge`() {
        val tent = StandBySignals(sideways = true, still = true, hingeHalfway = false, onCover = true, tentState = true)
        assertEquals("a narrow tent the hinge misses", StandByWay.TENT, standByWay(all, tent))
        assertNull("the phone says it is not a tent", standByWay(all, tent.copy(hingeHalfway = true, tentState = false)))
        assertEquals("a phone that does not say falls back to the hinge", StandByWay.TENT, standByWay(all, tent.copy(hingeHalfway = true, tentState = null)))
        assertNull("and without the hinge too", standByWay(all, tent.copy(tentState = null)))
    }

    @Test fun `a tent the phone reports still needs everything else`() {
        val tent = StandBySignals(sideways = true, still = true, onCover = true, tentState = true)
        assertNull("on the inner screen", standByWay(all, tent.copy(onCover = false)))
        assertNull("held in the air", standByWay(all, tent.copy(still = false)))
        assertNull("upright", standByWay(all, tent.copy(sideways = false)))
        assertNull("off by default", standByWay(all.copy(tent = false), tent))
    }

    // Ready is everything but settling: while it holds, a bump doesn't hide StandBy and a dismissal sticks.
    @Test fun `a phone that's only moving is still ready`() {
        val moving = onStand.copy(still = false)
        assertNull(standByWay(all, moving))
        assertEquals(StandByWay.CHARGING, standByWay(all, moving.copy(still = true)))
    }
}

class StillnessTrackerTest {
    private fun feed(tracker: StillnessTracker, x: Float, y: Float, z: Float, fromMs: Long, toMs: Long, jitter: Float = 0f): MotionState {
        var state = MotionState()
        var t = fromMs
        var i = 0
        while (t <= toMs) {
            val j = if (i++ % 2 == 0) jitter else -jitter
            state = tracker.add(x + j, y, z, t)
            t += 100
        }
        return state
    }

    @Test fun `on its side and set down is sideways and still`() {
        val state = feed(StillnessTracker(), 9.6f, .3f, 1.5f, 0, 3_000)
        assertTrue(state.sideways)
        assertTrue(state.still)
    }

    @Test fun `leaning back on a stand still counts as on its side`() {
        assertTrue(feed(StillnessTracker(), 6.9f, .2f, 6.9f, 0, 3_000).sideways)
    }

    @Test fun `upright or lying flat isn't on its side`() {
        assertFalse("upright", feed(StillnessTracker(), .4f, 9.7f, 1f, 0, 3_000).sideways)
        assertFalse("flat on a table", feed(StillnessTracker(), .2f, .3f, 9.8f, 0, 3_000).sideways)
    }

    @Test fun `a shaking hand or too short a wait isn't still`() {
        assertFalse("shaking", feed(StillnessTracker(), 9.6f, .3f, 1.5f, 0, 3_000, jitter = 1.2f).still)
        assertFalse("only a second", feed(StillnessTracker(), 9.6f, .3f, 1.5f, 0, 1_000).still)
    }
}
