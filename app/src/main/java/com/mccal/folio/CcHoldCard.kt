package com.mccal.folio

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** What a press and hold on a Control Center control opens a card for (see [CcHoldCard]). */
internal enum class CcHold { BRIGHTNESS, VOLUME }

/** A slider position as the number the card shows beside it: 0 to 1 as 0% to 100%, held inside that range. */
internal fun ccPercentLabel(value: Float): String = "${(value.coerceIn(0f, 1f) * 100).roundToInt()}%"

/**
 * The card a press and hold on a control opens (as iOS's Haptic Touch): the control bigger, with what its short press leaves out. It
 * opens with one soft scale and fade, and with a fade alone under Reduce Motion; nothing pulses or flashes. A tap outside, or Back, closes it.
 */
@Composable
internal fun CcHoldCard(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val reduce = LocalReduceMotion.current
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(if (entered) 1f else 0f, if (reduce) androidx.compose.animation.core.snap() else FolioMotion.spring(FolioMotion.Quick), label = "hold card")
    FolioDialog(onDismiss) {
        Column(Modifier.width(300.dp).heightIn(max = 560.dp)
            .graphicsLayer { alpha = progress; val s = if (reduce) 1f else .94f + .06f * progress; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(FolioRadius.PANEL.dp)).background(FolioColors.SecondaryBackground.copy(alpha = .97f))
            .padding(FolioSpace.LARGE.dp), verticalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
            Text(title, color = Color.White, fontSize = FolioType.CALLOUT.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

/**
 * A wide slider for a hold card: the fill follows the finger along the bar, a tap sets the spot it lands on, and the number sits at the end.
 * TalkBack adjusts it like the tall sliders on the panel. [onStart] runs when a finger lands (the brightness one asks for the settings
 * permission there) and [onHold] says whether a finger is down, so the real level is not read back under it.
 */
@Composable
internal fun CcHoldSlider(icon: ImageVector, label: String, value: Float, onStart: () -> Unit = {}, onHold: (Boolean) -> Unit = {}, onValue: (Float) -> Unit) {
    val reduce = LocalReduceMotion.current
    val shown by animateFloatAsState(value.coerceIn(0f, 1f), if (reduce) androidx.compose.animation.core.snap() else FolioMotion.spring(FolioMotion.Snap), label = label)
    Column(verticalArrangement = Arrangement.spacedBy(FolioSpace.TINY.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold)
            Text(ccPercentLabel(value), color = FolioGlass.secondary, fontSize = FolioType.FOOTNOTE.sp)
        }
        Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(FolioRadius.GROUPED_CARD.dp)).background(Color.White.copy(alpha = .14f))
            .pointerInput(Unit) { detectTapGestures { offset -> onStart(); onValue((offset.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragStart = { onStart(); onHold(true) }, onDragEnd = { onHold(false) }, onDragCancel = { onHold(false) }) { change, _ ->
                    change.consume(); onValue((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { target -> onStart(); onValue(target.coerceIn(0f, 1f)); true }
            }) {
            Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(shown).background(Color.White))
            Icon(icon, null, tint = if (shown > .1f) FolioColors.MenuSurface else Color.White, modifier = Modifier.align(Alignment.CenterStart).padding(start = FolioSpace.COMFY.dp).size(24.dp))
        }
    }
}

/** A text row on a hold card that does something when tapped (Open volume panel). */
@Composable
internal fun CcHoldAction(text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.CARD.dp)).background(Color.White.copy(alpha = .12f)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
        .padding(vertical = FolioSpace.MEDIUM.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.SemiBold)
    }
}
