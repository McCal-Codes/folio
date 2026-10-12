package com.mccal.folio

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

internal data class DragRegion(val target: DropTarget, val bounds: Rect, val appId: String?, val page: Int?,
    val widgetId: Int? = null, val folderId: String? = null, val scope: String? = null) {
    val movable get() = appId != null || (widgetId != null && widgetId != EMPTY_WIDGET)
}

@Stable
internal class HomeDragState {
    val regions = mutableStateMapOf<DropTarget, DragRegion>()
    private val regionOwners = mutableMapOf<DropTarget, Any>()
    var source by mutableStateOf<DragRegion?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var origin by mutableStateOf(Offset.Zero)
    var rootBounds by mutableStateOf(Rect.Zero)
    val rootOrigin get() = rootBounds.topLeft
    var originPage = 0
    var moved by mutableStateOf(false)
    var activeSourceScope by mutableStateOf<String?>(null)
    val active get() = source != null
    /** The 0.6.9 motion pass's lift and landing for the app being carried ([DragLanding]). */
    val landing = DragLanding()
    /** The app whose folder plate is showing because the carried app is held over its middle (FolderOrRoom). */
    var mergePreview by mutableStateOf<String?>(null)
    /** The finger's last moments, for the speed it lets go with ([releaseVelocity]). Not state: only read on release. */
    private val trail = ArrayDeque<Pair<Long, Offset>>()
    fun track(timeMillis: Long, point: Offset) {
        trail.addLast(timeMillis to point)
        while (trail.size > 2 && timeMillis - trail.first().first > TRAIL_MS) trail.removeFirst()
    }
    /** Pixels per second over the last [TRAIL_MS] of the drag; zero if the finger was still. */
    fun releaseVelocity(): Offset = releaseVelocity(trail.toList())
    fun hit(point: Offset, pages: Set<Int>) = regions.values
        .filter { (it.page == null || it.page in pages) && it.bounds.contains(point) &&
            (source != null || it.target !is DropTarget.Folder) && it.scope == activeSourceScope }
        .maxByOrNull(::dragRegionPriority)
    fun destination(point: Offset, pages: Set<Int>): DragRegion? {
        regions[DropTarget.Remove]?.takeIf { source?.target !is DropTarget.Library && it.bounds.contains(point) }?.let { return it }
        return regions.values.filter {
            (it.page == null || it.page in pages) && it.bounds.contains(point) && when (it.target) {
                // A moving widget is rendered at its preview footprint and registers the
                // same Widget target again. Never let that preview shadow the Home cells
                // beneath it. Widgets always move through the cell grid.
                is DropTarget.Widget -> false
                is DropTarget.Home -> source?.appId != null || source?.target is DropTarget.Widget
                is DropTarget.Dock -> source?.appId != null
                // A dragged folder is drawn under the finger with its own drop target, and folders don't nest,
                // so a folder only ever lands on Home cells (and apps never on the folder they're leaving).
                is DropTarget.Folder -> source?.appId?.let { !isFolderId(it) } == true && source?.target !is DropTarget.Folder
                DropTarget.Remove -> source?.target !is DropTarget.Library
                is DropTarget.Library -> false
            }
        }.maxByOrNull(::dragRegionPriority)
    }
    fun clear() { source = null; moved = false; trail.clear(); mergePreview = null }

    fun register(owner: Any, region: DragRegion) {
        regionOwners[region.target] = owner
        regions[region.target] = region
    }

    fun update(owner: Any, target: DropTarget, appId: String?, page: Int?, widgetId: Int?, folderId: String?, scope: String?) {
        regions[target]?.takeIf { regionOwners[target] === owner }?.let {
            if (it.appId != appId || it.widgetId != widgetId || it.page != page || it.folderId != folderId || it.scope != scope) {
                regions[target] = it.copy(appId = appId, widgetId = widgetId, page = page, folderId = folderId, scope = scope)
            }
        }
    }

    fun unregister(owner: Any, target: DropTarget) {
        if (regionOwners[target] === owner) {
            regionOwners.remove(target)
            regions.remove(target)
        }
    }
}

private const val TRAIL_MS = 90L

/** The speed between the oldest and newest point of a short trail, in pixels per second; zero without movement or time. */
internal fun releaseVelocity(trail: List<Pair<Long, Offset>>): Offset {
    if (trail.size < 2) return Offset.Zero
    val (t0, p0) = trail.first(); val (t1, p1) = trail.last()
    val seconds = (t1 - t0) / 1000f
    return if (seconds <= 0f) Offset.Zero else (p1 - p0) / seconds
}

/** Page turning follows the window edges, including the space beside the fixed dock. */
internal fun dragEdgeDirection(point: Offset, window: Rect, edgeWidth: Float): Int = when {
    window.isEmpty || !window.contains(point) -> 0
    point.x < window.left + edgeWidth -> -1
    point.x > window.right - edgeWidth -> 1
    else -> 0
}

@Composable
internal fun Modifier.dropRegion(drag: HomeDragState, target: DropTarget, appId: String? = null, page: Int? = null,
    widgetId: Int? = null, folderId: String? = null, scope: String? = null): Modifier {
    val owner = remember { Any() }
    DisposableEffect(drag, target, owner) { onDispose { drag.unregister(owner, target) } }
    SideEffect { drag.update(owner, target, appId, page, widgetId, folderId, scope) }
    return onGloballyPositioned { coordinates ->
        drag.register(owner, DragRegion(target, coordinates.boundsInRoot(), appId, page, widgetId, folderId, scope))
    }
}

