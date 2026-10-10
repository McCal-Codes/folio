package com.mccal.folio

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.floor

/**
 * The page dots' answer to a swipe: the active dot stretches toward the next page as the finger moves and the tail follows, so
 * a swipe has a visible companion. Plain math so it can be tested; drawing is in [Modifier.dotWorm].
 */
internal object DotsWorm {
    /**
     * [pos] is the page position as a fraction (the current page plus the pager's offset). The head reaches the next dot in
     * the first half of the move and the tail catches up in the second half. Both are in dot units, so a dot's centre is its index.
     */
    fun span(pos: Float): Pair<Float, Float> {
        val base = floor(pos)
        val f = pos - base
        return (base + minOf(1f, 2f * f)) to (base + maxOf(0f, 2f * f - 1f))
    }

    /** The worm fades out as the pager leaves the last real page for the "+" slot, which is not a page with a dot of its own. */
    fun alpha(pos: Float, realPages: Int): Float = (realPages - pos).coerceIn(0f, 1f)
}

/**
 * Draws the stretching active dot over the row of resting dots, in the draw phase only: the pager's offset is read when drawing,
 * so a swipe invalidates this layer and nothing recomposes. [dotWidthPx] is the width of one dot's cell and [padPx] the row's
 * left padding.
 */
internal fun Modifier.dotWorm(position: () -> Float, realPages: Int, color: Color, dotWidthPx: Float, padPx: Float, radiusPx: Float): Modifier =
    drawWithContent {
        drawContent()
        val pos = position()
        val alpha = DotsWorm.alpha(pos, realPages)
        if (alpha <= 0f) return@drawWithContent
        val (head, tail) = DotsWorm.span(pos.coerceAtLeast(0f))
        val cy = size.height / 2f
        val x0 = padPx + dotWidthPx * (tail.coerceAtMost(realPages - 1f) + .5f) - radiusPx
        val x1 = padPx + dotWidthPx * (head.coerceAtMost(realPages - 1f) + .5f) + radiusPx
        drawRoundRect(color.copy(alpha = color.alpha * alpha), Offset(x0, cy - radiusPx), Size(x1 - x0, radiusPx * 2f), CornerRadius(radiusPx))
    }
