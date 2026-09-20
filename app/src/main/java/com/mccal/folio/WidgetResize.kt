package com.mccal.folio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * One widget being resized on Home: which slot the handles are on, how many cells it covers while the drag is
 * going on, what the widget itself will allow, and the pitches that turn a finger's travel into whole cells.
 *
 * These were eight separate pieces of state in [LauncherScreen], set together and cleared together. A resize never
 * outlived the activity — [slot] was only ever `remember`ed — so gathering the rest behind it changes nothing about
 * when a half-finished resize disappears.
 */
@Stable
internal class WidgetResize {
    /** The placement slot under the handles, or null when no widget is being resized. */
    var slot by mutableStateOf<Int?>(null)
        private set

    /** What the widget itself allows; null for a widget that hasn't said. */
    var constraints by mutableStateOf<WidgetSpanConstraints?>(null)
        private set

    /** Cells the widget covers as the drag goes on, before anything is committed. */
    var width by mutableIntStateOf(1)
    var height by mutableIntStateOf(1)

    /** A cell's width and a row's height in pixels, plus the two pitches the top rows and the app rows use. */
    var pitchX by mutableFloatStateOf(1f)
    var pitchY by mutableFloatStateOf(1f)
    var topPitch by mutableFloatStateOf(1f)
    var appPitch by mutableFloatStateOf(1f)

    val active: Boolean get() = slot != null

    fun start(slot: Int, width: Int, height: Int, constraints: WidgetSpanConstraints?) {
        this.slot = slot
        this.width = width
        this.height = height
        this.constraints = constraints
    }

    fun stop() {
        slot = null
    }
}

@Composable
internal fun rememberWidgetResize(): WidgetResize = remember { WidgetResize() }
