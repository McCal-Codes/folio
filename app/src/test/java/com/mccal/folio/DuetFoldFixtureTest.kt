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

    /** A hinge sweep every [stepMs]: straight from [from] to [to] degrees over [ms], starting at [startMs]. */
    private fun sweep(startMs: Long, from: Float, to: Float, ms: Long, stepMs: Long = 20): List<Pair<Long, Float>> =
        (0..(ms / stepMs)).map { i -> val f = i * stepMs / ms.toFloat(); (startMs + i * stepMs) to (from + (to - from) * f) }

    /** Closing halfway and opening back up, on a continuous angle (the root feed), is one smooth motion, not a reset. */
    @Test fun `closing halfway and opening again follows the hinge both ways with no snap`() {
        val fold = timeline(expanded = true)
        val closing = sweep(0, 180f, 70f, 1_100)
        val opening = sweep(1_120, 70f, 180f, 1_100)
        val m = fold.play(closing + opening, until = 2_400)
        val steps = m.zipWithNext { a, b -> kotlin.math.abs(b - a) }
        assertTrue("no jump anywhere (largest ${steps.max()}): $m", steps.all { it < .09f })
        val peakAt = m.indices.maxByOrNull { m[it] }!!
        assertTrue("it built while closing: ${m.max()}", m.max() > .5f)
        assertTrue("and it eases back down while opening, not all at once", m.drop(peakAt).zipWithNext().count { (a, b) -> b < a } > 20)
        assertEquals("clear once flat again", 0f, m.last(), 1e-4f)
        assertTrue(fold.opening)
    }

    @Test fun `the effect on the way back up matches the effect at the same angle on the way down`() {
        val fold = timeline(expanded = true)
        var down = -1f; var up = -1f
        val readings = sweep(0, 180f, 80f, 1_000) + sweep(1_020, 80f, 180f, 1_000)
        fold.play(readings, until = 2_050) { t ->
            // Sample at 130 degrees on each leg: 180 - 100 * (t / 1000) on the way down, 80 + 100 * ((t - 1020) / 1000) on the way up.
            if (t == 500L) down = fold.targetM(t)
            if (t == 1_520L) up = fold.targetM(t)
        }
        assertTrue("both legs measured ($down, $up)", down > 0f && up > 0f)
        assertEquals("the same angle gives nearly the same strength", down, up, .12f)
    }

    @Test fun `closing again after reopening carries straight on`() {
        val fold = timeline(expanded = true)
        val readings = sweep(0, 180f, 90f, 800) + sweep(820, 90f, 150f, 600) + sweep(1_440, 150f, 40f, 900)
        val m = fold.play(readings, until = 2_400)
        val steps = m.zipWithNext { a, b -> kotlin.math.abs(b - a) }
        assertTrue("no jump (largest ${steps.max()})", steps.all { it < .09f })
        assertTrue("strong again by 40 degrees: ${m.last()}", m.last() > .6f)
    }

    @Test fun `holding it part way lets go gently, with a slower follow, not a snap`() {
        val fold = timeline(expanded = true)
        val closing = sweep(0, 180f, 100f, 700)
        fold.play(closing, until = 700)
        assertEquals("quick while following the hand", 28f, fold.followMs, 1e-3f)
        // held still for well over STALL_MS
        val held = (710L..2_000L step 10L).map { fold.targetM(it) }
        assertEquals("then it lets go", 0f, held.last(), 1e-4f)
        assertTrue("with a soft release: ${fold.followMs}", fold.followMs > 100f)
    }

    @Test fun `a new fold goes back to quick tracking after a release`() {
        val fold = timeline(expanded = true)
        fold.play(sweep(0, 180f, 100f, 700), until = 2_000)
        assertTrue(fold.followMs > 100f)
        fold.onAngle(180f, 3_000_000_000L, 3_000L)
        fold.onAngle(120f, 3_100_000_000L, 3_100L)
        assertEquals("quick again", 28f, fold.followMs, 1e-3f)
    }

    // The cover opening, as traced on the Fold8 on 7 Oct 2026: the old curve was already 31% at 14 degrees and popped in within two frames.
    @Test fun `the cover builds from nothing as it leaves closed`() {
        assertEquals(0f, coverBuildAtAngle(0f), 1e-6f)
        assertEquals(0f, coverBuildAtAngle(8f), 1e-6f)
        assertTrue("barely anything at 14 degrees (was .31): ${coverBuildAtAngle(14f)}", coverBuildAtAngle(14f) < .03f)
    }

    @Test fun `the cover never hits suddenly and is full by the handoff`() {
        val curve = (0..96).map { coverBuildAtAngle(it.toFloat()) }
        assertTrue("only ever grows", curve.zipWithNext().all { (a, b) -> b >= a - 1e-6f })
        assertTrue("no degree adds more than 4%: ${curve.zipWithNext { a, b -> b - a }.max()}", curve.zipWithNext { a, b -> b - a }.max() < .04f)
        assertEquals("full at the handoff", 1f, coverBuildAtAngle(88f), 1e-6f)
        assertEquals(1f, coverBuildAtAngle(96f), 1e-6f)
        assertTrue("about half way up at the middle of the range: ${coverBuildAtAngle(48f)}", coverBuildAtAngle(48f) in 0.4f..0.6f)
    }

    @Test fun `on a continuous angle the cover follows the hinge up to the handoff without a jump`() {
        val fold = timeline(expanded = false)
        // Closed, then a slow open: the first few in-between readings teach the timeline the angle is continuous.
        val readings = listOf(0L to 0f) + sweep(100, 3f, 96f, 1_000, stepMs = 20)
        val m = fold.play(readings, until = 1_200)
        val tail = m.drop(25) // after it has learned the angle is continuous
        val steps = tail.zipWithNext { a, b -> kotlin.math.abs(b - a) }
        assertTrue("no jump (largest ${steps.max()})", steps.all { it < .06f })
        assertTrue("it only grows while opening", tail.zipWithNext().all { (a, b) -> b >= a - 1e-4f })
        assertTrue("and is full at the handoff: ${m.last()}", m.last() > .97f)
    }

    @Test fun `when only steps are known the cover eases in and out over a short time`() {
        val fold = timeline(expanded = false)
        fold.play(listOf(0L to 0f), until = 50)
        fold.onAngle(90f, 100_000_000L, 100L)
        val m = (100L..500L step 10L).map { fold.targetM(it) }
        assertTrue("starts gently: ${m[1]}", m[1] < .08f)
        assertTrue("only grows", m.zipWithNext().all { (a, b) -> b >= a - 1e-4f })
        assertEquals("full by 300 ms", 1f, m.last(), 1e-3f)
    }

    @Test fun `the cover recedes a little with the effect and not at all under Reduce Motion`() {
        assertEquals(1f, coverSettleScale(0f, reduceMotion = false), 1e-6f)
        assertEquals(.955f, coverSettleScale(1f, reduceMotion = false), 1e-3f)
        assertTrue(coverSettleScale(.5f, false) in .955f..1f)
        assertEquals("never beyond the ends", coverSettleScale(1f, false), coverSettleScale(3f, false), 1e-6f)
        assertEquals(1f, coverSettleScale(1f, reduceMotion = true), 1e-6f)
    }
}
