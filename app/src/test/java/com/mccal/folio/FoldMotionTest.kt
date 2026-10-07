package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldMotionTest {
    @Test fun rippleIsNeutralAtRestAndSmallWhenClosed() {
        val rest = FoldMotionMath.ripple(0f, .8f)
        assertEquals(1f, rest.scale, .001f); assertEquals(0f, rest.translateYDp, .001f); assertEquals(1f, rest.alpha, .001f)
        val closed = FoldMotionMath.ripple(1f, 0f)
        assertEquals(FoldMotionMath.RIPPLE_MIN_SCALE, closed.scale, .001f)
        assertEquals(FoldMotionMath.RIPPLE_MIN_ALPHA, closed.alpha, .001f)
    }

    @Test fun iconsNearTheHingeArriveBeforeFarOnes() {
        val near = FoldMotionMath.ripple(.6f, .1f)
        val far = FoldMotionMath.ripple(.6f, .9f)
        assertTrue(near.alpha > far.alpha)
        assertTrue(near.translateYDp < far.translateYDp)
    }

    @Test fun rippleRunsTheSameWayBackwards() {
        // The same amount gives the same values opening or closing, so a half-closed phone opened again is one motion.
        assertEquals(FoldMotionMath.ripple(.4f, .5f), FoldMotionMath.ripple(.4f, .5f))
        val farFirstWhenFolding = FoldMotionMath.ripple(.3f, .9f).alpha < FoldMotionMath.ripple(.3f, .1f).alpha
        assertTrue(farFirstWhenFolding)
    }

    @Test fun depthIsNeutralAtRestAndTiny() {
        assertEquals(1f, FoldMotionMath.wallpaperScale(0f), 0f)
        assertEquals(0f, FoldMotionMath.wallpaperShiftDp(0f), 0f)
        assertEquals(0f, FoldMotionMath.iconShiftDp(0f), 0f)
        assertTrue(FoldMotionMath.wallpaperScale(1f) <= 1.05f)
        assertTrue(FoldMotionMath.wallpaperShiftDp(1f) < 0f && FoldMotionMath.iconShiftDp(1f) > 0f)
    }

    @Test fun lightIsOffAtBothEndsAndSubtleInTheMiddle() {
        assertEquals(0f, FoldMotionMath.lightAlpha(0f), .001f)
        assertEquals(0f, FoldMotionMath.lightAlpha(1f), .001f)
        assertEquals(FoldMotionMath.LIGHT_ALPHA, FoldMotionMath.lightAlpha(.5f), .001f)
        assertTrue(FoldMotionMath.LIGHT_ALPHA <= .3f)
        assertTrue(FoldMotionMath.lightPosition(.75f) < FoldMotionMath.lightPosition(.25f))
    }

    @Test fun distanceIsMeasuredFromTheHinge() {
        val scope = FoldMotionScope({ 1f }, FoldMotionOptions(), hingeIsHorizontal = false, hingePx = 500f, farthestPx = 500f)
        assertEquals(0f, scope.distance(500f, 10f), 0f)
        assertEquals(1f, scope.distance(0f, 10f), 0f)
        assertEquals(.5f, scope.distance(750f, 10f), 0f)
        val flat = FoldMotionScope({ 1f }, FoldMotionOptions(), hingeIsHorizontal = true, hingePx = 300f, farthestPx = 600f)
        assertEquals(.5f, flat.distance(10f, 600f), 0f)
    }

    @Test fun optionsAnyIsFalseOnlyWhenAllAreOff() {
        assertTrue(FoldMotionOptions().any)
        assertTrue(!FoldMotionOptions(false, false, false).any)
        assertTrue(FoldMotionOptions(false, true, false).any)
    }
}
