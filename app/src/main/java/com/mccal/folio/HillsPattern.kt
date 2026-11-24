package com.mccal.folio

import android.graphics.Bitmap
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A piece's drawing with the colors taken out, so a color slider can follow a finger.
 *
 * [renderHills] works every pixel out from the shape of the hills, the glow, the mist and the moon, and mixes the piece's colors
 * through them. None of that shape depends on the colors, and the mixing is a straight weighted sum: each pixel is some amount
 * of each color in the palette (the sky's stops, the five hill tones, the glow, the moon's disc and halo) added together. This
 * works the amounts out once for a size and keeps them, so changing the colors is one multiply-and-add per pixel and color, about
 * a twentieth of the work of drawing the piece again, which is what lets the picture keep up with a slider.
 *
 * The result matches [renderHills] to within one level in 255 (the same arithmetic in a different order).
 */
internal class HillsPattern private constructor(
    private val recipe: HillsRecipe,
    val n: Int,
    private val weights: FloatArray,
    private val colors: Int,
) {
    private val hillAt = recipe.sky.size
    private val glowAt = hillAt + recipe.hills.size

    /** Draws the piece with [tweaks], a row at a time into [row] and handed to [emit], asking [keepGoing] before each row. */
    fun renderRows(tweaks: WallpaperTweaks, keepGoing: () -> Boolean = { true }, emit: (y: Int, row: IntArray) -> Unit) {
        val palette = FloatArray(colors * 3)
        fun put(index: Int, rgb: Int, tweak: ColorTweak) {
            val c = tweakedRgb(rgb, tweak)
            palette[index * 3] = c[0]; palette[index * 3 + 1] = c[1]; palette[index * 3 + 2] = c[2]
        }
        val skyTweak = tweaks.effective(TweakPart.SKY)
        recipe.sky.forEachIndexed { i, stop -> put(i, stop.second, skyTweak) }
        val hillsTweak = tweaks.effective(TweakPart.HILLS)
        recipe.hills.forEachIndexed { i, rgb -> put(hillAt + i, rgb, hillsTweak) }
        val glowTweak = tweaks.effective(TweakPart.GLOW)
        put(glowAt, recipe.glow, glowTweak)
        recipe.moon?.let { put(glowAt + 1, it.disc, glowTweak); put(glowAt + 2, it.halo, glowTweak) }

        val row = IntArray(n)
        for (y in 0 until n) {
            if (!keepGoing()) return
            for (x in 0 until n) {
                val base = (y * n + x) * colors
                var r = 0f; var g = 0f; var b = 0f
                for (q in 0 until colors) {
                    val w = weights[base + q]
                    if (w == 0f) continue
                    r += palette[q * 3] * w; g += palette[q * 3 + 1] * w; b += palette[q * 3 + 2] * w
                }
                // The same fine noise as renderHills, so a long soft gradient does not band.
                var h = x * 374761393 + y * 668265263
                h = (h xor (h ushr 13)) * 1274126177
                h = h xor (h ushr 16)
                val noise = ((h and 0xFF) / 255f - .5f) * (3f / 255f)
                val ri = ((r + noise).coerceIn(0f, 1f) * 255f + .5f).toInt()
                val gi = ((g + noise).coerceIn(0f, 1f) * 255f + .5f).toInt()
                val bi = ((b + noise).coerceIn(0f, 1f) * 255f + .5f).toInt()
                row[x] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
            emit(y, row)
        }
    }

    /** The piece with [tweaks] as an [n] by [n] bitmap, or null when [keepGoing] said stop. Call off the main thread. */
    fun render(tweaks: WallpaperTweaks, keepGoing: () -> Boolean = { true }): Bitmap? {
        val bitmap = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        var stopped = false
        renderRows(tweaks, { keepGoing().also { if (!it) stopped = true } }) { y, row -> bitmap.setPixels(row, 0, n, 0, y, n, 1) }
        if (stopped) { bitmap.recycle(); return null }
        return bitmap
    }

    companion object {
        /** Sizes above this are drawn the ordinary way: the amounts for one pixel take [colors] floats each. */
        const val MAX_SIZE = 512

        /** Works out the amounts for [recipe] at [size] pixels square, or null when [keepGoing] said stop. Call off the main thread. */
        fun build(recipe: HillsRecipe, size: Int, keepGoing: () -> Boolean = { true }): HillsPattern? {
            val n = size.coerceIn(16, MAX_SIZE)
            val skyCount = recipe.sky.size
            val hillAt = skyCount
            val glowAt = hillAt + recipe.hills.size
            val discAt = glowAt + 1
            val haloAt = glowAt + 2
            val k = glowAt + 3
            val weights = FloatArray(n * n * k)
            val ridges = Array(recipe.layers.size) { i ->
                val l = recipe.layers[i]
                FloatArray(n) { x ->
                    val u = x.toFloat() / n
                    val a = (u * 2f * Math.PI.toFloat() * l.frequency + l.phase)
                    val b = (u * 2f * Math.PI.toFloat() * l.frequency * 2.3f + l.phase * 1.9f)
                    l.base + l.amplitude * sin(a) + l.amplitude * .4f * sin(b)
                }
            }
            val cur = FloatArray(k)
            for (y in 0 until n) {
                if (!keepGoing()) return null
                val v = y.toFloat() / n
                // The sky is a mix of two neighbouring stops, held flat past the first and last.
                var s = 0
                while (s < skyCount - 1 && v > recipe.sky[s + 1].first) s++
                val skyLow = s
                val skyHigh = if (s >= skyCount - 1) -1 else s + 1
                val skyT = if (skyHigh < 0) 0f else {
                    val (p0, _) = recipe.sky[s]; val (p1, _) = recipe.sky[s + 1]
                    ((v - p0) / (p1 - p0)).coerceIn(0f, 1f)
                }
                val gy = (v - recipe.glowY) * 1.2f
                for (x in 0 until n) {
                    val u = x.toFloat() / n
                    java.util.Arrays.fill(cur, 0f)
                    if (skyHigh < 0) cur[skyCount - 1] = 1f else { cur[skyLow] = 1f - skyT; cur[skyHigh] = skyT }
                    val gx = u - recipe.glowX
                    cur[glowAt] += exp(-(gx * gx + gy * gy) * 4f) * recipe.glowAmount
                    for (li in recipe.layers.indices) {
                        val dv = v - ridges[li][x]
                        if (dv <= 0f) continue
                        val layer = recipe.layers[li]
                        val cover = smoothStepWeight(0f, .006f, dv)
                        val shade = (dv / .35f).coerceIn(0f, 1f)
                        val mist = if (dv < .6f) exp(-dv * 14f) * layer.mist else 0f
                        for (q in 0 until k) cur[q] *= 1f - cover
                        cur[hillAt + layer.top] += cover * (1f - shade) * (1f - mist)
                        cur[hillAt + layer.bottom] += cover * shade * (1f - mist)
                        cur[glowAt] += cover * mist
                    }
                    val moon = recipe.moon
                    if (moon != null) {
                        val mx = u - moon.x; val my = v - moon.y
                        val d2 = mx * mx + my * my
                        if (d2 < .26f) {
                            cur[haloAt] += exp(-d2 / (.17f * .17f)) * .28f
                            val disc = smoothStepWeight(.064f, .044f, sqrt(d2)) * .90f
                            if (disc > 0f) {
                                for (q in 0 until k) cur[q] *= 1f - disc
                                cur[discAt] += disc
                            }
                        }
                    }
                    System.arraycopy(cur, 0, weights, (y * n + x) * k, k)
                }
            }
            return HillsPattern(recipe, n, weights, k)
        }

        private fun smoothStepWeight(a: Float, b: Float, t: Float): Float {
            val x = ((t - a) / (b - a)).coerceIn(0f, 1f)
            return x * x * (3f - 2f * x)
        }
    }
}

private fun exp(x: Float): Float = kotlin.math.exp(x.toDouble()).toFloat()
