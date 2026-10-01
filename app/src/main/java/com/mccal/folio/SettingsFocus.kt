package com.mccal.folio

import androidx.compose.animation.core.Animatable
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
}

/** Gives a Settings row the scroll-and-glow when search picked it. Costs nothing for every other row. */
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun Modifier.settingsFocus(label: String): Modifier {
    if (SettingsFocus.label != label) return this
    val requester = remember { BringIntoViewRequester() }
    val glow = remember { Animatable(0f) }
    LaunchedEffect(label) {
        delay(250) // the page is still sliding in
        requester.bringIntoView()
        glow.snapTo(.28f)
        glow.animateTo(0f, tween(1600))
        if (SettingsFocus.label == label) SettingsFocus.label = null
    }
    return bringIntoViewRequester(requester).drawBehind {
        drawRoundRect(Color.White.copy(alpha = glow.value), cornerRadius = CornerRadius(10.dp.toPx()))
    }
}
