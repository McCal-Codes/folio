package com.mccal.folio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperBlurTest {
    private fun gray(value: Int) = (0xFF shl 24) or (value shl 16) or (value shl 8) or value
    private fun channel(pixel: Int) = pixel and 0xFF

    /** Left half black, right half white: the hardest edge a photo can have. */
    private fun split(width: Int, height: Int) = IntArray(width * height) { if (it % width < width / 2) gray(0) else gray(255) }

    @Test fun aRadiusOfZeroLeavesThePictureAlone() {
        val pixels = split(8, 4)
        val before = pixels.copyOf()
        boxBlur(pixels, 8, 4, 0)
        assertArrayEquals(before, pixels)
    }

    @Test fun aFlatPictureStaysFlat() {
        val pixels = IntArray(10 * 10) { gray(120) }
        boxBlur(pixels, 10, 10, 3)
        assertTrue(pixels.all { it == gray(120) })
    }

    @Test fun aHardEdgeBecomesAGradient() {
        val pixels = split(40, 3)
        boxBlur(pixels, 40, 3, 4)
        val row = pixels.take(40).map(::channel)
        // Far from the edge nothing changed, at the edge the values meet in the middle, and they rise without going back.
        assertEquals(0, row[0]); assertEquals(255, row[39])
        assertTrue(row[19] in 1..254 && row[20] in 1..254)
        assertTrue(row.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test fun theAverageBrightnessIsKept() {
        val pixels = split(40, 40)
        val before = pixels.map(::channel).average()
        boxBlur(pixels, 40, 40, 5)
        assertEquals(before, pixels.map(::channel).average(), 2.0)
    }

    @Test fun aPictureSmallerThanTheRadiusStillBlursWithoutCrashing() {
        val pixels = split(2, 2)
        boxBlur(pixels, 2, 2, 5)
        assertEquals(4, pixels.size)
    }

    @Test fun theSliderGrowsTheRadiusAndTheSpreadTogether() {
        assertEquals(0, blurRadiusFor(0f, 1080))
        assertEquals(30, blurRadiusFor(.5f, 1080))
        assertEquals(60, blurRadiusFor(1f, 1080))
        assertEquals(5, blurRadiusFor(.5f, 180))
        assertEquals(0f, blurSigmaFraction(0f), 0f)
        assertTrue(blurSigmaFraction(.25f) < blurSigmaFraction(.5f) && blurSigmaFraction(.5f) < blurSigmaFraction(1f))
        // Three box passes of radius r spread as sqrt(r * (r + 1)); at the top of the slider that is 10.5 of 180.
        assertEquals(kotlin.math.sqrt(110f) / 180f, blurSigmaFraction(1f), 1e-6f)
    }

    @Test fun anOutOfRangeAmountIsHeldToTheSlider() {
        assertEquals(0, blurRadiusFor(-1f, 1080))
        assertEquals(60, blurRadiusFor(3f, 1080))
        assertEquals(0f, dimAlpha(-.5f), 0f)
        assertEquals(dimAlpha(1f), dimAlpha(9f), 0f)
    }

    @Test fun dimGoesFromNoneToMostButNeverBlack() {
        assertEquals(0f, dimAlpha(0f), 0f)
        assertTrue(dimAlpha(1f) >= .5f && dimAlpha(1f) < 1f)
        assertTrue(dimAlpha(.3f) < dimAlpha(.6f))
    }

    @Test fun anAdjustmentWithNothingInItIsNone() {
        assertTrue(PhotoAdjust.NONE.isNone)
        assertTrue(PhotoAdjust(blur = 0f, dim = 0f).isNone)
        assertTrue(!PhotoAdjust(blur = .1f).isNone)
        assertTrue(!PhotoAdjust(dim = .1f).isNone)
    }
}
