package com.mccal.folio

/**
 * The Controls page of Control Center as places on a grid, so a control can be moved to any free slot and resized from its corner
 * (McCal 25 Oct 2026, from the iOS editor; INT-20 in docs/standards/interaction.md). Pure rules only: nothing here draws or touches
 * Android, and nothing here moves a control by itself. A drop on a taken slot, or a size that does not fit, leaves the layout as it was.
 *
 * Today the page is an ordered list laid out by [controlCenterGrid]; [ccDefaultLayout] gives the same page as slots, so a saved list
 * becomes slots without anything moving.
 */
data class CcSlot(val id: String, val col: Int, val row: Int, val w: Int, val h: Int) {
    val right get() = col + w
    val bottom get() = row + h
}

/** The fixed modules; every other id is a [CcControl] name. */
internal const val CC_CONNECTIVITY = "connectivity"
internal const val CC_MEDIA = "media"
internal const val CC_BRIGHTNESS = "brightness"
internal const val CC_VOLUME = "volume"
internal const val CC_FOCUS = "focus"

/** The sizes (columns, rows) a control can take on a grid [cols] wide. The first is the size it starts with. */
internal fun ccAllowedSizes(id: String, cols: Int): List<Pair<Int, Int>> = when (id) {
    CC_CONNECTIVITY -> listOf(2 to 2)
    CC_MEDIA -> listOf(2 to 2, 2 to 1, cols to 2).distinct().filter { it.first <= cols }
    CC_BRIGHTNESS, CC_VOLUME -> listOf(1 to 2, 1 to 3)
    CC_FOCUS -> listOf(cols to 1, 2 to 1, 2 to 2).distinct().filter { it.first <= cols }
    else -> listOf(1 to 1, 2 to 1)
}

/** The places Control Center's Controls page has today, for [controls] in their saved order: the same page [controlCenterGrid] draws. */
internal fun ccDefaultLayout(cols: Int, controls: List<String>, focus: Boolean): List<CcSlot> {
    val base = mutableListOf<CcSlot>()
    val out = base
    out += CcSlot(CC_CONNECTIVITY, 0, 0, 2, 2)
    // Focus starts as the small 2 by 1 bar (as iOS draws it on the Duo) and the small controls take the cells beside it; it can still be
    // dragged out to the full width. The controls fill the first free cells, reading left to right and top to bottom.
    if (cols >= 6) {
        out += CcSlot(CC_MEDIA, 2, 0, cols - 4, 2)
        out += CcSlot(CC_BRIGHTNESS, cols - 2, 0, 1, 2)
        out += CcSlot(CC_VOLUME, cols - 1, 0, 1, 2)
        if (focus) out += CcSlot(CC_FOCUS, 0, 2, 2, 1)
    } else {
        out += CcSlot(CC_MEDIA, 2, 0, 2, 2)
        // The two sliders sit beside the Focus bar and the first small controls.
        out += CcSlot(CC_BRIGHTNESS, 2, 2, 1, 2)
        out += CcSlot(CC_VOLUME, 3, 2, 1, 2)
        if (focus) out += CcSlot(CC_FOCUS, 0, 2, 2, 1)
    }
    var placed: List<CcSlot> = base
    for (id in controls) placed = ccAdd(placed, id, cols, CC_MAX_SAVED_ROWS) ?: placed
    return placed
}

/** The cap on rows when placing or checking layouts: far more than any window shows, so a bad file cannot make a huge grid. */
private const val CC_MAX_SAVED_ROWS = 40

/** How many grid rows the slots use. */
internal fun ccRowsUsed(slots: List<CcSlot>): Int = slots.maxOfOrNull { it.bottom } ?: 0

/** Whether [slot] is inside a grid [cols] wide and [rows] tall and overlaps none of [others]. */
internal fun ccFits(others: List<CcSlot>, slot: CcSlot, cols: Int, rows: Int): Boolean =
    slot.col >= 0 && slot.row >= 0 && slot.right <= cols && slot.bottom <= rows && slot.w > 0 && slot.h > 0 &&
        others.none { it.id != slot.id && it.col < slot.right && slot.col < it.right && it.row < slot.bottom && slot.row < it.bottom }

