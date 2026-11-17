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

    @Test fun theWorkingSizeKeepsTheShapeAndNeverGrows() {
        assertEquals(180 to 135, blurWorkingSize(4000, 3000))
        assertEquals(101 to 180, blurWorkingSize(1080, 1920))
        assertEquals(50 to 40, blurWorkingSize(50, 40))
        assertEquals(1 to 180, blurWorkingSize(1, 4000))
    }
}
