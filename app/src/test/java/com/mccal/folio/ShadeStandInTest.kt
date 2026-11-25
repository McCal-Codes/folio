package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The frosted stand-in that holds the place of Home's blurred picture while the panel opens, and hands over to it. */
class ShadeStandInTest {
    @Test fun `while the picture is pending the stand-in is at full strength`() {
        assertEquals(SHADE_STAND_IN_ALPHA, shadeStandInAlpha(pending = true, hasPicture = false, fade = 0f), 0f)
    }

    @Test fun `the hand-over is continuous, then eases to nothing as the picture arrives`() {
        val atArrival = shadeStandInAlpha(pending = false, hasPicture = true, fade = 0f)
        assertEquals("no step when the picture shows up", SHADE_STAND_IN_ALPHA, atArrival, 1e-6f)
        var last = atArrival
        for (i in 1..20) {
            val a = shadeStandInAlpha(pending = false, hasPicture = true, fade = .94f * i / 20f)
            assertTrue(a <= last + 1e-6f); last = a
        }
        assertEquals(0f, last, 1e-6f)
    }

    @Test fun `with no picture expected and none shown there is no stand-in`() {
        assertEquals(0f, shadeStandInAlpha(pending = false, hasPicture = false, fade = 0f), 0f)
        assertEquals("an overshooting fade is held to the end", 0f, shadeStandInAlpha(false, true, 5f), 1e-6f)
    }
}
