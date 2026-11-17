package com.mccal.folio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Where a drag of ([dxPx], [dyPx]) leaves a control whose top left corner was at ([col], [row]), on a grid whose cells (with their gap)
 * are [stepPx] apart: the nearest whole cell, so a drag that has not gone halfway to the next cell lands back where it started.
 */
internal fun ccDragTarget(col: Int, row: Int, dxPx: Float, dyPx: Float, stepPx: Float): Pair<Int, Int> =
    if (stepPx <= 0f) col to row else (col + (dxPx / stepPx).roundToInt()) to (row + (dyPx / stepPx).roundToInt())

/**
 * A press held still for the long-press time and then dragged: the move gesture. It is [detectDragGesturesAfterLongPress] except that a touch an
 * ancestor has already consumed does not cancel it. A page that scrolls (a short window) took the press first, and the stock detector then gave
 * up, so on that page a held control never lifted (14 Nov 2026, emulator). A move past the touch slop before the time is up still cancels, so the
 * page scrolls under a swipe. [onDragStart] gets where in the control the finger is.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectHeldDrag(
    onDragStart: (Offset) -> Unit, onDrag: (Offset) -> Unit, onDragEnd: () -> Unit, onDragCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val timedOut = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull
                if (change.changedToUpIgnoreConsumed() || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull
            }
        } == null
        if (!timedOut) return@awaitEachGesture
        onDragStart(down.position)
        // Own loop instead of drag(): on a page that scrolls an ancestor has already consumed the moves, and drag() then reports none of them.
        var finished = false
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            // A cancelled touch (the system took it back) arrives as a release that is already consumed: that one goes home, it does not drop.
            if (change.changedToUpIgnoreConsumed()) { finished = !change.isConsumed; change.consume(); break }
            onDrag(change.positionChangeIgnoreConsumed())
            change.consume()
        }
        if (finished) onDragEnd() else onDragCancel()
    }
}

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

/** How close to the top or bottom of the page, in dp, a held control starts scrolling it, and the most it scrolls a frame. */
private const val CC_SCROLL_EDGE_DP = 56
private const val CC_SCROLL_STEP_DP = 14

/** How much a lifted control grows (5%); the drag adds it back, since the pointer measures inside the grown control. */
private const val CC_LIFT_GROW = .05f

/** A control tile's corner, the same one `CcTile` draws, so the outline and the lift match the tile exactly. */
private val CcTileCorner = RoundedCornerShape(26.dp)

/**
 * Control Center's Controls page as places on a grid (FeatureGate.CC_EDITOR): each of [slots] is drawn at its place and size by [content].
 * While [editing], a press and hold lifts a control (it grows a little, with a shadow and a haptic tap) and it follows the finger. The place it
 * would take is outlined, and a neighbor that would trade places slides into the one it leaves, so letting go is never a surprise; a drop that
 * cannot work, or one off the grid, returns the control home. Held near the top or bottom of a page that scrolls, the page scrolls. A corner
 * handle resizes (the controls it would push aside move away as it is dragged), and a minus takes a control that can be taken off ([removable]).
 * Every change is handed to [onChange] as a whole new list that [ccDrop] and [ccResize] have already checked. The corner handle is a small
 * 32 dp target so it never covers the middle of a one-cell tile, where a press and hold has to reach the drag. Nothing here draws a control itself.
 * Not editing, a press and hold on a place no control holds starts editing ([onEnterEdit]), as on iOS.
 *
 * Under Reduce Motion nothing moves but the control being dragged: neighbors, the settle and the lift all snap.
 *
 * TalkBack gets the same editing as actions (move, change size, remove) and each control says where it is, since a drag cannot be made without sight.
 * A page that scrolls hands over its [scroll] state and [viewport] (the page's own box) for the scroll at the edges.
 */
