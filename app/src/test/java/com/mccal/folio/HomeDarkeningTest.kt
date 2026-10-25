package com.mccal.folio

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDarkeningTest {
    private val none = HomeDarkening()
    private val dimmed = HomeDarkening(dim = .3f)
    private val scrimmed = HomeDarkening(dim = 0f, scrim = HomeScrim(top = .29f, bottom = .32f))

    @Test fun nothingDrawnMeansNoDarkening() {
        assertEquals(0f, none.alphaOver(Rect(.1f, .1f, .9f, .3f)), 1e-6f)
    }

    @Test fun theDimIsTheSameEverywhere() {
        assertEquals(.3f, dimmed.alphaOver(Rect(.1f, .1f, .9f, .3f)), 1e-4f)
        assertEquals(.3f, dimmed.alphaOver(Rect(.1f, .5f, .9f, .6f)), 1e-4f)
    }

    @Test fun theScrimOnlyReachesTheEdges() {
        // The clock near the top is under the top band; a box in the middle third is untouched.
        val atTop = scrimmed.alphaOver(Rect(.1f, .02f, .9f, .12f))
        val middle = scrimmed.alphaOver(Rect(.1f, .45f, .9f, .55f))
        assertTrue("under the top band (was $atTop)", atTop > .05f)
        assertEquals(0f, middle, 1e-6f)
    }

    @Test fun theScrimIsStrongestAtTheEdgeAndFadesInward() {
        val edge = scrimmed.alphaOver(Rect(.1f, 0f, .9f, .02f))
        val inner = scrimmed.alphaOver(Rect(.1f, .25f, .9f, .27f))
        assertTrue(edge > inner)
        assertTrue(edge <= .29f)
    }

    @Test fun dimAndScrimComposeLikeTwoBlacks() {
        val both = HomeDarkening(dim = .3f, scrim = HomeScrim(top = .29f, bottom = 0f))
        val top = both.alphaOver(Rect(.1f, 0f, .9f, .01f))
        // a + b - ab with the scrim near its peak: more than the dim alone, less than the sum.
        assertTrue(top > .3f && top < .3f + .29f)
    }

    @Test fun darkeningAPatchLowersItsLuminanceAndColor() {
        val region = RegionStats(mean = .6f, p15 = .5f, p85 = .7f, r = 200f, g = 180f, b = 160f)
        val sample = ClockInkSample(region, region, emptyList(), estimated = false)
        val darker = sample.darkenedBy(.3f)
        assertTrue(darker.under.p85 < sample.under.p85)
        assertEquals(140f, darker.under.r, 1e-3f)
        assertSame(sample, sample.darkenedBy(0f))
    }

    @Test fun aPaleSkyUnderTheDimNeedsLessShade() {
        // Pale and mixed behind the clock: raw, it needs a strong halo; once Home's dim is over it, a lighter one is enough.
        val raw = clockShade(.8f).alpha
        val dimmedSample = clockShade(.8f * Math.pow(.7, 2.2).toFloat()).alpha
        assertTrue(dimmedSample < raw)
    }

    private fun assertSame(a: Any, b: Any) = org.junit.Assert.assertSame(a, b)
}