/** Moves [id] so its top left corner is at ([col], [row]). A taken or out-of-bounds place leaves the layout as it was. */
internal fun ccMove(slots: List<CcSlot>, id: String, col: Int, row: Int, cols: Int, rows: Int): List<CcSlot> {
    val slot = slots.firstOrNull { it.id == id } ?: return slots
    val moved = slot.copy(col = col, row = row)
    return if (ccFits(slots, moved, cols, rows)) slots.map { if (it.id == id) moved else it } else slots
}

/**
 * What a drop of [id] with its top left corner at ([col], [row]) does: [ccMove] when the place is free; when another control stands there,
 * the two trade places if each fits where the other was (so a small control can be dropped straight onto another one, as on iOS, instead of
 * needing a free cell first). Anything else leaves the layout as it was.
 */
internal fun ccDrop(slots: List<CcSlot>, id: String, col: Int, row: Int, cols: Int, rows: Int): List<CcSlot> {
    val slot = slots.firstOrNull { it.id == id } ?: return slots
    val moved = ccMove(slots, id, col, row, cols, rows)
    if (moved != slots || (slot.col == col && slot.row == row)) return moved
    val dropped = slot.copy(col = col, row = row)
    if (dropped.col < 0 || dropped.row < 0 || dropped.right > cols || dropped.bottom > rows) return slots
    // The control under the dragged one's top left corner; where the drop covers several, the one the finger is over wins.
    val other = slots.firstOrNull { it.id != id && col >= it.col && col < it.right && row >= it.row && row < it.bottom } ?: return slots
    val rest = slots.filter { it.id != id && it.id != other.id }
    val first = dropped
    val second = other.copy(col = slot.col, row = slot.row)
    if (!ccFits(rest, first, cols, rows) || !ccFits(rest + first, second, cols, rows)) return slots
    return slots.map { if (it.id == id) first else if (it.id == other.id) second else it }
}

/** Gives [id] the size ([w], [h]) if the control can take it and it fits where it stands; otherwise nothing changes. */
internal fun ccResize(slots: List<CcSlot>, id: String, w: Int, h: Int, cols: Int, rows: Int): List<CcSlot> {
    val slot = slots.firstOrNull { it.id == id } ?: return slots
    if ((w to h) !in ccAllowedSizes(id, cols)) return slots
    return ccGrowInto(slots, slot.copy(w = w, h = h), cols, rows) ?: slots
}

/** The modules and sliders that stay where they are; only the small controls (toggles and shortcuts) step aside when another control grows over them. */
private val CC_STAYS_PUT = setOf(CC_CONNECTIVITY, CC_MEDIA, CC_BRIGHTNESS, CC_VOLUME, CC_FOCUS)

/**
 * [slots] with [resized] in place of the control it was. Where it covers other small controls they step aside to the first free places, reading
 * left to right and top to bottom, so a bigger control does not need an empty cell beside it (a full page had none, and nothing could be resized).
 * Null when it leaves the grid, would cover a module or slider, or the displaced controls have nowhere to go. The list keeps its order.
 */
internal fun ccGrowInto(slots: List<CcSlot>, resized: CcSlot, cols: Int, rows: Int): List<CcSlot>? {
    if (resized.col < 0 || resized.row < 0 || resized.right > cols || resized.bottom > rows || resized.w <= 0 || resized.h <= 0) return null
    val covered = slots.filter { it.id != resized.id && it.col < resized.right && resized.col < it.right && it.row < resized.bottom && resized.row < it.bottom }
    if (covered.any { it.id in CC_STAYS_PUT }) return null
    var placed = slots.filter { it.id != resized.id && covered.none { c -> c.id == it.id } } + resized
    for (moving in covered.sortedWith(compareBy({ it.row }, { it.col }))) {
        val spot = (0 until rows).firstNotNullOfOrNull { r ->
            (0..(cols - moving.w)).firstNotNullOfOrNull { c -> moving.copy(col = c, row = r).takeIf { ccFits(placed, it, cols, rows) } }
        } ?: return null
        placed += spot
    }
    return slots.map { old -> placed.first { it.id == old.id } }
}

/**
 * The size a corner drag lands on: of the sizes [id] can take that fit where it stands, the one nearest the dragged corner, given in
 * cells ([dragW], [dragH]) as a drag past the grid's own cells would reach. Null when it can take none of them there.
 */
