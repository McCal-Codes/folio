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

/** Control Center's gap between cells and its smallest cell (dp), as drawn in TopPanels. */
internal const val CC_GAP_DP = 12f
internal const val CC_MIN_CELL_DP = 44f
/** The Control Center column's own side padding (16 dp each side) inside the panel width. */
internal const val CC_SIDE_PADDING_DP = 32f
/** The page strip: a column at the edge on a wide panel, a row under the grid on a narrow one. */
internal const val CC_STRIP_DP = 56f
internal const val CC_EDGE_STRIP_MIN_DP = 560f
/** A cell may grow to this times the size the person chose, to fill the width; past that another column is added instead. */
internal const val CC_FILL_MAX = 1.5f
internal val CC_COLUMN_STEPS = listOf(4, 6, 8)

/** The pages Control Center is split into, reached from the strip: what is used most, media and sound, and the connections. */
internal enum class CcPage { CONTROLS, MEDIA, CONNECTIVITY }

/**
 * How many rows the Controls page needs. With four columns the first four small controls sit beside the sliders; with six or eight the
 * sliders join the top row (beside the connectivity and Now Playing modules) and every small control runs in rows of [columns].
 */
internal fun controlCenterRows(columns: Int, controls: Int, focus: Boolean): Int =
    if (columns >= 6) 3 + (if (focus) 1 else 0) + (controls + columns - 1) / columns
    else 5 + (if (focus) 1 else 0) + (maxOf(0, controls - 4) + 3) / 4

/** Columns and the size of one cell, in dp. */
internal data class CcGrid(val columns: Int, val cellDp: Float, val stripAtEdge: Boolean)

/**
 * Control Center fills the window instead of sitting in a corner: the narrowest of 4, 6 or 8 columns whose cells are not bigger than
 * [CC_FILL_MAX] times the chosen size ([sizeCellDp]), so a wide window gets more columns, not giant tiles. A window too short for the
 * rows takes a wider grid (fewer rows) while that makes the cell at least 6 dp bigger. The page strip takes a column at the edge on a
 * wide panel and a row under the grid on a narrow one. A null [heightDp] means no height limit.
 */
internal fun controlCenterGrid(panelWidthDp: Float, heightDp: Float?, controls: Int, focus: Boolean, sizeCellDp: Float): CcGrid {
    val edge = panelWidthDp >= CC_EDGE_STRIP_MIN_DP
    val gridWidth = panelWidthDp - CC_SIDE_PADDING_DP - if (edge) CC_STRIP_DP else 0f
    val maxCell = sizeCellDp * CC_FILL_MAX
    fun widthCell(c: Int) = (gridWidth - CC_GAP_DP * (c - 1)) / c
    fun heightCell(c: Int): Float {
        val rows = controlCenterRows(c, controls, focus)
        return heightDp?.let { (it - 60f - (if (edge) 0f else CC_STRIP_DP) - CC_GAP_DP * (rows - 1)) / rows } ?: Float.MAX_VALUE
    }
    fun cell(c: Int) = minOf(widthCell(c), heightCell(c), maxCell)
    var columns = CC_COLUMN_STEPS.firstOrNull { widthCell(it) <= maxCell } ?: CC_COLUMN_STEPS.last()
    while (columns < CC_COLUMN_STEPS.last() && cell(columns) < sizeCellDp) {
        val next = CC_COLUMN_STEPS[CC_COLUMN_STEPS.indexOf(columns) + 1]
        if (cell(next) >= cell(columns) + 6f) columns = next else break
    }
    return CcGrid(columns, maxOf(CC_MIN_CELL_DP, cell(columns)), edge)
}

/**
 * The saved layout. Settings from before the choice existed only had "clock beside notifications": on is Full screen (how it was),
 * off is Card (the narrower panel it gave). Anything unreadable, or nothing at all, is Full screen.
 */
internal fun ncLayoutFrom(saved: String?, legacySplit: Boolean?): NcLayout =
    NcLayout.entries.firstOrNull { it.name == saved } ?: if (legacySplit == false) NcLayout.CARD else NcLayout.FULL_SCREEN
