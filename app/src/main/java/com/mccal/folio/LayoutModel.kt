package com.mccal.folio

/** Jake's reference measures 76px dock artwork against 106px home artwork. */
fun dockIconSize(homeIconSize: Float) = homeIconSize * (76f / 106f)

data class LayoutPreset(
    val iconSize: Float = 66f,
    val rowGap: Float = 8f,
    val dockWidth: Float = 68f,
    val dockPosition: Float = 0.56f,
    val dockAlignToGrid: Boolean = true,
    /** Home Screen & Dock › Dock: per screen, like the other values here. */
    val dockPlacement: DockPlacement = DockPlacement.AUTOMATIC,
    /** Home Screen & Dock › Status: level with the first row of apps, or at [statusPosition]. */
    val statusAlignToGrid: Boolean = true,
    /** 0 = the top of the screen, 1 = as low as it goes while the dock still fits under it. */
    val statusPosition: Float = 0f,
    /**
     * Space between columns. [DEFAULT_COLUMN_GAP] is Folio's own spacing for each screen; every dp more shrinks the icons
     * by that much (the grid can't grow past the window), every dp less narrows the columns so apps sit closer.
     */
    val columnGap: Float = DEFAULT_COLUMN_GAP,
    /** Extra space between dock apps, on the Side Bar and in the bottom bar. */
    val dockSpacing: Float = 0f,
    /** Height of the widget rows, as a share of Folio's own for the screen. */
    val widgetScale: Float = 1f,
    /** Home Screen & Dock › Position › Apps › Top: the page starts at the top instead of centered in the free space. */
    val pageTop: Boolean = false,
) {
    fun sanitized() = copy(
        iconSize = iconSize.coerceIn(40f, 68f),
        rowGap = rowGap.coerceIn(0f, 28f),
        dockWidth = dockWidth.coerceIn(56f, 84f),
        dockPosition = dockPosition.coerceIn(0f, 1f),
        statusPosition = statusPosition.coerceIn(0f, 1f),
        columnGap = columnGap.takeIf { it.isFinite() }?.coerceIn(8f, 40f) ?: DEFAULT_COLUMN_GAP,
        dockSpacing = dockSpacing.takeIf { it.isFinite() }?.coerceIn(0f, 24f) ?: 0f,
        widgetScale = widgetScale.takeIf { it.isFinite() }?.coerceIn(.8f, 1.25f) ?: 1f,
    )
}

const val DEFAULT_COLUMN_GAP = 16f
/** Narrowest column (dp) when app names show, so common names fit on one line. */
const val MIN_LABELED_COLUMN = 64f

enum class DockPlacement {
    /** Side Bar, or a bar along the bottom when a regular-size screen is upright (iPhone Duo). */
    AUTOMATIC,
    SIDE,
    /** Along the bottom wherever it fits: short landscape phone screens keep the Side Bar so every row stays full size. */
    BOTTOM;
    companion object { fun parse(name: String?) = entries.firstOrNull { it.name == name } ?: AUTOMATIC }
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
    /** The bottom dock was chosen on a phone-sized screen: it sits under the grid, beside the status Side Bar. */
    val dockBesideRail: Boolean = false,
    val dockBarHeight: Float = 0f,
    /** Where the status Side Bar starts. */
    val statusTop: Float = contentTop,
    /** App rows this layout was made for (see [visibleHomeRows]), and how many would fit this window. */
    val appRows: Int = BASE_APP_ROWS,
    val fitAppRows: Int = BASE_APP_ROWS,
    /** The space under each row's label, after any tightening for extra rows. */
    val rowGap: Float = 8f,
    /** Columns narrower than the grid (a smaller Space between columns): where the first one starts. */
    val columnsInset: Float = 0f,
    /** The bottom dock bar's distance between apps. */
    val dockPitch: Float = 0f,
)

