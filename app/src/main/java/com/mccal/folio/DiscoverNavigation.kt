package com.mccal.folio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/** Positive overscroll from home page one opens the feed without renumbering pinned pages. */
@Composable
internal fun Modifier.discoverSwipe(enabled: Boolean, onOpen: () -> Unit): Modifier {
    val allowed = rememberUpdatedState(enabled)
    val open = rememberUpdatedState(onOpen)
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val connection = remember(threshold) {
        object : NestedScrollConnection {
            var distance = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && allowed.value) distance = (distance + available.x).coerceAtLeast(0f)
                else if (!allowed.value) distance = 0f
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                val shouldOpen = allowed.value && distance >= threshold
                distance = 0f
                if (shouldOpen) open.value()
                return if (shouldOpen) available else Velocity.Zero
            }
        }
    }
    return nestedScroll(connection)
}

/** Reserve the saved dock width plus its surrounding wallpaper, at any display density. */
internal fun discoverDockFraction(widthDp: Float, dockWidthDp: Float): Float =
    ((dockWidthDp.coerceIn(56f, MAX_DOCK_WIDTH) + 28f) / widthDp.coerceAtLeast(1f)).coerceIn(.05f, .45f)
