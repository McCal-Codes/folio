package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockShadeTest {
    @Test fun aMidToneNeedsNoMoreThanTheSoftestHalo() {
        // Brightest 15% at a luminance white already nearly reads on: the minimum is enough.
        val shade = clockShade(.22f)
        assertEquals(CLOCK_SHADE_MIN, shade.alpha, 1e-4f)
        assertTrue(shade.ratio >= 4.5f)
    }

    @Test fun aPaleMixedPictureGetsAStrongerHaloThanTheOldFixedForty() {
        // The overcast-sky-and-branches case from the Lab: its brightest 15% is far above what 40% black can bring down.
        val old = contrastRatio(1f, .8f * Math.pow(.6, 2.2).toFloat())
        assertTrue("a fixed 40% leaves this under 4.5:1 (was $old)", old < 4.5f)
        val shade = clockShade(.8f)
        assertTrue(shade.alpha > CLOCK_SHADE_MIN)
        assertTrue("reaches 4.5:1 (was ${shade.ratio})", shade.ratio >= 4.499f)
    }

    @Test fun evenPureWhiteBehindTheClockStaysAHaloNotAFilter() {
        // Pure white is the worst case there is, and about 54% black already brings white ink to 4.5:1, under the 65% cap.
        val shade = clockShade(1f)
        assertTrue(shade.alpha <= CLOCK_SHADE_MAX)
        assertTrue("about 54%, was ${shade.alpha}", shade.alpha in .5f..CLOCK_SHADE_MAX)
        assertTrue(shade.ratio >= 4.499f)
    }

    @Test fun theHaloGrowsWithTheBrightness() {
        val alphas = listOf(.25f, .4f, .6f, .8f, 1f).map { clockShade(it).alpha }
        assertEquals(alphas.sorted(), alphas)
    }

    @Test fun aShadedResultCarriesItsOwnAlpha() {
        val pale = ClockInkSample(RegionStats(.8f, .6f, .85f, 230f, 230f, 230f), RegionStats(.8f, .6f, .85f, 230f, 230f, 230f), emptyList(), estimated = false)
        val result = resolveClockInk(BigClockStyle(), pale, fallbackDark = false)
        assertTrue(result.shade || result.color != androidx.compose.ui.graphics.Color.White)
        if (result.shade) assertTrue(result.shadeAlpha in CLOCK_SHADE_MIN..CLOCK_SHADE_MAX)
    }
}
