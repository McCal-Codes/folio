package com.mccal.folio

import androidx.compose.ui.unit.dp

import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput

/** One adjacent page per ordinary pointer gesture, including its release fling. */
internal class PageGestureLimits(private val pager: PagerState) : NestedScrollConnection, PagerSnapDistance {
    var anchor: Int? = null
    var pointerDown = false
    var editing = false
    /** Lowest physical page a swipe may reach (e.g. no Today View page while it's shown beside Home). */
    var minPage = 0
    /**
     * New motion's rubber band: how far the pages are pulled past a true end, in pixels along the pager (positive past
     * the last page). Drawn as a translation by whoever shows the pages, never laid out. Always 0 with New motion off.
     */
    var stretch by androidx.compose.runtime.mutableFloatStateOf(0f)
    /** True while a touch has caught a settling page, so the canceled settle leaves the stretch under the finger. */
    var holdStretch = false
    /** Whether the first page may stretch; off while Discover owns the pull from the first page. */
    var leadingStretch = true
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val start = anchor ?: return Offset.Zero
        if (!pointerDown || editing || source != NestedScrollSource.UserInput || available.x == 0f) return Offset.Zero
        val stride = pager.layoutInfo.pageSize + pager.layoutInfo.pageSpacing
        if (stride <= 0) return Offset.Zero
        val position = pager.currentPage + pager.currentPageOffsetFraction
        val allowed = boundedPagePosition(position - available.x / stride, start, pager.pageCount, minPage)
        return Offset(available.x + (allowed - position) * stride, 0f)
    }
    override fun calculateTargetPage(startPage: Int, suggestedTargetPage: Int, velocity: Float,
        pageSize: Int, pageSpacing: Int): Int =
        if (editing) suggestedTargetPage else boundedPagePosition(suggestedTargetPage.toFloat(), anchor ?: startPage, pager.pageCount, minPage).toInt()
}

internal fun boundedPagePosition(position: Float, anchor: Int, count: Int, minPage: Int = 0): Float {
    val low = maxOf(anchor - 1, minPage, 0).coerceAtMost(count - 1).coerceAtLeast(0)
    val high = (anchor + 1).coerceAtMost(count - 1).coerceAtLeast(low)
    return position.coerceIn(low.toFloat(), high.toFloat())
}

internal fun releasePage(position: Float, anchor: Int, count: Int, velocity: Float, threshold: Float,
    distanceThreshold: Float = .2f, minPage: Int = 0): Int {
    val displacement = position - anchor
    val candidate = when {
        velocity < -threshold -> floor(position + .0001f).toInt() + 1
        velocity > threshold -> ceil(position - .0001f).toInt() - 1
        abs(displacement) >= distanceThreshold -> anchor + sign(displacement).toInt()
        else -> anchor
    }
    return boundedPagePosition(candidate.toFloat(), anchor, count, minPage).toInt()
}

/** iOS-style: pull down from the top-left for notifications, top-right for quick settings;
 * a downward swipe that starts lower on Home opens search. */
internal fun shadePanelForStart(startX: Float, startY: Float, width: Float, topZone: Float): ShadePanel =
    when {
        startY >= topZone -> ShadePanel.SEARCH
        startX < width / 2f -> ShadePanel.NOTIFICATIONS
        else -> ShadePanel.QUICK_SETTINGS
    }

/** Choose an axis before children see the slop-crossing event. A vertical list must not
 * steal a mostly-horizontal swipe simply because one fast sample crossed both thresholds.
 * PagerState still owns scrolling, layout, cancellation, and the settling animation. */
