package com.mccal.folio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Blur for a wallpaper photo, the way iOS's Blur switch in the wallpaper editor softens a picture so icons stand out on it.
 *
 * The framing screen blurs live with the GPU; Set saves a copy blurred here, at the size it is kept (never the whole 12-megapixel
 * photo), with a strength that gives the same look.
 */
private const val BLUR_SIDE = 180
private const val MAX_BLUR_RADIUS = 10f
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

/**
 * What a person did to a photo on the framing screen: how much to blur it and how much to darken it, each 0 (none) to 1 (the most).
 * Both are drawn live under the picture there and baked into the saved copy by [Bitmap.blurredForSaving] and [Bitmap.dimmed].
 */
internal data class PhotoAdjust(val blur: Float = 0f, val dim: Float = 0f) {
    val isNone: Boolean get() = blur <= 0f && dim <= 0f

    companion object { val NONE = PhotoAdjust() }
}

/**
 * The blur's strength is a box radius at a 180 px picture, 10 at the top of the slider (half of that is the one fixed blur
 * Folio had before the slider). Everything else is worked out from it so the live preview and the saved copy look the same.
 */
private fun radiusAtReference(amount: Float) = amount.coerceIn(0f, 1f) * MAX_BLUR_RADIUS

/** The box radius that gives [amount] on a picture [side] px on its longest side. */
internal fun blurRadiusFor(amount: Float, side: Int): Int = (radiusAtReference(amount) * side / BLUR_SIDE).roundToInt()

/**
 * The spread (a Gaussian's sigma) of the blur at [amount], as a fraction of the picture's longest side. Three box passes of radius r
 * spread as sqrt(r * (r + 1)), and this is the number the live preview hands to the GPU so it matches the saved copy.
 */
internal fun blurSigmaFraction(amount: Float): Float {
    val r = radiusAtReference(amount)
    return sqrt(r * (r + 1f)) / BLUR_SIDE
}

/** How dark the dim slider's top end is: 70% black, so the picture is still there to judge. */
private const val MAX_DIM = .7f

/** The black laid over the picture for a dim [amount]; 0 for none. */
internal fun dimAlpha(amount: Float): Float = amount.coerceIn(0f, 1f) * MAX_DIM

/**
 * The blurred picture at the size it is saved: this picture's own shape, at most [BLURRED_SAVE_SIDE] on its longest side, with
 * [amount] of blur. It is shrunk first (halving, which keeps the shrink smooth) so a 12-megapixel photo is never blurred whole.
 */
internal fun Bitmap.blurredForSaving(amount: Float): Bitmap {
    val scale = min(1f, BLURRED_SAVE_SIDE.toFloat() / max(width, height))
    val targetWidth = max(1, (width * scale).roundToInt()); val targetHeight = max(1, (height * scale).roundToInt())
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
    boxBlur(pixels, targetWidth, targetHeight, blurRadiusFor(amount, max(targetWidth, targetHeight)))
    return Bitmap.createBitmap(pixels, targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
}

/** A copy of this picture with [amount] of dim (see [dimAlpha]): the same as laying that much black over it. */
internal fun Bitmap.dimmed(amount: Float): Bitmap {
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val keep = 1f - dimAlpha(amount)
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(keep, 0f, 0f, 0f, 0f, 0f, keep, 0f, 0f, 0f, 0f, 0f, keep, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
    }
    Canvas(out).drawBitmap(this, 0f, 0f, paint)
    return out
}
