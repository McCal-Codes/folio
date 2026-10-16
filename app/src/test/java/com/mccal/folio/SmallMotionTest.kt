package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt

/** The small motion pieces: a badge pops only when a count first appears, and its spring never passes 1.12. */
class SmallMotionTest {
    @Test fun `a badge pops when a count appears on an icon that had none`() {
        assertTrue(badgeAppeared(0, 3))
        assertTrue(badgeAppeared(-1, 1))
    }

    @Test fun `a badge does not pop when the number changes or goes away`() {
        assertFalse(badgeAppeared(3, 4))
        assertFalse(badgeAppeared(4, 3))
        assertFalse(badgeAppeared(2, 0))
        assertFalse(badgeAppeared(0, 0))
    }

    @Test fun `the pop starts near 60 percent and the bounce spring stays under 1_12`() {
        assertEquals(.6f, BADGE_POP_FROM, 0f)
        val (damping, _) = FolioMotion.Bounce
        val overshoot = 100 * exp(-PI * damping / sqrt(1.0 - damping * damping))
        assertTrue("overshoot $overshoot%", 1 + overshoot / 100 < 1.12)
    }
}
