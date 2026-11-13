package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Where a drag of ([dxPx], [dyPx]) leaves a control whose top left corner was at ([col], [row]), on a grid whose cells (with their gap)
 * are [stepPx] apart: the nearest whole cell, so a drag that has not gone halfway to the next cell lands back where it started.
 */
internal fun ccDragTarget(col: Int, row: Int, dxPx: Float, dyPx: Float, stepPx: Float): Pair<Int, Int> =
    if (stepPx <= 0f) col to row else (col + (dxPx / stepPx).roundToInt()) to (row + (dyPx / stepPx).roundToInt())

/** The size, in cells, a corner dragged ([dxPx], [dyPx]) from a control [w] by [h] reaches; [ccSnapSize] picks the size it lands on. */
internal fun ccDragSize(w: Int, h: Int, dxPx: Float, dyPx: Float, stepPx: Float): Pair<Float, Float> =
    if (stepPx <= 0f) w.toFloat() to h.toFloat() else (w + dxPx / stepPx) to (h + dyPx / stepPx)

/** The next size [id] can take that fits where it stands, after its current one; null when it can take no other. For TalkBack, which cannot drag a corner. */
internal fun ccNextSize(slots: List<CcSlot>, id: String, cols: Int, rows: Int): Pair<Int, Int>? {
    val slot = slots.firstOrNull { it.id == id } ?: return null
    val sizes = ccAllowedSizes(id, cols)
    val start = sizes.indexOf(slot.w to slot.h)
    return (1 until sizes.size + 1).map { sizes[(start + it).mod(sizes.size)] }
        .firstOrNull { it != (slot.w to slot.h) && ccFits(slots, slot.copy(w = it.first, h = it.second), cols, rows) }
}

/**
 * Control Center's Controls page as places on a grid (FeatureGate.CC_EDITOR): each of [slots] is drawn at its place and size by [content].
 * While [editing], a press and hold lifts a control to drag it onto a free place, a corner handle resizes it, and a minus takes a control
 * that can be taken off ([removable]). Every change is handed to [onChange] as a whole new list that [ccMove] and [ccResize] have already
 * checked, so a drop on a taken place or a size that does not fit changes nothing. Nothing here draws a control itself.
 *
 * TalkBack gets the same editing as actions (move, change size, remove), since a drag cannot be made without sight.
 */
