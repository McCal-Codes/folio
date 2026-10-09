package com.mccal.folio

import kotlin.math.abs

/** What an icon touch has become so far: keep watching, a swipe, or not an icon gesture (a tap, a drag, a page swipe). */
internal enum class IconSwipe { PENDING, UP, DOWN, CANCEL }

/**
 * The icon swipe rules as plain math, so they can be tested and the touch code only feeds them numbers. A swipe starts
 * above Android's bottom gesture strip (so a gesture-navigation swipe never also fires an icon), passes [thresholdPx],
 * and stays mostly vertical ([CONE]); moving far in any other direction hands the touch back to paging and drag.
 */
internal object IconGesture {
    /** How steep a swipe must be: sideways travel under this share of the vertical travel. Today's value. */
    const val CONE = 0.6f
    /** How far a finger moves before it counts as a swipe, in dp. Today's value. */
    const val THRESHOLD_DP = 28f

    /** Whether a touch that began at [startY] (window pixels) is above the bottom gesture strip of height [bottomInset]. */
    fun startsAboveInset(startY: Float, windowHeight: Float, bottomInset: Float) = startY < windowHeight - bottomInset

    /**
     * [dx] and [dy] are how far the finger has moved from where it went down, in pixels (up is negative). [up] and [down]
     * say which swipes this icon has. Moving past the threshold any other way is [IconSwipe.CANCEL], as it always was.
     */
    fun classify(dx: Float, dy: Float, thresholdPx: Float, up: Boolean, down: Boolean, startAllowed: Boolean = true): IconSwipe {
        if (!startAllowed) return IconSwipe.CANCEL
        if (up && -dy > thresholdPx && abs(dx) < -dy * CONE) return IconSwipe.UP
        if (down && dy > thresholdPx && abs(dx) < dy * CONE) return IconSwipe.DOWN
        if (abs(dy) > thresholdPx || abs(dx) > thresholdPx) return IconSwipe.CANCEL
        return IconSwipe.PENDING
    }
}
