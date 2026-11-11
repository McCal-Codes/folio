package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The dock over another app keeps its apps whole and on the screen, and follows Dock › Size. */
class OverlayDockPlanTest {
    /** The panel as drawn: padding, the apps, a divider and the Home button, with a gap between each child. */
    private fun height(p: OverlayDockPlan, space: Float = 12f) =
        if (p.shown > 0) 2f * space + p.shown * p.icon + 1f + p.icon + (p.shown + 1) * space else 2f * space + p.icon

    @Test fun `a dock that fits shows every app`() {
        val plan = overlayDockPlan(915f, 6, 1f)
        assertEquals(6, plan.shown)
        assertEquals(50f, plan.icon, 0f)
        assertEquals(72f, plan.width, 0f)
    }

    @Test fun `it never runs off the screen, whatever the screen and size`() {
        for (h in listOf(320f, 411f, 568f, 640f, 740f, 915f, 1280f)) for (size in listOf(.8f, 1f, 1.6f)) for (count in 0..8) {
            val plan = overlayDockPlan(h, count, size)
            assertTrue("$h $size $count: shows ${plan.shown}", plan.shown in 0..count)
            assertTrue("$h $size $count: needs ${height(plan)}", plan.shown == 0 || height(plan) <= h - 32f + .5f)
        }
    }

    @Test fun `a short screen shows fewer apps, a tall one all of them`() {
        assertTrue(overlayDockPlan(500f, 8, 1f).shown < 8)
        assertEquals(8, overlayDockPlan(1100f, 8, 1f).shown)
    }

    @Test fun `larger dock apps are larger and fewer fit`() {
        val normal = overlayDockPlan(640f, 8, 1f)
        val big = overlayDockPlan(640f, 8, 1.6f)
        assertTrue(big.icon > normal.icon && big.width > normal.width)
        assertTrue(big.shown <= normal.shown)
    }
}
