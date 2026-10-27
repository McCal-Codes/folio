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

/**
 * Whether letting go of Control Center's swipe-away pull closes it: the panel was pulled up (the offset is negative) past [thresholdPx], or the
 * finger left with an upward speed over [flickPxPerSec] ([velocityY] is negative going up). Both limits are in pixels of the density they are asked at.
 */
internal fun ccSwipeAwayCloses(offsetPx: Float, velocityY: Float, thresholdPx: Float, flickPxPerSec: Float): Boolean =
    offsetPx < 0f && (offsetPx < -thresholdPx || velocityY < -flickPxPerSec)

/**
 * The pull's offset when the panel is [open] or closed again. A swipe-away close leaves the offset where the finger let go while the panel
 * fades out, and the same panel is reused when it is pulled down again in that time, so opening starts from rest; closing leaves it as it is.
 */
internal fun ccSwipeAwayOnOpenChange(open: Boolean, offsetPx: Float): Float = if (open) 0f else offsetPx

/** The smallest cell Control Center ever draws; under it the page scrolls. */
internal const val CC_MIN_CELL_DP = 44f
/** A cell may grow to this times the size the person chose, to fill the width; past that another pair of columns is added instead. */
internal const val CC_FILL_MAX = 1.5f
/** The gap between cells as a share of the chosen cell size, and how many widths of the page strip a panel needs before the strip stands at its edge. */
internal const val CC_GAP_RATIO = .18f
internal const val CC_EDGE_STRIP_PANEL_RATIO = 5f

/** The pages Control Center is split into, reached from the strip: what is used most, media and sound, and the connections. */
internal enum class CcPage { CONTROLS, MEDIA, CONNECTIVITY }

/** Air above the header and below the pages, inside the panel, so the first and last rows never touch the edge of the panel. */
internal const val CC_PANEL_PAD_DP = 12f

/** Below this height (dp) the page strip goes into the header instead of taking a row of its own under the grid. */
internal const val CC_STRIP_IN_HEADER_BELOW_DP = 560f

/** The width the header needs for its two buttons and [pages] dots, each dot a 24 dp touch target with 8 dp around it. */
internal fun ccHeaderMinWidth(pages: Int): Float = 2 * 48f + pages * 24f + (pages + 1) * 8f

/** Rows of [cellDp] tall with [gapDp] between them that fit in [heightDp]. */
internal fun ccRowsThatFit(heightDp: Float, cellDp: Float, gapDp: Float): Int = ((heightDp + gapDp) / (cellDp + gapDp)).toInt().coerceAtLeast(0)

/**
 * How the small controls are split over the Controls pages so that none of them scrolls (PNL-1): [perPage] is how many go on each page, and
 * [slidersOnFirst] says whether the two sliders fit on the first page beside them (four columns) or in its top row (six or more); if they
 * do not fit they stay on the Media page, and so does Focus when the first page has no row for it. The first page also holds the connectivity, Now Playing and Focus modules, so it holds fewer.
 * A null [pageHeightDp] means no height limit: everything on one page, as before.
 */
internal data class CcPlan(val perPage: List<Int>, val slidersOnFirst: Boolean, val focusOnFirst: Boolean = true)

internal fun ccPlanControls(columns: Int, cellDp: Float, gapDp: Float, pageHeightDp: Float?, controls: Int, focus: Boolean): CcPlan {
    if (pageHeightDp == null) return CcPlan(listOf(controls), true)
    val rows = ccRowsThatFit(pageHeightDp, cellDp, gapDp)
    val focusFirst = !focus || rows >= 3 // below three rows the two modules fill the page, and Focus goes to the Media page
    val free = rows - 2 - (if (focus && focusFirst) 1 else 0) // rows left under the modules
    val slidersOnFirst: Boolean
    val first: Int
    if (columns >= 6) { slidersOnFirst = true; first = maxOf(0, free) * columns }
    else if (free >= 2) { slidersOnFirst = true; first = 4 + (free - 2) * 4 }
    else { slidersOnFirst = false; first = maxOf(0, free) * 4 }
    val onFirst = minOf(controls, first)
    var left = controls - onFirst
    val perOther = maxOf(1, rows) * columns
    val pages = mutableListOf(onFirst)
    while (left > 0) { val n = minOf(left, perOther); pages += n; left -= n }
    return CcPlan(pages, slidersOnFirst, focusFirst)
}