internal fun dragRegionPriority(region: DragRegion): Int = when (region.target) {
    is DropTarget.Folder -> 3
    is DropTarget.Widget -> 2
    is DropTarget.Library -> if (region.scope != null) 2 else 0
    else -> 1
}

/** Keeps the grabbed point over the pointer while resolving the widget's top-left cell. */
internal fun adjustedWidgetDropIndex(
    rawIndex: Int,
    placement: WidgetPlacement,
    sourceBounds: Rect,
    grabPoint: Offset,
): Int {
    val columnOffset = (((grabPoint.x - sourceBounds.left) / sourceBounds.width.coerceAtLeast(1f)) * placement.spanX)
        .toInt().coerceIn(0, placement.spanX - 1)
    val rowOffset = (((grabPoint.y - sourceBounds.top) / sourceBounds.height.coerceAtLeast(1f)) * placement.spanY)
        .toInt().coerceIn(0, placement.spanY - 1)
    val page = homeCellPage(rawIndex)
    val rawLocal = homeCellLocal(rawIndex)
    val column = (rawLocal % GRID_COLUMNS - columnOffset).coerceIn(0, GRID_COLUMNS - placement.spanX)
    val row = (rawLocal / GRID_COLUMNS - rowOffset)
        .coerceIn(0, GRID_ROWS - placement.spanY.coerceAtMost(GRID_ROWS))
    return homeCellIndex(page, row * GRID_COLUMNS + column)
}

/**
 * A native collection widget consumes its own MOVE events even while the finger has only
 * jittered. Compose's stock long-press helper treats that consumption as cancellation, so a
 * Calendar-style widget could scroll but could no longer be picked up. The root observes the
 * Initial pass and arbitrates intent from geometry instead: jitter keeps waiting, while a real
 * move, release/cancel, or second pointer yields the stream to the provider.
 */
private suspend fun AwaitPointerEventScope.awaitWidgetLongPressOrCancellation(
    down: PointerInputChange,
): PointerInputChange? {
    var current = down
    var canceled = false
    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (!canceled) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            canceled = change == null || !change.pressed ||
                event.changes.any { it.id != down.id && it.pressed } ||
                (change.position - down.position).getDistance() > viewConfiguration.touchSlop
            if (!canceled) current = change!!
        }
    }
    return current.takeUnless { canceled }
}

/** The root owns the pointer so paging cannot dispose the active gesture. */
@Composable
internal fun Modifier.homeDragInput(
    drag: HomeDragState, enabled: Boolean, page: Int, eligiblePages: Set<Int> = setOf(page),
    onStart: () -> Unit, onFinish: (Boolean) -> Unit,
    /** Jiggle mode: moving a finger past touch slop picks the item up without a long-press. */
    immediate: Boolean = false,
): Modifier {
    val currentImmediate by rememberUpdatedState(immediate)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentPage by rememberUpdatedState(page)
    val currentEligiblePages by rememberUpdatedState(eligiblePages)
    val start by rememberUpdatedState(onStart)
    val finish by rememberUpdatedState(onFinish)
    return onGloballyPositioned { drag.rootBounds = it.boundsInRoot() }.pointerInput(drag) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!currentEnabled) return@awaitEachGesture
            val point = down.position + drag.rootOrigin
            val region = drag.hit(point, currentEligiblePages)?.takeIf { it.movable } ?: return@awaitEachGesture
            var movedAlready = false
            if (currentImmediate) {
                val result = awaitSlopOrLongPress(down) ?: return@awaitEachGesture
                movedAlready = result
            } else if (region.target is DropTarget.Widget && (region.widgetId ?: EMPTY_WIDGET) >= 0) {
                awaitWidgetLongPressOrCancellation(down) ?: return@awaitEachGesture
            } else {
                awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            }
            drag.source = region
            drag.pointer = point
            drag.origin = point
            drag.originPage = currentPage
            drag.moved = movedAlready
            drag.track(down.uptimeMillis, point)
            start()
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || event.changes.any { it.id != down.id && it.pressed }) {
                        event.changes.forEach { it.consume() }
                        finish(true)
                        break
                    }
                    drag.pointer = change.position + drag.rootOrigin
                    drag.track(change.uptimeMillis, drag.pointer)
                    if ((drag.pointer - drag.origin).getDistance() > viewConfiguration.touchSlop) drag.moved = true
                    change.consume()
                    if (!change.pressed) {
                        finish(false)
                        break
                    }
                    if (!drag.active) break
                }
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                drag.clear()
                throw cancel
            }
        }
    }
}

/** true once the finger moves past slop, false after a long-press without moving, null on release or cancel. */
private suspend fun AwaitPointerEventScope.awaitSlopOrLongPress(down: PointerInputChange): Boolean? {
    var result: Boolean? = false
    val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed || change.isConsumed || event.changes.any { it.id != down.id && it.pressed }) {
                result = null; break
            }
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                change.consume(); result = true; break
            }
        }
        Unit
    }
    return if (finished == null) false else result
}
