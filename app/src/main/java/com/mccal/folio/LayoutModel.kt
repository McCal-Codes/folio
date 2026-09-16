package com.mccal.folio

/** Jake's reference measures 76px dock artwork against 106px home artwork. */
fun dockIconSize(homeIconSize: Float) = homeIconSize * (76f / 106f)

data class LayoutPreset(
    val iconSize: Float = 66f,
    val rowGap: Float = 8f,
    val dockWidth: Float = 68f,
    val dockPosition: Float = 0.56f,
    val dockAlignToGrid: Boolean = true,
) {
    fun sanitized() = copy(
        iconSize = iconSize.coerceIn(40f, 68f),
        rowGap = rowGap.coerceIn(0f, 28f),
        dockWidth = dockWidth.coerceIn(56f, 84f),
        dockPosition = dockPosition.coerceIn(0.25f, 0.75f),
    )
}

data class HomeGeometry(
    val expanded: Boolean,
    val homeWidth: Float,
    val gridWidth: Float,
    val iconSize: Float,
    val rowHeight: Float,
    val widgetHeight: Float,
    val contentTop: Float,
    val dockTop: Float,
    val dockHeight: Float,
    val dockRowHeight: Float,
    /** Short, wide windows: the page's rows are laid out as two 4-column halves side by side. */
    val splitColumns: Boolean = false,
    val cellWidth: Float = gridWidth / 4f,
    val zoneGap: Float = 0f,
    /** Tall, roomy windows (the unfolded screen in portrait): one centered page with the dock as a bar along the bottom. */
    val horizontalDock: Boolean = false,
    val dockBarHeight: Float = 0f,
)

/**
 * Where Home's 4×6 cells draw on a page. Stacked: one 4-column grid whose first two rows are the
 * half-height widget rows. Two columns (short, wide windows): rows before [splitRow] on the left and the
 * rest on the right, like a stacked iOS layout rearranging into two columns when there's width for it.
 */
data class HomeCellLayout(val cellWidth: Float, val topPitch: Float, val rowHeight: Float, val splitRow: Int?, val zoneGap: Float,
    val widgetsOnTop: Boolean = true) {
    /** Rows 0–1 are widget-height halves, except on a two-column page with no widgets up there (then they're app rows). */
    fun pitch(row: Int) = if (row < 2 && (splitRow == null || widgetsOnTop)) topPitch else rowHeight
    private fun onRight(row: Int) = splitRow != null && row >= splitRow
    fun x(column: Int, row: Int) = (if (onRight(row)) 4f * cellWidth + zoneGap else 0f) + column * cellWidth
    fun y(row: Int) = ((if (onRight(row)) splitRow!! else 0) until row).fold(0f) { sum, r -> sum + pitch(r) }
    fun spanHeight(row: Int, rows: Int) = (row until row + rows).fold(0f) { sum, r -> sum + pitch(r) }
    fun height(rows: Int) = if (splitRow == null) spanHeight(0, rows) else maxOf(spanHeight(0, splitRow), spanHeight(splitRow, rows - splitRow))

    companion object {
        /** [widgets] are (row, spanY) of the page's widgets; a widget is never cut across the two halves. */
        fun forPage(geometry: HomeGeometry, widgets: List<Pair<Int, Int>>): HomeCellLayout {
            val topPitch = (geometry.widgetHeight + 18f) / 2f
            val split = if (!geometry.splitColumns) null else
                // Like iPhone Duo's Home: widgets top-left with two app rows under them, the other app rows on the right.
                (if (widgets.any { it.first < 2 }) listOf(4, 2, 3) else listOf(3, 4, 2))
                    .firstOrNull { s -> widgets.none { (row, span) -> row < s && row + span > s } }
            return HomeCellLayout(geometry.cellWidth, topPitch, geometry.rowHeight, split, geometry.zoneGap,
                widgetsOnTop = widgets.any { it.first < 2 })
        }
    }
}

