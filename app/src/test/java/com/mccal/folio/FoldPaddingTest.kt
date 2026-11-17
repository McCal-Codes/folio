package com.mccal.folio

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 0.6.9.1 crash ("Padding must be non-negative" in FoldAvoidingBox): the spring that moves a panel off the hinge went under zero on its
 * way back to the window's edge, and `Modifier.padding` throws on a negative value.
 */
class FoldPaddingTest {
    @Test fun `a padding below zero is zero and the rest are unchanged`() {
        assertEquals(0.dp, (-0.4).dp.atLeastZero())
        assertEquals(0.dp, 0.dp.atLeastZero())
        assertEquals(12.dp, 12.dp.atLeastZero())
    }

    @Test fun `the fold spring really does pass below its target, so the clamp is needed`() {
        // The spring FoldAvoidingBox animates its padding with: damping .9 is under 1, so it overshoots.
        val vector = SpringSpec<Float>(dampingRatio = .9f, stiffness = 380f).vectorize(Float.VectorConverter)
        val start = AnimationVector1D(120f)
        val target = AnimationVector1D(0f)
        val lowest = (0..2000 step 4).minOf { vector.getValueFromNanos(it * 1_000_000L, start, target, AnimationVector1D(0f)).value }
        assertTrue("expected an overshoot, lowest was $lowest", lowest < 0f)
    }
}
