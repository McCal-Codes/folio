package com.mccal.folio

import com.mccal.folio.duet.DuetClip
import com.mccal.folio.duet.DuetMotionMath
import com.mccal.folio.duet.DuetStyles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Duo Clip's curves, as measured from the 16 Nov 2026 Duo clip (see DuetClip). */
class DuetClipTest {
    private fun easeInOutSine(t: Float) = (-(Math.cos(Math.PI * t) - 1) / 2).toFloat()
    /** The strength FoldTransition drives the effect with at a given share of the fold. */
    private fun strength(progress: Float) = easeInOutSine(progress) * DuetClip.HOLD

    @Test fun `progress takes the ease back out, even for a fold that has only just begun`() {
        for (p in listOf(.005f, .02f, .1f, .25f, .5f, .75f, .99f)) assertEquals(p, DuetClip.progress(strength(p)), 1e-4f)
        assertEquals(0f, DuetClip.progress(0f), 1e-6f)
        assertEquals("past the held strength it stays closed, not NaN", 1f, DuetClip.progress(5f), 1e-6f)
        assertEquals(0f, DuetClip.progress(-1f), 1e-6f)
    }

    @Test fun `the blur comes up to half within about 7 degrees, and is full by about 22 degrees`() {
        assertEquals(0f, DuetClip.blur(0f), 0f)
        assertEquals(0f, DuetClip.blur(DuetClip.BLUR_FROM), 0f)
        assertEquals(DuetClip.BLUR_JUMP, DuetClip.blur(DuetClip.BLUR_JUMP_AT), 1e-6f)
        assertEquals(1f, DuetClip.blur(DuetClip.BLUR_FULL_AT), 1e-6f)
        assertEquals(1f, DuetClip.blur(.9f), 0f)
        assertTrue("nearly half the blur is there by 6 degrees", DuetClip.blur(.033f) > .4f)
    }

    @Test fun `the shade starts at the outer edge and the hinge strip keeps its light longer`() {
        assertEquals(0f, DuetClip.shade(0f, 1f), 0f)
        assertTrue("a degree or two in, the shade has barely begun", DuetClip.shade(.01f, 1f) < .12f)
        val a = .19f
        assertTrue("outer edge is about four fifths dark at 34 degrees", DuetClip.shade(a, 1f) > .75f)
        assertTrue("the middle is about half dark", DuetClip.shade(a, .4f) in .3f..DuetClip.shade(a, 1f))
        assertEquals("the strip next to the hinge is untouched", 0f, DuetClip.shade(a, 0f), 0f)
        assertEquals(DuetClip.SHADE_PEAK, DuetClip.shade(1f, 0f), 1e-6f)
        assertEquals("closed, the whole half is black", DuetClip.SHADE_PEAK, DuetClip.shade(1f, .5f), 1e-6f)
    }

    @Test fun `the reflection shows only while the half is most of the way over, and fades toward the outer edge`() {
        assertEquals(0f, DuetClip.reflect(0f, 0f), 0f)
        assertEquals(0f, DuetClip.reflect(.1f, 0f), 0f)
        assertEquals("gone by the time it is nearly closed", 0f, DuetClip.reflect(.7f, 0f), 0f)
        assertEquals(DuetClip.REFLECT_PEAK, DuetClip.reflect(.37f, 0f), 1e-6f)
        assertTrue(DuetClip.reflect(.37f, .5f) < DuetClip.reflect(.37f, 0f))
        assertEquals("nothing at the outer edge", 0f, DuetClip.reflect(.37f, 1f), 1e-6f)
        // Opening starts where the Fold8 lights its panel (about 125 degrees), already inside the window.
        assertTrue(DuetClip.reflect(DuetClip.progress(DuetStyles.CLIP.startAt), 0f) > .3f)
    }

    @Test fun `the curves only ever rise with the fold`() {
        for (edge in listOf(0f, .12f, .4f, .8f, 1f)) {
            var blur = 0f; var shade = 0f
            for (i in 0..200) {
                val p = i / 200f
                assertTrue(DuetClip.blur(p) >= blur - 1e-6f); assertTrue(DuetClip.shade(p, edge) >= shade - 1e-6f)
                blur = DuetClip.blur(p); shade = DuetClip.shade(p, edge)
            }
        }
    }

