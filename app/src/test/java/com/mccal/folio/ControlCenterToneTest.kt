package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Clear glass is as light as the backdrop allows and no lighter: white text stays at 4.5:1 or better over any backdrop (PNL-6). */
class ControlCenterToneTest {
    /** The sRGB value of the tile where the text sits: black dim over the backdrop, then the tile's black and white. */
    private fun shown(v: Float, t: CcGlassTone): Float = v * (1f - t.dim) * (1f - t.tileBlack) * (1f - t.tileWhite) + t.tileWhite

    private fun luminance(srgb: Float): Float = Math.pow(((srgb + .055f) / 1.055f).toDouble(), 2.4).toFloat()

    @Test fun `white text keeps 4_5 to 1 over every backdrop brightness`() {
        for (i in 0..100) {
            val v = i / 100f
            val tone = ccClearTone(v)
            val contrast = 1.05f / (luminance(shown(v, tone)) + .05f)
            assertTrue("brightness $v gives $contrast", contrast >= 4.49f)
        }
    }

    @Test fun `a dark backdrop gets almost no dim and see-through tiles, a bright one a stronger dim and darker tiles`() {
        val dark = ccClearTone(.2f)
        assertEquals(0f, dark.dim, 0.001f); assertEquals(0f, dark.tileBlack, 0.001f); assertEquals(.16f, dark.tileWhite, 0.001f)
        val bright = ccClearTone(1f)
        assertTrue(bright.dim >= .29f)
        assertTrue(bright.tileBlack > .1f)
        assertTrue(bright.tileWhite < dark.tileWhite)
    }

    @Test fun `the tone changes smoothly with the backdrop, so nothing jumps between two almost equal wallpapers`() {
        var last = ccClearTone(0f)
        for (i in 1..200) {
            val tone = ccClearTone(i / 200f)
            assertTrue("dim jumped at ${i / 200f}", kotlin.math.abs(tone.dim - last.dim) < .05f)
            assertTrue("black jumped at ${i / 200f}", kotlin.math.abs(tone.tileBlack - last.tileBlack) < .06f)
            assertTrue("white jumped at ${i / 200f}", kotlin.math.abs(tone.tileWhite - last.tileWhite) < .06f)
            last = tone
        }
    }

    @Test fun `the dim is never darker than today's fixed one`() {
        for (i in 0..100) assertTrue(ccClearTone(i / 100f).dim <= CC_DEFAULT_TONE.dim)
    }

    @Test fun `brightness reads the bright parts of a picture`() {
        fun argb(r: Int, g: Int, b: Int) = (255 shl 24) or (r shl 16) or (g shl 8) or b
        assertEquals(.5f, ccBrightness(IntArray(0)), 0.001f)
        assertEquals(0f, ccBrightness(IntArray(100) { argb(0, 0, 0) }), 0.01f)
        assertEquals(1f, ccBrightness(IntArray(100) { argb(255, 255, 255) }), 0.01f)
        // a mostly dark picture with a bright tenth stays dark: the 85th percentile, not the maximum
        val mixed = IntArray(100) { if (it < 10) argb(255, 255, 255) else argb(20, 20, 20) }
        assertTrue(ccBrightness(mixed) < .2f)
        // a quarter bright is read as bright
        val quarter = IntArray(100) { if (it < 25) argb(255, 255, 255) else argb(20, 20, 20) }
        assertTrue(ccBrightness(quarter) > .9f)
    }
}