@Composable
internal fun CcSlotGrid(
    slots: List<CcSlot>, cols: Int, rows: Int, cell: Dp, gap: Dp, editing: Boolean, raised: String?,
    removable: (String) -> Boolean, labelOf: @Composable (String) -> String,
    onChange: (List<CcSlot>) -> Unit, onRemove: (String) -> Unit,
    content: @Composable BoxScope.(CcSlot) -> Unit,
) {
    val density = LocalDensity.current
    // In a right-to-left layout the grid is mirrored, so a drag to the right goes toward lower columns.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val flip = if (rtl) -1f else 1f
    val step = cell + gap
    val stepPx = with(density) { step.toPx() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var resizing by remember { mutableStateOf<String?>(null) }
    var resize by remember { mutableStateOf(Offset.Zero) }
    // The gesture handlers below live as long as a control keeps its id, so they read the layout as it is when a finger lifts, not as it was drawn.
    val latestSlots by rememberUpdatedState(slots)
    val latestChange by rememberUpdatedState(onChange)
    val latestRows by rememberUpdatedState(rows)
    val latestStep by rememberUpdatedState(stepPx)
    fun span(n: Int): Dp = cell * n + gap * (n - 1)
    val left = stringResource(R.string.cc_move_left)
    val right = stringResource(R.string.cc_move_right)
    val up = stringResource(R.string.cc_move_up)
    val down = stringResource(R.string.cc_move_down)
    val bigger = stringResource(R.string.cc_change_size)
    val removeLabel = stringResource(R.string.cc_remove)

    Box(Modifier.size(span(cols), if (rows > 0) span(rows) else 0.dp)) {
        // The free places, as faint circles, so it is plain where a control can be dropped (as iOS).
        if (editing) ccFreeCells(slots, cols, rows).forEach { (c, r) ->
            Box(Modifier.offset(step * c, step * r).size(cell).padding(cell * .08f).clip(CircleShape).background(Color.White.copy(alpha = .07f)))
        }
        slots.forEach { slot ->
            val id = slot.id
            val lifted = dragging == id
            val live = resizing == id
            // While the corner is dragged the control shows the size it will land on, so nothing is a surprise at the drop.
            val landing = if (live) ccSnapSize(slots, id, ccDragSize(slot.w, slot.h, resize.x * flip, resize.y, stepPx).first, ccDragSize(slot.w, slot.h, resize.x * flip, resize.y, stepPx).second, cols, rows) else null
            val shown = landing?.let { slot.copy(w = it.first, h = it.second) } ?: slot
            val name = labelOf(id)
            Box(
                Modifier.offset(step * slot.col, step * slot.row).size(span(shown.w), span(shown.h))
                    .zIndex(if (lifted || live || raised == id) 1f else 0f)
                    .graphicsLayer {
                        if (lifted) { translationX = drag.x; translationY = drag.y; scaleX = 1.04f; scaleY = 1.04f }
                    }
                    .then(if (!editing) Modifier else Modifier
                        .semantics {
                            customActions = buildList {
                                fun move(label: String, dc: Int, dr: Int) = add(CustomAccessibilityAction(label) {
                                    val moved = ccMove(slots, id, slot.col + dc, slot.row + dr, cols, rows)
                                    if (moved != slots) onChange(moved)
                                    moved != slots
                                })
                                move("$left, $name", -1, 0); move("$right, $name", 1, 0); move("$up, $name", 0, -1); move("$down, $name", 0, 1)
                                if (ccAllowedSizes(id, cols).size > 1) add(CustomAccessibilityAction("$bigger, $name") {
                                    val next = ccNextSize(slots, id, cols, rows)
                                    if (next != null) onChange(ccResize(slots, id, next.first, next.second, cols, rows))
                                    next != null
                                })
                                if (removable(id)) add(CustomAccessibilityAction("$removeLabel, $name") { onRemove(id); true })
                            }
                        }),
            ) {
                content(shown)
                if (editing) {
                    // Above the control, so a held press reaches the drag and never the tile's own press (a tile does nothing while editing).
                    Box(Modifier.matchParentSize()
                        .pointerInput(id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragging = id; drag = Offset.Zero },
                                onDrag = { change, by -> change.consume(); drag += by },
                                onDragEnd = {
                                    val now = latestSlots.firstOrNull { it.id == id }
                                    if (now != null) {
                                        val (c, r) = ccDragTarget(now.col, now.row, drag.x * flip, drag.y, latestStep)
                                        val moved = ccMove(latestSlots, id, c, r, cols, latestRows)
                                        if (moved != latestSlots) latestChange(moved)
                                    }
                                    dragging = null; drag = Offset.Zero
                                },
                                onDragCancel = { dragging = null; drag = Offset.Zero },
                            )
                        })
                    // A still outline: the places are being arranged, so nothing else about the control moves or glows.
                    if (lifted || live) Box(Modifier.matchParentSize().border(1.5.dp, Color.White.copy(alpha = .9f), RoundedCornerShape(26.dp)))
                    if (removable(id)) Box(Modifier.align(Alignment.TopStart).offset((-4).dp, (-4).dp).size(44.dp)
                        .pointerInput(id) { detectTapGestures { onRemove(id) } }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White.copy(alpha = .92f)), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(10.dp, 2.dp).background(Color.Black.copy(alpha = .75f)))
                        }
                    }
                    if (ccAllowedSizes(id, cols).size > 1) Box(Modifier.align(Alignment.BottomEnd).offset(10.dp, 10.dp).size(FolioTouch.MIN.dp)
                        .pointerInput(id) {
                            detectDragGestures(
                                onDragStart = { resizing = id; resize = Offset.Zero },
                                onDrag = { change, by -> change.consume(); resize += by },
                                onDragEnd = {
                                    val now = latestSlots.firstOrNull { it.id == id }
                                    if (now != null) {
                                        val (dw, dh) = ccDragSize(now.w, now.h, resize.x * flip, resize.y, latestStep)
                                        val target = ccSnapSize(latestSlots, id, dw, dh, cols, latestRows)
                                        if (target != null && target != (now.w to now.h)) latestChange(ccResize(latestSlots, id, target.first, target.second, cols, latestRows))
                                    }
                                    resizing = null; resize = Offset.Zero
                                },
                                onDragCancel = { resizing = null; resize = Offset.Zero },
                            )
                        }, contentAlignment = Alignment.Center) {
                        // A curved handle that follows the control's own corner, as iOS draws it, instead of a dot on the corner.
                        Canvas(Modifier.size(30.dp)) {
                            val stroke = 4.dp.toPx()
                            val r = 26.dp.toPx()
                            val color = Color.White.copy(alpha = if (live) 1f else .85f)
                            if (rtl) drawArc(color, startAngle = 90f, sweepAngle = 90f, useCenter = false, topLeft = Offset(stroke, size.height - 2 * r - stroke), size = Size(2 * r, 2 * r), style = Stroke(stroke, cap = StrokeCap.Round))
                            else drawArc(color, startAngle = 0f, sweepAngle = 90f, useCenter = false,
                                topLeft = Offset(size.width - 2 * r - stroke, size.height - 2 * r - stroke), size = Size(2 * r, 2 * r), style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                    }
                }
            }
        }
    }
}