/** Advance old defaults without changing individually tuned values. */
fun upgradePreset(preset: LayoutPreset, schema: Int, expanded: Boolean): LayoutPreset = when {
    schema < 2 -> preset.copy(
        iconSize = if (preset.iconSize == if (expanded) 58f else 54f) 66f else preset.iconSize,
        rowGap = if (preset.rowGap == 12f) 8f else preset.rowGap,
        dockWidth = if (preset.dockWidth == 64f) 68f else preset.dockWidth,
    )
    schema == 2 && preset.iconSize == 60f -> preset.copy(iconSize = 66f)
    else -> preset
}

/** Shortest height that still counts as regular size (the unfolded screen in either rotation; the cover's landscape is ~475dp). */
const val REGULAR_MIN_HEIGHT_DP = 560f

/** Regular size class in both dimensions, like iOS size classes: never a device, display or orientation check. */
fun isRegularSize(widthDp: Float, heightDp: Float) = widthDp >= 600f && heightDp >= REGULAR_MIN_HEIGHT_DP

fun homeGeometry(width: Float, height: Float, preset: LayoutPreset, labels: Boolean, statusHeight: Float = 0f, labelHeight: Float = 20f, inLibrary: Boolean = false, homeBottomSpace: Float = 44f,
    /** Whether round controls (search, back) sit at the bottom of the rail on Home; without them the dock may run lower. */
    railControls: Boolean = true): HomeGeometry {
    val p = preset.sanitized()
    // Unfolded Duo layout only with regular size both ways; the cover in landscape is still compact.
    // Two Duo panels side by side need a window wider than tall. Taller than wide (portrait), iPhone Duo keeps one
    // centered Home page with the dock as a horizontal bar: the only pose where Apple keeps horizontal bars.
    val horizontalDock = width >= 600f && height >= REGULAR_MIN_HEIGHT_DP && height > width
    val expanded = width >= 650f && height >= REGULAR_MIN_HEIGHT_DP && !horizontalDock
    val homeWidth = if (expanded) minOf(460f, width * 0.56f) else width
    val dockBarHeight = if (horizontalDock) dockIconSize(p.iconSize) + 28f else 0f
    val homeBottomSpace = homeBottomSpace + if (horizontalDock) dockBarHeight + 16f else 0f
    // Columns about as wide as an icon and its breathing room, centered, instead of stretching across a big screen.
    // Status sits in the top-right corner, so the same margin is kept on both sides and the grid stays centered.
    var gridWidth = if (horizontalDock) minOf(width - 2f * (p.dockWidth + 56f), 4f * p.iconSize * 1.9f).coerceAtLeast(4f * (p.iconSize + 16f))
        else (homeWidth - p.dockWidth - 44f).coerceAtLeast(192f)
    // Keep the same icon rhythm when labels are hidden; allow larger system text to fit.
    val labelSpace = if (labels) maxOf(20f, labelHeight) else 20f
    fun rowFor(iconSize: Float, gap: Float) = maxOf(48f, iconSize + labelSpace) + gap
    var icon = minOf(p.iconSize, (gridWidth / 4f - 10f).coerceAtLeast(32f))
    var gap = p.rowGap
    var widget = minOf(176f, gridWidth / 2f - 5f).coerceAtLeast(88f)
    val fitHeight = height - 16f - homeBottomSpace
    // Short, wide windows (the cover in landscape): two 4-column halves side by side instead of one tall,
    // squashed grid, so icons stay full size (iPad likewise keeps widgets in a column beside its apps).
    val zoneGap = 28f
    val splitCell = (gridWidth - zoneGap) / 8f
    val splitColumns = !expanded && width > height * 1.15f && splitCell >= 64f
    if (splitColumns) {
        icon = minOf(p.iconSize, splitCell - 16f)
        if (4f * rowFor(icon, gap) > fitHeight) gap = 0f
        if (4f * rowFor(icon, gap) > fitHeight) icon = minOf(icon, (fitHeight / 4f - labelSpace).coerceAtLeast(40f))
        widget = minOf(176f, 2f * splitCell - 10f)
        gridWidth = 8f * splitCell + zoneGap
    } else {
        // Other short windows: tighten row spacing, then icons, then the widget row, so a page fits the
        // height instead of running under the controls. Rows stay at least 48dp tall.
        fun needed() = widget + 18f + 4f * rowFor(icon, gap)
        if (needed() > fitHeight) gap = 0f
        if (needed() > fitHeight) icon = minOf(icon, ((fitHeight - widget - 18f) / 4f - labelSpace).coerceAtLeast(40f))
        if (needed() > fitHeight) widget = minOf(widget, (fitHeight - 18f - 4f * rowFor(icon, gap)).coerceAtLeast(88f))
    }
    val row = rowFor(icon, gap)
    // Center the page vertically. Two columns: the left half (widget row plus two app rows) is the taller one.
    val pageHeight = if (splitColumns) maxOf(widget + 18f + 2f * row, 3f * row) else widget + 18f + 4f * row
    val contentTop = ((height - pageHeight - homeBottomSpace) / 2f).coerceIn(16f, 72f)
    // Search reclaims the redundant bottom controls' space for all four dock apps.
    // Extremely short windows still scroll rather than reduce touch targets below 48dp.
    // The status rail sits at the content top; in two columns the dock shares its edge with it, so it starts below.
    val homeReserve = if (railControls) 124f else 28f
    val bottomReserve = if (inLibrary) 12f else homeReserve
    val belowStatus = contentTop + statusHeight
    val topLimit = if (splitColumns && statusHeight > 0f && height - belowStatus - bottomReserve >= 4f * 48f + 16f) belowStatus
        else maxOf(8f, statusHeight)
    // Outer dock edges span the first through third icon images, excluding the last label.
    // Never shorter than four 48dp dock targets, even when a short window has shrunk the rows.
    val desiredHeight = maxOf(4f * 48f + 16f, if (p.dockAlignToGrid) 2f * row + icon else 256f)
    val dockHeight = minOf(desiredHeight, (height - topLimit - bottomReserve).coerceAtLeast(76f))
    val dockRowHeight = ((dockHeight - 16f) / 4f).coerceAtLeast(48f)
    // Use the home position as the anchor, so removing library buttons does not
    // move a low-positioned dock on ordinary page swipes. Move up only to fit.
    val homeDockHeight = minOf(desiredHeight, (height - topLimit - homeReserve).coerceAtLeast(76f))
    // Aligned with the app rows: below the widget row when stacked; in two columns the app rows start at the
    // top, so the dock starts below the status rail instead of running into it.
    // Two columns (iPhone Duo): status pinned to the top of the rail, the dock to the bottom, open space between.
    val homeDockTop = (if (splitColumns) height - homeReserve - homeDockHeight
        else if (p.dockAlignToGrid) contentTop + widget + 18f else height * p.dockPosition - homeDockHeight / 2f)
        .coerceIn(topLimit, maxOf(topLimit, height - homeDockHeight - homeReserve))
    val dockTop = homeDockTop.coerceIn(topLimit, maxOf(topLimit, height - dockHeight - bottomReserve))
    return HomeGeometry(expanded, homeWidth, gridWidth, icon, row, widget, contentTop, dockTop, dockHeight, dockRowHeight,
        splitColumns = splitColumns, cellWidth = if (splitColumns) splitCell else gridWidth / 4f, zoneGap = if (splitColumns) zoneGap else 0f,
        horizontalDock = horizontalDock, dockBarHeight = dockBarHeight)
}

