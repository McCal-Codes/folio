package com.mccal.folio

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class SliderTrackContrastTest {
    private fun linear(channel: Int): Double = (channel / 255.0).let { if (it <= .03928) it / 12.92 else ((it + .055) / 1.055).pow(2.4) }
    private fun luminance(rgb: Int) = .2126 * linear(rgb shr 16 and 0xFF) + .7152 * linear(rgb shr 8 and 0xFF) + .0722 * linear(rgb and 0xFF)

    /** [alpha] white laid over [surface], as the slider's unfilled track is. */
    private fun whiteOver(surface: Int, alpha: Float): Int {
        fun mix(shift: Int) = ((255 * alpha + (surface shr shift and 0xFF) * (1 - alpha)) + .5f).toInt()
        return (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    private fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = luminance(a).let { x -> luminance(b).let { y -> maxOf(x, y) to minOf(x, y) } }
        return (hi + .05) / (lo + .05)
    }

    @Test fun theUnfilledTrackIsSeenAgainstTheSettingsCard() {
        // WCAG 1.4.11 asks 3 to 1 of a control's edge; the card is what settings sliders sit on.
        assertTrue(contrast(whiteOver(0x242428, SLIDER_TRACK_ALPHA), 0x242428) >= 2.95)
    }

    @Test fun theOldTrackWasNot() {
        assertTrue(contrast(whiteOver(0x242428, .22f), 0x242428) < 2.5)
    }
}
