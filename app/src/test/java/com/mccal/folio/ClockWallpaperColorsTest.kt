package com.mccal.folio

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    // ---- the Fold8 check of #353: the colors come from Home's wallpaper tone, decided by the user's setting ----

    private val androidTone = WallpaperTone(primary = blue, secondary = orange, tertiary = green, fromAndroid = true)

    @Test fun `a tone from Android's wallpaper gives the clock all three of its colors`() {
        val sample = clockSampleFromTone(androidTone)!!
        assertTrue(sample.estimated)
        assertNotNull("Tinted is offered", sample.wallpaperTint())
        assertEquals(listOf(blue, orange, green).map(::vividTint).distinct(), sample.readableSuggestions())
    }

    @Test fun `when Android shares no colors the clock has none to offer and does not make any up`() {
        assertNull(clockSampleFromTone(WallpaperTone(fromAndroid = true)))
        val none: android.app.WallpaperColors? = null
        val tone = none.tone(fromAndroid = true)
        assertTrue(tone.fromAndroid)
        assertNull(tone.primary); assertNull(tone.secondary); assertNull(tone.tertiary)
    }

    @Test fun `Android's colors carry the tertiary color and say where they came from`() {
        val colors = android.app.WallpaperColors(android.graphics.Color.valueOf(blue), android.graphics.Color.valueOf(orange),
            android.graphics.Color.valueOf(green))
        assertEquals(androidTone, colors.tone(fromAndroid = true))
        assertFalse("a Folio picture's tone is not Android's", colors.tone().fromAndroid)
    }

    @Test fun `Automatic draws the same ink before and after the first change in Customize`() {
        // Before any change the clock is not customized and draws Home's ink; after one it resolves Automatic. With
        // Android's wallpaper those must match, or the time flips from white to dark on the first tap (the Fold8 check).
        for (dark in listOf(false, true)) {
            val untouched = HomeInk(dark).primary
            assertEquals(untouched, resolveClockInk(BigClockStyle(), clockSampleFromTone(androidTone), fallbackDark = dark).color)
            assertEquals(untouched, resolveClockInk(BigClockStyle(face = "SERIF"), clockSampleFromTone(androidTone), fallbackDark = dark).color)
            assertEquals(untouched, resolveClockInk(BigClockStyle(), clockSampleFromTone(WallpaperTone(fromAndroid = true)), fallbackDark = dark).color)
        }
    }

    @Test fun `Diagnostics says what Android returned for the wallpaper colors`() {
        val text = Diagnostics.wallpaperReportText(systemWallpaper = true, folioPictureSaved = true, home = listOf(blue, orange, green), lock = null)
        assertTrue(text, text.contains("behind Home: Android's wallpaper (a Folio picture is saved)"))
        assertTrue(text, text.contains("Android's colors, home: #1E5AA8 #E07A1F #2E8B57"))
        assertTrue(text, text.contains("Android's colors, lock: none"))
        assertTrue(Diagnostics.wallpaperReportText(false, false, null, emptyList()).contains("Folio's background (a Folio picture is not saved)"))
    }

    // ---- the Fold8 check of 14 Oct: One UI shares no wallpaper colors, so the phone's theme colors come second ----

    private val purple = 0xFF6750A4.toInt()
    private val mauve = 0xFF625B71.toInt()
    private val rose = 0xFF7D5260.toInt()
    private val palette = SystemPalette(purple, mauve, rose)

    @Test fun `Android's wallpaper colors win over the system palette`() {
        val both = androidTone.copy(palette = palette)
        assertEquals(WallpaperColorSource.ANDROID_COLORS, both.colorSource())
        assertEquals(clockSampleFromTone(androidTone), clockSampleFromTone(both))
    }

    @Test fun `with no wallpaper colors the system palette gives the clock its three accents`() {
        val tone = WallpaperTone(fromAndroid = true, palette = palette)
        assertEquals(WallpaperColorSource.SYSTEM_PALETTE, tone.colorSource())
        val sample = clockSampleFromTone(tone)!!
        assertTrue("still estimated, so the soft shade goes behind the time", sample.estimated)
        assertEquals(listOf(purple, mauve, rose).map(::vividTint).distinct(), sample.readableSuggestions())
        assertEquals(vividTint(purple), sample.wallpaperTint())
        assertTrue(resolveClockInk(BigClockStyle(mode = "WALLPAPER"), sample, fallbackDark = false).shade)
    }

    @Test fun `neither source leaves none, and a Folio picture never uses the palette`() {
        assertEquals(WallpaperColorSource.NONE, WallpaperTone(fromAndroid = true).colorSource())
        assertEquals(WallpaperColorSource.NONE, WallpaperTone(primary = blue, palette = palette).colorSource())
    }

    @Test fun `the palette stays out of the wallpaper's own colors that the rail and glass read`() {
        val none: android.app.WallpaperColors? = null
        val tone = none.tone(fromAndroid = true, palette = palette)
        assertNull(tone.primary); assertNull(tone.secondary); assertNull(tone.tertiary)
        assertFalse("no hints from a palette, so Home's ink is unchanged", tone.prefersDarkText)
        assertEquals(palette, tone.palette)
    }

    @Test fun `Automatic draws the same ink with palette colors`() {
        val sample = clockSampleFromTone(WallpaperTone(fromAndroid = true, palette = palette))
        for (dark in listOf(false, true)) assertEquals(HomeInk(dark).primary, resolveClockInk(BigClockStyle(), sample, fallbackDark = dark).color)
    }

    @Test fun `Diagnostics names the source of the Big Clock's colors`() {
        assertTrue(Diagnostics.wallpaperReportText(true, false, listOf(blue), null, palette.colors).contains("Big Clock colors: from Android's wallpaper colors"))
        val fromPalette = Diagnostics.wallpaperReportText(true, false, null, null, palette.colors, "home_wallpaper")
        assertTrue(fromPalette, fromPalette.contains("Big Clock colors: from the system palette"))
        assertTrue(fromPalette, fromPalette.contains("system palette (accent 1, 2, 3 at 500): #6750A4 #625B71 #7D5260, theme color source: home_wallpaper"))
        assertTrue(Diagnostics.wallpaperReportText(true, false, null, null, null).contains("Big Clock colors: none"))
        assertTrue(Diagnostics.wallpaperReportText(false, true, listOf(blue), null, palette.colors).contains("Big Clock colors: from Folio's background"))
    }

    @Test fun `the theme color source is read from AOSP's setting`() {
        assertEquals("home_wallpaper", Diagnostics.themeColorSource("""{"android.theme.customization.color_source":"home_wallpaper","android.theme.customization.system_palette":"B1611C"}"""))
        assertEquals("preset", Diagnostics.themeColorSource("""{ "android.theme.customization.color_source" : "preset" }"""))
        assertNull(Diagnostics.themeColorSource("""{"android.theme.customization.system_palette":"B1611C"}"""))
        assertNull(Diagnostics.themeColorSource(null))
    }
}
