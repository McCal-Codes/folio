package com.mccal.folio

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.exp

/** One loop of the loader, in milliseconds, before Settings › Animation Speed scales it. */
internal const val SNAP_LOOP_MS = 2000

/**
 * Where the two halves are at one moment of the loop. Lengths are in loader units: the open loader is about 176 wide and
 * 130 tall, and [snapPose] draws it at whatever size it is given.
 *
 * [gap] is the space between the halves, [scaleX] and [scaleY] the squash when they meet, [drop] the small nudge down on that
 * hit, and [ringAlpha] and [ringScale] the faint ring that spreads from the seam.
 */
internal data class SnapPose(val gap: Float, val scaleX: Float, val scaleY: Float, val drop: Float, val ringAlpha: Float, val ringScale: Float)

internal const val SNAP_OPEN_GAP = 40f
internal const val SNAP_SHUT_GAP = 4f
/** The ring is a hint, not a flash: it never gets brighter than this, and it fades in over two frames. */
internal const val SNAP_RING_PEAK = .3f

/**
 * The snap, as a function of how far through the loop it is (0 to 1; the loop is 60 frames). Hold open, speed up shut with
 * nothing slowing the hit, squash and a faint ring, hold shut, then spring open with a little overshoot and settle. The
 * spring is a damped cosine in the formula, not a Compose spring, because the loop is endless and timed. The end matches
 * the start, so repeating it has no seam. Pure, so a test can walk every frame.
 */
internal fun snapPose(phase: Float): SnapPose {
    val frame = (phase.coerceIn(0f, 1f)) * 60f
    val closeStart = 8f; val hit = 14f; val openStart = 32f; val openEnd = 46f
    fun unit(x: Float) = x.coerceIn(0f, 1f)
    val gap = when {
        frame < closeStart -> SNAP_OPEN_GAP
        frame < hit -> {
            val x = unit((frame - closeStart) / (hit - closeStart))
            SNAP_OPEN_GAP + (SNAP_SHUT_GAP - SNAP_OPEN_GAP) * x * x * x // accelerates into the hit
        }
        frame < openStart -> SNAP_SHUT_GAP
        else -> {
            val x = unit((frame - openStart) / (openEnd - openStart))
            SNAP_SHUT_GAP + (SNAP_OPEN_GAP - SNAP_SHUT_GAP) * (1f - exp(-7f * x) * cos(9f * x)) // overshoots, then settles
        }
    }
    val since = frame - hit
    val squash = if (since in 0f..12f) exp(-.45f * since) * cos(since * 1.1f) else 0f
    val ringT = unit(since / 12f)
    val ringAlpha = if (since in 0f..12f) SNAP_RING_PEAK * unit(since / 2f) * (1f - ringT) * (1f - ringT) else 0f
    return SnapPose(
        gap = gap, scaleX = 1f + .1f * squash, scaleY = 1f - .07f * squash,
        drop = 3f * squash.coerceAtLeast(0f), ringAlpha = ringAlpha, ringScale = 1f + ringT,
    )
}

/**
 * The loading mark: two halves like a Fold's hinge that snap shut with a click and spring open again, forever. It stands in
 * for an indeterminate spinner where the wait has no known length. Animation Speed scales the loop. With Reduce Motion on
 * the halves stay apart and only fade slowly, so the screen still says it is working without moving.
 *
 * [description] is read by TalkBack; leave it null where the surrounding row already says what is loading.
 */
@Composable
internal fun SnapLoader(modifier: Modifier = Modifier, size: Dp = 26.dp, color: Color = LocalAccent.current.ink, description: String? = null) {
    val still = LocalReduceMotion.current
    val speed = MotionSpeed.current.factor
    val transition = rememberInfiniteTransition(label = "snap loader")
    val phase = transition.animateFloat(0f, 1f, infiniteRepeatable(tween((SNAP_LOOP_MS / speed).toInt(), easing = LinearEasing)), label = "snap phase")
    val breathe = transition.animateFloat(.55f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "snap breathe")
    val soft = remember(color) { lerp(color, Color.White, .45f) }
    val a11y = Modifier.semantics {
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        description?.let { contentDescription = it }
    }
    Canvas(modifier.size(size).then(a11y)) {
        val u = this.size.minDimension / 190f
        val pose = if (still) SnapPose(gap = 20f, scaleX = 1f, scaleY = 1f, drop = 0f, ringAlpha = 0f, ringScale = 1f) else snapPose(phase.value)
        val fade = if (still) breathe.value else 1f
        val panelW = 68f * u; val panelH = 130f * u; val gap = pose.gap * u
        val cx = this.size.width / 2f; val cy = this.size.height / 2f
        if (pose.ringAlpha > 0f) {
            val rw = 150f * u * pose.ringScale; val rh = rw * .8f
            drawRoundRect(soft.copy(alpha = pose.ringAlpha), Offset(cx - rw / 2f, cy - rh / 2f), Size(rw, rh), CornerRadius(rh / 2f),
                style = Stroke(width = (3f * u).coerceAtLeast(1f)))
        }
        withTransform({
            translate(0f, pose.drop * u)
            scale(pose.scaleX, pose.scaleY, Offset(cx, cy))
        }) {
            val total = panelW * 2f + gap
            val left = cx - total / 2f; val top = cy - panelH / 2f
            val big = 20f * u; val small = 6f * u
            // Outer corners are round and the corners at the seam are tight, so the halves read as one device folding.
            fun half(x: Float, outerLeft: Boolean): Path = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(
                    x, top, x + panelW, top + panelH,
                    topLeftCornerRadius = CornerRadius(if (outerLeft) big else small), bottomLeftCornerRadius = CornerRadius(if (outerLeft) big else small),
                    topRightCornerRadius = CornerRadius(if (outerLeft) small else big), bottomRightCornerRadius = CornerRadius(if (outerLeft) small else big),
                ))
            }
            drawPath(half(left, true), color.copy(alpha = fade))
            drawPath(half(left + panelW + gap, false), soft.copy(alpha = fade))
        }
    }
}
