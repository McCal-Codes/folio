package com.mccal.folio

import kotlin.math.roundToInt

const val GRID_COLUMNS = 4
/** Stored rows per page: the two half-height widget rows and up to [MAX_APP_ROWS] app rows ("More rows"). */
const val GRID_ROWS = 2 + 7
const val HOME_CELLS = GRID_COLUMNS * GRID_ROWS
/** App rows every page shows; Automatic adds up to [MAX_APP_ROWS] where the screens have room. */
const val BASE_APP_ROWS = 4
const val MAX_APP_ROWS = GRID_ROWS - 2
/** Before More rows, pages were 4×6 (24 cells) and the retained schema-5 overflow widget sat at row 6. */
const val LEGACY_GRID_ROWS = 6
const val LEGACY_HOME_CELLS = GRID_COLUMNS * LEGACY_GRID_ROWS
/** The furthest a freely placed widget is drawn from the cells it keeps, in cells. */
const val MAX_WIDGET_OFFSET = 0.5f
const val EMPTY_WIDGET = -1
const val CLOCK_WIDGET = -2
const val DATE_WIDGET = -3
const val INFO_WIDGET = -4
const val NEEDS_BINDING_WIDGET = -5

/**
 * [offsetX] and [offsetY] are how far a widget is drawn from its cells, in cells (-0.5 to 0.5), when it was placed freely.
 * The cells it covers stay reserved, so apps never end up under it; zero (the default) draws it exactly on the grid.
 */
data class WidgetPlacement(val slot: Int, val id: Int, val page: Int, val column: Int, val row: Int, val spanX: Int, val spanY: Int,
    val offsetX: Float = 0f, val offsetY: Float = 0f)
data class WidgetRestore(val slot: Int, val providerComponent: String, val userSerial: Long, val title: String,
    val profileLabel: String, val isWork: Boolean = false, val sourceScope: String? = null)

val DEFAULT_WIDGET_PLACEMENTS = listOf(
    WidgetPlacement(0, CLOCK_WIDGET, 0, 0, 0, 2, 2),
    WidgetPlacement(1, DATE_WIDGET, 0, 2, 0, 2, 2),
)

fun homeCellPage(index: Int) = Math.floorDiv(index, HOME_CELLS)
fun homeCellLocal(index: Int) = Math.floorMod(index, HOME_CELLS)
fun homeCellIndex(page: Int, local: Int): Int {
    require(page >= -1 && local in 0 until HOME_CELLS)
    return page * HOME_CELLS + local
}

/** Rows a page shows: the widget rows and [appRows] app rows. */
fun visibleHomeRows(appRows: Int) = 2 + appRows.coerceIn(BASE_APP_ROWS, MAX_APP_ROWS)

/** Whether automatic placement may use [index]: cells in rows Home doesn't show are skipped (the next page is used instead). */
fun homeCellShown(index: Int, appRows: Int) = homeCellLocal(index) / GRID_COLUMNS < visibleHomeRows(appRows)

/**
 * Rows drawn on a page: the rows Home shows, or more when apps or widgets already sit lower, so nothing is ever hidden
 * (say, after the other screen showed more rows). [cells] are the page's local cells.
 */
fun shownHomeRows(appRows: Int, cells: List<String?>, widgets: List<WidgetPlacement>): Int {
    val lastApp = cells.indexOfLast { it != null }.let { if (it < 0) 0 else it / GRID_COLUMNS + 1 }
    val lastWidget = widgets.filter { it.row < GRID_ROWS }.maxOfOrNull { minOf(GRID_ROWS, it.row + it.spanY) } ?: 0
    return maxOf(visibleHomeRows(appRows), lastApp, lastWidget).coerceAtMost(GRID_ROWS)
}