@Composable
internal fun CcSlotGrid(
    slots: List<CcSlot>, cols: Int, rows: Int, cell: Dp, gap: Dp, editing: Boolean, raised: String?,
    removable: (String) -> Boolean, labelOf: @Composable (String) -> String,
    onChange: (List<CcSlot>) -> Unit, onRemove: (String) -> Unit,
    onEnterEdit: () -> Unit = {}, scroll: ScrollState? = null, viewport: () -> LayoutCoordinates? = { null },
    content: @Composable BoxScope.(CcSlot) -> Unit,
) {
    val density = LocalDensity.current
    // In a right-to-left layout the grid is mirrored, so a drag to the right goes toward lower columns.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val flip = if (rtl) -1f else 1f
    val step = cell + gap
    val stepPx = with(density) { step.toPx() }
    val reduceMotion = LocalReduceMotion.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val resources = LocalContext.current.resources
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf<String?>(null) }
    // How far the finger has gone since the lift, in pixels, and where in the control it holds it.
    var drag by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var resizing by remember { mutableStateOf<String?>(null) }
    var resize by remember { mutableStateOf(Offset.Zero) }
    // A change just handed to onChange, shown until the saved layout catches up, so nothing flashes back to the old places for a frame.
    var pending by remember { mutableStateOf<List<CcSlot>?>(null) }
    // The control that is settling into its new place, and how far it still is from it (the layout moves it at once, this carries it there).
    var landing by remember { mutableStateOf<String?>(null) }
    val settle = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val base = pending ?: slots
    // The gesture handlers below live as long as a control keeps its id, so they read the layout as it is when a finger lifts, not as it was drawn.
    val latestBase by rememberUpdatedState(base)
    val latestChange by rememberUpdatedState(onChange)
    val latestRemove by rememberUpdatedState(onRemove)
    val latestEnter by rememberUpdatedState(onEnterEdit)
    val latestRows by rememberUpdatedState(rows)
    val latestCols by rememberUpdatedState(cols)
    val latestStep by rememberUpdatedState(stepPx)
    val latestReduce by rememberUpdatedState(reduceMotion)
    val latestScroll by rememberUpdatedState(scroll)
    val latestViewport by rememberUpdatedState(viewport)
    fun span(n: Int): Dp = cell * n + gap * (n - 1)

    // What letting go would do, from where the finger has taken the lifted control (read only when it changes, not on every pixel).
    val dropPreview by remember {
        derivedStateOf {
            val id = dragging
            val now = if (id == null) null else latestBase.firstOrNull { it.id == id }
            if (id == null || now == null || latestStep <= 0f) null
            else ccPreviewDrop(latestBase, id, now.col + drag.x * flip / latestStep, now.row + drag.y / latestStep, latestCols, latestRows)
        }
    }
    // The layout while a corner is dragged: the control at the size it will land on and whatever it pushes aside already moved.
    val resizePreview by remember {
        derivedStateOf {
            val id = resizing
            val now = if (id == null) null else latestBase.firstOrNull { it.id == id }
            if (id == null || now == null) null else {
                val (dw, dh) = ccDragSize(now.w, now.h, resize.x * flip, resize.y, latestStep)
                val target = ccSnapSize(latestBase, id, dw, dh, latestCols, latestRows)
                if (target == null) latestBase else ccResize(latestBase, id, target.first, target.second, latestCols, latestRows)
            }
        }
    }
    val layout = dropPreview?.slots ?: resizePreview ?: base

    fun hold(next: List<CcSlot>) {
        pending = next
        scope.launch { delay(700); if (pending == next) pending = null }
    }
    LaunchedEffect(slots) { if (pending == slots) pending = null }
    // Leaving edit mode mid-gesture (Done, Back) drops whatever is held; the handlers go with the editing layer and would never say so.
    LaunchedEffect(editing) { if (!editing) { dragging = null; drag = Offset.Zero; resizing = null; resize = Offset.Zero } }

    // A tick each time the place the control would take changes, as the Home screen does when an icon crosses to the next cell.
    val landingKey: Any? = dropPreview?.landing?.let { it.col to it.row } ?: resizePreview?.firstOrNull { it.id == resizing }?.let { it.w to it.h }
    var lastKey by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(landingKey, dragging, resizing) {
        if (dragging == null && resizing == null) lastKey = null
        else { if (lastKey != null && lastKey != landingKey) haptic.perform(FolioHaptic.Step); lastKey = landingKey }
    }

    // Held near the top or bottom of a page that scrolls, the page scrolls and the control stays under the finger.
    LaunchedEffect(dragging != null) {
        if (dragging == null) return@LaunchedEffect
        val edge = with(density) { CC_SCROLL_EDGE_DP.dp.toPx() }
        val most = with(density) { CC_SCROLL_STEP_DP.dp.toPx() }
        while (true) {
            withFrameNanos { }
            val id = dragging ?: break
            val state = latestScroll ?: continue
            val page = latestViewport() ?: continue
            val grid = gridCoords ?: continue
            if (!page.isAttached || !grid.isAttached) continue
            val origin = latestBase.firstOrNull { it.id == id } ?: break
            val top = page.positionInWindow().y
            val finger = grid.positionInWindow().y + origin.row * latestStep + drag.y + grab.y
            val by = ccAutoScrollStep(finger, top, top + page.size.height, edge, most)
            if (by != 0f) { val used = state.scrollBy(by); if (used != 0f) drag += Offset(0f, used) }
        }
    }

    val left = stringResource(R.string.cc_move_left)
    val right = stringResource(R.string.cc_move_right)
    val up = stringResource(R.string.cc_move_up)
    val down = stringResource(R.string.cc_move_down)
    val bigger = stringResource(R.string.cc_change_size)
    val removeLabel = stringResource(R.string.cc_remove)

    fun endDrag(id: String, commit: Boolean) {
        val origin = latestBase.firstOrNull { it.id == id }
        val next = (if (commit) dropPreview?.slots else null) ?: latestBase
        val place = next.firstOrNull { it.id == id }
        val from = drag
        dragging = null; drag = Offset.Zero
        if (origin == null || place == null) return
        // The layout puts the control in its new place at once; this much translation keeps it where the finger left it, then carries it in.
        val residual = Offset(from.x + (origin.col - place.col) * latestStep * flip, from.y + (origin.row - place.row) * latestStep)
        landing = id
        if (next != latestBase) { hold(next); latestChange(next); haptic.perform(FolioHaptic.Commit) }
        scope.launch {
            settle.snapTo(residual)
            if (latestReduce) settle.snapTo(Offset.Zero) else settle.animateTo(Offset.Zero, FolioMotion.spring(FolioMotion.Quick))
            if (landing == id) landing = null
        }
    }

    Box(Modifier.size(span(cols), if (rows > 0) span(rows) else 0.dp).onGloballyPositioned { gridCoords = it }) {
        // Not editing, a press and hold on a place no control holds starts editing; the controls above take their own presses first.
        if (!editing) Box(Modifier.matchParentSize().pointerInput(Unit) {
            detectTapGestures(onLongPress = { haptic.perform(FolioHaptic.PickedUp); latestEnter() })
        })
        // The free places, as faint circles, so it is plain where a control can be dropped (as iOS).
        if (editing) ccFreeCells(layout, cols, rows).forEach { (c, r) ->
            Box(Modifier.offset(step * c, step * r).size(cell).padding(cell * .08f).clip(CircleShape).background(Color.White.copy(alpha = .07f)))
        }
        // The place the held control would take: where it lands if it were let go now.
        dropPreview?.landing?.let { g ->
            Box(Modifier.offset(step * g.col, step * g.row).size(span(g.w), span(g.h)).clip(CcTileCorner)
                .background(Color.White.copy(alpha = .16f)).border(1.5.dp, Color.White.copy(alpha = .55f), CcTileCorner))
        }
        layout.forEach { slot ->
            val id = slot.id
            key(id) {
                val origin = latestBase.firstOrNull { it.id == id } ?: slot
                val lifted = dragging == id
                val settling = landing == id
                val live = resizing == id
                val lift by animateFloatAsState(if (lifted) 1f else 0f, if (reduceMotion) snap() else FolioMotion.spring(FolioMotion.Quick), label = "cc lift")
                // The pointer reports its moves in the control's own (scaled) space, so a finger travels `grow` times as far as the numbers say.
                val liftNow by rememberUpdatedState(lift)
                // The held control stays at the place it was lifted from (the drag translates it); the rest slide to the places the drop would give them.
                val at = if (lifted) origin else slot
                val place by animateIntOffsetAsState(IntOffset((stepPx * at.col).roundToInt(), (stepPx * at.row).roundToInt()),
                    if (reduceMotion || lifted || settling || live) snap() else FolioMotion.spring(FolioMotion.Quick), label = "cc place")
                val name = labelOf(id)
                Box(
                    Modifier.offset { place }.size(span(slot.w), span(slot.h))
                        .zIndex(if (lifted || settling || live || raised == id) 1f else 0f)
                        .graphicsLayer {
                            if (lifted) { translationX = drag.x; translationY = drag.y }
                            else if (settling) { translationX = settle.value.x; translationY = settle.value.y }
                            val grow = 1f + CC_LIFT_GROW * lift
                            scaleX = grow; scaleY = grow
                            if (lift > 0f) { shadowElevation = 16.dp.toPx() * lift; shape = CcTileCorner; clip = false }
                        }
                        .then(if (!editing) Modifier else Modifier
                            .semantics {
                                stateDescription = resources.getString(R.string.cc_tile_place, slot.row + 1, slot.col + 1, slot.w, slot.h)
                                customActions = buildList {
                                    fun move(label: String, dc: Int, dr: Int) = add(CustomAccessibilityAction(label) {
                                        val now = latestBase.firstOrNull { it.id == id } ?: return@CustomAccessibilityAction false
                                        // A control in the way trades places with it, as a drop does.
                                        val next = ccDrop(latestBase, id, now.col + if (rtl) -dc else dc, now.row + dr, latestCols, latestRows)
                                        val landed = next.firstOrNull { it.id == id }
                                        if (next != latestBase && landed != null) {
                                            hold(next); latestChange(next)
                                            view.announceForAccessibility(resources.getString(R.string.cc_moved_to, name, landed.row + 1, landed.col + 1))
                                        } else view.announceForAccessibility(resources.getString(R.string.cc_cant_move, name))
                                        next != latestBase
                                    })
                                    move("$left, $name", -1, 0); move("$right, $name", 1, 0); move("$up, $name", 0, -1); move("$down, $name", 0, 1)
                                    if (ccAllowedSizes(id, cols).size > 1) add(CustomAccessibilityAction("$bigger, $name") {
                                        val next = ccNextSize(latestBase, id, latestCols, latestRows)
                                        if (next != null) {
                                            val resized = ccResize(latestBase, id, next.first, next.second, latestCols, latestRows)
                                            hold(resized); latestChange(resized)
                                            view.announceForAccessibility(resources.getString(R.string.cc_resized, name, next.first, next.second))
                                        } else view.announceForAccessibility(resources.getString(R.string.cc_cant_move, name))
                                        next != null
                                    })
                                    if (removable(id)) add(CustomAccessibilityAction("$removeLabel, $name") { latestRemove(id); view.announceForAccessibility(resources.getString(R.string.cc_removed_undo, name)); true })
                                }
                            }),
                ) {
                    content(slot)
                    if (editing) {
                        // Above the control, so a held press reaches the drag and never the tile's own press (a tile does nothing while editing).
                        Box(Modifier.matchParentSize()
                            .pointerInput(id) {
                                detectHeldDrag(
                                    onDragStart = { pos ->
                                        scope.launch { settle.stop() }
                                        landing = null; dragging = id; drag = Offset.Zero; grab = pos
                                        haptic.perform(FolioHaptic.PickedUp)
                                    },
                                    onDrag = { by -> drag += by * (1f + CC_LIFT_GROW * liftNow) },
                                    onDragEnd = { endDrag(id, commit = true) },
                                    // Cancelled (the system took the touch): the control goes home.
                                    onDragCancel = { endDrag(id, commit = false) },
                                )
                            })
                        // A still outline: the places are being arranged, so nothing else about the control moves or glows.
                        if (lifted || live) Box(Modifier.matchParentSize().border(1.5.dp, Color.White.copy(alpha = .9f), CcTileCorner))
                        // The touch boxes of the badge (top left) and the resize handle (bottom right) are sized from the control itself: each reaches
                        // at most two thirds of the way to the tile's middle, so the middle always stays free for a press and hold to lift the control.
                        // At a fixed 36 dp the badge's box covered the middle of a 44 dp tile and a held press there removed the control (Cast, 14 Nov 2026).
                        val tile = minOf(span(slot.w), span(slot.h))
                        val badgeBox = (tile * .68f).coerceIn(24.dp, 36.dp)
                        val badgeOut = badgeBox / 3
                        if (removable(id)) Box(Modifier.align(Alignment.TopStart).offset(-badgeOut, -badgeOut).size(badgeBox)
                            .pointerInput(id) { detectTapGestures { haptic.perform(FolioHaptic.Refuse); latestRemove(id); view.announceForAccessibility(resources.getString(R.string.cc_removed_undo, name)) } }, contentAlignment = Alignment.BottomEnd) {
                            // Small and the same on a big tile as on a small one; drawn at the box's inner corner, only a little past the tile's own corner,
                            // so it is not cut off at the top of a page that scrolls. The box around it is the tap target.
                            val badge = (cell * .4f).coerceIn(16.dp, 20.dp)
                            Box(Modifier.offset((-4).dp, (-4).dp).size(badge).clip(CircleShape).background(Color.White.copy(alpha = .92f)), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(badge * .45f, 2.dp).background(Color.Black.copy(alpha = .75f)))
                            }
                        }
                        val handleBox = (tile * .64f).coerceIn(24.dp, 32.dp)
                        val handleOut = handleBox * .43f
                        if (ccAllowedSizes(id, cols).size > 1) Box(Modifier.align(Alignment.BottomEnd).offset(handleOut, handleOut).size(handleBox)
                            .pointerInput(id) {
                                detectDragGestures(
                                    onDragStart = { resizing = id; resize = Offset.Zero; haptic.perform(FolioHaptic.PickedUp) },
                                    onDrag = { change, by -> change.consume(); resize += by },
                                    onDragEnd = {
                                        val next = resizePreview
                                        resizing = null; resize = Offset.Zero
                                        if (next != null && next != latestBase) { hold(next); latestChange(next); haptic.perform(FolioHaptic.Commit) }
                                    },
                                    onDragCancel = { resizing = null; resize = Offset.Zero },
                                )
                            }, contentAlignment = Alignment.Center) {
                            // A curved handle that follows the control's own corner, as iOS draws it, instead of a dot on the corner.
                            // The touch box sits further out and smaller than before (so the middle of a one-cell tile reaches the move drag), and the arc is drawn
                            // back where it was, hugging the corner.
                            Canvas(Modifier.offset(7.dp - handleOut - (30.dp - handleBox) / 2, 7.dp - handleOut - (30.dp - handleBox) / 2).size(30.dp)) {
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
}
