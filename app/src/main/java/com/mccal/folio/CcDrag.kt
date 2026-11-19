package com.mccal.folio

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The pure rules behind dragging in Control Center's editor (CcSlotGrid): where a held control would land, how fast the page scrolls
 * while it is held near an edge, and the Undo history of an edit session. Nothing here draws or touches Android.
 */

/** What letting go would do: [slots] is the whole layout after the drop and [landing] the place the dragged control takes (null: it was not found). */
internal data class CcDropPreview(val slots: List<CcSlot>, val landing: CcSlot?)

/** How far from the finger, in cells, a place may be and still catch a drop. Beyond it the control goes home, so a drag off the grid is a cancel. */
internal const val CC_DROP_REACH = 1.5f

/**
 * Where the control [id] lands when its top left corner is held at the fractional cell ([col], [row]) (its own cell plus how far the drag has gone, in
 * cells). Of the places around the finger the drop rules ([ccDrop]) accept, the nearest wins, so the control snaps to a real cell and the cell it
 * covers (a free one, or a neighbor that then trades places) is always one it can take. The control's own place is always among them; with nothing
 * within [reach] the control stays where it was, which is how a drag that ends over a tile that cannot move, or off the grid, cancels.
 */
internal fun ccPreviewDrop(slots: List<CcSlot>, id: String, col: Float, row: Float, cols: Int, rows: Int, reach: Float = CC_DROP_REACH): CcDropPreview {
    val slot = slots.firstOrNull { it.id == id } ?: return CcDropPreview(slots, null)
    val home = CcDropPreview(slots, slot)
    if (cols <= 0 || rows <= 0) return home
    val maxCol = cols - slot.w
    val maxRow = rows - slot.h
    if (maxCol < 0 || maxRow < 0) return home
    val c0 = floor(col).toInt() - 1
    val c1 = ceil(col).toInt() + 1
    val r0 = floor(row).toInt() - 1
    val r1 = ceil(row).toInt() + 1
    val limit = reach * reach
    var best: CcDropPreview = home
    // The control's own place counts only while the finger is near it; every other place has to be one the drop rules accept.
    var bestDistance = ((slot.col - col) * (slot.col - col) + (slot.row - row) * (slot.row - row)).let { if (it <= limit) it else Float.MAX_VALUE }
    for (c in max(0, c0)..min(maxCol, c1)) for (r in max(0, r0)..min(maxRow, r1)) {
        if (c == slot.col && r == slot.row) continue
        val distance = (c - col) * (c - col) + (r - row) * (r - row)
        if (distance > limit || distance >= bestDistance) continue
        val dropped = ccDrop(slots, id, c, r, cols, rows)
        if (dropped == slots) continue
        best = CcDropPreview(dropped, dropped.first { it.id == id })
        bestDistance = distance
    }
    return best
}

/**
 * Which edge of a page a held control is at, for carrying it to the next page: 1 within [edge] of the top (the next page comes from above) or
 * higher, -1 within [edge] of the bottom or lower (the page before), 0 anywhere else. The finger is at [fingerY] and the page shows [top] to
 * [bottom] (window pixels). A page too short to have a middle has no edges.
 */
internal fun ccEdgeZone(fingerY: Float, top: Float, bottom: Float, edge: Float): Int {
    if (bottom <= top || edge <= 0f) return 0
    val zone = min(edge, (bottom - top) / 2f)
    return if (fingerY < top + zone) 1 else if (fingerY > bottom - zone) -1 else 0
}

/** The page the Controls are on as one thing to put back: the chosen controls and the saved places (null: the default places). */
internal data class CcEditState(val controls: List<String>, val layout: CcSavedLayout?)

/** How many steps of Undo an edit session keeps. */
internal const val CC_UNDO_DEPTH = 30

/** The steps an edit session can take back, oldest first. Nothing in it outlives the session. */
internal data class CcEditHistory(val steps: List<CcEditState> = emptyList()) {
    val canUndo get() = steps.isNotEmpty()

    /** The history with [before] (the page as it was just before a change) added; a step that repeats the last one, or changes nothing, is not kept. */
    fun push(before: CcEditState, after: CcEditState? = null): CcEditHistory =
        if (steps.lastOrNull() == before || before == after) this else CcEditHistory((steps + before).takeLast(CC_UNDO_DEPTH))

    /** The state to put back and the history without it; null when there is nothing to undo. */
    fun pop(): Pair<CcEditState, CcEditHistory>? = steps.lastOrNull()?.let { it to CcEditHistory(steps.dropLast(1)) }
}
