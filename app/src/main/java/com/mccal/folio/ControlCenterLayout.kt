package com.mccal.folio

/**
 * The Controls page of Control Center as places on a grid, so a control can be moved to any free slot and resized from its corner
 * (McCal 25 Oct 2026, from the iOS editor; INT-20 in docs/standards/interaction.md). Pure rules only: nothing here draws or touches
 * Android, and nothing here moves a control by itself. A drop on a taken slot, or a size that does not fit, leaves the layout as it was.
 *
 * Today the page is an ordered list laid out by [controlCenterGrid]; [ccDefaultLayout] gives the same page as slots, so a saved list
 * becomes slots without anything moving.
 */
internal data class CcSlot(val id: String, val col: Int, val row: Int, val w: Int, val h: Int) {
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
    val out = mutableListOf<CcSlot>()
    out += CcSlot(CC_CONNECTIVITY, 0, 0, 2, 2)
    if (cols >= 6) {
        out += CcSlot(CC_MEDIA, 2, 0, cols - 4, 2)
        out += CcSlot(CC_BRIGHTNESS, cols - 2, 0, 1, 2)
        out += CcSlot(CC_VOLUME, cols - 1, 0, 1, 2)
        var row = 2
        if (focus) { out += CcSlot(CC_FOCUS, 0, row, cols, 1); row++ }
        controls.forEachIndexed { i, id -> out += CcSlot(id, i % cols, row + i / cols, 1, 1) }
    } else {
        out += CcSlot(CC_MEDIA, 2, 0, 2, 2)
        var row = 2
        if (focus) { out += CcSlot(CC_FOCUS, 0, row, 4, 1); row++ }
        // The first four small controls sit as a 2 by 2 beside the two sliders.
        controls.take(4).forEachIndexed { i, id -> out += CcSlot(id, i % 2, row + i / 2, 1, 1) }
        out += CcSlot(CC_BRIGHTNESS, 2, row, 1, 2)
        out += CcSlot(CC_VOLUME, 3, row, 1, 2)
        row += 2
        controls.drop(4).forEachIndexed { i, id -> out += CcSlot(id, i % 4, row + i / 4, 1, 1) }
    }
    return out
}

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

/** Gives [id] the size ([w], [h]) if the control can take it and it fits where it stands; otherwise nothing changes. */
internal fun ccResize(slots: List<CcSlot>, id: String, w: Int, h: Int, cols: Int, rows: Int): List<CcSlot> {
    val slot = slots.firstOrNull { it.id == id } ?: return slots
    if ((w to h) !in ccAllowedSizes(id, cols)) return slots
    val resized = slot.copy(w = w, h = h)
    return if (ccFits(slots, resized, cols, rows)) slots.map { if (it.id == id) resized else it } else slots
}

/**
 * The size a corner drag lands on: of the sizes [id] can take that fit where it stands, the one nearest the dragged corner, given in
 * cells ([dragW], [dragH]) as a drag past the grid's own cells would reach. Null when it can take none of them there.
 */
internal fun ccSnapSize(slots: List<CcSlot>, id: String, dragW: Float, dragH: Float, cols: Int, rows: Int): Pair<Int, Int>? {
    val slot = slots.firstOrNull { it.id == id } ?: return null
    return ccAllowedSizes(id, cols)
        .filter { (w, h) -> ccFits(slots, slot.copy(w = w, h = h), cols, rows) }
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

/** The free single cells of a grid [cols] by [rows], which the editor draws as faint circles. */
internal fun ccFreeCells(slots: List<CcSlot>, cols: Int, rows: Int): List<Pair<Int, Int>> =
    (0 until rows).flatMap { r -> (0 until cols).map { c -> c to r } }
        .filter { (c, r) -> slots.none { c >= it.col && c < it.right && r >= it.row && r < it.bottom } }
