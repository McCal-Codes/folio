package com.mccal.folio

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Folio's own layered-hills wallpapers, drawn from a recipe instead of read from a file, so the colors can be changed.
 *
 * The shipped webp stays the picture a piece starts as (it is exact and costs nothing to draw). When someone changes the
 * colors, the same recipe is drawn on the device with the tweak applied: hue, saturation and lightness, for everything
 * at once or for the sky, the hills or the glow (and the moon, where a piece has one). Still pictures only: nothing
 * animates, so nothing can flash (WCAG 2.3.1).
 */

/** The parts of a piece that take their own color change. [ALL] moves them together. */
internal enum class TweakPart { ALL, SKY, HILLS, GLOW }

/**
 * A change to one part's colors. [hue] is degrees around the wheel, [saturation] and [lightness] are -100 to 100 and are
 * proportional (+100 lightness is white, -100 is black, saturation -100 is grey), so a small move is a small change on
 * any color and the dark colors in these pieces do not blow out.
 */
internal data class ColorTweak(val hue: Float = 0f, val saturation: Float = 0f, val lightness: Float = 0f) {
    val isIdentity: Boolean get() = hue == 0f && saturation == 0f && lightness == 0f
    operator fun plus(other: ColorTweak) = ColorTweak(hue + other.hue, saturation + other.saturation, lightness + other.lightness)
}

/** What a person changed on one piece: one tweak for everything, plus one for each part, which stack. */
internal data class WallpaperTweaks(
    val all: ColorTweak = ColorTweak(),
    val sky: ColorTweak = ColorTweak(),
    val hills: ColorTweak = ColorTweak(),
    val glow: ColorTweak = ColorTweak(),
) {
    val isIdentity: Boolean get() = all.isIdentity && sky.isIdentity && hills.isIdentity && glow.isIdentity

    operator fun get(part: TweakPart): ColorTweak = when (part) {
        TweakPart.ALL -> all
        TweakPart.SKY -> sky
        TweakPart.HILLS -> hills
        TweakPart.GLOW -> glow
    }

    fun with(part: TweakPart, tweak: ColorTweak): WallpaperTweaks = when (part) {
        TweakPart.ALL -> copy(all = tweak)
        TweakPart.SKY -> copy(sky = tweak)
        TweakPart.HILLS -> copy(hills = tweak)
        TweakPart.GLOW -> copy(glow = tweak)
    }

    /** What is actually applied to a part: the change for everything, plus that part's own. */
    fun effective(part: TweakPart): ColorTweak = if (part == TweakPart.ALL) all else all + this[part]

    /** Whole numbers, four groups: "hue,saturation,lightness" for everything, sky, hills and glow. */
    fun save(): String = listOf(all, sky, hills, glow).joinToString(";") {
        "${it.hue.roundToInt()},${it.saturation.roundToInt()},${it.lightness.roundToInt()}"
    }

    companion object {
        val NONE = WallpaperTweaks()

        /** Anything that does not read back exactly is no change at all: a bad value never recolors Home. */
        fun parse(text: String?): WallpaperTweaks {
            val groups = text?.split(';') ?: return NONE
            if (groups.size != 4) return NONE
            val tweaks = groups.map { group ->
                val n = group.split(',').map { it.trim().toFloatOrNull() ?: return NONE }
                if (n.size != 3) return NONE
                ColorTweak(n[0].coerceIn(-360f, 360f), n[1].coerceIn(-100f, 100f), n[2].coerceIn(-100f, 100f))
            }
            return WallpaperTweaks(tweaks[0], tweaks[1], tweaks[2], tweaks[3])
        }
    }
}

/** One ridge of hills: where it sits, how it rolls, the two colors it fades between and how much glow mists its top. */
internal class HillLayer(val base: Float, val top: Int, val bottom: Int, val amplitude: Float, val frequency: Float, val phase: Float, val mist: Float)

/** A pale disc with a soft halo. */
internal class Moon(val x: Float, val y: Float, val disc: Int, val halo: Int)

/** Everything needed to draw one piece. Colors are 0xRRGGBB; positions are fractions of the picture. */
internal class HillsRecipe(
    val sky: List<Pair<Float, Int>>,
    val glow: Int, val glowX: Float, val glowY: Float, val glowAmount: Float,
    val hills: List<Int>,
    val layers: List<HillLayer>,
    val moon: Moon?,
)

