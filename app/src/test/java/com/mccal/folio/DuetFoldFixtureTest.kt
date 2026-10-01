package com.mccal.folio

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Recorded folds played through Duet's timeline, the way hingewave (MIT) keeps its motion honest with fixture files.
 * The readings are a Galaxy Z Fold8's public hinge sensor: 0, 90 and 180 only, about 200 ms apart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DuetFoldFixtureTest {
    private fun timeline(expanded: Boolean) = FoldTimeline(ApplicationProvider.getApplicationContext()).also {
        it.stayAwake = false
        it.expanded = expanded
    }

    /** Plays (ms, degrees) readings; returns the effect strength sampled every 10 ms across [until] ms. */
    private fun FoldTimeline.play(readings: List<Pair<Long, Float>>, until: Long, at: (Long) -> Unit = {}): List<Float> {
        val out = mutableListOf<Float>()
        var next = 0
        for (t in 0L..until step 10L) {
            while (next < readings.size && readings[next].first <= t) { onAngle(readings[next].second, readings[next].first * 1_000_000L, readings[next].first); next++ }
            at(t)
            out += targetM(t)
        }
        return out
    }

    @Test fun `closing on the open screen builds the effect and marks the fold as closing`() {
        val fold = timeline(expanded = true)
        val m = fold.play(listOf(0L to 180f, 200L to 90f, 400L to 90f, 600L to 0f), until = 600)
        assertFalse("folding is not opening", fold.opening)
        val built = m.drop(20) // after the 90° step at 200 ms
        assertTrue("the effect builds while folding: $built", built.zipWithNext().all { (a, b) -> b >= a - 1e-4f })
        assertTrue("and gets strong before closed", built.last() > .5f)
        assertTrue("never beyond full", m.all { it in 0f..1f })
    }

    @Test fun `opening lands on the open screen and clears in about half a second`() {
        val fold = timeline(expanded = false)
        fold.play(listOf(0L to 0f, 200L to 90f), until = 200)
        assertTrue("starting to open from the cover counts as opening", fold.opening)
        fold.expanded = true
        fold.onDisplaySwitched(300)
        assertEquals("the new panel starts fully frosted", 1f, fold.targetM(300), 1e-4f)
        fold.onPanelLit(350)
        val clearing = (350L..900L step 10L).map { fold.targetM(it) }
        assertTrue("it only ever clears: $clearing", clearing.zipWithNext().all { (a, b) -> b <= a + 1e-4f })
        assertEquals("clear by 520 ms after the panel lights", 0f, clearing.last(), 1e-4f)
        assertTrue(fold.opening)
    }

    @Test fun `opening back up before closing counts as opening again`() {
        val fold = timeline(expanded = true)
        fold.play(listOf(0L to 180f, 200L to 90f, 400L to 180f), until = 400)
        assertTrue(fold.opening)
    }

    @Test fun `a display switch wakes the idle loop at once`() {
        val fold = timeline(expanded = false)
        assertFalse(fold.wake.tryReceive().isSuccess)
        fold.expanded = true
        fold.onDisplaySwitched(0)
        assertTrue(fold.wake.tryReceive().isSuccess)
    }
}
