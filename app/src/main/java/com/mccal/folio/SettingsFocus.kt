package com.mccal.folio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The setting Settings search just sent the reader to, by the name the row shows. The row with that name scrolls into
 * view and glows once, then it is forgotten: coming back to the page later does nothing, and a row that isn't there
 * (an option hidden until its switch is on) doesn't flash the next time it appears.
 */
internal object SettingsFocus {
    var label: String? by mutableStateOf(null)

    /** How long a search target waits for its row to show up before it is dropped. */
    const val PATIENCE_MS = 3000L

    /**
     * The glow rises and falls once, the way a tapped row does on iOS, instead of appearing at full strength. One slow
     * pulse, far from the three-a-second flash limit (WCAG 2.3.1), and it only changes opacity, so Reduce Motion keeps it.
     */
    const val GLOW_PEAK = .28f
    const val GLOW_IN_MS = 180
    const val GLOW_OUT_MS = 1500
}

/** Gives a Settings row the scroll-and-glow when search picked it. Costs nothing for every other row. */
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun Modifier.settingsFocus(label: String): Modifier {
    if (SettingsFocus.label != label) return this
    val requester = remember { BringIntoViewRequester() }
    val glow = remember { Animatable(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val room = with(androidx.compose.ui.platform.LocalDensity.current) { 140.dp.toPx() }
    LaunchedEffect(label) {
        delay(250) // the page is still sliding in
        // Asks for room above and below the row, so it lands clear of the bars rather than flush against an edge.
        requester.bringIntoView(Rect(0f, -room, size.width.toFloat(), size.height + room))
        glow.animateTo(SettingsFocus.GLOW_PEAK, tween(SettingsFocus.GLOW_IN_MS, easing = FastOutSlowInEasing))
        glow.animateTo(0f, tween(SettingsFocus.GLOW_OUT_MS, easing = FastOutSlowInEasing))
        if (SettingsFocus.label == label) SettingsFocus.label = null
    }
    return onSizeChanged { size = it }.bringIntoViewRequester(requester).drawBehind {
        drawRoundRect(Color.White.copy(alpha = glow.value), cornerRadius = CornerRadius(10.dp.toPx()))
    }
}