/** The rows of cells page [index] of the plan uses, for the height the pager is given. */
internal fun ccPlanRows(plan: CcPlan, index: Int, columns: Int, focus: Boolean): Int {
    val n = plan.perPage[index]
    fun rowsOf(count: Int, per: Int) = (count + per - 1) / per
    if (index > 0) return rowsOf(n, columns)
    val top = 2 + (if (focus && plan.focusOnFirst) 1 else 0)
    return top + when {
        columns >= 6 -> rowsOf(n, columns)
        plan.slidersOnFirst -> 2 + rowsOf(maxOf(0, n - 4), 4)
        else -> rowsOf(n, 4)
    }
}

/** The rows the Media page uses: Now Playing and the sliders three tall, and Focus under them when it did not fit on the first page. */
internal fun ccMediaRows(plan: CcPlan, focus: Boolean): Int = 3 + (if (focus && !plan.focusOnFirst) 1 else 0)

/**
 * The pages after the Controls pages. Without [dedupe] they are always Media and Connectivity, as they were. With it, a page is
 * shown only when the first page cannot hold what it holds, so the strip never offers a page that repeats the first:
 *  - the connectivity block and Now Playing are each two rows tall and stand at the top of the first page, so when two rows fit
 *    ([pageHeightDp], null for no limit) both are there already, and the Connectivity page (the same four switches as a list)
 *    and Media's Now Playing are not needed;
 *  - Media still holds what the plan could not put on the first page: the sliders ([CcPlan.slidersOnFirst]) and Focus
 *    ([CcPlan.focusOnFirst], when [focus] is on);
 *  - when not even two rows fit, both pages are kept, since the first page cannot hold either module.
 * A pure function of the same numbers as [ccPlanControls], so a test can run it over every screen.
 */
internal fun ccExtraPages(plan: CcPlan, pageHeightDp: Float?, cellDp: Float, gapDp: Float, focus: Boolean, dedupe: Boolean): List<CcPage> {
    if (!dedupe) return listOf(CcPage.MEDIA, CcPage.CONNECTIVITY)
    val modulesFit = pageHeightDp == null || ccRowsThatFit(pageHeightDp, cellDp, gapDp) >= 2
    val media = !modulesFit || !plan.slidersOnFirst || (focus && !plan.focusOnFirst)
    return listOfNotNull(CcPage.MEDIA.takeIf { media }, CcPage.CONNECTIVITY.takeIf { !modulesFit })
}

/** Whether the Media page draws the brightness and volume sliders: always before, and with [dedupe] only when the first page could not hold them. */
internal fun ccMediaPageHasSliders(plan: CcPlan, dedupe: Boolean): Boolean = !dedupe || !plan.slidersOnFirst

/** The height of the page strip standing at the edge: the battery ring (when shown) and a 48 dp dot for each of [pages], [gapDp] apart, and the extra gap under the ring when [apart]. */
internal fun ccEdgeStripHeight(pages: Int, battery: Boolean, gapDp: Float, apart: Boolean): Float {
    val dots = pages * 48f + (pages - 1).coerceAtLeast(0) * gapDp
    return if (battery) 48f + gapDp + (if (apart) CC_BATTERY_APART_DP else 0f) + dots else dots
}

/** The extra space between the battery ring and the first page dot, so the ring reads as a status and not as a page; on top of the strip's own gap between targets. */
internal const val CC_BATTERY_APART_DP = FolioSpace.LARGE.toFloat()