@Composable
internal fun Modifier.onePageGestures(
    pager: PagerState,
    limits: PageGestureLimits,
    motion: WorkspacePageMotion? = null,
    enabled: Boolean = true,
    canStartDownwardSwipe: (Offset) -> Boolean = { true },
    /** False for touches that belong to another control (e.g. the page scrubber) and must not page Home. */
    canStartGesture: (Offset) -> Boolean = { true },
    onDownwardSwipe: ((ShadePanel) -> Unit)? = null,
    onLeadingOverscroll: (() -> Unit)? = null,
    /** New motion: whether the first page stretches past its edge (not while Discover opens from there). */
    leadingStretch: Boolean = true,
) : Modifier {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentCanStartDownwardSwipe by rememberUpdatedState(canStartDownwardSwipe)
    val currentCanStartGesture by rememberUpdatedState(canStartGesture)
    val currentDownwardSwipe by rememberUpdatedState(onDownwardSwipe)
    val currentLeadingOverscroll by rememberUpdatedState(onLeadingOverscroll)
    val currentLeadingStretch by rememberUpdatedState(leadingStretch)
    // New motion's pager feel: catching a settling page is feedback, so it stays under Reduce Motion; the rubber band
    // and the overshoot are spatial, so Reduce Motion keeps today's hard stop.
    val currentCatch by rememberUpdatedState(FolioMotion.v2)
    val currentFeel by rememberUpdatedState(FolioMotion.v2 && !LocalReduceMotion.current)
    return nestedScroll(limits).pointerInput(pager, limits, motion) {
        coroutineScope {
            var motionJob: Job? = null
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val gestureEnabled = currentEnabled && currentCanStartGesture(down.position)
                val anchor = pager.currentPage
                val trace = DuoMotionTrace.begin(anchor,
                    pager.currentPage + pager.currentPageOffsetFraction, down.position.x, down.position.y)
                if (!gestureEnabled) {
                    trace?.cancel("disabled", pager.currentPage + pager.currentPageOffsetFraction)
                    return@awaitEachGesture
                }
                val feel = currentFeel
                limits.leadingStretch = currentLeadingStretch
                // Like iOS, a touch on a page that is still moving stops it there, on touch-down rather than after
                // touch slop, and that touch does not open the app under it.
                val caught = currentCatch && PagerFeel.catches(motionJob?.isActive == true,
                    pager.currentPage + pager.currentPageOffsetFraction, pager.targetPage, limits.stretch, 2f * density)
                var carried = 0f
                if (caught) {
                    carried = limits.stretch
                    limits.holdStretch = true
                    motionJob?.cancel()
                    down.consume()
                }
                limits.anchor = anchor
                limits.pointerDown = true
                val tracker = VelocityTracker().apply { addPointerInputChange(down) }
                var dragPositions: Channel<Float>? = null
                var dragSlopOffset = 0f
                var releaseVelocity = 0f
                var leadingDrag = 0f
                var canceled = false
                var cancelReason: String? = null
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || change.isConsumed || limits.editing || event.changes.any { it.id != down.id && it.pressed }) {
                            canceled = true
                            cancelReason = when {
                                change == null -> "pointer_missing"
                                change.isConsumed -> "consumed"
                                limits.editing -> "editing"
                                else -> "multiple_pointers"
                            }
                            break
                        }
                        // Match Compose's drag tracking, including Android's batched historical
                        // samples and its handling of the final finger-up event.
                        tracker.addPointerInputChange(change)
                        leadingDrag = maxOf(leadingDrag, change.position.x - down.position.x)
                        val terminalRelease = event.type == PointerEventType.Release && change.changedToUp()
                        if (dragPositions == null && (change.pressed || terminalRelease)) {
                            val distance = change.position - down.position
                            if (maxOf(abs(distance.x), abs(distance.y)) > viewConfiguration.touchSlop) {
                                trace?.slop(distance.x, distance.y,
                                    pager.currentPage + pager.currentPageOffsetFraction)
                                if (abs(distance.y) >= abs(distance.x)) {
                                    // A terminal release can preserve meaningful travel after a
                                    // blocked frame dropped every MOVE. It can page horizontally,
                                    // but must not trigger a vertical system action on finger-up.
                                    val openDownward = change.pressed && distance.y > 0f && currentDownwardSwipe != null &&
                                        currentCanStartDownwardSwipe(down.position)
                                    cancelReason = if (openDownward) "downward_action" else "vertical_axis"
                                    if (openDownward) {
                                        change.consume()
                                        currentDownwardSwipe?.invoke(shadePanelForStart(down.position.x, down.position.y, size.width.toFloat(),
                                            maxOf(size.height * .18f, 120.dp.toPx())))
                                    }
                                    break
                                }
                                motionJob?.cancel()
                                // Pointer input can outpace layout on a busy frame. Keep the
                                // latest absolute drag position rather than queueing and replaying
                                // every stale delta after the finger has already moved on.
                                val channel = Channel<Float>(Channel.CONFLATED)
                                dragPositions = channel
                                dragSlopOffset = sign(distance.x) * viewConfiguration.touchSlop
                                motionJob = launch(start = CoroutineStart.UNDISPATCHED) {
                                    fun stride() = (pager.layoutInfo.pageSize + pager.layoutInfo.pageSpacing).toFloat().coerceAtLeast(1f)
                                    fun position() = pager.currentPage + pager.currentPageOffsetFraction
                                    fun visualOffset() = motion?.offset(position()) ?: position() * stride()
                                    limits.holdStretch = false
                                    try {
                                        pager.scroll(MutatePriority.UserInput) {
                                            with(pager) { updateTargetPage((anchor - sign(distance.x).toInt()).coerceIn(limits.minPage.coerceAtMost(pager.pageCount - 1), pager.pageCount - 1)) }
                                            fun moveBy(pixels: Float) {
                                                val current = position()
                                                val requested = motion?.positionAfterVisualDelta(current, pixels)
                                                    ?: (current + pixels / stride())
                                                val allowed = boundedPagePosition(requested, anchor, pager.pageCount, limits.minPage)
                                                scrollBy((allowed - current) * stride())
                                            }
                                            val dragStartVisualOffset = visualOffset()
                                            // A caught stretch carries on from where it was, without a jump.
                                            val feelStartVisualOffset = dragStartVisualOffset + PagerFeel.excessFor(carried, size.width.toFloat())
                                            for (dragPosition in channel) {
                                                if (dragPosition != 0f) with(pager) { updateTargetPage((anchor - sign(dragPosition).toInt()).coerceIn(limits.minPage.coerceAtMost(pager.pageCount - 1), pager.pageCount - 1)) }
                                                if (feel) placeFeel(pager, motion, limits, feelStartVisualOffset - dragPosition, size.width.toFloat(), stretchy = true) {
                                                    boundedPagePosition(it, anchor, pager.pageCount, limits.minPage)
                                                }
                                                else moveBy(dragStartVisualOffset - dragPosition - visualOffset())
                                            }
                                            val velocity = if (canceled) 0f else releaseVelocity
                                            // A relaxed thumb swipe should commit even if the finger
                                            // slows before lifting. Cap the distance on the unfolded
                                            // display so it never requires a half-screen hand stretch.
                                            val releaseDirection = when {
                                                position() > anchor -> 1
                                                position() < anchor -> -1
                                                velocity < 0f -> 1
                                                velocity > 0f -> -1
                                                else -> 0
                                            }
                                            val adjacent = (anchor + releaseDirection).coerceIn(limits.minPage.coerceAtMost(pager.pageCount - 1), pager.pageCount - 1)
                                            val releaseStride = motion?.stride(anchor, adjacent)
                                                ?.takeIf { it > 0f } ?: stride()
                                            val target = if (canceled) position().roundToInt() else releasePage(
                                                position(), anchor, pager.pageCount, velocity, 250f * density,
                                                distanceThreshold = minOf(.2f, 72f * density / releaseStride), minPage = limits.minPage
                                            )
                                            trace?.release(position(), velocity, target, canceled)
                                            with(pager) { updateTargetPage(target) }
                                            if (feel) {
                                                settleFeel(pager, motion, limits, target, velocity, size.width.toFloat(), stretchy = true,
                                                    spring = PagerFeel.settleSpring(velocity, 250f * density, limits.stretch != 0f, reduce = false))
                                                return@scroll
                                            }
                                            val startVisualOffset = visualOffset()
                                            val targetVisualOffset = motion?.offset(target.toFloat()) ?: target * stride()
                                            val distanceToPage = targetVisualOffset - startVisualOffset
                                            animate(0f, distanceToPage, initialVelocity = (-velocity).coerceIn(-stride() * 5f, stride() * 5f)) { value, _ ->
                                                // Springs can overshoot the one-page bound. Base every
                                                // frame on the pager's actual position so a rejected delta
                                                // cannot become a persistent settling offset.
                                                moveBy(startVisualOffset + value - visualOffset())
                                            }
                                            // The final callback normally lands exactly here; resolve any
                                            // subpixel pager rounding before this scroll mutation completes.
                                            moveBy(targetVisualOffset - visualOffset())
                                        }
                                        trace?.motionCompleted(position(), visualOffset())
                                    } catch (failure: CancellationException) {
                                        trace?.motionCanceled(position(), visualOffset())
                                        if (!limits.holdStretch) limits.stretch = 0f
                                        throw failure
                                    }
                                    if (!canceled && anchor == 0 && leadingDrag >= 72f * density) {
                                        currentLeadingOverscroll?.invoke()
                                    }
                                }
                                channel.trySend(distance.x - dragSlopOffset)
                            }
                        } else if (dragPositions != null) {
                            dragPositions?.trySend(change.position.x - down.position.x - dragSlopOffset)
                        }
                        if (dragPositions != null || caught) change.consume()
                        if (!change.pressed) {
                            if (trace != null) trace.terminal(event.type.toString(),
                                change.position.x - down.position.x, change.position.y - down.position.y,
                                change.uptimeMillis, android.os.SystemClock.uptimeMillis(),
                                change.historical.size, change.isConsumed,
                                pager.currentPage + pager.currentPageOffsetFraction)
                            releaseVelocity = tracker.calculateVelocity().x
                            break
                        }
                    }
                } finally {
                    limits.pointerDown = false
                    dragPositions?.close()
                    if (caught && dragPositions == null) {
                        // Caught and let go without a swipe: the pages go to the nearest page, as on iOS.
                        motionJob = launch(start = CoroutineStart.UNDISPATCHED) {
                            limits.holdStretch = false
                            val target = PagerFeel.caughtPage(pager.currentPage + pager.currentPageOffsetFraction,
                                pager.pageCount, limits.minPage)
                            try {
                                pager.scroll(MutatePriority.UserInput) {
                                    with(pager) { updateTargetPage(target) }
                                    settleFeel(pager, motion, limits, target, 0f, size.width.toFloat(), stretchy = feel,
                                        spring = PagerFeel.settleSpring(0f, 1f, limits.stretch != 0f, reduce = !feel))
                                }
                            } catch (failure: CancellationException) {
                                if (!limits.holdStretch) limits.stretch = 0f
                                throw failure
                            }
                        }
                    }
                    val finalCancelReason = cancelReason
                    if (finalCancelReason != null) {
                        trace?.cancel(finalCancelReason, pager.currentPage + pager.currentPageOffsetFraction)
                    } else if (dragPositions == null) {
                        trace?.release(pager.currentPage + pager.currentPageOffsetFraction,
                            releaseVelocity, anchor, canceled = false)
                    }
                }
            }
        }
    }
}