/** Keep stored order stable across installs, removals and configuration changes. */
fun reconcileOrder(saved: List<String>, installed: List<String>): List<String> {
    val present = installed.toSet()
    return (saved.filter { it in present } + installed).distinct()
}

/** Installing an app must never create a home-screen pin. */
fun reconcilePins(saved: List<String>, installed: List<String>): List<String> {
    val available = installed.toSet()
    return saved.filter { it in available }.distinct()
}

fun migrateHomePins(legacy: List<String>, installed: List<String>, suggested: List<String>): List<String> {
    val surviving = reconcilePins(legacy, installed)
    val oldSet = surviving.toSet()
    val wasReordered = surviving.isNotEmpty() && surviving != installed.filter { it in oldSet }
    return if (wasReordered) surviving.take(16) else reconcilePins(suggested, installed).take(16)
}

fun homePageCount(cellCount: Int) = maxOf(1, (cellCount + HOME_CELLS - 1) / HOME_CELLS)

fun moveApp(order: List<String>, id: String, offset: Int): List<String> {
    val from = order.indexOf(id)
    if (from < 0) return order
    val to = (from + offset).coerceIn(0, order.lastIndex)
    return order.toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * A Home page's own look (after Atria's per-page layouts): icon size and labels. It only changes how icons draw inside
 * their cells, never the grid, so widgets, dragging and the dock stay lined up on every page.
 */