internal object WallpaperRecipes {
    /** The five tones the four-layer hills move through, lightest ridge to the darkest foreground. */
    private val HILL_COLORS = listOf(0x3F8A8A, 0x2D7478, 0x1E5A60, 0x133F46, 0x0C2C32)
    private val LAYERS = listOf(
        HillLayer(.58f, 0, 1, .040f, .9f, 1.3f, .28f),
        HillLayer(.68f, 1, 2, .045f, 1.2f, 2.6f, .18f),
        HillLayer(.79f, 2, 3, .040f, 1.0f, 4.1f, .10f),
        HillLayer(.90f, 3, 4, .030f, 1.4f, 5.3f, .05f),
    )

    private val recipes: Map<String, HillsRecipe> = mapOf(
        "moon-hills" to HillsRecipe(
            sky = listOf(0f to 0x0A1F24, .45f to 0x14414A, .7f to 0x2A6A70),
            glow = 0x8FD0C4, glowX = .5f, glowY = .55f, glowAmount = .55f,
            hills = HILL_COLORS, layers = LAYERS,
            moon = Moon(.56f, .455f, disc = 0xE4F6EE, halo = 0xCFEEE6),
        ),
        "ember-hills" to HillsRecipe(
            sky = listOf(0f to 0x0A1F24, .42f to 0x14414A, .62f to 0x2A6A70, .72f to 0x7A8F7E),
            glow = 0xF0B48A, glowX = .5f, glowY = .60f, glowAmount = .42f,
            hills = HILL_COLORS, layers = LAYERS, moon = null,
        ),
    )

    fun of(id: String): HillsRecipe? = recipes[id]
    fun isCustomizable(id: String) = id in recipes
}

private fun channel(rgb: Int, shift: Int) = ((rgb shr shift) and 0xFF) / 255f