/**
 * New motion: puts the pages at the [desired] visual offset. They scroll as far as [clampTo] lets them; past a true end
 * the rest of the finger's travel becomes [PageGestureLimits.stretch] on iOS's rubber band curve, if [stretchy].
 */
private fun ScrollScope.placeFeel(pager: PagerState, motion: WorkspacePageMotion?, limits: PageGestureLimits,
    desired: Float, width: Float, stretchy: Boolean, clampTo: (Float) -> Float) {
    val stride = (pager.layoutInfo.pageSize + pager.layoutInfo.pageSpacing).toFloat().coerceAtLeast(1f)
    val current = pager.currentPage + pager.currentPageOffsetFraction
    val requested = motion?.position(desired) ?: (desired / stride)
    val clamped = clampTo(requested)
    val side = if (stretchy) PagerFeel.stretchSide(requested, clamped, pager.pageCount, limits.minPage, limits.leadingStretch) else 0
    limits.stretch = if (side == 0) 0f else PagerFeel.rubberBand(desired - (motion?.offset(clamped) ?: (clamped * stride)), width)
    scrollBy((clamped - current) * stride)
}

/**
 * New motion: settles the pages on [target] from where they are, a stretch included, on [spring] (or the spring Home
 * settles on today when it is null). A flick passes a middle page by a little and eases back; past a true end the
 * overshoot stretches instead, so the hard clamp never flattens it.
 */
