package com.mccal.folio

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.tanh

/**
 * How Home's pages feel under the finger with New motion on (beta 4): a rubber band at the true first and last page,
 * a page that is still settling caught by the next touch, and a small overshoot when a flick lands. The math lives here
 * so it is unit tested; PageGestures.kt does the moving. With New motion off none of this runs.
 */
internal object PagerFeel {
    /** iOS's rubber band constant: UIScrollView gives this much of the finger's travel past an end, less and less. */
    const val RESISTANCE = .55f

    /** How far a flick's landing may pass a page in the middle of Home, as a share of a page. */
    const val OVERSHOOT = .08f

    /** A settle closer than this to its page (a share of a page) has visibly stopped, so a touch is an ordinary tap. */
    const val CATCH_SLACK = .02f

    /** The first page a swipe can ever reach (Today View beside Home moves it), and the last (the App Library). */
    fun firstPage(count: Int, minPage: Int) = minPage.coerceIn(0, lastPage(count))
    fun lastPage(count: Int) = (count - 1).coerceAtLeast(0)

    /**
     * How far the pages move for [excess] pixels of finger travel past an end: iOS's curve, which gives less and less
     * and never reaches [dimension] (the page width). The sign follows [excess].
     */
    fun rubberBand(excess: Float, dimension: Float): Float {
        if (dimension <= 0f || excess == 0f) return 0f
        return sign(excess) * (1f - 1f / (abs(excess) * RESISTANCE / dimension + 1f)) * dimension
    }

    /** The finger travel that gives [stretch]: [rubberBand] backwards, so a caught stretch carries on without a jump. */
    fun excessFor(stretch: Float, dimension: Float): Float {
        if (dimension <= 0f || stretch == 0f) return 0f
        val share = (abs(stretch) / dimension).coerceAtMost(.99f)
        return sign(stretch) * dimension / RESISTANCE * (1f / (1f - share) - 1f)
    }

    /**
     * Which way a [requested] position spills past a true end, given where it was [clamped]: -1 before the first page,
     * 1 past the last, 0 when it is inside or only meets the one-page-per-swipe bound (that one stays a hard stop).
     * [leading] is off while Discover owns the pull from the first page.
     */
    fun stretchSide(requested: Float, clamped: Float, count: Int, minPage: Int, leading: Boolean, trailing: Boolean = true): Int = when {
        requested < clamped && leading && clamped <= firstPage(count, minPage) -> -1
        requested > clamped && trailing && clamped >= lastPage(count) -> 1
        else -> 0
    }

    /**
     * Where a settling page is drawn for the spring's raw [position], on its way from [start] to [target]. Past a page in
     * the middle the overshoot is eased so even a hard flick passes by at most [OVERSHOOT]; past a true end it is left
     * alone, because there the rubber band takes it.
     */
    fun settlePosition(position: Float, start: Float, target: Int, count: Int, minPage: Int): Float {
        val over = position - target
        if (over == 0f) return position
        val travel = if (start != target.toFloat()) sign(target - start) else sign(over)
        if (sign(over) != travel) return position
        val end = if (travel > 0f) lastPage(count) else firstPage(count, minPage)
        if (target == end) return position
        return target + travel * OVERSHOOT * tanh(abs(over) / OVERSHOOT)
    }

    /**
     * The spring a release settles on, or null to keep the one Home uses today. A flick lands on [FolioMotion.Bounce], so
     * it passes the page a little and eases back; a release out of a stretch springs back on [FolioMotion.Settle].
     * Reduce Motion keeps today's settle.
     */
    fun settleSpring(velocity: Float, flick: Float, stretched: Boolean, reduce: Boolean): Pair<Float, Float>? = when {
        reduce -> null
        abs(velocity) > flick -> FolioMotion.Bounce
        stretched -> FolioMotion.Settle
        else -> null
    }

    /** Whether a touch-down catches the pages: they are still visibly moving toward their page or out of a stretch. */
    fun catches(settling: Boolean, position: Float, target: Int, stretch: Float, stretchSlack: Float): Boolean =
        settling && (abs(position - target) > CATCH_SLACK || abs(stretch) > stretchSlack)

    /** Where a caught page goes when the finger lifts without a swipe: the nearest page, like iOS. */
    fun caughtPage(position: Float, count: Int, minPage: Int) =
        position.roundToInt().coerceIn(firstPage(count, minPage), lastPage(count))
}