    @Test fun `the opening frame, at the Fold8's panel, is fully blurred and the outer edge is dark`() {
        val start = DuetClip.progress(DuetStyles.CLIP.startAt)
        assertEquals("the Fold8's panel lights at 125 degrees, 30% folded", .30f, start, .02f)
        assertEquals(1f, DuetClip.blur(start), 0f)
        assertTrue(DuetClip.shade(start, 1f) > .85f)
        assertTrue("the hinge strip is still lit", DuetClip.shade(start, .05f) < .4f)
    }

    @Test fun `on the cover, Duo Clip's hinge is the screen's edge and the whole screen moves away from it`() {
        // Natural orientation: the cover's hinge edge is on the left, so the half moves after a hinge at 0.
        assertEquals(com.mccal.folio.FoldGeometry(false, 0f, true), com.mccal.folio.duet.clipCoverPanel(400f, 800f, com.mccal.folio.FoldGeometry(false, 123f, false)))
        // Rotated 180 degrees: the edge is on the right, so the half moves before a hinge at the far side.
        assertEquals(com.mccal.folio.FoldGeometry(false, 400f, false), com.mccal.folio.duet.clipCoverPanel(400f, 800f, com.mccal.folio.FoldGeometry(false, 123f, true)))
        // Top and bottom edges use the height.
        assertEquals(com.mccal.folio.FoldGeometry(true, 0f, true), com.mccal.folio.duet.clipCoverPanel(400f, 800f, com.mccal.folio.FoldGeometry(true, 5f, false)))
        assertEquals(com.mccal.folio.FoldGeometry(true, 800f, false), com.mccal.folio.duet.clipCoverPanel(400f, 800f, com.mccal.folio.FoldGeometry(true, 5f, true)))
    }

}

class DuetMotionTest {
    @Test fun `small turns are ignored and large ones are bounded`() {
        assertEquals(0f, DuetMotionMath.deadZone(.02f), 0f)
        assertEquals(0f, DuetMotionMath.deadZone(Float.NaN), 0f)
        assertTrue(DuetMotionMath.deadZone(.5f) > .4f)
        assertEquals(-DuetMotionMath.deadZone(.3f), DuetMotionMath.deadZone(-.3f), 1e-6f)
        assertEquals(1f, DuetMotionMath.offset(Math.PI.toFloat() / 3f), 1e-4f)
        assertEquals(1f, DuetMotionMath.offset(2f), 1e-4f)
    }

    @Test fun `thirty degrees stretches the half about 1_41 times and no turn goes past 4`() {
        val thirty = DuetMotionMath.offset((Math.PI / 6).toFloat())
        assertEquals(1.414f, DuetMotionMath.gain(thirty), .005f)
        assertEquals(1f, DuetMotionMath.gain(0f), 0f)
        assertEquals(4f, DuetMotionMath.gain(10f), 1e-5f)
        assertEquals(.25f, DuetMotionMath.gain(-10f), 1e-5f)
    }

    @Test fun `smoothing follows the target and a zero time constant jumps to it`() {
        assertEquals(1f, DuetMotionMath.smooth(0f, 1f, 16f, 0f), 0f)
        val a = DuetMotionMath.smooth(0f, 1f, 16f, 90f)
        assertTrue(a > 0f && a < .25f)
        assertTrue(DuetMotionMath.smooth(a, 1f, 16f, 90f) > a)
    }

    @Test fun `the reference settles after 350 ms, or 120 once the hand is still`() {
        assertTrue(!DuetMotionMath.settled(100_000_000L, 100_000_000L))
        assertTrue(DuetMotionMath.settled(130_000_000L, 90_000_000L))
        assertTrue(!DuetMotionMath.settled(300_000_000L, 10_000_000L))
        assertTrue(DuetMotionMath.settled(350_000_000L, 0L))
    }

    @Test fun `rotating the display turns the axes and four quarter turns come back`() {
        assertEquals(2f to 3f, DuetMotionMath.rotate(2f, 3f, 0))
        var p = 2f to 3f
        repeat(4) { p = DuetMotionMath.rotate(p.first, p.second, 1) }
        assertEquals(2f to 3f, p)
        assertEquals(-2f to -3f, DuetMotionMath.rotate(2f, 3f, 2))
    }

    @Test fun `Duo Clip is the one new style that has a tilt response, on by default`() {
        assertEquals(1f, DuetStyles.CLIP.perspective, 0f)
        assertEquals(0f, DuetStyles.reducedMotion(DuetStyles.CLIP).perspective, 0f)
    }
}
