package com.mccal.folio

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** StandBy at night stays readable: WCAG 1.4.3 asks 4.5:1 for small text and 3:1 for text as large as the clock. */
class StandByNightContrastTest {
    private fun luminance(argb: Long): Double {
        fun channel(c: Long) = (c / 255.0).let { if (it <= .03928) it / 12.92 else ((it + .055) / 1.055).pow(2.4) }
        return .2126 * channel(argb shr 16 and 0xFF) + .7152 * channel(argb shr 8 and 0xFF) + .0722 * channel(argb and 0xFF)
    }
    private fun contrast(a: Long, b: Long): Double {
        val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (light + .05) / (dark + .05)
    }
    private val black = 0xFF000000L
    private val card = FolioColors.Value.StandByNightCard

    @Test fun `the big clock reads at its size`() {
        assertTrue(contrast(FolioColors.Value.StandByNight, black) >= 3.0)
    }

    @Test fun `small night text reads on black and on the night cards`() {
        for (text in listOf(FolioColors.Value.StandByNightText, FolioColors.Value.StandByNightSoft)) {
            assertTrue("%08X on black".format(text), contrast(text, black) >= 4.5)
            assertTrue("%08X on the night card".format(text), contrast(text, card) >= 4.5)
        }
    }

    @Test fun `the old dimmed night text didn't`() {
        // The clock's red at 75% over black, what the date and chips used before: 2.2:1.
        val old = 0xFF861C16L
        assertTrue(contrast(old, black) < 4.5)
    }
}