internal fun ccSnapSize(slots: List<CcSlot>, id: String, dragW: Float, dragH: Float, cols: Int, rows: Int): Pair<Int, Int>? {
    val slot = slots.firstOrNull { it.id == id } ?: return null
    return ccAllowedSizes(id, cols)
        .filter { (w, h) -> ccGrowInto(slots, slot.copy(w = w, h = h), cols, rows) != null }
        .minByOrNull { (w, h) -> (w - dragW) * (w - dragW) + (h - dragH) * (h - dragH) }
}

/** Takes [id] off the page. */
internal fun ccRemove(slots: List<CcSlot>, id: String): List<CcSlot> = slots.filter { it.id != id }

/** Puts [id] in the first free place, reading left to right and top to bottom, at the size it starts with; null if there is none. */
internal fun ccAdd(slots: List<CcSlot>, id: String, cols: Int, rows: Int): List<CcSlot>? {
    if (slots.any { it.id == id }) return slots
    val (w, h) = ccAllowedSizes(id, cols).firstOrNull() ?: return null
    for (row in 0 until rows) for (col in 0..(cols - w)) {
        val slot = CcSlot(id, col, row, w, h)
        if (ccFits(slots, slot, cols, rows)) return slots + slot
    }
    return null
}

/**
 * The first row of each page of the editor, for a window that holds [rowsPerPage] rows (PNL-1: what does not fit goes to another page, never into
 * a scroll). [totalRows] is how many rows the page set must cover at least (the editor adds one empty row to drop into). A page ends at the
 * last row boundary within [rowsPerPage] that no control stands across, so a tall control is never cut in two; a page that begins in an empty
 * stretch jumps to the next control. With nowhere clean to break, the page is cut at [rowsPerPage] anyway.
 */
internal fun ccPageStarts(slots: List<CcSlot>, rowsPerPage: Int, totalRows: Int): List<Int> {
    val per = rowsPerPage.coerceAtLeast(1)
    val total = maxOf(totalRows, ccRowsUsed(slots), 1)
    val starts = mutableListOf(0)
    var start = 0
    while (true) {
        var end = start + per
        if (end >= total) break
        while (end > start + 1 && slots.any { it.row < end && it.bottom > end }) end--
        if (slots.any { it.row < end && it.bottom > end }) end = start + per
        start = slots.filter { it.row >= end }.minOfOrNull { it.row } ?: end
        if (start >= total) break
        starts += start
    }
    return starts
}

/** The controls standing on the page of rows [start] until [end], moved up so the page begins at row 0 (what the page's grid draws). */
internal fun ccPageSlots(slots: List<CcSlot>, start: Int, end: Int): List<CcSlot> =
    slots.filter { it.row >= start && it.row < end }.map { it.copy(row = it.row - start) }