/** Old 24-cell pages to 36-cell ones: rows 0–5 keep their local index, so page*24+local becomes page*36+local. */
fun migrateLegacyHomeSlots(slots: List<String?>): List<String?> {
    if (slots.isEmpty()) return slots
    val result = MutableList<String?>(((slots.size - 1) / LEGACY_HOME_CELLS + 1) * HOME_CELLS) { null }
    slots.forEachIndexed { index, id -> result[index / LEGACY_HOME_CELLS * HOME_CELLS + index % LEGACY_HOME_CELLS] = id }
    return result.dropLastWhile { it == null }
}

/** The unfolded-only page keeps its cells; the new rows start empty. */
fun migrateLegacyLeadingSlots(slots: List<String?>): List<String?> = slots.take(HOME_CELLS) + List((HOME_CELLS - slots.size).coerceAtLeast(0)) { null }

/** The retained overflow widget sat just under the old grid (row 6); it moves under the new one. Rows 0–5 are unchanged. */
fun migrateLegacyWidgetPlacement(placement: WidgetPlacement): WidgetPlacement =
    if (placement.row >= LEGACY_GRID_ROWS) placement.copy(row = placement.row + GRID_ROWS - LEGACY_GRID_ROWS) else placement

private fun normalizedLeadingSlots(slots: List<String?>) =
    normalizeHomeSlots(slots.take(HOME_CELLS)).let { it + List(HOME_CELLS - it.size) { null } }

/** Nulls are intentional empty home cells. Only unused trailing cells are removed. */
fun normalizeHomeSlots(slots: List<String?>): List<String?> {
    val seen = mutableSetOf<String>()
    return slots.map { it?.takeIf { id -> id.isNotBlank() && seen.add(id) } }.dropLastWhile { it == null }
}

fun reconcileHomeSlots(slots: List<String?>, installed: Set<String>) =
    normalizeHomeSlots(slots.map { it?.takeIf(installed::contains) })

data class HomeLayout(
    val slots: List<String?>,
    val dock: List<String?>,
    val widgetPlacements: List<WidgetPlacement> = emptyList(),
    val folders: List<FolderEntry> = emptyList(),
    val widgetRestores: List<WidgetRestore> = emptyList(),
    val leadingSlots: List<String?> = List(HOME_CELLS) { null },
    /** Pages the user added explicitly; empty pages survive until removed. */
    val minPages: Int = 1,
) {
    val widgets: List<Int> get() {
        val last = widgetPlacements.maxOfOrNull { it.slot } ?: -1
        return List(maxOf(3, last + 1)) { slot -> placement(slot)?.id ?: EMPTY_WIDGET }
    }
    fun placement(slot: Int) = widgetPlacements.firstOrNull { it.slot == slot }
    fun folder(id: String) = folders.firstOrNull { it.id == id }
    fun widgetRestore(slot: Int) = widgetRestores.firstOrNull { it.slot == slot }
    val contentPageCount get() = maxOf(homePageCount(slots.size),
        widgetPlacements.filter { it.page >= 0 }.maxOfOrNull { it.page + 1 } ?: 1)
    val pageCount get() = maxOf(contentPageCount, minPages)
    fun slotAt(index: Int): String? = when (homeCellPage(index)) {
        -1 -> leadingSlots.getOrNull(homeCellLocal(index))
        in 0..Int.MAX_VALUE -> slots.getOrNull(index)
        else -> null
    }
    fun indexOfShortcut(id: String): Int? {
        val leading = leadingSlots.indexOf(id)
        if (leading >= 0) return homeCellIndex(-1, leading)
        return slots.indexOf(id).takeIf { it >= 0 }
    }
    fun slotsForPage(page: Int): List<String?> = when {
        page == -1 -> normalizedLeadingSlots(leadingSlots)
        page >= 0 -> List(HOME_CELLS) { local -> slots.getOrNull(homeCellIndex(page, local)) }
        else -> emptyList()
    }
    fun withSlot(index: Int, value: String?): HomeLayout = if (homeCellPage(index) == -1) {
        copy(leadingSlots = normalizedLeadingSlots(leadingSlots).toMutableList().apply { this[homeCellLocal(index)] = value })
    } else {
        val next = slots.toMutableList().apply { while (size <= index) add(null); this[index] = value }
        copy(slots = next.dropLastWhile { it == null })
    }
}