private suspend fun ScrollScope.settleFeel(pager: PagerState, motion: WorkspacePageMotion?, limits: PageGestureLimits,
    target: Int, velocity: Float, width: Float, stretchy: Boolean, spring: Pair<Float, Float>?) {
    fun stride() = (pager.layoutInfo.pageSize + pager.layoutInfo.pageSpacing).toFloat().coerceAtLeast(1f)
    fun visualAt(position: Float) = motion?.offset(position) ?: (position * stride())
    fun positionAt(visual: Float) = motion?.position(visual) ?: (visual / stride())
    val first = PagerFeel.firstPage(pager.pageCount, limits.minPage).toFloat()
    val last = PagerFeel.lastPage(pager.pageCount).toFloat()
    val start = visualAt(pager.currentPage + pager.currentPageOffsetFraction) + PagerFeel.excessFor(limits.stretch, width)
    val startPosition = positionAt(start)
    val targetVisual = visualAt(target.toFloat())
    val initialVelocity = (-velocity).coerceIn(-stride() * 5f, stride() * 5f)
    val frame: (Float, Float) -> Unit = { value, _ ->
        val shown = PagerFeel.settlePosition(positionAt(start + value), startPosition, target, pager.pageCount, limits.minPage)
        placeFeel(pager, motion, limits, visualAt(shown), width, stretchy) { it.coerceIn(first, last) }
    }
    if (spring == null) animate(0f, targetVisual - start, initialVelocity = initialVelocity, block = frame)
    else animate(0f, targetVisual - start, initialVelocity = initialVelocity, animationSpec = FolioMotion.spring(spring), block = frame)
    limits.stretch = 0f
    placeFeel(pager, motion, limits, targetVisual, width, stretchy = false) { it.coerceIn(first, last) }
}
