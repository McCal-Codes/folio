package com.mccal.folio

/** Where an island notice goes and how big it may be, in dp. See docs/plan-0-6-9-sweep-and-polish.md, M4b. */
internal data class NoticePlacement(val width: Float, val x0: Float, val x1: Float, val maxHeight: Float)

private const val NOTICE_MIN_WIDTH = 220f
private const val NOTICE_MAX_WIDTH = 340f
private const val NOTICE_MARGIN = 8f

/**
 * How wide a notice wants to be for its text: the icon and padding, about 7 dp a character, and room for a button.
 * A guess is enough, because the card only has to be neither cramped nor mostly empty; the text wraps inside it.
 */
internal fun noticeWantWidth(textLength: Int, hasAction: Boolean): Float =
    (80f + textLength * 7.2f + if (hasAction) 72f else 0f).coerceIn(NOTICE_MIN_WIDTH, NOTICE_MAX_WIDTH)

/**
 * The card for a notice grown from the camera at [centerX] with its top at [top]. It fits [want], never wider than
 * the room on the camera's side of a bent hinge (inside an 8 dp margin), and never taller than the room above a
 * laptop-style fold. [hingeSpans] are the hinge's extents along its axis (empty when there is no active hinge);
 * [hingeVertical] says which axis. A card near a corner (the inner screen's camera) shifts inward: the range
 * [x0]..[x1] is where its left edge may sit, and the camera stays inside the card.
 */
internal fun noticePlacement(
    windowWidth: Float, windowHeight: Float, centerX: Float, top: Float, want: Float,
    hingeSpans: List<ClosedFloatingPointRange<Float>> = emptyList(), hingeVertical: Boolean = true,
): NoticePlacement {
    var x0 = NOTICE_MARGIN
    var x1 = windowWidth - NOTICE_MARGIN
    var bottom = windowHeight - NOTICE_MARGIN
    if (hingeSpans.isNotEmpty()) {
        val sorted = hingeSpans.sortedBy { it.start }
        val along = if (hingeVertical) windowWidth else windowHeight
        val point = if (hingeVertical) centerX else top
        // The panels between the hinges, as ranges along the hinge's axis.
        val panels = mutableListOf<ClosedFloatingPointRange<Float>>()
        var start = 0f
        for (h in sorted) { if (h.start > start) panels += start..h.start; start = maxOf(start, h.endInclusive) }
        if (along > start) panels += start..along
        val panel = panels.firstOrNull { point in it } ?: panels.minByOrNull { minOf(kotlin.math.abs(point - it.start), kotlin.math.abs(point - it.endInclusive)) }
        if (panel != null) {
            if (hingeVertical) { x0 = maxOf(x0, panel.start + NOTICE_MARGIN); x1 = minOf(x1, panel.endInclusive - NOTICE_MARGIN) }
            else bottom = minOf(bottom, panel.endInclusive - NOTICE_MARGIN)
        }
    }
    val room = (x1 - x0).coerceAtLeast(0f)
    val width = want.coerceIn(minOf(NOTICE_MIN_WIDTH, room), minOf(NOTICE_MAX_WIDTH, room))
    return NoticePlacement(width, x0, x1, (bottom - top).coerceAtLeast(96f))
}

/** Where the card's left edge sits: centered on the camera, kept inside [placement]'s range. */
internal fun noticeLeft(placement: NoticePlacement, centerX: Float, width: Float): Float =
    (centerX - width / 2f).coerceIn(placement.x0, maxOf(placement.x0, placement.x1 - width))
