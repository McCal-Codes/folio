package com.mccal.folio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The recipe behind Folio's own wallpapers: the HSL math, what is saved, and that a drawn piece follows its tweaks. */
class WallpaperRecipeTest {
    private fun draw(id: String, tweaks: WallpaperTweaks, n: Int = 48): IntArray {
        val out = IntArray(n * n)
        renderHills(WallpaperRecipes.of(id)!!, tweaks, n) { y, row -> row.copyInto(out, y * n) }
        return out
    }

    private fun channels(pixel: Int) = intArrayOf((pixel shr 16) and 0xFF, (pixel shr 8) and 0xFF, pixel and 0xFF)

    @Test
    fun `no change leaves a color exactly as it is`() {
        val c = tweakedRgb(0x2A6A70, ColorTweak())
        assertEquals(0x2A / 255f, c[0], 1e-6f)
        assertEquals(0x6A / 255f, c[1], 1e-6f)
        assertEquals(0x70 / 255f, c[2], 1e-6f)
    }

    @Test
    fun `full lightness is white and none is black`() {
        assertArrayEquals(floatArrayOf(1f, 1f, 1f), tweakedRgb(0x2A6A70, ColorTweak(lightness = 100f)), 1e-4f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), tweakedRgb(0x2A6A70, ColorTweak(lightness = -100f)), 1e-4f)
    }

    @Test
    fun `no saturation is grey and the hue turns a color around the wheel`() {
        val grey = tweakedRgb(0x2A6A70, ColorTweak(saturation = -100f))
        assertEquals(grey[0], grey[1], 1e-4f); assertEquals(grey[1], grey[2], 1e-4f)
        // Pure red a third of the way round is pure green, two thirds is blue.
        assertArrayEquals(floatArrayOf(0f, 1f, 0f), tweakedRgb(0xFF0000, ColorTweak(hue = 120f)), 1e-4f)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f), tweakedRgb(0xFF0000, ColorTweak(hue = -120f)), 1e-4f)
        assertArrayEquals(tweakedRgb(0xFF0000, ColorTweak()), tweakedRgb(0xFF0000, ColorTweak(hue = 360f)), 1e-4f)
    }

    @Test
    fun `a part's change stacks on the change to everything`() {
        val t = WallpaperTweaks(all = ColorTweak(hue = 10f), sky = ColorTweak(hue = 20f, lightness = 5f))
        assertEquals(30f, t.effective(TweakPart.SKY).hue, 0f)
        assertEquals(10f, t.effective(TweakPart.HILLS).hue, 0f)
        assertEquals(10f, t.effective(TweakPart.ALL).hue, 0f)
    }

    @Test
    fun `changes read back as saved and anything else is no change`() {
        val t = WallpaperTweaks(ColorTweak(95f, -10f, 5f), ColorTweak(), ColorTweak(-140f, 0f, 20f), ColorTweak(0f, 30f, 0f))
        assertEquals(t, WallpaperTweaks.parse(t.save()))
        assertTrue(WallpaperTweaks.parse(WallpaperTweaks.NONE.save()).isIdentity)
        for (bad in listOf(null, "", "1,2,3", "a,b,c;1,2,3;1,2,3;1,2,3", "1,2;1,2,3;1,2,3;1,2,3", "1,2,3;1,2,3;1,2,3;1,2,3;1,2,3")) {
            assertTrue("'$bad' must be no change", WallpaperTweaks.parse(bad).isIdentity)
        }
        // A hand-edited out-of-range value is held to the sliders' range.
        assertEquals(100f, WallpaperTweaks.parse("0,500,0;0,0,0;0,0,0;0,0,0").all.saturation, 0f)
    }

    @Test
    fun `both pieces have a recipe and others do not`() {
        assertTrue(WallpaperRecipes.isCustomizable("moon-hills"))
        assertTrue(WallpaperRecipes.isCustomizable("ember-hills"))
        assertFalse(WallpaperRecipes.isCustomizable("sage-dunes"))
        assertFalse(WallpaperRecipes.isCustomizable("wavy-blue-lines"))
        assertNull(WallpaperRecipes.of("sage-dunes"))
        // Every recipe piece is also a built-in picture with a credit, so there is always a shipped file to start from.
        for (id in listOf("moon-hills", "ember-hills")) assertTrue(id in BackgroundLibrary.builtInIds)
    }

    @Test
    fun `drawing is steady and a change moves the picture`() {
        val base = draw("moon-hills", WallpaperTweaks.NONE)
        assertArrayEquals(base, draw("moon-hills", WallpaperTweaks.NONE))
        val turned = draw("moon-hills", WallpaperTweaks(all = ColorTweak(hue = 150f)))
        assertFalse(base.contentEquals(turned))
        assertFalse(draw("moon-hills", WallpaperTweaks.NONE).contentEquals(draw("ember-hills", WallpaperTweaks.NONE)))
    }

    @Test
    fun `each part changes only its own pixels`() {
        val n = 48
        val base = draw("ember-hills", WallpaperTweaks.NONE, n)
        val hills = draw("ember-hills", WallpaperTweaks(hills = ColorTweak(lightness = 60f)), n)
        // The top row is open sky, far from the glow: hills are not in it, so it must not move; a row near the bottom is all hills.
        for (x in 0 until n) assertEquals(base[x], hills[x])
        assertNotEquals(base[(n - 2) * n + n / 2], hills[(n - 2) * n + n / 2])
        val sky = draw("ember-hills", WallpaperTweaks(sky = ColorTweak(lightness = 60f)), n)
        assertNotEquals(base[0], sky[0])
        assertEquals(base[(n - 1) * n + n / 2], sky[(n - 1) * n + n / 2])
    }

    @Test
    fun `the moon is lighter than the sky around it and follows the glow tweak`() {
        val n = 96
        val recipe = WallpaperRecipes.of("moon-hills")!!
        val moon = recipe.moon!!
        val cx = (moon.x * n).toInt(); val cy = (moon.y * n).toInt()
        val base = draw("moon-hills", WallpaperTweaks.NONE, n)
        val atMoon = channels(base[cy * n + cx]); val beside = channels(base[cy * n + (cx - n / 4).coerceAtLeast(0)])
        assertTrue("moon should be paler than the sky beside it", atMoon.sum() > beside.sum())
        val dim = draw("moon-hills", WallpaperTweaks(glow = ColorTweak(lightness = -80f)), n)
        assertTrue(channels(dim[cy * n + cx]).sum() < atMoon.sum())
    }

    @Test
    fun `every pixel is opaque`() {
        for (id in listOf("moon-hills", "ember-hills")) for (p in draw(id, WallpaperTweaks(all = ColorTweak(hue = 200f, saturation = 100f, lightness = 100f)))) {
            assertEquals(0xFF, p ushr 24)
        }
        assertNotNull(draw("moon-hills", WallpaperTweaks.NONE))
    }
}
