package com.mccal.folio

/**
 * What a downward pull opens on a wide screen. A phone keeps one panel at a time ([SINGLE_TOP]); a wide screen is the clock beside
 * a full-height list ([SPLIT_CLOCK], how it has been), a card under the corner that was pulled ([CARD]), or Notification Center and
 * Control Center together ([BOTH]).
 */
internal enum class ShadeShape { SINGLE_TOP, SPLIT_CLOCK, CARD, BOTH }

/** The narrowest window that has room for the clock beside the list when it is not a regular-size window (a phone on its side). */
internal const val SPLIT_CLOCK_MIN_DP = 600

/** The narrowest window that has room for two panels side by side; a narrower one opens a card instead. */
internal const val SIDE_BY_SIDE_MIN_DP = 640

/**
 * The shape for [choice] on a window [widthDp] wide. [wide] is Folio's "regular" layout class, [landscape] whether it is wider than
 * tall, and [hinged] whether a fold is in the way (the panels stay on the side the fold leaves, so two do not fit).
 */
internal fun shadeShape(choice: NcLayout, wide: Boolean, landscape: Boolean, widthDp: Int, hinged: Boolean, panel: ShadePanel, clockOn: Boolean = true): ShadeShape = when {
    // A phone on its side is wide but short: the big clock above the list left room for a card and a half, so the clock goes beside it.
    // [hinged] is not asked here: over another app it is always true (no fold information), and on Home a fold already confines the panel to its own side.
    !wide -> if (panel == ShadePanel.NOTIFICATIONS && landscape && widthDp >= SPLIT_CLOCK_MIN_DP && clockOn) ShadeShape.SPLIT_CLOCK else ShadeShape.SINGLE_TOP
    choice == NcLayout.CARD -> ShadeShape.CARD
    choice == NcLayout.SIDE_BY_SIDE -> if (widthDp >= SIDE_BY_SIDE_MIN_DP && !hinged) ShadeShape.BOTH else ShadeShape.CARD
    panel == ShadePanel.NOTIFICATIONS && landscape -> ShadeShape.SPLIT_CLOCK
    else -> ShadeShape.SINGLE_TOP
}

/**
 * Whether an action that opens a shade should draw Folio's own panel over the open app instead of the phone's: only when the switch and
 * Folio's panels are both on, Safe Mode is off, the phone is unlocked (the lock screen keeps Android's) and it is a panel Folio has.
 */
internal fun shadeOverAppsWanted(switchOn: Boolean, panelsOn: Boolean, safeMode: Boolean, locked: Boolean, panel: ShadePanel): Boolean =
    switchOn && panelsOn && !safeMode && !locked && panel != ShadePanel.SEARCH

/** The panel a pull that starts at [x] on a screen [width] wide opens: the left half Notification Center, the right half Control Center (as on Home). */
internal fun pullPanel(x: Float, width: Float): ShadePanel = if (x < width / 2f) ShadePanel.NOTIFICATIONS else ShadePanel.QUICK_SETTINGS

/**
 * Whether a drag of ([dx], [dy]) from the top edge is a pull down: one and a half touch slops down (early, because the system takes over a
 * pull that goes much further and cancels this window's touch), and more down than across by half, so the
 * diagonal swipe a one-handed reach from a corner makes counts, and a sideways swipe along the edge does not.
 */
internal fun isPullDown(dx: Float, dy: Float, slop: Float): Boolean = dy > slop * 1.5f && dy > kotlin.math.abs(dx) * .5f

/** The pull strip's height: the status bar's own, but never so thin a finger misses it or so deep it covers the app's top bar. */
internal fun pullStripHeight(statusBarPx: Int, density: Float): Int = statusBarPx.coerceIn((24 * density).toInt(), (48 * density).toInt())

/**
 * Whether the strip over the top edge of other apps should exist: the switch is on and so are the panels over apps, Safe Mode is off,
 * Folio's own Home is not in front (it has its own pulls) and the app is not full screen (Android keeps its swipe down from the top there).
 */
internal fun pullStripWanted(pullOn: Boolean, overApps: Boolean, panelsOn: Boolean, safeMode: Boolean, folioInFront: Boolean, fullScreen: Boolean): Boolean =
    pullOn && overApps && panelsOn && !safeMode && !folioInFront && !fullScreen

/** Control Center's smallest cell and the 4-column gap (dp), as drawn in TopPanels. */
internal const val CC_GAP_DP = 12f
internal const val CC_MIN_CELL_DP = 44f
/** The Control Center column's own side padding (16 dp each side) inside the panel width. */
internal const val CC_SIDE_PADDING_DP = 32f
/** The panel width under which Control Center never uses six columns (the 25 Oct research suggested about 520 dp; to be checked against font scaling). */
internal const val CC_SIX_COLUMN_MIN_WIDTH_DP = 520f

/** How many rows Control Center needs: with 4 columns the first four small controls sit beside the sliders; with 6 the sliders join the top row. */
internal fun controlCenterRows(columns: Int, controls: Int, focus: Boolean): Int =
    if (columns == 6) 3 + (if (focus) 1 else 0) + (controls + 5) / 6
    else 5 + (if (focus) 1 else 0) + (maxOf(0, controls - 4) + 3) / 4

/** The cell a Control Center of [columns] columns can have in a [widthDp] by [heightDp] panel, never above [sizeCellDp] (null height: unbounded). */
internal fun controlCenterCell(columns: Int, widthDp: Float, heightDp: Float?, controls: Int, focus: Boolean, sizeCellDp: Float): Float {
    val rows = controlCenterRows(columns, controls, focus)
    val byWidth = (widthDp - CC_SIDE_PADDING_DP - CC_GAP_DP * (columns - 1)) / columns
    val byHeight = heightDp?.let { (it - 60f - CC_GAP_DP * (rows - 1)) / rows } ?: Float.MAX_VALUE
    return minOf(sizeCellDp, byWidth, byHeight)
}

/**
 * Control Center keeps its four columns, like the iPad's, unless that squeezes its cells by height while the window has width to spare
 * (a phone or a fold on its side): then six columns put the sliders in the top row and spend width instead of height. Six are used only
 * when they give cells at least 6 dp bigger, so a window that is narrow, or tall enough, never changes.
 */
internal fun controlCenterColumns(widthDp: Float, heightDp: Float?, controls: Int, focus: Boolean, sizeCellDp: Float): Int {
    // Six columns need room to breathe: under about 520 dp of panel width (a split half, a narrow side of a fold) four stay.
    if (widthDp < CC_SIX_COLUMN_MIN_WIDTH_DP) return 4
    val four = controlCenterCell(4, widthDp, heightDp, controls, focus, sizeCellDp)
    if (four >= sizeCellDp) return 4
    val six = controlCenterCell(6, widthDp, heightDp, controls, focus, sizeCellDp)
    return if (six >= four + 6f) 6 else 4
}

/**
 * The saved layout. Settings from before the choice existed only had "clock beside notifications": on is Full screen (how it was),
 * off is Card (the narrower panel it gave). Anything unreadable, or nothing at all, is Full screen.
 */
internal fun ncLayoutFrom(saved: String?, legacySplit: Boolean?): NcLayout =
    NcLayout.entries.firstOrNull { it.name == saved } ?: if (legacySplit == false) NcLayout.CARD else NcLayout.FULL_SCREEN
