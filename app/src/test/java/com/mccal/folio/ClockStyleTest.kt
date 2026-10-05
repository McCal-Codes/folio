package com.mccal.folio

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockStyleTest {
    private fun region(mean: Float, p15: Float = mean, p85: Float = mean) = RegionStats(mean, p15, p85, 128f, 128f, 128f)
    private fun sample(under: RegionStats, suggestions: List<Int> = emptyList()) = ClockInkSample(under, under, suggestions)

    @Test fun `contrast of white on black is 21 to 1 and is symmetric`() {
        assertEquals(21f, contrastRatio(1f, 0f), .01f)
        assertEquals(contrastRatio(1f, .2f), contrastRatio(.2f, 1f), 0f)
    }

    @Test fun `without a picture the clock keeps the whole wallpaper ink, and White stays white`() {
        assertEquals(Color.White, resolveClockInk(BigClockStyle(), null, fallbackDark = false).color)
        assertEquals(FolioColors.SecondaryBackground, resolveClockInk(BigClockStyle(), null, fallbackDark = true).color)
        assertEquals(Color.White, resolveClockInk(BigClockStyle(mode = "WHITE"), null, fallbackDark = true).color)
    }

    @Test fun `automatic picks dark ink over a pale picture and white over a dark one`() {
        assertEquals(FolioColors.SecondaryBackground, resolveClockInk(BigClockStyle(), sample(region(.85f)), fallbackDark = false).color)
        assertEquals(Color.White, resolveClockInk(BigClockStyle(), sample(region(.02f)), fallbackDark = true).color)
    }

    @Test fun `a busy middle-grey picture gets white ink on a soft shade instead of failing`() {
        val busy = region(mean = .2f, p15 = .02f, p85 = .6f)
        val result = resolveClockInk(BigClockStyle(), sample(busy), fallbackDark = false)
        assertEquals(Color.White, result.color)
        assertTrue(result.shade)
    }

    @Test fun `white over a pale picture is shaded, not left unreadable`() {
        val pale = sample(region(.9f))
        val white = resolveClockInk(BigClockStyle(mode = "WHITE"), pale, fallbackDark = false)
        assertEquals(Color.White, white.color)
        assertTrue(white.shade)
        assertFalse(resolveClockInk(BigClockStyle(mode = "WHITE"), sample(region(.02f)), fallbackDark = false).shade)
    }

    @Test fun `custom only offers colors that hold AA under the clock, and falls back to automatic with none`() {
        val darkSky = sample(region(.02f), listOf(0xFFFFE066.toInt(), 0xFF101820.toInt()))
        assertEquals(listOf(0xFFFFE066.toInt()), darkSky.readableSuggestions())
        assertEquals(Color(0xFFFFE066.toInt()), resolveClockInk(BigClockStyle(mode = "CUSTOM"), darkSky, false).color)
        val none = sample(region(.02f), emptyList())
        assertEquals(Color.White, resolveClockInk(BigClockStyle(mode = "CUSTOM", customIndex = 3), none, false).color)
    }

    @Test fun `the default style is exactly what the clock always drew`() {
        val d = BigClockStyle()
        assertEquals("AUTO", d.mode); assertEquals(600, d.weight); assertEquals(1f, d.size, 0f)
        assertEquals("SANS", d.face); assertEquals("SOFT", d.shadow); assertEquals("LONG", d.date)
        assertTrue(d.showNext); assertEquals("CENTER", d.align)
    }

    @Test fun `every look is a distinct combination, and Tinted is the only one that needs a picture`() {
        val keys = ClockLooks.map { listOf(it.mode, it.weight, it.face, it.shadow) }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(listOf("tinted"), ClockLooks.filter { it.mode == "WALLPAPER" }.map { it.id })
    }
}
