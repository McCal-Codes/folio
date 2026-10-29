package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupPlacementTest {
    @Test fun `a menu centers on its icon when the window has room`() {
        assertEquals(100f, popupLeft(center = 230f, width = 260f, window = 800f, margin = 14f), 0.01f)
    }

    @Test fun `a menu near an edge is held inside the margin`() {
        assertEquals(14f, popupLeft(center = 20f, width = 260f, window = 800f, margin = 14f), 0.01f)
        assertEquals(800f - 260f - 14f, popupLeft(center = 790f, width = 260f, window = 800f, margin = 14f), 0.01f)
    }

    @Test fun `a window narrower than the menu does not throw`() {
        // 200 dp wide: 200 - 260 - 14 is below the 14 dp margin, which used to make coerceIn throw.
        assertEquals(14f, popupLeft(center = 100f, width = 260f, window = 200f, margin = 14f), 0.01f)
        assertEquals(14f, popupLeft(center = 0f, width = 260f, window = 200f, margin = 14f), 0.01f)
    }

    @Test fun `a zero width window does not throw`() {
        assertEquals(12f, popupLeft(center = 0f, width = 0f, window = 0f, margin = 12f), 0.01f)
        assertEquals(12f, popupLeft(center = 0f, width = 320f, window = 0f, margin = 12f), 0.01f)
    }

    @Test fun `every window from zero to wide gives a left edge at or past the margin`() {
        for (w in 0..1200 step 7) for (c in 0..w step 31) {
            val left = popupLeft(c.toFloat(), 260f, w.toFloat(), 14f)
            assertTrue("window $w center $c", left >= 14f)
            if (w >= 260 + 28) assertTrue("window $w center $c", left + 260f <= w - 14f + 0.01f)
        }
    }

    @Test fun `the panel width from a narrow window pins instead of rounding into a throw`() {
        // AppPanel: panelW = min(320, window - 24), so the upper bound equals the margin up to float rounding.
        for (density in listOf(2f, 2.625f, 2.75f, 3f, 3.5f)) for (dp in 120..343) {
            val windowPx = dp * density
            val panelPx = minOf(320f * density, windowPx - 24f * density)
            val left = popupLeft(windowPx / 2f, panelPx, windowPx, 12f * density)
            assertTrue("dp $dp density $density", left >= 12f * density)
        }
    }

    @Test fun `the lifted bar stays between floor and ceiling and tolerates a ceiling under the floor`() {
        assertEquals(300, liftedBarOffset(300, 40, 900))
        assertEquals(40, liftedBarOffset(-5, 40, 900))
        assertEquals(900, liftedBarOffset(5000, 40, 900))
        assertEquals(40, liftedBarOffset(100, 40, 20))
    }
}
