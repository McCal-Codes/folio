package com.mccal.folio

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class DragLandingTest {
    @Test fun `the release speed is the distance over the time of the trail`() {
        val v = releaseVelocity(listOf(0L to Offset(0f, 0f), 50L to Offset(100f, -50f)))
        assertEquals(2000f, v.x, .01f)
        assertEquals(-1000f, v.y, .01f)
    }

    @Test fun `a still finger or a single point lets go with no speed`() {
        assertEquals(Offset.Zero, releaseVelocity(listOf(10L to Offset(5f, 5f))))
        assertEquals(Offset.Zero, releaseVelocity(listOf(10L to Offset(5f, 5f), 10L to Offset(50f, 5f))))
        assertEquals(Offset.Zero, releaseVelocity(emptyList()))
    }

    @Test fun `only the last moments of a drag count toward the speed`() {
        val drag = HomeDragState()
        drag.track(0L, Offset(0f, 0f))
        drag.track(50L, Offset(400f, 0f))
        drag.track(200L, Offset(400f, 0f))
        drag.track(210L, Offset(410f, 0f))
        // The fast start is more than 90 ms old by the end, so the slow finish is what the icon carries.
        assertEquals(1000f, drag.releaseVelocity().x, .01f)
    }

    @Test fun `clearing a drag forgets its trail`() {
        val drag = HomeDragState()
        drag.track(0L, Offset(0f, 0f)); drag.track(20L, Offset(100f, 0f))
        drag.clear()
        assertEquals(Offset.Zero, drag.releaseVelocity())
    }

    @Test fun `an icon's cell is hidden while its copy is in the air, and fades in under Reduce Motion`() {
        assertEquals(1f, DragLanding.cellAlpha("maps", flyingId = "gmail", reduced = false, copyAlpha = 1f), 0f)
        assertEquals(1f, DragLanding.cellAlpha("maps", flyingId = null, reduced = false, copyAlpha = 1f), 0f)
        assertEquals(0f, DragLanding.cellAlpha("gmail", flyingId = "gmail", reduced = false, copyAlpha = .4f), 0f)
        assertEquals(.6f, DragLanding.cellAlpha("gmail", flyingId = "gmail", reduced = true, copyAlpha = .4f), .001f)
    }

    private fun fly(flight: BounceFlight, target: (Int) -> Offset, speed: Float = 1f, frames: Int = 120): Int {
        repeat(frames) { f -> flight.step(target(f), 1f / 60f, speed); if (flight.arrived(target(f))) return f }
        return -1
    }

    @Test fun `the landing settles on its cell with Bounce's small overshoot`() {
        val flight = BounceFlight(Offset(0f, 0f), Offset.Zero)
        var furthest = 0f
        val done = (0 until 120).firstOrNull { flight.step(Offset(100f, 0f), 1f / 60f, 1f); furthest = maxOf(furthest, flight.position.x); flight.arrived(Offset(100f, 0f)) }
        assertEquals(true, done != null && done < 60)
        // Damping .7 passes the line by about 4.6%: visible, never a wobble.
        assertEquals(true, furthest in 103f..106f)
    }

    @Test fun `a cell that moves during the landing is followed without stalling`() {
        val flight = BounceFlight(Offset(0f, 0f), Offset.Zero)
        // Home slides down 32 px over the first eight frames as jiggle mode starts.
        val done = fly(flight, { f -> Offset(140f, 1360f + minOf(f, 8) * 4f) })
        assertEquals(true, done in 1..70)
        assertEquals(1392f, flight.position.y, .5f)
        val early = BounceFlight(Offset(0f, 0f), Offset.Zero)
        repeat(3) { f -> early.step(Offset(140f, 1360f + f * 4f), 1f / 60f, 1f) }
        assertEquals(true, early.position.x > 10f)
    }

    @Test fun `Animation Speed reaches the landing`() {
        val standard = fly(BounceFlight(Offset.Zero, Offset.Zero), { Offset(100f, 0f) })
        val snappy = fly(BounceFlight(Offset.Zero, Offset.Zero), { Offset(100f, 0f) }, speed = MotionSpeed.SNAPPY.factor)
        assertEquals(true, snappy in 1 until standard)
    }

    @Test fun `without the motion pass a drag keeps the old copy`() {
        val landing = DragLanding()
        landing.begin(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), "gmail", Offset(10f, 10f), pass = false, reduceMotion = false)
        assertEquals(false, landing.lifted)
        landing.land(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), "gmail", Offset(10f, 10f), Offset.Zero)
        assertEquals(null, landing.flyingId)
    }
}