sealed interface DropTarget {
    data class Home(val index: Int) : DropTarget
    data class Dock(val index: Int) : DropTarget
    data class Library(val id: String) : DropTarget
    data class Widget(val index: Int) : DropTarget
    data class Folder(val id: String) : DropTarget
    data object Remove : DropTarget
}

fun canPlaceInDock(layout: HomeLayout, id: String): Boolean =
    id.isNotBlank() && !isReservedFolderId(id) && layout.folders.none { id in it.appIds } &&
        (id in layout.dock || layout.dock.any { it == null } || layout.dock.size < MAX_DOCK_SLOTS)

fun WidgetPlacement.coveredIndices(): Set<Int> {
    if (page < -1) return emptySet()
    return buildSet {
        repeat(spanY) { y -> repeat(spanX) { x ->
            if (row + y < GRID_ROWS) add(homeCellIndex(page, (row + y) * GRID_COLUMNS + column + x))
        } }
    }
}

private fun WidgetPlacement.valid() =
    slot >= 0 && id != EMPTY_WIDGET && page >= -1 && column >= 0 && row >= 0 &&
        spanX in 1..GRID_COLUMNS && spanY in 1..GRID_ROWS && column + spanX <= GRID_COLUMNS &&
        row + spanY <= GRID_ROWS && offsetX.isFinite() && offsetY.isFinite() && offsetX in -MAX_WIDGET_OFFSET..MAX_WIDGET_OFFSET &&
        offsetY in -MAX_WIDGET_OFFSET..MAX_WIDGET_OFFSET

private fun widgetCells(layout: HomeLayout, exceptSlot: Int? = null) = layout.widgetPlacements
    .filter { it.slot != exceptSlot }.flatMapTo(mutableSetOf()) { it.coveredIndices() }

private fun overlaps(a: WidgetPlacement, b: WidgetPlacement) = a.page == b.page &&
    a.column < b.column + b.spanX && b.column < a.column + a.spanX &&
    a.row < b.row + b.spanY && b.row < a.row + a.spanY

/** Builds a non-persistable placement draft when the requested rectangle is available within the page's first [rows] rows. */
fun widgetCandidate(layout: HomeLayout, slot: Int, targetIndex: Int, spanX: Int, spanY: Int, rows: Int = GRID_ROWS): WidgetPlacement? {
    val page = homeCellPage(targetIndex)
    if (slot < 0 || page !in -1..layout.pageCount) return null
    val local = homeCellLocal(targetIndex)
    val candidate = WidgetPlacement(slot, EMPTY_WIDGET, page,
        local % GRID_COLUMNS, local / GRID_COLUMNS, spanX, spanY)
    if (spanX !in 1..GRID_COLUMNS || spanY !in 1..GRID_ROWS ||
        candidate.column + spanX > GRID_COLUMNS || candidate.row + spanY > minOf(rows, GRID_ROWS)) return null
    if (layout.widgetPlacements.any { it.slot != slot && overlaps(it, candidate) }) return null
    if (candidate.coveredIndices().any { layout.slotAt(it) != null }) return null
    return candidate
}

/**
 * Moves use insertion order and transfer shortcuts between Home and the dock. Apps pushed along skip cells in rows a page
 * doesn't show with [appRows] app rows (the dragged app's own cell and the target are always usable: they're on screen).
 */