/**
 * Where Home's cells (4 columns, [GRID_ROWS] rows) draw on a page. Stacked: one 4-column grid whose first two rows are the
 * half-height widget rows. Two columns (short, wide windows): rows before [splitRow] on the left and the
 * rest on the right, like a stacked iOS layout rearranging into two columns when there's width for it.
 */
data class HomeCellLayout(val cellWidth: Float, val topPitch: Float, val rowHeight: Float, val splitRow: Int?, val zoneGap: Float,
    val widgetsOnTop: Boolean = true, val inset: Float = 0f) {
    /** Rows 0–1 are widget-height halves, except on a two-column page with no widgets up there (then they're app rows). */
    fun pitch(row: Int) = if (row < 2 && (splitRow == null || widgetsOnTop)) topPitch else rowHeight
    private fun onRight(row: Int) = splitRow != null && row >= splitRow
    fun x(column: Int, row: Int) = inset + (if (onRight(row)) 4f * cellWidth + zoneGap else 0f) + column * cellWidth
    fun y(row: Int) = ((if (onRight(row)) splitRow!! else 0) until row).fold(0f) { sum, r -> sum + pitch(r) }
    fun spanHeight(row: Int, rows: Int) = (row until row + rows).fold(0f) { sum, r -> sum + pitch(r) }
    fun height(rows: Int) = if (splitRow == null) spanHeight(0, rows) else maxOf(spanHeight(0, splitRow), spanHeight(splitRow, rows - splitRow))

    companion object {
        /**
         * [widgets] are (row, spanY) of the page's widgets; a widget is never cut across the two halves. Two columns keep
         * four app rows split between the halves; extra rows (More rows) continue in the right half after its rows.
         */
        fun forPage(geometry: HomeGeometry, widgets: List<Pair<Int, Int>>): HomeCellLayout {
            val topPitch = (geometry.widgetHeight + 18f) / 2f
            val split = if (!geometry.splitColumns) null else
                // Like iPhone Duo's Home: widgets top-left with two app rows under them, the other app rows on the right.
                (if (widgets.any { it.first < 2 }) listOf(4, 2, 3) else listOf(3, 4, 2))
                    .firstOrNull { s -> widgets.none { (row, span) -> row < s && row + span > s } }
            return HomeCellLayout(geometry.cellWidth, topPitch, geometry.rowHeight, split, geometry.zoneGap,
                widgetsOnTop = widgets.any { it.first < 2 }, inset = geometry.columnsInset)
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

/** Android's medium window width class starts here (window size classes: 600, 840, 1200, 1600 dp). */
const val ANDROID_MEDIUM_WIDTH_DP = 600f

/**
 * Folio's own height for the regular Home layout, not an Android size class (Android's medium height starts at
 * 480 dp): Home's widget row, four app rows, status and dock need about this much. The unfolded screen clears it in
 * either rotation; the cover in landscape (~475 dp) doesn't.
 */
const val HOME_REGULAR_MIN_HEIGHT_DP = 560f

/**
 * Whether a window gets Folio's regular Home layout (like an iOS regular size class in both dimensions): Android's
 * medium width and Folio's own [HOME_REGULAR_MIN_HEIGHT_DP]. Never a device, display or orientation check.
 * [classScale] converts to dp at the phone's own density (see [classScale]), so a changed display size can't turn a
 * phone-sized screen into a tablet one.
 */
fun fitsRegularHomeLayout(widthDp: Float, heightDp: Float, classScale: Float = 1f) =
    widthDp * classScale >= ANDROID_MEDIUM_WIDTH_DP && heightDp * classScale >= HOME_REGULAR_MIN_HEIGHT_DP

/**
 * Whether a configuration change actually moved the walls: a fold, an unfold, a rotation or a resize, rather than
 * something like a font-scale or theme change that leaves the window where it was. A few dp of difference is the
 * system bars coming and going, not a new screen.
 */
fun windowChangedShape(was: Pair<Int, Int>?, now: Pair<Int, Int>, slack: Int = 8): Boolean =
    was != null && (kotlin.math.abs(was.first - now.first) > slack || kotlin.math.abs(was.second - now.second) > slack)

/** Current density over the device's own ([stableDpi]); 1 when either is unknown. */
fun classScale(densityDpi: Int, stableDpi: Int): Float =
    if (densityDpi <= 0 || stableDpi <= 0) 1f else densityDpi.toFloat() / stableDpi

fun homeGeometry(width: Float, height: Float, preset: LayoutPreset, labels: Boolean, statusHeight: Float = 0f, labelHeight: Float = 20f, inLibrary: Boolean = false, homeBottomSpace: Float = 44f,
    /** Whether round controls (search, back) sit at the bottom of the rail on Home; without them the dock may run lower. */
    railControls: Boolean = true,
    /** See [fitsRegularHomeLayout]: size classes are judged at the phone's own density. */
    classScale: Float = 1f,
    /** App rows to lay out (More rows); the result's [HomeGeometry.fitAppRows] says how many fit this window. */
    appRows: Int = BASE_APP_ROWS,
    /** A book-style fold down the middle of the window: the Home half stays on its side of it. */
    foldAtCenter: Boolean = false,
    /** Rows › Automatic: space left under the last row that fits is shared between the rows (up to 12 dp each). */
    fillSpace: Boolean = false): HomeGeometry {
    val p = preset.sanitized()
    // Unfolded Duo layout only with regular size both ways; the cover in landscape is still compact.
    // Two Duo panels side by side need a window wider than tall. Taller than wide (portrait), iPhone Duo keeps one
    // centered Home page with the dock as a horizontal bar: the only pose where Apple keeps horizontal bars.
    val regular = fitsRegularHomeLayout(width, height, classScale)
    val tallRegular = regular && height > width
    // Landscape phone screens keep the Side Bar dock: a bottom bar would take too much of a short screen.
    val horizontalDock = when (p.dockPlacement) {
        DockPlacement.AUTOMATIC -> tallRegular
        DockPlacement.SIDE -> false
        // Short windows (split screen, pop-up windows) keep the Side Bar too, so the page still fits above the bar.
        DockPlacement.BOTTOM -> regular || (height > width && height >= HOME_REGULAR_MIN_HEIGHT_DP)
    }
    // Everywhere but the upright unfolded screen, the status Side Bar stays and the bottom dock sits beside it.
    val dockBesideRail = horizontalDock && !tallRegular
    val expanded = width * classScale >= 650f && height * classScale >= HOME_REGULAR_MIN_HEIGHT_DP && !tallRegular
    val homeWidth = if (expanded) minOf(460f, width * 0.56f, if (foldAtCenter) width / 2f else Float.MAX_VALUE) else width
    val dockBarHeight = if (horizontalDock) dockIconSize(p.iconSize) + 28f else 0f
    val homeBottomSpace = homeBottomSpace + if (horizontalDock) dockBarHeight + 16f else 0f
    // Columns about as wide as an icon and its breathing room, centered, instead of stretching across a big screen.
    // Status sits in the top-right corner, so the same margin is kept on both sides and the grid stays centered.
    // Portrait (horizontal dock): the four columns spread across the screen between equal margins, like iPad mini's
    // Home, rather than sitting in a narrow block when Display size / Screen zoom gives the screen more room (issue #10).
    // Upright with a Side Bar dock: the same centered spread, beside the dock.
    var gridWidth = if (tallRegular && horizontalDock) minOf(width - 2f * (p.dockWidth + 56f), 4f * p.iconSize * 2.6f).coerceAtLeast(4f * (p.iconSize + 16f))
        else if (tallRegular) minOf(width - p.dockWidth - 44f, 4f * p.iconSize * 2.6f).coerceAtLeast(192f)
        else (homeWidth - p.dockWidth - 44f).coerceAtLeast(192f)
    // Keep the same icon rhythm when labels are hidden; allow larger system text to fit.
    val labelSpace = if (labels) maxOf(20f, labelHeight) else 20f
    fun rowFor(iconSize: Float, gap: Float) = maxOf(48f, iconSize + labelSpace) + gap
    // Icons take at most 80% of their column, so the space between apps grows with the screen instead of
    // shrinking to a sliver on narrow phones.
    var icon = minOf(p.iconSize, (gridWidth / 4f * .8f).coerceAtLeast(32f))
    var gap = p.rowGap
    val fitHeight = height - 16f - homeBottomSpace
    // Space between columns, relative to Folio's own for this window: more shrinks icons (not below 40 dp), less
    // narrows the columns (never so far that apps are closer than the 8 dp / fifth-of-a-column rule).
    val columnExtra = p.columnGap - DEFAULT_COLUMN_GAP
    // With app names showing, columns stay wide enough for a typical name ("Calculator", "Play Store") on one line.
    val minLabeledCell = if (labels) MIN_LABELED_COLUMN else 0f
    fun narrowed(cell: Float) = if (columnExtra >= 0f) cell else minOf(cell, maxOf(cell + columnExtra, icon + 8f, icon / .8f, minLabeledCell))
    fun spacedIcon(value: Float) = if (columnExtra <= 0f) value else minOf(value, maxOf(40f, value - columnExtra))
    val rows = appRows.coerceIn(BASE_APP_ROWS, MAX_APP_ROWS)
    // Rows the page is spaced and centered for: the rows it shows (Rows › 4 keeps today's layout).
    var layoutRows = rows
    // More rows never tighten the space under labels below 4 dp.
    val tightGap = minOf(p.rowGap, 4f)
    var fitRows = BASE_APP_ROWS
    // Short, wide windows (the cover in landscape): two 4-column halves side by side instead of one tall,
    // squashed grid, so icons stay full size (iPad likewise keeps widgets in a column beside its apps).
    val zoneGap = 28f
    var splitCell = (gridWidth - zoneGap) / 8f
    val splitColumns = !expanded && width > height * 1.15f && splitCell >= 64f
    var cell = gridWidth / 4f
    var widget: Float
    if (splitColumns) {
        icon = spacedIcon(minOf(p.iconSize, splitCell - 16f))
        splitCell = narrowed(splitCell)
        if (4f * rowFor(icon, gap) > fitHeight) gap = 0f
        if (4f * rowFor(icon, gap) > fitHeight) icon = minOf(icon, (fitHeight / 4f - labelSpace).coerceAtLeast(40f))
        widget = (minOf(176f, 2f * splitCell - 10f) * p.widgetScale).coerceAtLeast(88f)
        // The left half holds the widget row over two app rows; shorten the widget before it runs under the controls.
        if (widget + 18f + 2f * rowFor(icon, gap) > fitHeight) widget = (fitHeight - 18f - 2f * rowFor(icon, gap)).coerceAtLeast(88f)
        // Narrower columns keep the two halves together, centered like the page itself.
        gridWidth = 8f * splitCell + zoneGap
    } else {
        icon = spacedIcon(icon)
        cell = narrowed(cell)
        // The widget row is as wide as the four columns.
        widget = (minOf(176f, 2f * cell - 5f) * p.widgetScale).coerceAtLeast(88f)
        fun needed(n: Int = BASE_APP_ROWS, g: Float = gap) = widget + 18f + n * rowFor(icon, g)
        // More rows: as many app rows as fit at full size, plus one if a tighter space under the labels makes room.
        val fitting = (BASE_APP_ROWS..MAX_APP_ROWS).lastOrNull { needed(it) <= fitHeight } ?: (BASE_APP_ROWS - 1)
        fitRows = (if (fitting < MAX_APP_ROWS && needed(fitting + 1, tightGap) <= fitHeight) fitting + 1 else fitting)
            .coerceAtLeast(BASE_APP_ROWS)
        // Other short windows: tighten row spacing, then icons, then the widget row, so the four app rows fit the
        // height instead of running under the controls. Rows stay at least 48dp tall.
        if (needed() > fitHeight) gap = 0f
        if (needed() > fitHeight) icon = minOf(icon, ((fitHeight - widget - 18f) / 4f - labelSpace).coerceAtLeast(40f))
        if (needed() > fitHeight) widget = minOf(widget, (fitHeight - 18f - 4f * rowFor(icon, gap)).coerceAtLeast(88f))
        // Laid out for the rows shown, so Rows › 4 looks exactly like before; switching the setting re-centers the page.
        layoutRows = rows
        // Extra rows tighten the spacing only when that's what makes them fit; otherwise the page scrolls.
        if (layoutRows > BASE_APP_ROWS && needed(layoutRows) > fitHeight && needed(layoutRows, minOf(gap, tightGap)) <= fitHeight)
            gap = minOf(gap, tightGap)
    }
    // Automatic rows fill the height: leftover space (less than a row) is shared out as even space between rows
    // instead of a wide empty margin, capped so pages never look stretched; the rest stays as the centering margin.
    // (Not in windows so short the spacing was already squeezed to fit.)
    if (fillSpace && !splitColumns && !p.pageTop && gap >= tightGap) {
        val leftover = fitHeight - (widget + 18f + layoutRows * rowFor(icon, gap))
        if (leftover > 0f) gap += minOf(leftover / (layoutRows + 1), 12f)
    }
    val row = rowFor(icon, gap)
    // Center the page vertically. Two columns: the left half (widget row plus two app rows) is the taller one; extra
    // rows continue in the right half and scroll, so they don't move the page either.
    val pageHeight = if (splitColumns) maxOf(widget + 18f + 2f * row, 3f * row) else widget + 18f + layoutRows * row
    // Centered in the free space; tall phones may sit lower (up to 18% of the height) instead of leaving the bottom empty.
    val contentTop = if (p.pageTop) 16f else ((height - pageHeight - homeBottomSpace) / 2f).coerceIn(16f, maxOf(72f, height * .18f))
    // Search reclaims the redundant bottom controls' space for all four dock apps.
    // Extremely short windows still scroll rather than reduce touch targets below 48dp.
    // The status rail sits at the content top; in two columns the dock shares its edge with it, so it starts below.
    val homeReserve = if (railControls) 124f else 28f
    val bottomReserve = if (inLibrary) 12f else homeReserve
    // A custom status position runs from the top down to where the status still leaves room for four dock targets
    // under it (or, with the dock at the bottom, until it would reach the dock bar). Before the status is measured it
    // sits level with the apps.
    val statusSpan = (statusHeight - 22f).coerceAtLeast(0f)
    val statusTop = if (p.statusAlignToGrid || statusHeight <= 0f) contentTop else {
        val lowest = if (horizontalDock) height - homeBottomSpace - statusSpan
            else height - homeReserve - 4f * 48f - 16f - 10f - statusSpan
        16f + p.statusPosition * (lowest - 16f).coerceAtLeast(0f)
    }
    // [statusHeight] is the rail's full height (its location slot included) plus a 22dp margin, and the rail sits at
    // [contentTop], so the dock starts 10dp under the status capsule whatever the capsule holds (Focus, Silent, Rings…).
    // In very short windows the gap gives way first, never the capsule itself.
    val statusBottom = if (statusHeight > 0f) statusTop + statusHeight - 22f else 0f
    val belowStatus = if (statusHeight > 0f) maxOf(statusBottom, minOf(statusBottom + 10f, height - homeReserve - 76f)) else 0f
    val topLimit = maxOf(8f, belowStatus)
    // Outer dock edges span the first through third icon images, excluding the last label.
    // Never shorter than four 48dp dock targets, even when a short window has shrunk the rows.
    // Space between dock apps makes the dock taller; aligned with the rows, it grows evenly above and below them.
    val dockSpace = p.dockSpacing
    val desiredHeight = maxOf(4f * 48f + 16f, (if (p.dockAlignToGrid) 2f * row + icon else 256f) + 4f * dockSpace)
    val dockHeight = minOf(desiredHeight, (height - topLimit - bottomReserve).coerceAtLeast(76f))
    val dockRowHeight = ((dockHeight - 16f) / 4f).coerceAtLeast(48f)
    // Use the home position as the anchor, so removing library buttons does not
    // move a low-positioned dock on ordinary page swipes. Move up only to fit.
    val homeDockHeight = minOf(desiredHeight, (height - topLimit - homeReserve).coerceAtLeast(76f))
    // Aligned with the app rows: below the widget row when stacked; in two columns the app rows start at the
    // top, so the dock starts below the status rail instead of running into it.
    // Two columns (iPhone Duo): status pinned to the top of the rail, the dock to the bottom, open space between.
    val homeDockTop = (if (splitColumns) height - homeReserve - homeDockHeight
        else if (p.dockAlignToGrid) contentTop + widget + 18f - 2f * dockSpace else height * p.dockPosition - homeDockHeight / 2f)
        .coerceIn(topLimit, maxOf(topLimit, height - homeDockHeight - homeReserve))
    val dockTop = homeDockTop.coerceIn(topLimit, maxOf(topLimit, height - dockHeight - bottomReserve))
    // Bottom bar: the spacing widens each app's slot, but the bar still fits its window (beside the Side Bar, or the whole width).
    val basePitch = dockIconSize(icon) + 22f
    val barRoom = (if (dockBesideRail) width - p.dockWidth - 12f else width) - 32f
    val dockPitch = if (dockSpace <= 0f) basePitch else minOf(basePitch + dockSpace, maxOf(basePitch, (barRoom - 16f) / 4f))
    // Upright unfolded with the dock at the bottom, the page is centered, so narrower columns stay centered too.
    val columnsInset = if (!splitColumns && tallRegular && horizontalDock) (gridWidth - 4f * cell) / 2f else 0f
    return HomeGeometry(expanded, homeWidth, gridWidth, icon, row, widget, contentTop, dockTop, dockHeight, dockRowHeight,
        splitColumns = splitColumns, cellWidth = if (splitColumns) splitCell else cell, zoneGap = if (splitColumns) zoneGap else 0f,
        horizontalDock = horizontalDock, dockBarHeight = dockBarHeight, dockBesideRail = dockBesideRail, statusTop = statusTop,
        appRows = rows, fitAppRows = if (splitColumns) BASE_APP_ROWS else fitRows, rowGap = gap, columnsInset = columnsInset, dockPitch = dockPitch)
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
 * App Library category columns for a library [widthDp] wide: as many tiles as fit at about the size they are on a
 * phone (~150dp with 14dp gaps, to the nearest count), so the unfolded screen shows more categories rather than bigger
 * ones. The library panel isn't centered on the fold, so an even column count wouldn't keep tiles off the crease.
 */
fun libraryColumns(widthDp: Float): Int =
    kotlin.math.round((widthDp + LIBRARY_GAP_DP) / (LIBRARY_TILE_DP + LIBRARY_GAP_DP)).toInt().coerceIn(2, 6)

private const val LIBRARY_TILE_DP = 150f
private const val LIBRARY_GAP_DP = 14f

/**
 * How much larger Folio draws on big screens (tablets, desktop windows, large foldables), so Home, the dock, panels
 * and sheets fill the space like iPad does instead of looking like a phone layout floating in a big window.
 * Exactly 1 on phones, flip phones and the Galaxy Z Fold's inner screen; grows with the smaller of the two sides.
 */
fun uiScale(widthDp: Float, heightDp: Float, classScale: Float = 1f): Float {
    if (!fitsRegularHomeLayout(widthDp, heightDp, classScale)) return 1f
    // Judged at the device's own density: a phone set to a larger Smallest width / smaller Screen zoom has more dp,
    // not a bigger screen, and scaling it up would undo that choice (and shrink Home below the unfolded layout).
    val long = maxOf(widthDp, heightDp) * classScale
    val short = minOf(widthDp, heightDp) * classScale
    return minOf(long / 960f, short / 700f).coerceIn(1f, 1.45f)
}
