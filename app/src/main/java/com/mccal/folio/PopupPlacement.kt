package com.mccal.folio

/**
 * Left edge for a pop-up [width] wide centered on [center], kept [margin] inside a window [window] wide. When the
 * window is narrower than the pop-up plus its margins the pop-up is pinned to the left margin: Kotlin's
 * `coerceIn` throws when its upper bound is below its lower bound, which is what a 270 dp split-screen window did.
 * Every unit is the same (px or dp).
 */
internal fun popupLeft(center: Float, width: Float, window: Float, margin: Float): Float =
    (center - width / 2f).coerceIn(margin, maxOf(margin, window - width - margin))

/** Moves a bar's bottom offset to [wanted], between [floor] and [ceiling], and never throws when the window is so short that [ceiling] is below [floor]. */
internal fun liftedBarOffset(wanted: Int, floor: Int, ceiling: Int): Int = wanted.coerceIn(floor, maxOf(floor, ceiling))
