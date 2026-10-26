package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The box blur behind the panels: flat stays flat, a point spreads without gaining brightness, and the edges hold. */
class FrostBlurTest {
    private fun gray(v: Int) = (255 shl 24) or (v shl 16) or (v shl 8) or v
    private fun level(p: Int) = p and 255

    @Test fun `a flat picture stays flat`() {
        val pixels = IntArray(20 * 12) { gray(90) }
        FrostBlur.boxBlur(pixels, 20, 12, 3, 3)
        assertTrue(pixels.all { it == gray(90) })
    }

    @Test fun `a single bright point spreads out and never gets brighter`() {
        val w = 41; val h = 41
        val pixels = IntArray(w * h) { gray(0) }
        pixels[20 * w + 20] = gray(255)
        FrostBlur.boxBlur(pixels, w, h, 3, 3)
        assertTrue(level(pixels[20 * w + 20]) < 255)
        assertTrue(level(pixels[20 * w + 22]) > 0)
        assertTrue(pixels.all { level(it) <= 255 })
        // Even in the middle the point is only a small share of what it was.
        assertTrue(level(pixels[20 * w + 20]) < 40)
    }

    @Test fun `a hard edge becomes a smooth ramp that rises without a step`() {
        val w = 60; val h = 3
        val pixels = IntArray(w * h) { gray(if (it % w < w / 2) 0 else 200) }
        FrostBlur.boxBlur(pixels, w, h, 3, 3)
        val row = (0 until w).map { level(pixels[w + it]) }
        assertEquals(0, row.first()); assertEquals(200, row.last())
        for (i in 1 until w) assertTrue("row steps up by at most 60: ${row[i] - row[i - 1]}", row[i] - row[i - 1] in 0..60)
        assertTrue("the middle is in between", row[w / 2] in 30..170)
    }

    @Test fun `nothing happens with no radius and no crash on one pixel`() {
        val pixels = intArrayOf(gray(10), gray(200))
        FrostBlur.boxBlur(pixels, 2, 1, 0, 3)
        assertEquals(gray(10), pixels[0]); assertEquals(gray(200), pixels[1])
        val one = intArrayOf(gray(77)); FrostBlur.boxBlur(one, 1, 1, 3, 3); assertEquals(gray(77), one[0])
    }
}
