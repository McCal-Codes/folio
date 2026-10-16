package com.mccal.folio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The 0.6.9 motion pieces (docs/standards/motion-references.md): small moves with a little personality, drawn from the
 * Mockup Lab's "Motion ideas". Each call site only uses them while [FolioMotion.v2] is on, so a phone outside the beta
 * sees what it saw before. Every piece honors Reduce Motion by jumping to the end.
 */

/**
 * Where a highlight sits as a fractional index: it glides to the selected item on [FolioMotion.Sheet] instead of
 * jumping, and follows a new tap from wherever it is (the spring carries its speed). Read it in a draw or layer lambda
 * so the glide never recomposes.
 */
@Composable
internal fun rememberGlide(index: Int): State<Float> {
    val reduce = LocalReduceMotion.current
    val position = remember { Animatable(index.toFloat()) }
    LaunchedEffect(index, reduce) {
        if (reduce) position.snapTo(index.toFloat())
        else position.animateTo(index.toFloat(), FolioMotion.spring(FolioMotion.Sheet))
    }
    return position.asState()
}

/**
 * A count that rolls one digit at a time, like a counter: only the digits that changed move, each sliding up when the
 * count goes up and down when it goes down, on [FolioMotion.Settle]. The first number simply appears. A text that is not
 * all digits (99+) rolls as a whole. With the motion pass off or Reduce Motion on it is plain text.
 */
@Composable
internal fun RollingCount(count: Int, text: (Int) -> String, color: Color, fontSize: TextUnit, style: TextStyle, modifier: Modifier = Modifier) {
    val draw: @Composable (String) -> Unit = { value ->
        Text(value, color = color, fontWeight = FontWeight.SemiBold, fontSize = fontSize, maxLines = 1, softWrap = false, style = style, modifier = modifier)
    }
    val shown = text(count)
    if (!FolioMotion.v2 || LocalReduceMotion.current) { draw(shown); return }
    val memory = remember { RollMemory(count) }
    if (count != memory.count) { memory.up = count > memory.count; memory.count = count }
    val up = memory.up
    if (rollsPerDigit(shown)) {
        androidx.compose.foundation.layout.Row {
            // Keyed from the right, so the ones digit stays the ones digit when a tens digit appears.
            shown.forEachIndexed { index, char ->
                androidx.compose.runtime.key(shown.length - index) { RollingSlot(char.toString(), up, draw) }
            }
        }
    } else {
        RollingSlot(shown, up, draw)
    }
}

private class RollMemory(var count: Int, var up: Boolean = true)

/** Per-digit rolling needs a text of digits only; "99+" and the like roll as one piece. */
internal fun rollsPerDigit(text: String) = text.isNotEmpty() && text.all { it.isDigit() }

/** Which digits of [new] differ from [old], lined up from the right (the ones place first). Only these move. */
internal fun changedDigits(old: String, new: String): List<Boolean> =
    new.indices.map { i -> old.getOrNull(old.length - new.length + i) != new[i] }

@Composable
private fun RollingSlot(value: String, up: Boolean, content: @Composable (String) -> Unit) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val spring = FolioMotion.spring<androidx.compose.ui.unit.IntOffset>(FolioMotion.Settle)
            val fade = androidx.compose.animation.core.tween<Float>(120)
            (slideInVertically(spring) { h -> if (up) h else -h } + fadeIn(fade)) togetherWith
                (slideOutVertically(spring) { h -> if (up) -h else h } + fadeOut(fade)) using SizeTransform(clip = true)
        },
        label = "rolling digit",
    ) { v -> content(v) }
}

/**
 * True for a moment after an install or update finishes: [busy] has just gone from true to false and the package is on
 * the phone. It is the cue to show [InstallDonePop] before the row settles back to its button (under Reduce Motion the
 * check fades in instead of popping). Never true with the motion pass off, and never after a failure (the package has to be there).
 */
@Composable
internal fun rememberJustInstalled(busy: Boolean, installed: Boolean): Boolean {
    val installedNow = rememberUpdatedState(installed)
    var wasBusy by remember { mutableStateOf(busy) }
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(busy) {
        val finished = wasBusy && !busy
        wasBusy = busy
        if (finished && FolioMotion.v2) {
            // The package list updates a beat after the work ends; look again before calling it a success.
            delay(250)
            if (installedNow.value) { show = true; delay(900); show = false }
        }
    }
    return show
}

/**
 * The ring's last beat: a check pops in on [FolioMotion.Bounce], with a small overshoot, in the size the ring had. Under
 * Reduce Motion it fades in at full size instead of growing.
 */
@Composable
internal fun InstallDonePop() {
    val reduce = LocalReduceMotion.current
    val scale = remember { Animatable(if (reduce) 1f else .2f) }
    val fade = rememberReducedFade()
    LaunchedEffect(Unit) { scale.animateTo(1f, FolioMotion.spring(FolioMotion.Bounce)) }
    Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Rounded.Check, contentDescription = null, tint = FolioColors.Green,
            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value; alpha = scale.value.coerceIn(0f, 1f) * fade.value },
        )
    }
}

/** A badge pops in only when a count first appears on an icon that had none, not on every redraw or when the number changes. */
internal fun badgeAppeared(previous: Int, now: Int) = previous <= 0 && now > 0

/**
 * True for the composition in which [count] went from none to some on a tile that was already on screen. A tile that is
 * only being drawn (a page swiped into view) starts with its count already there and never pops. Always false with the
 * motion pass off or under Reduce Motion, so the badge simply appears.
 */
@Composable
internal fun rememberBadgeAppeared(count: Int): Boolean {
    val previous = remember { intArrayOf(count) }
    val appeared = badgeAppeared(previous[0], count) && FolioMotion.v2 && !LocalReduceMotion.current
    androidx.compose.runtime.SideEffect { previous[0] = count }
    return appeared
}

/** The badge's scale on arrival: from [BADGE_POP_FROM] to 1 on [FolioMotion.Bounce], which overshoots under 5%. Stays at 1 when [pop] is false. */
@Composable
internal fun rememberBadgePop(pop: Boolean): State<Float> {
    val scale = remember { Animatable(if (pop) BADGE_POP_FROM else 1f) }
    LaunchedEffect(Unit) { if (scale.value < 1f) scale.animateTo(1f, FolioMotion.spring(FolioMotion.Bounce)) }
    return scale.asState()
}

internal const val BADGE_POP_FROM = .6f
