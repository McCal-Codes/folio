package com.mccal.folio

import android.graphics.Bitmap

/**
 * The blur behind Folio's panels over another app, on a phone whose system will not blur windows. The screen picture is taken once, before the
 * panel exists, shrunk at once (so the full-size picture is dropped), blurred with a few cheap box passes off the main thread, and then drawn as an ordinary
 * image that the panel fades in: nothing is blurred per frame, so nothing can stutter. Small enough to be a few hundred kilobytes, never saved or sent.
 */
internal object FrostBlur {
    /** The longest side of the blurred picture, in pixels: the panel stretches it to the screen, and the stretch is part of the softness. */
    const val MAX_SIDE = 160
    const val RADIUS = 3
    const val PASSES = 3

    /** [source] shrunk by halves (which averages, so the picture stays clean) until its longest side is at most [maxSide], then blurred. [source] must be a software bitmap. */
    fun blurred(source: Bitmap, maxSide: Int = MAX_SIDE, radius: Int = RADIUS, passes: Int = PASSES): Bitmap {
        var current = source
        while (maxOf(current.width, current.height) > maxSide * 2) {
            val next = Bitmap.createScaledBitmap(current, maxOf(1, current.width / 2), maxOf(1, current.height / 2), true)
            if (current !== source) current.recycle()
            current = next
        }
        val scale = maxSide.toFloat() / maxOf(current.width, current.height)
        if (scale < 1f) {
            val next = Bitmap.createScaledBitmap(current, maxOf(1, (current.width * scale).toInt()), maxOf(1, (current.height * scale).toInt()), true)
            if (current !== source) current.recycle()
            current = next
        }
        val w = current.width; val h = current.height
        val pixels = IntArray(w * h)
        current.getPixels(pixels, 0, w, 0, 0, w, h)
        if (current !== source) current.recycle()
        boxBlur(pixels, w, h, radius, passes)
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Blurs [pixels] (ARGB, row by row) in place: [passes] rounds of a horizontal then a vertical average over [radius] pixels each way, edges held. Three passes make it smooth. */
    fun boxBlur(pixels: IntArray, w: Int, h: Int, radius: Int, passes: Int) {
        if (w <= 0 || h <= 0 || radius <= 0) return
        val line = IntArray(maxOf(w, h))
        val out = IntArray(maxOf(w, h))
        repeat(passes) {
            for (y in 0 until h) {
                for (x in 0 until w) line[x] = pixels[y * w + x]
                blurLine(line, out, w, radius)
                for (x in 0 until w) pixels[y * w + x] = out[x]
            }
            for (x in 0 until w) {
                for (y in 0 until h) line[y] = pixels[y * w + x]
                blurLine(line, out, h, radius)
                for (y in 0 until h) pixels[y * w + x] = out[y]
            }
        }
    }

    private fun blurLine(line: IntArray, out: IntArray, n: Int, radius: Int) {
        val span = 2 * radius + 1
        var a = 0; var r = 0; var g = 0; var b = 0
        for (i in -radius..radius) {
            val p = line[i.coerceIn(0, n - 1)]
            a += p ushr 24; r += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255
        }
        for (i in 0 until n) {
            val half = span / 2 // rounded, not cut off: six cut-offs would darken the picture a little
            out[i] = (((a + half) / span) shl 24) or (((r + half) / span) shl 16) or (((g + half) / span) shl 8) or ((b + half) / span)
            val add = line[(i + radius + 1).coerceAtMost(n - 1)]
            val drop = line[(i - radius).coerceAtLeast(0)]
            a += (add ushr 24) - (drop ushr 24); r += ((add shr 16) and 255) - ((drop shr 16) and 255)
            g += ((add shr 8) and 255) - ((drop shr 8) and 255); b += (add and 255) - (drop and 255)
        }
    }
}
