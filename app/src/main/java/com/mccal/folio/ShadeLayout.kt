package com.mccal.folio

/**
 * What a downward pull opens on a wide screen. A phone keeps one panel at a time ([SINGLE_TOP]); a wide screen is the clock beside
 * a full-height list ([SPLIT_CLOCK], how it has been), a card under the corner that was pulled ([CARD]), or Notification Center and
 * Control Center together ([BOTH]).
 */
internal enum class ShadeShape { SINGLE_TOP, SPLIT_CLOCK, CARD, BOTH }

/** The narrowest window that has room for two panels side by side; a narrower one opens a card instead. */
internal const val SIDE_BY_SIDE_MIN_DP = 640

/**
 * The shape for [choice] on a window [widthDp] wide. [wide] is Folio's "regular" layout class, [landscape] whether it is wider than
 * tall, and [hinged] whether a fold is in the way (the panels stay on the side the fold leaves, so two do not fit).
 */
internal fun shadeShape(choice: NcLayout, wide: Boolean, landscape: Boolean, widthDp: Int, hinged: Boolean, panel: ShadePanel): ShadeShape = when {
    !wide -> ShadeShape.SINGLE_TOP
    choice == NcLayout.CARD -> ShadeShape.CARD
    choice == NcLayout.SIDE_BY_SIDE -> if (widthDp >= SIDE_BY_SIDE_MIN_DP && !hinged) ShadeShape.BOTH else ShadeShape.CARD
    panel == ShadePanel.NOTIFICATIONS && landscape -> ShadeShape.SPLIT_CLOCK
    else -> ShadeShape.SINGLE_TOP
}

/**
 * The saved layout. Settings from before the choice existed only had "clock beside notifications": on is Full screen (how it was),
 * off is Card (the narrower panel it gave). Anything unreadable, or nothing at all, is Full screen.
 */
internal fun ncLayoutFrom(saved: String?, legacySplit: Boolean?): NcLayout =
    NcLayout.entries.firstOrNull { it.name == saved } ?: if (legacySplit == false) NcLayout.CARD else NcLayout.FULL_SCREEN