/** [rgb] moved by [tweak] in HSL, as three 0 to 1 floats. */
internal fun tweakedRgb(rgb: Int, tweak: ColorTweak): FloatArray {
    val r = channel(rgb, 16); val g = channel(rgb, 8); val b = channel(rgb, 0)
    if (tweak.isIdentity) return floatArrayOf(r, g, b)
    val hi = max(r, max(g, b)); val lo = min(r, min(g, b))
    var l = (hi + lo) / 2f
    val d = hi - lo
    var s = 0f
    var h = 0f
    if (d > 0f) {
        s = if (l > .5f) d / (2f - hi - lo) else d / (hi + lo)
        h = when (hi) {
            r -> (g - b) / d + (if (g < b) 6f else 0f)
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
    }
    h = ((h + tweak.hue) % 360f + 360f) % 360f
    s = (s * (1f + tweak.saturation.coerceIn(-100f, 100f) / 100f)).coerceIn(0f, 1f)
    val lt = tweak.lightness.coerceIn(-100f, 100f) / 100f
    l = if (lt > 0f) l + (1f - l) * lt else l + l * lt
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (rr, gg, bb) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return floatArrayOf((rr + m).coerceIn(0f, 1f), (gg + m).coerceIn(0f, 1f), (bb + m).coerceIn(0f, 1f))
}

private fun smoothStep(a: Float, b: Float, t: Float): Float {
    val x = ((t - a) / (b - a)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun expf(x: Float) = exp(x.toDouble()).toFloat()

/** Draws a [recipe] with [tweaks] as an [n] by [n] square, one row at a time into [row] and handed to [emit] (so no whole-picture array is held). */
internal fun renderHills(recipe: HillsRecipe, tweaks: WallpaperTweaks, n: Int, emit: (y: Int, row: IntArray) -> Unit) {
    val skyStops = recipe.sky.map { it.first to tweakedRgb(it.second, tweaks.effective(TweakPart.SKY)) }
    val hills = recipe.hills.map { tweakedRgb(it, tweaks.effective(TweakPart.HILLS)) }
    val glowTweak = tweaks.effective(TweakPart.GLOW)
    val glow = tweakedRgb(recipe.glow, glowTweak)
    val moonDisc = recipe.moon?.let { tweakedRgb(it.disc, glowTweak) }
    val moonHalo = recipe.moon?.let { tweakedRgb(it.halo, glowTweak) }
    val ridges = Array(recipe.layers.size) { i ->
        val l = recipe.layers[i]
        FloatArray(n) { x ->
            val u = x.toFloat() / n
            val a = (u * 2f * Math.PI.toFloat() * l.frequency + l.phase)
            val b = (u * 2f * Math.PI.toFloat() * l.frequency * 2.3f + l.phase * 1.9f)
            l.base + l.amplitude * kotlin.math.sin(a) + l.amplitude * .4f * kotlin.math.sin(b)
        }
    }
    val row = IntArray(n)
    val sky = FloatArray(3)
    val rgb = FloatArray(3)
    for (y in 0 until n) {
        val v = y.toFloat() / n
        // Piecewise-linear through the sky stops, held flat past the first and last.
        var k = 0
        while (k < skyStops.size - 1 && v > skyStops[k + 1].first) k++
        if (k >= skyStops.size - 1) {
            val c = skyStops.last().second
            sky[0] = c[0]; sky[1] = c[1]; sky[2] = c[2]
        } else {
            val (p0, c0) = skyStops[k]; val (p1, c1) = skyStops[k + 1]
            val t = ((v - p0) / (p1 - p0)).coerceIn(0f, 1f)
            for (q in 0..2) sky[q] = c0[q] * (1f - t) + c1[q] * t
        }
        val gy = (v - recipe.glowY) * 1.2f
        for (x in 0 until n) {
            val u = x.toFloat() / n
            val gx = u - recipe.glowX
            val g = expf(-(gx * gx + gy * gy) * 4f) * recipe.glowAmount
            for (q in 0..2) rgb[q] = sky[q] + glow[q] * g
            for (li in recipe.layers.indices) {
                val dv = v - ridges[li][x]
                if (dv <= 0f) continue
                val layer = recipe.layers[li]
                val cover = smoothStep(0f, .006f, dv)
                val shade = (dv / .35f).coerceIn(0f, 1f)
                val mist = if (dv < .6f) expf(-dv * 14f) * layer.mist else 0f
                val top = hills[layer.top]; val bottom = hills[layer.bottom]
                for (q in 0..2) {
                    var c = top[q] * (1f - shade) + bottom[q] * shade
                    c = c * (1f - mist) + glow[q] * mist
                    rgb[q] = rgb[q] * (1f - cover) + c * cover
                }
            }
            val moon = recipe.moon
            if (moon != null && moonDisc != null && moonHalo != null) {
                val mx = u - moon.x; val my = v - moon.y
                val d2 = mx * mx + my * my
                if (d2 < .26f) {
                    val halo = expf(-d2 / (.17f * .17f)) * .28f
                    for (q in 0..2) rgb[q] += moonHalo[q] * halo
                    val disc = smoothStep(.064f, .044f, kotlin.math.sqrt(d2)) * .90f
                    if (disc > 0f) for (q in 0..2) rgb[q] = rgb[q] * (1f - disc) + moonDisc[q] * disc
                }
            }
            // A little noise, like the shipped pictures have, so a long soft gradient does not band on an 8-bit screen.
            var h = x * 374761393 + y * 668265263
            h = (h xor (h ushr 13)) * 1274126177
            h = h xor (h ushr 16)
            val noise = ((h and 0xFF) / 255f - .5f) * (3f / 255f)
            val r = ((rgb[0] + noise).coerceIn(0f, 1f) * 255f + .5f).toInt()
            val gg = ((rgb[1] + noise).coerceIn(0f, 1f) * 255f + .5f).toInt()
            val b = ((rgb[2] + noise).coerceIn(0f, 1f) * 255f + .5f).toInt()
            row[x] = (0xFF shl 24) or (r shl 16) or (gg shl 8) or b
        }
        emit(y, row)
    }
}

/** The piece [id] drawn with [tweaks] at [size] pixels square, or null for a piece that has no recipe. Call off the main thread. */
internal fun renderTweaked(id: String, tweaks: WallpaperTweaks, size: Int): Bitmap? {
    val recipe = WallpaperRecipes.of(id) ?: return null
    val n = size.coerceIn(16, 2048)
    val bitmap = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    renderHills(recipe, tweaks, n) { y, row -> bitmap.setPixels(row, 0, n, 0, y, n, 1) }
    return bitmap
}

/** How wide the colored copy behind Home is drawn: a little under the shipped 2448, which a center crop never has to scale up much on a phone. */
internal const val TWEAKED_HOME_PX = 1800

/** The quick copy drawn while a slider is moving. */
internal const val TWEAKED_LIVE_PX = 640
