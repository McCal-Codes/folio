package com.mccal.folio

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * How far up the swipe that closes Notification Center or Control Center has pulled the panel. The panel follows the finger (see [fade] for how
 * it thins as it goes) and only the letting go decides: past [CC_SWIPE_AWAY_DP], or a quick flick up, it closes ([ccSwipeAwayCloses]); otherwise it
 * springs back. Closing at the lift, not part-way through the drag, means the touch is over by the time the panel goes, so the rest of the swipe
 * is never handed to the app underneath, and the panel never jumps shut under a finger that is still moving.
 */
internal class PanelPull {
    /** Pixels the panel is pulled up: 0 at rest, negative as it goes. */
    var offset by mutableFloatStateOf(0f)
        private set

    fun reset() { offset = 0f }

    /** A finger move of [dy] pixels: up pulls the panel, down gives back what was pulled but never pushes it below its place. */
    fun drag(dy: Float) { offset = (offset + dy).coerceAtMost(0f) }

    /**
     * The finger lifted with [velocityY] (negative going up). Closes with [close] when the pull or the flick is enough, unless [goBack] says the swipe
     * was taken by going back a page; either way a pull that did not close springs back.
     */
    fun release(scope: CoroutineScope, velocityY: Float, thresholdPx: Float, flickPx: Float, goBack: () -> Boolean, close: () -> Unit) {
        if (offset >= 0f) return
        if (ccSwipeAwayCloses(offset, velocityY, thresholdPx, flickPx) && !goBack()) { close(); return }
        scope.launch {
            androidx.compose.animation.core.animate(offset, 0f, animationSpec = FolioMotion.spring(FolioMotion.Quick)) { v, _ -> offset = v }
        }
    }

    companion object {
        /** The panel's opacity at [offsetPx] of pull on a panel [heightPx] tall: it thins as it goes and keeps a trace so it never vanishes under the finger. */
        fun fade(offsetPx: Float, heightPx: Float): Float =
            if (heightPx <= 0f) 1f else (1f + offsetPx / (heightPx * .9f)).coerceIn(.15f, 1f)
    }
}

/** The pull the panels share, for a list inside a panel to hand its leftover upward scroll to. Null outside the panels. */
internal val LocalPanelPull = staticCompositionLocalOf<PanelPull?> { null }

/**
 * The modifier that makes a drag up on an element pull the panel with the finger (see [PanelPull]); letting go closes it or lets it spring back. Put it
 * before the graphicsLayer that moves the panel so the touch area stays where the finger started. [pageBack] is asked first, so from Control Center's
 * Media or Connectivity page the swipe goes back a page, as it did when it closed at a fixed distance.
 */
@Composable
internal fun rememberPanelPull(pull: PanelPull, pageBack: CcPageBack, onClose: () -> Unit): Modifier {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val close by rememberUpdatedState(onClose)
    val threshold = with(density) { CC_SWIPE_AWAY_DP.dp.toPx() }
    val flick = with(density) { CC_SWIPE_AWAY_FLICK_DP.dp.toPx() }
    val state = rememberDraggableState { pull.drag(it) }
    return Modifier.draggable(state, Orientation.Vertical,
        onDragStopped = { velocity -> pull.release(scope, velocity, threshold, flick, goBack = { pageBack.goBack() }, close = close) })
}

/**
 * For a list inside a panel (Notification Center's): an upward scroll the list cannot use (it is at its end, or too short to scroll) pulls the panel
 * the way a swipe on its margins does, and a downward one gives the pull back before the list scrolls. Lifting a finger that pulled ends it as [rememberPanelPull] does.
 */
@Composable
internal fun rememberPanelPullConnection(pull: PanelPull?, onClose: () -> Unit): NestedScrollConnection? {
    if (pull == null) return null
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val close by rememberUpdatedState(onClose)
    val threshold = with(density) { CC_SWIPE_AWAY_DP.dp.toPx() }
    val flick = with(density) { CC_SWIPE_AWAY_FLICK_DP.dp.toPx() }
    return remember(pull) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || pull.offset >= 0f || available.y <= 0f) return Offset.Zero
                val used = minOf(available.y, -pull.offset)
                pull.drag(used)
                return Offset(0f, used)
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
                pull.drag(available.y)
                return Offset(0f, available.y)
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull.offset >= 0f) return Velocity.Zero
                pull.release(scope, available.y, threshold, flick, goBack = { false }, close = close)
                return available
            }
        }
    }
}