fun dropApp(layout: HomeLayout, id: String, target: DropTarget, appRows: Int = MAX_APP_ROWS): HomeLayout {
    if (id.isBlank() || layout.folders.any { id in it.appIds }) return layout
    return when (target) {
        is DropTarget.Home -> {
            val blocked = widgetCells(layout)
            val targetPage = homeCellPage(target.index)
            if (targetPage !in -1..layout.pageCount || target.index in blocked) return layout
            if (targetPage == -1) {
                val cells = normalizedLeadingSlots(layout.leadingSlots).toMutableList()
                val targetLocal = homeCellLocal(target.index)
                val sourceLocal = cells.indexOf(id)
                cells.indices.filter { it != sourceLocal && cells[it] == id }.forEach { cells[it] = null }
                val blockedLocal = blocked.filter { homeCellPage(it) == -1 }.mapTo(mutableSetOf(), ::homeCellLocal)
                if (targetLocal in blockedLocal) return layout
                if (sourceLocal == targetLocal) return layout
                blockedLocal += (0 until HOME_CELLS).filter { it != sourceLocal && it != targetLocal && !homeCellShown(it, appRows) }
                if (sourceLocal >= 0) {
                    if (cells[targetLocal] == null) {
                        cells[sourceLocal] = null
                        cells[targetLocal] = id
                        return layout.copy(leadingSlots = cells,
                            slots = layout.slots.map { it?.takeUnless(id::equals) }.dropLastWhile { it == null },
                            dock = layout.dock.map { it?.takeUnless(id::equals) })
                    }
                    val usable = (minOf(sourceLocal, targetLocal)..maxOf(sourceLocal, targetLocal)).filterNot { it in blockedLocal }
                    val from = usable.indexOf(sourceLocal); val to = usable.indexOf(targetLocal)
                    if (from < 0 || to < 0) return layout
                    if (from < to) for (position in from until to) cells[usable[position]] = cells[usable[position + 1]]
                    else for (position in from downTo to + 1) cells[usable[position]] = cells[usable[position - 1]]
                    cells[targetLocal] = id
                } else if (cells[targetLocal] == null) cells[targetLocal] = id else {
                    val later = (targetLocal + 1 until HOME_CELLS).firstOrNull { it !in blockedLocal && cells[it] == null }
                    val earlier = (targetLocal - 1 downTo 0).firstOrNull { it !in blockedLocal && cells[it] == null }
                    val vacancy = later ?: earlier ?: return layout
                    val usable = (minOf(vacancy, targetLocal)..maxOf(vacancy, targetLocal)).filterNot { it in blockedLocal }
                    if (vacancy > targetLocal) for (position in usable.lastIndex downTo 1) cells[usable[position]] = cells[usable[position - 1]]
                    else for (position in 0 until usable.lastIndex) cells[usable[position]] = cells[usable[position + 1]]
                    cells[targetLocal] = id
                }
                return layout.copy(leadingSlots = cells,
                    slots = layout.slots.map { it?.takeUnless(id::equals) }.dropLastWhile { it == null },
                    dock = layout.dock.map { it?.takeUnless(id::equals) })
            }
            val slots = layout.slots.toMutableList()
            val source = slots.indexOf(id)
            fun unusable(index: Int) = index in blocked || (index != source && index != target.index && !homeCellShown(index, appRows))
            slots.indices.filter { it != source && slots[it] == id }.forEach { slots[it] = null }
            while (slots.size <= target.index) slots.add(null)
            val occupied = slots[target.index] != null
            when {
                source == target.index -> Unit
                source >= 0 && !occupied -> { slots[source] = null; slots[target.index] = id }
                source >= 0 -> {
                    val usable = (minOf(source, target.index)..maxOf(source, target.index)).filterNot(::unusable)
                    val from = usable.indexOf(source)
                    val to = usable.indexOf(target.index)
                    if (from < 0 || to < 0) return layout
                    if (from < to) for (position in from until to) slots[usable[position]] = slots[usable[position + 1]]
                    else for (position in from downTo to + 1) slots[usable[position]] = slots[usable[position - 1]]
                    slots[target.index] = id
                }
                else -> {
                    if (!occupied) slots[target.index] = id else {
                        var vacancy = target.index + 1
                        val limit = HOME_CELLS * (layout.pageCount + 1)
                        while (vacancy < limit && (unusable(vacancy) || slots.getOrNull(vacancy) != null)) vacancy++
                        if (vacancy >= limit) return layout
                        while (slots.size <= vacancy) slots.add(null)
                        val usable = (target.index..vacancy).filterNot(::unusable)
                        for (position in usable.lastIndex downTo 1) slots[usable[position]] = slots[usable[position - 1]]
                        slots[target.index] = id
                    }
                }
            }
            layout.copy(slots = slots.dropLastWhile { it == null }, dock = layout.dock.map { it?.takeUnless { dockId -> dockId == id } },
                leadingSlots = normalizedLeadingSlots(layout.leadingSlots).map { it?.takeUnless(id::equals) })
        }
        is DropTarget.Dock -> {
            // The dock grows as apps are dragged in: when it has room to, one open place past its end takes an app, and a
            // full dock makes room for a drop onto an app rather than turning it away.
            val grows = layout.dock.size < MAX_DOCK_SLOTS
            if (target.index !in 0..(if (grows) layout.dock.size else layout.dock.lastIndex) || !canPlaceInDock(layout, id)) return layout
            val dock = layout.dock.toMutableList()
            val source = dock.indexOf(id)
            dock.indices.filter { it != source && dock[it] == id }.forEach { dock[it] = null }
            if (target.index == dock.size) dock.add(null)
            val occupied = dock[target.index] != null
            when {
                source == target.index -> Unit
                source >= 0 && !occupied -> { dock[source] = null; dock[target.index] = id }
                source >= 0 -> { dock.removeAt(source); dock.add(target.index, id) }
                !occupied -> dock[target.index] = id
                else -> {
                    val later = (target.index + 1 until dock.size).firstOrNull { dock[it] == null }
                    val earlier = (target.index - 1 downTo 0).firstOrNull { dock[it] == null }
                    when {
                        later != null -> { for (i in later downTo target.index + 1) dock[i] = dock[i - 1]; dock[target.index] = id }
                        earlier != null -> { for (i in earlier until target.index) dock[i] = dock[i + 1]; dock[target.index] = id }
                        grows -> { dock.add(null); for (i in dock.lastIndex downTo target.index + 1) dock[i] = dock[i - 1]; dock[target.index] = id }
                        else -> return layout
                    }
                }
            }
            layout.copy(slots = layout.slots.map { it?.takeUnless { app -> app == id } }.dropLastWhile { it == null }, dock = trimmedDock(dock),
                leadingSlots = normalizedLeadingSlots(layout.leadingSlots).map { it?.takeUnless(id::equals) })
        }
        else -> layout
    }
}