/**
 * How many rows the Controls page needs. With four columns the first four small controls sit beside the sliders; with more the sliders join
 * the top row (beside the connectivity and Now Playing modules) and every small control runs in rows of [columns].
 */
internal fun controlCenterRows(columns: Int, controls: Int, focus: Boolean): Int =
    if (columns >= 6) 3 + (if (focus) 1 else 0) + (controls + columns - 1) / columns
    else 5 + (if (focus) 1 else 0) + (maxOf(0, controls - 4) + 3) / 4

/** Columns, the size of one cell and of the gap in dp, and where the page strip stands. */
internal data class CcGrid(val columns: Int, val cellDp: Float, val gapDp: Float, val stripAtEdge: Boolean, val stripDp: Float, val stripInHeader: Boolean = false)

/**
 * Control Center fills the window instead of sitting in a corner. Nothing here is a device: it is worked out from the panel's width and
 * height, the size the person chose ([sizeCellDp]), the touch target ([touchDp], the strip's buttons), the side padding the caller draws
 * ([sidePaddingDp]) and the controls they have ([controls]).
 *  - Columns come in pairs (4, 6, 8 ...): the fewest whose cell is not bigger than [CC_FILL_MAX] times the chosen size, so a wide window
 *    gets more columns, not giant tiles, but never more than the controls can use.
 *  - A window too short for the rows takes the next pair while that makes the cell at least a gap bigger.
 *  - The page strip is one touch target wide plus a gap. It stands at the edge when the panel is wide enough for that to cost little, and
 *    lies under the grid otherwise.
 * A null [heightDp] means no height limit.
 */
internal fun controlCenterGrid(panelWidthDp: Float, heightDp: Float?, controls: Int, focus: Boolean, sizeCellDp: Float,
    touchDp: Float = 48f, sidePaddingDp: Float = 32f): CcGrid {
    val gap = sizeCellDp * CC_GAP_RATIO
    val strip = touchDp + gap
    val edge = panelWidthDp >= CC_EDGE_STRIP_PANEL_RATIO * strip
    // Too short to spend a row of height on the page strip: it moves into the header, between the two buttons, as small dots.
    val inHeader = !edge && heightDp != null && heightDp < CC_STRIP_IN_HEADER_BELOW_DP
    val gridWidth = panelWidthDp - sidePaddingDp - if (edge) strip else 0f
    val maxCell = sizeCellDp * CC_FILL_MAX
    val maxColumns = (((controls + 1) / 2) * 2).coerceIn(4, 12)
    fun widthCell(c: Int) = (gridWidth - gap * (c - 1)) / c
    fun heightCell(c: Int): Float {
        val rows = controlCenterRows(c, controls, focus)
        return heightDp?.let { (it - (touchDp + gap) - (if (edge || inHeader) 0f else strip) - gap * (rows - 1)) / rows } ?: Float.MAX_VALUE
    }
    fun cell(c: Int) = minOf(widthCell(c), heightCell(c), maxCell)
    var columns = 4
    while (columns < maxColumns && widthCell(columns) > maxCell) columns += 2
    while (columns < maxColumns && cell(columns) < sizeCellDp && cell(columns + 2) >= cell(columns) + gap) columns += 2
    return CcGrid(columns, maxOf(CC_MIN_CELL_DP, cell(columns)), gap, edge, strip, inHeader)
}

/**
 * The saved layout. Settings from before the choice existed only had "clock beside notifications": on is Full screen (how it was),
 * off is Card (the narrower panel it gave). Anything unreadable, or nothing at all, is Full screen.
 */
internal fun ncLayoutFrom(saved: String?, legacySplit: Boolean?): NcLayout =
    NcLayout.entries.firstOrNull { it.name == saved } ?: if (legacySplit == false) NcLayout.CARD else NcLayout.FULL_SCREEN


