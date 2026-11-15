package com.mccal.folio

import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 0.6.9.1 crash ("Padding must be non-negative" in FoldAvoidingBox: an underdamped spring went under zero on its way back to the
 * window's edge), and the report's log tail, which the window system's own lines used to fill.
 */
class FoldPaddingAndLogTest {
    @Test fun `a padding below zero is zero and the rest are unchanged`() {
        assertEquals(0.dp, (-0.4).dp.atLeastZero())
        assertEquals(0.dp, 0.dp.atLeastZero())
        assertEquals(12.dp, 12.dp.atLeastZero())
    }

    @Test fun `the fold spring really does pass below its target, so the clamp is needed`() {
        // The spring FoldAvoidingBox animates its padding with: damping .9 is under 1, so it overshoots.
        val spec = SpringSpec<Float>(dampingRatio = .9f, stiffness = 380f)
        val vector = spec.vectorize(Float.VectorConverter)
        val start = AnimationVector1D(120f)
        val target = AnimationVector1D(0f)
        val zero = AnimationVector1D(0f)
        val lowest = (0..2000 step 4).minOf { vector.getValueFromNanos(it * 1_000_000L, start, target, zero).value }
        assertTrue("expected an overshoot, lowest was $lowest", lowest < 0f)
    }

    @Test fun `the log tail drops the window system's lines and keeps Folio's own`() {
        val log = listOf(
            "11-13 19:05:43.267 I/InsetsController(24174): onStateChanged: host=com.mccal.folio",
            "11-13 19:05:43.268 I/VRI[MainActivity]@8cfe68(24174): handleResized, frames=...",
            "11-13 19:05:43.300 W/DuoDiscover(24174): Discover protocol unavailable",
            "11-13 19:05:43.301 E/perf_hint(24174): Session ID too large, must fit 32-bit integer",
            "11-13 19:05:43.400 E/AndroidRuntime(24174): FATAL EXCEPTION: main",
        ).joinToString("\n")
        val kept = Diagnostics.quietLog(log).lines()
        assertEquals(listOf("W/DuoDiscover", "E/AndroidRuntime"), kept.map { it.substringAfter(' ').substringAfter(' ').substringBefore('(') })
    }

    @Test fun `a line with no tag stays with the line before it`() {
        val log = "11-13 19:05:43.267 I/InsetsController(24174): onStateChanged: state=InsetsState: {\n    more of the same line\n" +
            "11-13 19:05:43.300 W/Folio(24174): kept\n    continuation of kept"
        val kept = Diagnostics.quietLog(log).lines()
        assertEquals(2, kept.size)
        assertTrue(kept[0].contains("W/Folio"))
        assertTrue(kept[1].contains("continuation of kept"))
        assertFalse(Diagnostics.quietLog(log).contains("more of the same line"))
    }
}
