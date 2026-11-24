package com.mccal.folio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * The turn a widget makes when Edit Widget opens its options: the widget swings edge-on and its back, a card of
 * settings, swings in the same way, as in iOS (measured on a Duo screen recording, 23 Nov 2026: about 0.45 s in, 0.64 s out).
 * Pure numbers here so they can be tested; [WidgetFlip] below draws them.
 */
internal object WidgetFlip {
    /** Reduce Motion: the two faces cross-fade over this long instead of turning. */
    const val FADE_MS = FolioMotion.FADE_MS

    /** How far the camera sits from the widget, in widget widths: near enough to read as a turn, far enough not to distort the edges. */
    const val CAMERA_WIDTHS = 3f

    /** The back shows once the turn is past the half, where the widget is edge-on and neither face can be seen. */
    fun showsBack(progress: Float): Boolean = progress.coerceIn(0f, 1f) >= .5f

    /**
     * Rotation about the vertical axis, in degrees, at [progress] 0 (front) to 1 (back). The front turns 0 to 90, then the
     * back arrives from -90 to 0, so both faces read the right way round and the turn keeps one direction.
     */
    fun rotationY(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return if (p < .5f) 180f * p else 180f * p - 180f
    }

    /** The front's opacity under Reduce Motion (a plain cross-fade); the back's is its complement. */
    fun fade(progress: Float): Float = 1f - progress.coerceIn(0f, 1f)

    /** The layer's `cameraDistance` for a widget [widthPx] wide, with [PageEffectSpec.POINTS_PER_INCH] explaining the unit. */
    fun cameraDistance(widthPx: Float): Float = CAMERA_WIDTHS * widthPx / PageEffectSpec.POINTS_PER_INCH
}

/**
 * [front] until [flipped], then [back], turning between them. The front stays composed while the back shows, so a photo
 * does not decode again on the way back. The back is only composed while it is on screen or turning.
 */
@Composable
internal fun WidgetFlip(flipped: Boolean, modifier: Modifier = Modifier, back: @Composable () -> Unit, front: @Composable () -> Unit) {
    val still = LocalReduceMotion.current
    val progress = remember { Animatable(if (flipped) 1f else 0f) }
    LaunchedEffect(flipped, still) {
        val target = if (flipped) 1f else 0f
        if (still) progress.animateTo(target, tween(WidgetFlip.FADE_MS))
        else progress.animateTo(target, FolioMotion.spring(FolioMotion.Settle))
    }
    val backShowing by remember { derivedStateOf { WidgetFlip.showsBack(progress.value) } }
    val turning by remember { derivedStateOf { progress.value > 0f } }
    Box(modifier) {
        Box(Modifier.fillMaxSize()
            .graphicsLayer {
                if (still) alpha = WidgetFlip.fade(progress.value)
                else {
                    rotationY = WidgetFlip.rotationY(progress.value)
                    cameraDistance = WidgetFlip.cameraDistance(size.width)
                    alpha = if (backShowing) 0f else 1f
                }
            }
            // What is not showing is not read out or reachable.
            .then(if (backShowing) Modifier.clearAndSetSemantics {} else Modifier)) { front() }
        if (turning || flipped) {
            Box(Modifier.fillMaxSize()
                .graphicsLayer {
                    if (still) alpha = 1f - WidgetFlip.fade(progress.value)
                    else {
                        rotationY = WidgetFlip.rotationY(progress.value)
                        cameraDistance = WidgetFlip.cameraDistance(size.width)
                        alpha = if (backShowing) 1f else 0f
                    }
                }
                // Taps on the back's blank parts must not fall through to the front below.
                .clickable(remember { MutableInteractionSource() }, null) {}) { back() }
        }
    }
}
