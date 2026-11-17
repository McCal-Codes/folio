package com.mccal.folio

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Blur for a wallpaper photo, the way iOS's Blur switch in the wallpaper editor softens a picture so icons stand out on it.
 *
 * The picture is shrunk to a small size first, blurred there, and scaled back up: blurring a 12-megapixel photo at full size
 * would take seconds, while the small copy takes a few milliseconds and the scaled-up result is smooth anyway.
 */
private const val BLUR_SIDE = 180
private const val BLUR_RADIUS = 5
private const val BLUR_PASSES = 3

/** The longest side the saved, blurred wallpaper keeps; it has no detail to lose above this. */
private const val BLURRED_SAVE_SIDE = 1080

/**
 * Blurs [pixels] (ARGB, [width] by [height]) in place with [BLUR_PASSES] box passes of [radius], which together approximate a
 * Gaussian. Edges repeat their last pixel, so the picture does not darken at its borders. A radius of 0 changes nothing.
 */
internal fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int) {
    if (radius <= 0 || width <= 0 || height <= 0 || pixels.size < width * height) return
    val scratch = IntArray(width * height)
    repeat(BLUR_PASSES) {
        boxPass(pixels, scratch, width, height, radius, horizontal = true)
        boxPass(scratch, pixels, width, height, radius, horizontal = false)
    }
}

private fun boxPass(source: IntArray, target: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    val step = if (horizontal) 1 else width
    val window = 2 * radius + 1
    for (line in 0 until lines) {
        val start = if (horizontal) line * width else line
        var alpha = 0; var red = 0; var green = 0; var blue = 0
        for (k in -radius..radius) {
            val p = source[start + k.coerceIn(0, length - 1) * step]
            alpha += p ushr 24; red += (p shr 16) and 0xFF; green += (p shr 8) and 0xFF; blue += p and 0xFF
        }
        for (i in 0 until length) {
            target[start + i * step] = ((alpha / window) shl 24) or ((red / window) shl 16) or ((green / window) shl 8) or (blue / window)
            val leaving = source[start + (i - radius).coerceIn(0, length - 1) * step]
            val entering = source[start + (i + radius + 1).coerceIn(0, length - 1) * step]
            alpha += (entering ushr 24) - (leaving ushr 24)
            red += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
            green += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
            blue += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}

/** The size the blur works at: the longest side [BLUR_SIDE], never larger than the picture itself, never under 1 px. */
internal fun blurWorkingSize(width: Int, height: Int): Pair<Int, Int> {
    val scale = min(1f, BLUR_SIDE.toFloat() / max(1, max(width, height)))
    return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
}

/**
 * A blurred copy of this picture at a small size ([BLUR_SIDE] on its longest side). Draw it stretched over the original's
 * place: it has no detail left to lose. Halving first keeps the shrink smooth instead of skipping pixels.
 */
internal fun Bitmap.blurredSmall(): Bitmap {
    val (targetWidth, targetHeight) = blurWorkingSize(width, height)
    var current = this
    while (current.width / 2 >= targetWidth && current.height / 2 >= targetHeight) {
        val half = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
        if (current !== this) current.recycle()
        current = half
    }
    val small = if (current.width == targetWidth && current.height == targetHeight) current
        else Bitmap.createScaledBitmap(current, targetWidth, targetHeight, true).also { if (current !== this) current.recycle() }
    val pixels = IntArray(targetWidth * targetHeight)
    small.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
    if (small !== this) small.recycle()
    boxBlur(pixels, targetWidth, targetHeight, BLUR_RADIUS)
    return Bitmap.createBitmap(pixels, targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
}

/** The blurred picture at the size it is saved: this picture's own shape, at most [BLURRED_SAVE_SIDE] on its longest side. */
internal fun Bitmap.blurredForSaving(): Bitmap {
    val small = blurredSmall()
    val scale = min(1f, BLURRED_SAVE_SIDE.toFloat() / max(width, height))
    val saved = Bitmap.createScaledBitmap(small, max(1, (width * scale).roundToInt()), max(1, (height * scale).roundToInt()), true)
    if (saved !== small) small.recycle()
    return saved
}
