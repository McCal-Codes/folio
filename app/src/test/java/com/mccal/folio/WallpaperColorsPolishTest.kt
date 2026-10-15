package com.mccal.folio

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Two follow-ups to #353 from the Fold8 on 14 Oct: the Big Clock offered two periwinkle dots you could not tell apart, and
 * tinted icons' "Wallpaper color" dot stayed gray with Android's wallpaper because One UI shares no wallpaper colors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WallpaperColorsPolishTest {
    // ---- CIEDE2000 against Sharma, Wu and Dalal's published test pairs ----

    @Test fun `CIEDE2000 matches the published test pairs`() {
        fun de(l1: Double, a1: Double, b1: Double, l2: Double, a2: Double, b2: Double) =
            deltaE2000(doubleArrayOf(l1, a1, b1), doubleArrayOf(l2, a2, b2))
        assertEquals(2.0425, de(50.0, 2.6772, -79.7751, 50.0, 0.0, -82.7485), 1e-4)
        assertEquals(2.3669, de(50.0, 0.0, 0.0, 50.0, -1.0, 2.0), 1e-4)
        assertEquals(7.1792, de(50.0, 2.49, -0.001, 50.0, -2.49, 0.0009), 1e-4) // hues either side of 0 degrees
        assertEquals(27.1492, de(50.0, 2.5, 0.0, 73.0, 25.0, -18.0), 1e-4)
        assertEquals(1.2644, de(60.2574, -34.0099, 36.2677, 60.4626, -34.1751, 39.4387), 1e-4)
        assertEquals(0.0, deltaE2000(argbToLab(0xFF7785D9.toInt()), argbToLab(0xFF7785D9.toInt())), 1e-9)
    }

    // ---- collapsing colors too close to tell apart ----

    private val periwinkle = 0xFF7785D9.toInt()
    private val periwinkleTwin = 0xFF7784D9.toInt()
    private val lavender = 0xFFC577D9.toInt()
    private val orange = 0xFFE07A1F.toInt()

    @Test fun `near twins collapse into the first, in order, and different colors stay`() {
        assertTrue(looksSame(periwinkle, periwinkleTwin))
        assertFalse(looksSame(periwinkle, lavender))
        assertEquals(listOf(periwinkle, lavender, orange), distinctColors(listOf(periwinkle, periwinkleTwin, lavender, orange)))
        assertEquals(listOf(periwinkleTwin, lavender), distinctColors(listOf(periwinkleTwin, lavender, periwinkle)))
        assertEquals("one color of many twins is kept", listOf(periwinkle), distinctColors(listOf(periwinkle, periwinkleTwin, periwinkle)))
        assertEquals(emptyList<Int>(), distinctColors(emptyList()))
    }

    // One UI's palette for a periwinkle wallpaper: accents 1 and 2 share a hue, so vividTint makes them near twins.
    private val palette = SystemPalette(0xFF6F78B0.toInt(), 0xFF747892.toInt(), 0xFF8E6F96.toInt())
    private val paletteSample = clockSampleFromTone(WallpaperTone(fromAndroid = true, palette = palette))!!

    @Test fun `the clock's suggestions drop the near twin from the system palette`() {
        assertEquals(listOf(vividTint(palette.accent1), vividTint(palette.accent3)), paletteSample.readableSuggestions())
    }

    @Test fun `the color dots leave out the one the Wallpaper dot already shows, keeping each saved index`() {
        val tint = paletteSample.wallpaperTint()!!
        assertEquals(vividTint(palette.accent1), tint)
        assertEquals(listOf(IndexedValue(1, vividTint(palette.accent3))), paletteSample.colorDots())
        // A clock saved on the first color still draws it, and the Wallpaper dot shows as chosen for it.
        assertEquals(Color(tint), resolveClockInk(BigClockStyle(mode = "CUSTOM", customIndex = 0), paletteSample, false).color)
        assertTrue(paletteSample.wallpaperDotSelected(BigClockStyle(mode = "CUSTOM", customIndex = 0)))
        assertTrue(paletteSample.wallpaperDotSelected(BigClockStyle(mode = "WALLPAPER")))
        assertFalse(paletteSample.wallpaperDotSelected(BigClockStyle(mode = "CUSTOM", customIndex = 1)))
        assertFalse(paletteSample.wallpaperDotSelected(BigClockStyle()))
        assertEquals(Color(vividTint(palette.accent3)), resolveClockInk(BigClockStyle(mode = "CUSTOM", customIndex = 1), paletteSample, false).color)
    }

    @Test fun `a picture with no Wallpaper dot shows every readable color`() {
        // Pale under the clock, so the tint of what is under it fails AA and there is no Wallpaper dot.
        val pale = RegionStats(.9f, .9f, .9f, 240f, 240f, 240f)
        val picture = ClockInkSample(pale, pale, listOf(0xFF1A237E.toInt(), 0xFF4A148C.toInt()))
        assertNull(picture.wallpaperTint())
        assertEquals(picture.readableSuggestions(), picture.colorDots().map { it.value })
        assertEquals(listOf(0, 1), picture.colorDots().map { it.index })
    }

    // ---- tinted icons' "Wallpaper color" ----

    private val blue = 0xFF1E5AA8.toInt()

    @Test fun `Wallpaper color uses Android's wallpaper colors first`() {
        val tone = WallpaperTone(primary = blue, fromAndroid = true, palette = palette)
        assertEquals(blue, tone.choiceColor())
        assertEquals(vividTint(blue), wallpaperIconTint(tone))
    }

    @Test fun `Wallpaper color falls back to the system palette when Android shares no colors`() {
        val none: android.app.WallpaperColors? = null
        val tone = none.tone(fromAndroid = true, palette = palette)
        assertEquals(palette.accent1, tone.choiceColor())
        assertEquals(vividTint(palette.accent1), wallpaperIconTint(tone))
        assertNull("the rail and the glass still see no wallpaper color", tone.primary)
    }

    @Test fun `Wallpaper color is none with neither source, and a Folio picture uses its own color only`() {
        assertNull(wallpaperIconTint(WallpaperTone(fromAndroid = true)))
        assertEquals(blue, WallpaperTone(primary = blue).choiceColor())
        assertNull(WallpaperTone(palette = palette).choiceColor())
    }
}
