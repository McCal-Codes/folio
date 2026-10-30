package com.mccal.folio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

/** The single fade in when the fault appears. Nothing else ever fades, and with Reduce Motion this does not run. */
private const val FADE_IN_MS = 300

/** A change moves one element once, over a time picked between these. */
private const val STEP_MOVE_MIN_MS = 150
private const val STEP_MOVE_MAX_MS = 250

private const val HAIRLINE_TILT_DEGREES = -0.6f
private const val HAIRLINE_WIDTH_DP = 1.5f
private const val HINGE_GAP_DP = 2f

private val BAR_GRADIENT = arrayOf(0f to CrackgateColors.BarTop, .3f to CrackgateColors.BarCore, .7f to CrackgateColors.BarMiddle, 1f to CrackgateColors.BarBottom)

/**
 * The pretend hinge fault, drawn to fill the window it is in. Cyan and green only (never red), at most one 300 ms fade in and a few
 * single slow moves planned in [CrackgateShown.plan]; nothing loops, blinks or flashes. It takes no touches (the window is click-through).
 */
@Composable
internal fun CrackgateFault(shown: CrackgateShown) {
    val look = shown.look
    val reduce = CrackgateOwner.reduceMotion.collectAsState()
    val fade = remember(shown) { Animatable(if (reduce.value) 1f else 0f) }
    val barTop = remember(shown) { Animatable(look.barTopPercent) }
    val endGap = remember(shown) { Animatable(look.barEndGapPercent) }
    val brightFrom = remember(shown) { Animatable(look.brightFromPercent) }
    LaunchedEffect(shown) {
        if (reduce.value) { fade.snapTo(1f); return@LaunchedEffect }
        val fadeJob = launch { fade.animateTo(1f, tween(FADE_IN_MS, easing = LinearEasing)) }
        val stepsJob = launch {
            val dice = Random(shown.startedAtMs)
            for (glitch in shown.plan) {
                delay(glitch.delayMs)
                val move = tween<Float>(dice.nextInt(STEP_MOVE_MIN_MS, STEP_MOVE_MAX_MS + 1), easing = LinearEasing)
                when (glitch.step) {
                    CrackgateStep.BAR_HEIGHT -> barTop.animateTo(dice.nextInt(42, 69).toFloat(), move)
                    CrackgateStep.BAR_LENGTH -> endGap.animateTo(dice.nextInt(0, 13).toFloat(), move)
                    CrackgateStep.HAIRLINE_BRIGHT -> brightFrom.animateTo(dice.nextInt(44, 76).toFloat(), move)
                    CrackgateStep.ICON_STRIP -> Unit // there are no icons to move over other apps
                }
            }
        }
        // Reduce Motion turned on while it is up: no more fade and no more changes. Elements stay where they are; only a fade in progress ends at once.
        snapshotFlow { reduce.value }.first { it }
        fadeJob.cancelAndJoin(); stepsJob.cancelAndJoin()
        fade.snapTo(1f)
    }
    Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }) {
        drawHairline(brightFrom.value)
        when (look.kind) {
            CrackgateKind.BAR_CYAN -> {
                val x0 = size.width / 2f + HINGE_GAP_DP.dp.toPx()
                val x1 = size.width * (1f - endGap.value / 100f)
                val y = size.height * barTop.value / 100f
                rotate(look.tiltDegrees, Offset(x0, y)) { drawBar(x0, y, x1 - x0, size.height * look.barHeightPercent / 100f) }
            }
            CrackgateKind.BAR_CYAN_LEFT -> {
                val x1 = size.width / 2f - HINGE_GAP_DP.dp.toPx()
                val x0 = size.width * endGap.value / 100f
                val y = size.height * barTop.value / 100f
                rotate(-look.tiltDegrees, Offset(x1, y)) { drawBar(x0, y, x1 - x0, size.height * look.barHeightPercent / 100f) }
            }
            CrackgateKind.LINE_GREEN -> drawGreenLine(size.height * barTop.value / 100f, size.height * look.barHeightPercent / 100f)
            CrackgateKind.HAIRLINE_ONLY -> Unit
        }
    }
}

/** The thin line down the hinge: white down to [brightFromPercent] of the height, then a pale mint line with a faint glow to the bottom. */
private fun DrawScope.drawHairline(brightFromPercent: Float) {
    val x = size.width / 2f
    val split = size.height * brightFromPercent / 100f
    val thin = HAIRLINE_WIDTH_DP.dp.toPx()
    rotate(HAIRLINE_TILT_DEGREES, Offset(x, 0f)) {
        drawRect(CrackgateColors.HairUpper.copy(alpha = .9f), Offset(x - thin / 2f, 0f), Size(thin, split))
        val lowerWidth = 2.dp.toPx()
        drawRect(CrackgateColors.HairLowerGlow.copy(alpha = .16f), Offset(x - lowerWidth * 2.5f, split), Size(lowerWidth * 5f, size.height - split))
        drawRect(CrackgateColors.HairLowerGlow.copy(alpha = .3f), Offset(x - lowerWidth * 1.5f, split), Size(lowerWidth * 3f, size.height - split))
        drawRect(CrackgateColors.HairLower, Offset(x - lowerWidth / 2f, split), Size(lowerWidth, size.height - split))
    }
}

/** The bright cyan-white bar with a soft blue glow made of a few wider, fainter copies behind it. */
private fun DrawScope.drawBar(left: Float, top: Float, width: Float, height: Float) {
    if (width <= 0f || height <= 0f) return
    for (i in 4 downTo 1) {
        val grow = i * 3.5.dp.toPx()
        drawRoundRect(CrackgateColors.BarGlow.copy(alpha = .13f), Offset(left - grow, top - grow), Size(width + grow * 2f, height + grow * 2f), CornerRadius(grow + 2.dp.toPx()))
    }
    drawRoundRect(Brush.verticalGradient(colorStops = BAR_GRADIENT, startY = top, endY = top + height), Offset(left, top), Size(width, height), CornerRadius(2.dp.toPx()))
}

/** A thin green line across the whole width. */
private fun DrawScope.drawGreenLine(top: Float, height: Float) {
    val thick = height.coerceAtLeast(1.dp.toPx())
    for (i in 3 downTo 1) {
        val grow = i * 3.dp.toPx()
        drawRoundRect(CrackgateColors.GreenGlow.copy(alpha = .16f), Offset(0f, top - grow), Size(size.width, thick + grow * 2f), CornerRadius(grow))
    }
    drawRect(CrackgateColors.GreenLine, Offset(0f, top), Size(size.width, thick))
}