/**
 * What Clear glass needs over a backdrop for white text to keep 4.5:1 (PNL-6), so the panel is as light as the backdrop allows instead of
 * one fixed dim: a dark wallpaper gets almost no dim and see-through tiles, a bright one a stronger dim and then darker tiles. [dim] is the
 * black over the backdrop, [tileBlack] and [tileWhite] the tile's own fill (a tile that is clear is white at 16 percent; as it darkens the white fades).
 */
internal data class CcGlassTone(val dim: Float, val tileBlack: Float, val tileWhite: Float)

/** The tone Clear glass uses before the backdrop is known, and with any other look: today's dim and clear tiles. */
internal val CC_DEFAULT_TONE = CcGlassTone(dim = .42f, tileBlack = 0f, tileWhite = .16f)

private const val CC_CLEAR_MAX_DIM = .30f
private const val CC_CLEAR_TILE_WHITE = .16f
/** White text at 4.5:1 needs the background no brighter than this, as an sRGB value (relative luminance .18). */
private const val CC_TEXT_MAX_SRGB = .46f

/**
 * [brightness] is how bright the backdrop's bright parts are (its 95th percentile, sRGB 0 to 1: a patch under a control covering a twentieth of it counts). Values blend in sRGB, as Compose does.
 * The dim goes up only as far as needed, to [CC_CLEAR_MAX_DIM]; past that the tiles darken until the text is at 4.5:1.
 */
internal fun ccClearTone(brightness: Float, dimScale: Float = 1f): CcGlassTone {
    val v = brightness.coerceIn(0f, 1f)
    val w = CC_CLEAR_TILE_WHITE
    val needed = if (v <= 0f) 0f else 1f - ((CC_TEXT_MAX_SRGB - w) / (1f - w)) / v
    val effective = needed.coerceIn(0f, CC_CLEAR_MAX_DIM)
    val x = v * (1f - effective)
    fun shown(t: Float): Float {
        val white = w * (1f - minOf(1f, t / .2f))
        return x * (1f - t) * (1f - white) + white
    }
    var t = 0f
    while (t < 1f && shown(t) > CC_TEXT_MAX_SRGB) t += .01f
    // The dim is drawn inside a layer whose opacity is [dimScale] (a card or two side by side leave Home visible and scale the scrim to .6), so the
    // black drawn is the effective dim over that scale: it is the alpha to draw, not the dim the eye gets.
    return CcGlassTone((effective / dimScale.coerceIn(.1f, 1f)).coerceAtMost(1f), t.coerceAtMost(1f), w * (1f - minOf(1f, t / .2f)))
}

/** The 95th percentile of the relative luminance of [pixels] (ARGB) written as the gray that has it (sRGB, 0 to 1: a pure green is much brighter than its luma suggests, and the 4.5:1 rule is about luminance), a stand-in for how bright the backdrop's bright parts are, so a bright patch under a control is not averaged away; .5 with no pixels. */
private val LINEAR = FloatArray(256) { i -> val c = i / 255f; if (c <= .04045f) c / 12.92f else Math.pow(((c + .055f) / 1.055f).toDouble(), 2.4).toFloat() }

internal fun ccBrightness(pixels: IntArray): Float {
    if (pixels.isEmpty()) return .5f
    val histogram = IntArray(256)
    for (p in pixels) {
        val y = .2126f * LINEAR[(p shr 16) and 255] + .7152f * LINEAR[(p shr 8) and 255] + .0722f * LINEAR[p and 255]
        val encoded = if (y <= .0031308f) 12.92f * y else 1.055f * Math.pow(y.toDouble(), 1 / 2.4).toFloat() - .055f
        histogram[Math.round(encoded * 255f).coerceIn(0, 255)]++
    }
    val target = (pixels.size * .95f).toInt()
    var seen = 0
    for (level in 0..255) { seen += histogram[level]; if (seen > target) return level / 255f }
    return 1f
}