data class PageStyle(val iconScale: Float = 1f, val labels: Boolean? = null) {
    val isDefault get() = iconScale == 1f && labels == null

    /** Icon size and label visibility on this page, given the Home-wide values and the cell the icon lives in. */
    fun apply(geometry: HomeGeometry, labelsHome: Boolean): Pair<Float, Boolean> {
        val labels = labels ?: labelsHome
        // Without labels an icon may use the label's room too; it never outgrows its cell.
        val room = minOf(geometry.cellWidth - 8f, geometry.rowHeight - (if (labels) 22f else 8f))
        return (geometry.iconSize * iconScale).coerceIn(32f, maxOf(32f, room)) to labels
    }

    companion object {
        val SIZES = listOf("Small" to .82f, "Default" to 1f, "Large" to 1.14f)
    }
}

/**
 * iPhone Duo-style displacement around a horizontal fold (a half-open phone held upright): instead of leaving a row of
 * icons in the curve, that row and every row after it move down past the fold. A widget is never split: if one spans
 * the fold, the move starts at its first row. All values in dp; [gridTop] and the hinge are in window coordinates.
 * Returns the first moved row and how far rows move, or null when nothing is in the fold (or the page is two columns).
 */
fun foldDisplacement(cells: HomeCellLayout, rows: Int, gridTop: Float, hingeTop: Float, hingeBottom: Float,
    widgets: List<Pair<Int, Int>>, margin: Float = 12f): Pair<Int, Float>? {
    if (cells.splitRow != null || hingeBottom <= hingeTop - 1f) return null
    val hit = (0 until rows).firstOrNull { row ->
        val top = gridTop + cells.y(row)
        val bottom = top + cells.spanHeight(row, 1)
        bottom > hingeTop - margin && top < hingeBottom + margin
    } ?: return null
    val start = widgets.filter { (row, span) -> row < hit && row + span > hit }.minOfOrNull { it.first } ?: hit
    val shift = hingeBottom + margin - (gridTop + cells.y(start))
    return if (shift > 0f) start to shift else null
}

/**
 * App Library category columns for a library [widthDp] wide: as many ~160dp tiles (with 14dp gaps) as fit, like iPad.
 * Across a fold line running top to bottom the count is kept even so no tile sits on the crease, and it rounds to
 * whichever even count still gives good-sized tiles (a 606dp library gets four ~141dp tiles, not two giant ones).
 */
fun libraryColumns(widthDp: Float, verticalHinge: Boolean): Int {
    val fit = ((widthDp + LIBRARY_GAP_DP) / (LIBRARY_TILE_DP + LIBRARY_GAP_DP)).toInt().coerceIn(2, 6)
    if (!verticalHinge || fit % 2 == 0) return fit
    val wider = fit + 1
    val tile = (widthDp + LIBRARY_GAP_DP) / wider - LIBRARY_GAP_DP
    return if (wider <= 6 && tile >= LIBRARY_MIN_TILE_DP) wider else fit - 1
}

private const val LIBRARY_TILE_DP = 160f
private const val LIBRARY_GAP_DP = 14f
private const val LIBRARY_MIN_TILE_DP = 128f

/**
 * How much larger Folio draws on big screens (tablets, desktop windows, large foldables), so Home, the dock, panels
 * and sheets fill the space like iPad does instead of looking like a phone layout floating in a big window.
 * Exactly 1 on phones, flip phones and the Galaxy Z Fold's inner screen; grows with the smaller of the two sides.
 */
fun uiScale(widthDp: Float, heightDp: Float): Float {
    if (!isRegularSize(widthDp, heightDp)) return 1f
    val long = maxOf(widthDp, heightDp)
    val short = minOf(widthDp, heightDp)
    return minOf(long / 960f, short / 700f).coerceIn(1f, 1.45f)
}