/**
 * [dock] without the empty places past its last app, but never fewer than [MIN_DOCK_SLOTS] places and never more than
 * [MAX_DOCK_SLOTS]: the dock is as big as what is in it, and four when it holds fewer. A dock already that small is left alone.
 */
fun trimmedDock(dock: List<String?>): List<String?> {
    if (dock.size <= MIN_DOCK_SLOTS) return dock
    val trimmed = dock.take(MAX_DOCK_SLOTS).dropLastWhile { it == null }
    return if (trimmed.size >= MIN_DOCK_SLOTS) trimmed else trimmed + List(MIN_DOCK_SLOTS - trimmed.size) { null }
}

fun placeWidget(layout: HomeLayout, placement: WidgetPlacement): HomeLayout {
    if (!placement.valid()) return replaceWidgetAtSameFootprint(layout, placement)
    if (placement.id == NEEDS_BINDING_WIDGET && layout.widgetRestore(placement.slot) == null) return layout
    val without = layout.widgetPlacements.filterNot { it.slot == placement.slot }
    if (without.any { overlaps(it, placement) }) return layout
    if (placement.coveredIndices().any { layout.slotAt(it) != null }) return layout
    return layout.copy(widgetPlacements = (without + placement).sortedBy { it.slot },
        widgetRestores = if (placement.id == NEEDS_BINDING_WIDGET) layout.widgetRestores
            else layout.widgetRestores.filterNot { it.slot == placement.slot })
}

