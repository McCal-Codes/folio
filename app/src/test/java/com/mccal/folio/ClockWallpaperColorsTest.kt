package com.mccal.folio

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #330: with Android's own wallpaper behind Home the Big Clock offered only Automatic and White. Android shares the
 * wallpaper's main colors with every app, so Wallpaper and the color dots come from those now, with the soft shade behind
 * the time because Folio can't measure the contrast under the clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClockWallpaperColorsTest {
    private val blue = 0xFF1E5AA8.toInt()
    private val orange = 0xFFE07A1F.toInt()
    private val green = 0xFF2E8B57.toInt()
    private val colors = sampleFromWallpaperColors(blue, listOf(orange, green))

    @Test fun `Android's wallpaper colors make an estimated sample with a tint and every color offered`() {
        assertTrue(colors.estimated)
        assertNotNull("the Tinted look and the Wallpaper dot are offered", colors.wallpaperTint())
        assertEquals(listOf(blue, orange, green).map(::vividTint).distinct(), colors.readableSuggestions())
        assertEquals("a wallpaper with one color still offers it", 1, sampleFromWallpaperColors(blue, emptyList()).readableSuggestions().size)
    }

    @Test fun `Wallpaper and a picked color draw that color with the soft shade behind it`() {
        val tinted = resolveClockInk(BigClockStyle(mode = "WALLPAPER"), colors, fallbackDark = false)
        assertEquals(Color(colors.wallpaperTint()!!), tinted.color)
        assertTrue(tinted.shade)
        val second = resolveClockInk(BigClockStyle(mode = "CUSTOM", customIndex = 1), colors, fallbackDark = false)
        assertEquals(Color(vividTint(orange)), second.color)
        assertTrue(second.shade)
        // An index past the end falls back to the first color, as with a Folio picture.
        assertEquals(Color(vividTint(blue)), resolveClockInk(BigClockStyle(mode = "CUSTOM", customIndex = 9), colors, false).color)
    }

    @Test fun `Automatic and White draw exactly as before over Android's wallpaper`() {
        assertEquals(resolveClockInk(BigClockStyle(), null, fallbackDark = true), resolveClockInk(BigClockStyle(), colors, fallbackDark = true))
        assertEquals(resolveClockInk(BigClockStyle(), null, fallbackDark = false), resolveClockInk(BigClockStyle(), colors, fallbackDark = false))
        val white = resolveClockInk(BigClockStyle(mode = "WHITE"), colors, fallbackDark = true)
        assertEquals(Color.White, white.color)
        assertFalse(white.shade)
    }

    @Test fun `a Folio picture still only offers colors that hold AA under the clock`() {
        val region = RegionStats(.02f, .02f, .02f, 10f, 10f, 10f)
        val picture = ClockInkSample(region, region, listOf(0xFFFFE066.toInt(), 0xFF101820.toInt()))
        assertFalse(picture.estimated)
        assertEquals(listOf(0xFFFFE066.toInt()), picture.readableSuggestions())
        assertFalse(resolveClockInk(BigClockStyle(mode = "CUSTOM"), picture, false).shade)
    }
}
