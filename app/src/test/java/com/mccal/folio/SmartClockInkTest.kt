package com.mccal.folio

import org.junit.Assert.assertSame
import org.junit.Test

class SmartClockInkTest {
    /** A uniform patch of luminance [lum]: the ink that wins is whichever holds more contrast against it. */
    private fun patch(lum: Float): ClockInkSample {
        val region = RegionStats(lum, lum, lum, 128f, 128f, 128f)
        return ClockInkSample(region, region, emptyList(), estimated = false)
    }

    @Test fun theFirstMeasurementIsAlwaysTaken() {
        val first = patch(.4f)
        assertSame(first, keepOrSwap(null, first))
    }

    @Test fun aSampleThatKeepsTheSameInkReplacesTheOldOne() {
        // Both dark patches: white ink wins on both, so the numbers update.
        val old = patch(.10f); val next = patch(.12f)
        assertSame(next, keepOrSwap(old, next))
    }

    @Test fun aNearTieDoesNotFlipTheInk() {
        // Luminance .19 to .22 is where white and dark ink are almost equal; the clock's own digits in the picture can
        // push it either way, so the ink stays where it was until the other one clearly wins.
        val old = patch(.15f)   // white wins
        val tie = patch(.22f)   // dark edges ahead, but not by 1.5 times what white still holds
        assertSame(old, keepOrSwap(old, tie))
    }

    @Test fun aClearChangeOfPictureDoesFlipTheInk() {
        val old = patch(.15f)
        val pale = patch(.35f)  // dark wins by far
        assertSame(pale, keepOrSwap(old, pale))
    }

    @Test fun flippingBackNeedsTheSameMargin() {
        val old = patch(.35f)   // dark ink in use
        val tie = patch(.17f)   // white ahead, narrowly
        assertSame(old, keepOrSwap(old, tie))
        val dark = patch(.05f)  // white by far
        assertSame(dark, keepOrSwap(old, dark))
    }
}

class WallpaperBesideClockTest {
    private fun stats(mean: Float, p15: Float, p85: Float) = RegionStats(mean, p15, p85, 100f, 100f, 100f)
    private fun sample(mean: Float, p15: Float, p85: Float) = ClockInkSample(stats(mean, p15, p85), stats(mean, p15, p85), emptyList(), estimated = false)

    @Test fun stripsSitAboveAndBelowTheClockNotOnIt() {
        val box = androidx.compose.ui.geometry.Rect(.1f, .2f, .9f, .4f)
        val (above, below) = wallpaperBesideClock(box)
        org.junit.Assert.assertEquals(.1f, above!!.top, 1e-4f); org.junit.Assert.assertEquals(.2f, above.bottom, 1e-4f)
        org.junit.Assert.assertEquals(.4f, below!!.top, 1e-4f); org.junit.Assert.assertEquals(.5f, below.bottom, 1e-4f)
        org.junit.Assert.assertEquals(box.left, above.left, 1e-6f)
    }

    @Test fun aClockAtTheTopEdgeHasNoStripAboveIt() {
        val (above, below) = wallpaperBesideClock(androidx.compose.ui.geometry.Rect(.1f, 0f, .9f, .2f))
        org.junit.Assert.assertNull(above)
        org.junit.Assert.assertNotNull(below)
    }

    @Test fun theStricterOfTwoStripsKeepsTheWorstEnds() {
        val merged = strictest(sample(.3f, .1f, .5f), sample(.5f, .2f, .8f))
        org.junit.Assert.assertEquals(.1f, merged.under.p15, 1e-6f)
        org.junit.Assert.assertEquals(.8f, merged.under.p85, 1e-6f)
        org.junit.Assert.assertEquals(.4f, merged.under.mean, 1e-6f)
    }
}
