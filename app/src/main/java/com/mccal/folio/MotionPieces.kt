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
 * A count that rolls: a new number slides in from below when it goes up and from above when it goes down, while the old
 * one leaves the other way, on [FolioMotion.Menu]. The first number simply appears. With the motion pass off or Reduce
 * Motion on it is plain text.
 */
@Composable
internal fun RollingCount(count: Int, text: (Int) -> String, color: Color, fontSize: TextUnit, style: TextStyle, modifier: Modifier = Modifier) {
    fun plain(value: Int) = @Composable { Text(text(value), color = color, fontWeight = FontWeight.SemiBold, fontSize = fontSize, maxLines = 1, softWrap = false, style = style, modifier = modifier) }
    if (!FolioMotion.v2 || LocalReduceMotion.current) { plain(count)(); return }
    AnimatedContent(
        targetState = count,
        transitionSpec = {
            val up = targetState > initialState
            val spring = FolioMotion.spring<androidx.compose.ui.unit.IntOffset>(FolioMotion.Menu)
            val fade = androidx.compose.animation.core.tween<Float>(120)
            (slideInVertically(spring) { h -> if (up) h else -h } + fadeIn(fade)) togetherWith
                (slideOutVertically(spring) { h -> if (up) -h else h } + fadeOut(fade)) using SizeTransform(clip = true)
        },
        label = "rolling count",
    ) { value -> plain(value)() }
}

/**
 * True for a moment after an install or update finishes: [busy] has just gone from true to false and the package is on
 * the phone. It is the cue to show [InstallDonePop] before the row settles back to its button. Never true with the
 * motion pass off or Reduce Motion on, and never after a failure (the package has to be there).
 */
@Composable
internal fun rememberJustInstalled(busy: Boolean, installed: Boolean): Boolean {
    val reduce = LocalReduceMotion.current
    val installedNow = rememberUpdatedState(installed)
    var wasBusy by remember { mutableStateOf(busy) }
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(busy) {
        val finished = wasBusy && !busy
        wasBusy = busy
        if (finished && FolioMotion.v2 && !reduce) {
            // The package list updates a beat after the work ends; look again before calling it a success.
            delay(250)
            if (installedNow.value) { show = true; delay(900); show = false }
        }
    }
    return show
}

/** The ring's last beat: a check pops in on [FolioMotion.Bounce], with a small overshoot, in the size the ring had. */
@Composable
internal fun InstallDonePop() {
    val scale = remember { Animatable(.2f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, FolioMotion.spring(FolioMotion.Bounce)) }
    Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Rounded.Check, contentDescription = null, tint = FolioColors.Green,
            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value; alpha = scale.value.coerceIn(0f, 1f) },
        )
    }
}