/** Rebinds retained legacy overflow without making that footprint newly placeable. */
fun replaceWidgetAtSameFootprint(layout: HomeLayout, placement: WidgetPlacement): HomeLayout {
    val existing = layout.placement(placement.slot) ?: return layout
    val sameFootprint = placement.page == existing.page && placement.column == existing.column &&
        placement.row == existing.row && placement.spanX == existing.spanX && placement.spanY == existing.spanY
    val retainedSpecial = existing.page > 0 && existing.slot / 3 == existing.page && existing.slot % 3 == 2 &&
        existing.column == 0 && existing.row == GRID_ROWS && existing.spanX == GRID_COLUMNS && existing.spanY == 4
    if (!sameFootprint || !retainedSpecial || placement.id == EMPTY_WIDGET ||
        (placement.id == NEEDS_BINDING_WIDGET && layout.widgetRestore(placement.slot) == null)) return layout
    return layout.copy(widgetPlacements = layout.widgetPlacements.map { if (it.slot == placement.slot) placement else it },
        widgetRestores = if (placement.id == NEEDS_BINDING_WIDGET) layout.widgetRestores
            else layout.widgetRestores.filterNot { it.slot == placement.slot })
}

fun moveWidget(layout: HomeLayout, slot: Int, index: Int): HomeLayout {
    val old = layout.placement(slot) ?: return layout
    val page = homeCellPage(index)
    if (page !in -1..layout.pageCount) return layout
    val local = homeCellLocal(index)
    return placeWidget(layout, old.copy(page = page, column = local % GRID_COLUMNS,
        row = local / GRID_COLUMNS, spanY = old.spanY.coerceAtMost(GRID_ROWS), offsetX = 0f, offsetY = 0f))
}

/**
 * Places a widget at a position that need not sit on a cell ([column] and [row] are in cells, fractions allowed), on the
 * page it is already on. It keeps the cells nearest that position, and is drawn the rest of the way, so the cells it keeps
 * can't hold apps or other widgets: a spot those cells make impossible leaves the layout as it was.
 */
fun placeWidgetFreely(layout: HomeLayout, slot: Int, column: Float, row: Float): HomeLayout {
    val placement = freePlacement(layout.placement(slot) ?: return layout, column, row) ?: return layout
    return placeWidget(layout, placement)
}

/** The placement [old] would take at the fractional [column] and [row], or null for a position that isn't a number. */
fun freePlacement(old: WidgetPlacement, column: Float, row: Float): WidgetPlacement? {
    if (!column.isFinite() || !row.isFinite()) return null
    val maxColumn = GRID_COLUMNS - old.spanX
    val maxRow = GRID_ROWS - old.spanY
    val cell = column.roundToInt().coerceIn(0, maxColumn)
    val line = row.roundToInt().coerceIn(0, maxRow)
    // At the page's edge there is nothing to drift towards: the widget stays inside the grid.
    val x = (column - cell).coerceIn(if (cell == 0) 0f else -MAX_WIDGET_OFFSET, if (cell == maxColumn) 0f else MAX_WIDGET_OFFSET)
    val y = (row - line).coerceIn(if (line == 0) 0f else -MAX_WIDGET_OFFSET, if (line == maxRow) 0f else MAX_WIDGET_OFFSET)
    return old.copy(column = cell, row = line, offsetX = x, offsetY = y)
}