/** The whole layout with the page of rows [start] until [end] replaced by [page] (given as [ccPageSlots] gives it, from row 0). */
internal fun ccMergePage(slots: List<CcSlot>, start: Int, end: Int, page: List<CcSlot>): List<CcSlot> {
    val order = slots.map { it.id }
    val rest = slots.filter { !(it.row >= start && it.row < end) }
    val merged = rest + page.map { it.copy(row = it.row + start) }
    return merged.sortedBy { order.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
}

/**
 * [id] taken onto the page of rows [from] until [to]: its first free place there, reading from the top, or from the bottom when [fromBottom]
 * (a control moved to the page before lands at its end). Null when the page has no room for it.
 */
internal fun ccCarry(slots: List<CcSlot>, id: String, cols: Int, from: Int, to: Int, fromBottom: Boolean): List<CcSlot>? {
    val slot = slots.firstOrNull { it.id == id } ?: return null
    val rowsToTry = if (fromBottom) (to - slot.h downTo from) else (from..(to - slot.h))
    for (row in rowsToTry) for (col in 0..(cols - slot.w)) {
        val moved = slot.copy(col = col, row = row)
        if (ccFits(slots, moved, cols, to)) return slots.map { if (it.id == id) moved else it }
    }
    return null
}

/** The free single cells of a grid [cols] by [rows], which the editor draws as faint circles. */
internal fun ccFreeCells(slots: List<CcSlot>, cols: Int, rows: Int): List<Pair<Int, Int>> =
    (0 until rows).flatMap { r -> (0 until cols).map { c -> c to r } }
        .filter { (c, r) -> slots.none { c >= it.col && c < it.right && r >= it.row && r < it.bottom } }

/** A layout the person made in the editor, for a grid [cols] wide. A grid of another width cannot use it (see [ccLayoutFor]). */
data class CcSavedLayout(val cols: Int, val slots: List<CcSlot>, val focusOn: Boolean = false)

private fun ccKnownId(id: String): Boolean =
    id in setOf(CC_CONNECTIVITY, CC_MEDIA, CC_BRIGHTNESS, CC_VOLUME, CC_FOCUS) || CcControl.entries.any { it.name == id }

internal fun ccLayoutToJson(layout: CcSavedLayout): org.json.JSONObject = org.json.JSONObject()
    .put("cols", layout.cols)
    .put("focus", layout.focusOn)
    .put("slots", org.json.JSONArray(layout.slots.map { org.json.JSONObject().put("id", it.id).put("col", it.col).put("row", it.row).put("w", it.w).put("h", it.h) }))

/**
 * Reads a saved layout, or null when anything in it is wrong: a missing or unknown id, a repeated id, a size the control cannot take,
 * a place outside the grid, or two controls on one place. A bad file never half-applies; the page falls back to its default.
 */
internal fun ccLayoutFromJson(json: org.json.JSONObject?): CcSavedLayout? {
    if (json == null) return null
    val cols = json.optInt("cols", 0)
    val array = json.optJSONArray("slots") ?: return null
    if (cols !in 1..16) return null
    val slots = (0 until array.length()).map { i ->
        val o = array.optJSONObject(i) ?: return null
        val id = o.optString("id")
        if (!o.has("col") || !o.has("row") || !o.has("w") || !o.has("h")) return null
        CcSlot(id, o.optInt("col", -1), o.optInt("row", -1), o.optInt("w", 0), o.optInt("h", 0))
    }
    val ok = slots.map { it.id }.toSet().size == slots.size &&
        slots.all { ccKnownId(it.id) && (it.w to it.h) in ccAllowedSizes(it.id, cols) && ccFits(slots, it, cols, CC_MAX_SAVED_ROWS) }
    return if (ok) CcSavedLayout(cols, slots, json.optBoolean("focus", false)) else null
}

/**
 * The places to draw on a grid [cols] wide: the saved layout when it was made for this width, otherwise the default page. Controls
 * that left [controls] (turned off in Settings) are dropped from a saved layout, and ones newly turned on take the first free place,
 * so the list in Settings and the editor never disagree.
 */
internal fun ccLayoutFor(saved: CcSavedLayout?, cols: Int, controls: List<String>, focus: Boolean): List<CcSlot> {
    if (saved == null || saved.cols != cols) return ccDefaultLayout(cols, controls, focus)
    var slots = saved.slots.filter { s -> s.id in controls || !CcControl.entries.any { it.name == s.id } }
    if (!focus) slots = slots.filter { it.id != CC_FOCUS }
    for (id in controls) if (slots.none { it.id == id }) slots = ccAdd(slots, id, cols, CC_MAX_SAVED_ROWS) ?: slots
    // Focus switched on in Settings after the layout was saved with it off takes the first free place; once the layout has seen it on, a
    // missing Focus was taken off in the editor and stays off.
    if (focus && !saved.focusOn && slots.none { it.id == CC_FOCUS }) slots = ccAdd(slots, CC_FOCUS, cols, CC_MAX_SAVED_ROWS) ?: slots
    // A module (connectivity, Now Playing, brightness, volume, Focus) missing from a saved layout was taken off in the editor, so it stays off
    // until it is added back from the gallery.
    return slots
}

/** The modules a saved layout has taken off, which the editor's gallery offers to add back ([focus] false means Focus is off in Settings). */
internal fun ccMissingModules(slots: List<CcSlot>, focus: Boolean): List<String> =
    listOf(CC_CONNECTIVITY, CC_MEDIA, CC_BRIGHTNESS, CC_VOLUME, CC_FOCUS)
        .filter { id -> slots.none { it.id == id } && (id != CC_FOCUS || focus) }
