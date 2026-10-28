package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

/** BI-3: chrome text (A11Y-13) stops growing at a cap, so the Market's tab labels fit at 200% font scale instead of ending in "Featur...". */
class ChromeTextSizeTest {
    @Test fun `text up to the cap scales as the system says`() {
        assertEquals(11f, cappedTextSize(11f, fontScale = 1f, cap = 1.3f), 0.001f)
        assertEquals(11f, cappedTextSize(11f, fontScale = .85f, cap = 1.3f), 0.001f)
        assertEquals(11f, cappedTextSize(11f, fontScale = 1.3f, cap = 1.3f), 0.001f)
    }

    @Test fun `past the cap the size comes back down so the drawn text is no larger than the cap`() {
        // 11 sp at 2.0 draws as 22 sp, and capped it must draw as 11 x 1.3 = 14.3 sp.
        val size = cappedTextSize(11f, fontScale = 2f, cap = 1.3f)
        assertEquals(14.3f, size * 2f, 0.001f)
        assertEquals(14.3f, cappedTextSize(11f, fontScale = 3f, cap = 1.3f) * 3f, 0.001f)
    }

    @Test fun `a bad font scale changes nothing`() {
        assertEquals(11f, cappedTextSize(11f, fontScale = 0f, cap = 1.3f), 0.001f)
        assertEquals(11f, cappedTextSize(11f, fontScale = Float.NaN, cap = 1.3f), 0.001f)
    }
}