fun resizeWidget(layout: HomeLayout, slot: Int, spanX: Int, spanY: Int): HomeLayout {
    val old = layout.placement(slot) ?: return layout
    return placeWidget(layout, old.copy(spanX = spanX, spanY = spanY, offsetX = 0f, offsetY = 0f))
}

/** Remove only the shortcut/placement, never the installed app or widget binding. */
fun removePlacement(layout: HomeLayout, source: DropTarget): HomeLayout = when (source) {
    is DropTarget.Home -> if (layout.slotAt(source.index)?.let(::isFolderId) == true) layout
        else layout.withSlot(source.index, null)
    is DropTarget.Dock -> layout.copy(dock = layout.dock.mapIndexed { i, id -> if (i == source.index) null else id })
    is DropTarget.Widget -> layout.copy(widgetPlacements = layout.widgetPlacements.filterNot { it.slot == source.index },
        widgetRestores = layout.widgetRestores.filterNot { it.slot == source.index })
    else -> layout
}

/** Adds [id] to the first free cell (skipping [blocked] cells and rows pages don't show with [appRows] app rows). */
fun pinHomeApp(slots: List<String?>, id: String, pinned: Boolean, blocked: Set<Int> = emptySet(), appRows: Int = MAX_APP_ROWS): List<String?> {
    if (!pinned) return normalizeHomeSlots(slots.map { if (it == id) null else it })
    if (id in slots) return slots
    fun free(index: Int) = index !in blocked && homeCellShown(index, appRows)
    val gap = slots.indices.firstOrNull { slots[it] == null && free(it) }
    if (gap != null) return slots.toMutableList().apply { set(gap, id) }
    var index = slots.size
    while (!free(index)) index++
    return slots + List(index - slots.size) { null } + id
}

fun migrateSchema5Apps(slots: List<String?>): List<String?> {
    if (slots.isEmpty()) return emptyList()
    val result = MutableList(((slots.lastIndex / 16) + 1) * HOME_CELLS) { null as String? }
    slots.forEachIndexed { index, id -> result[index / 16 * HOME_CELLS + 8 + index % 16] = id }
    return normalizeHomeSlots(result)
}

fun migrateSchema5Widgets(widgets: List<Int>): List<WidgetPlacement> = buildList {
    widgets.forEachIndexed { slot, id ->
        if (id == EMPTY_WIDGET) return@forEachIndexed
        val page = slot / 3
        when (slot % 3) {
            0 -> add(WidgetPlacement(slot, id, page, 0, 0, 2, 2))
            1 -> add(WidgetPlacement(slot, id, page, 2, 0, 2, 2))
            2 -> if (page == 0) add(WidgetPlacement(slot, id, -1, 0, 0, 4, 6))
                else add(WidgetPlacement(slot, id, page, 0, GRID_ROWS, 4, 4))
        }
    }
}

/**
 * The first spot a [spanX]×[spanY] widget fits on Home without moving anything: page by page from [startPage],
 * top-left first, then on a new page after the last. Returns the page and the cell index of the widget's corner.
 */
fun firstFreeWidgetSpot(layout: HomeLayout, spanX: Int, spanY: Int, startPage: Int = 0, appRows: Int = MAX_APP_ROWS): Pair<Int, Int>? {
    val rows = visibleHomeRows(appRows)
    if (spanX !in 1..GRID_COLUMNS || spanY !in 1..rows) return null
    val widgetCells = layout.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
    val pages = (startPage.coerceIn(0, layout.pageCount) until layout.pageCount) + (0 until startPage.coerceIn(0, layout.pageCount)) + layout.pageCount
    for (page in pages) for (row in 0..rows - spanY) for (column in 0..GRID_COLUMNS - spanX) {
        val cells = buildList { repeat(spanY) { y -> repeat(spanX) { x -> add(homeCellIndex(page, (row + y) * GRID_COLUMNS + column + x)) } } }
        if (cells.none { it in widgetCells || layout.slotAt(it) != null }) return page to cells.first()
    }
    return null
}
