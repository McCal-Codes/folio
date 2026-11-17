package com.mccal.folio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Dynamic class: D2 Transitional. A removed Home icon shrinks and fades where it stood, as on iOS.
 *
 * Source of truth: the layout, which removes the icon at once and is not waiting for this (DYN-2). The picture here is a
 * copy of the icon left behind at its old place, drawn for a moment and then dropped; it holds no state anyone reads.
 * Persistent state: none.
 * Reduce Motion: no shrinking, only a fade over [FolioMotion.FADE_MS] (DYN-11).
 * When not visible: nothing runs (the copy lives only while it plays).
 * Animation owner: [RemovedIconsHost], placed once on Home behind the grid, so the emptied cell is clear for it.
 */
@Stable
internal class RemovedIcons {
    private var next = 0L
    val icons = mutableStateListOf<RemovedIcon>()

    /** Leaves a copy of [app] at [bounds] (window pixels) to play out. Nothing is left if the icon has no place yet. */
    fun launch(app: AppEntry, bounds: android.graphics.Rect, size: Dp, radius: Dp, labelled: Boolean) {
        if (bounds.isEmpty) return
        icons += RemovedIcon(next++, app, bounds.left.toFloat(), bounds.top.toFloat(), size, radius, labelled)
    }

    fun finished(icon: RemovedIcon) { icons.remove(icon) }
}

internal class RemovedIcon(val id: Long, val app: AppEntry, val left: Float, val top: Float, val size: Dp, val radius: Dp, val labelled: Boolean)

/** The place Home's remove buttons leave their icon's copy, or null where nothing plays it (previews). */
internal val LocalRemovedIcons = staticCompositionLocalOf<RemovedIcons?> { null }

@Composable
internal fun RemovedIconsHost(removed: RemovedIcons) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    // Icons are reported in window pixels, so this layer says where its own corner is.
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        removed.icons.forEach { icon -> key(icon.id) { RemovedIconView(icon, origin) { removed.finished(icon) } } }
    }
}

@Composable
private fun RemovedIconView(icon: RemovedIcon, origin: Offset, onDone: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val gone = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // Quick is the answer to a touch; the icon goes the way it was told to, with no bounce to speak of.
        val spec: AnimationSpec<Float> = if (reduceMotion) tween(FolioMotion.FADE_MS) else FolioMotion.spring<Float>(FolioMotion.Quick)
        gone.animateTo(1f, spec)
        onDone()
    }
    val ink = LocalHomeInk.current
    Box(Modifier.offset { IntOffset((icon.left - origin.x).roundToInt(), (icon.top - origin.y).roundToInt()) }
        .size(icon.size)
        .graphicsLayer {
            val progress = gone.value.coerceIn(0f, 1f)
            alpha = 1f - progress
            // Shrinks to nothing: the end of "going away" needs no number of its own.
            if (!reduceMotion) { scaleX = 1f - progress; scaleY = 1f - progress }
        }
        // A copy is not a control: TalkBack has already heard the icon go.
        .clearAndSetSemantics { }) {
        AppIcon(icon.app, null, Modifier.fillMaxSize(), shape = RoundedCornerShape(icon.radius))
        if (icon.labelled) {
            val labelFactor = glanceFontFactor(LocalDensity.current.fontScale)
            Text(icon.app.label, color = ink.primary, fontSize = (LocalLabelSize.current.sp * labelFactor).sp,
                lineHeight = (LocalLabelSize.current.lineSp * labelFactor).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                style = TextStyle(shadow = ink.labelShadow),
                modifier = Modifier.wrapContentSize(unbounded = true, align = Alignment.TopCenter).offset(y = icon.size + FolioSpace.TINY.dp))
        }
    }
}
