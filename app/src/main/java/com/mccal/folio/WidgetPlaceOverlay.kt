package com.mccal.folio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Places a widget freely on Home: drag it anywhere, or nudge it by a quarter of a cell with the arrows. A dashed outline
 * shows the cells it will keep for itself, which apps can't use, and it turns red where those cells are taken.
 * [pitchX] and [pitchY] are the pixels in a cell's width and in one row at the widget's row.
 */
@Composable
internal fun WidgetPlaceOverlay(
    placement: WidgetPlacement, bounds: Rect, layout: HomeLayout, pitchX: Float, pitchY: Float,
    onApply: (Float, Float) -> Unit, onClose: () -> Unit,
) {
    var dx by remember(placement.slot) { mutableFloatStateOf(0f) }
    var dy by remember(placement.slot) { mutableFloatStateOf(0f) }
    val column = placement.column + placement.offsetX + dx / pitchX
    val row = placement.row + placement.offsetY + dy / pitchY
    val target = freePlacement(placement, column, row)
    val valid = target != null && placeWidget(layout, target).placement(placement.slot) == target
    val line = if (valid) Color.White else Color(0xFFFF6B6B)
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val widgetBox = Rect(bounds.left + dx, bounds.top + dy, bounds.right + dx, bounds.bottom + dy)
    // The cells it keeps: where the saved footprint is, moved by the whole cells the finger has crossed.
    val footLeft = bounds.left - placement.offsetX * pitchX + ((target?.column ?: placement.column) - placement.column) * pitchX
    val footTop = bounds.top - placement.offsetY * pitchY + ((target?.row ?: placement.row) - placement.row) * pitchY
    val clockName = stringResource(R.string.big_clock)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.offset { IntOffset(footLeft.roundToInt(), footTop.roundToInt()) }
            .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
            .drawBehind {
                drawRoundRect(line.copy(alpha = .8f), cornerRadius = CornerRadius(22.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
            })
        Box(Modifier.offset { IntOffset(widgetBox.left.roundToInt(), widgetBox.top.roundToInt()) }
            .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
            .border(BorderStroke(3.dp, line), RoundedCornerShape(FolioRadius.PANEL.dp))
            .testTag("widget-place-frame-${placement.slot}")
            .semantics { contentDescription = clockName }
            .pointerInput(placement.slot) {
                detectDragGestures { change, amount -> change.consume(); dx += amount.x; dy += amount.y }
            })
        val bottom = widgetBox.center.y < window.height / 2f
        Row(Modifier.align(if (bottom) Alignment.BottomCenter else Alignment.TopCenter).padding(FolioSpace.MEDIUM.dp)
            .background(Glass.copy(alpha = .96f), RoundedCornerShape(FolioRadius.GROUPED_CARD.dp)),
            verticalAlignment = Alignment.CenterVertically) {
            listOf(Triple(R.string.move_left, Icons.Rounded.ArrowBack, -1f to 0f), Triple(R.string.move_up, Icons.Rounded.ArrowUpward, 0f to -1f),
                Triple(R.string.move_down, Icons.Rounded.ArrowDownward, 0f to 1f), Triple(R.string.move_right, Icons.Rounded.ArrowForward, 1f to 0f))
                .forEach { (label, icon, step) ->
                    IconButton(onClick = { dx += step.first * pitchX / 4f; dy += step.second * pitchY / 4f }, Modifier.size(FolioTouch.MIN.dp)) {
                        Icon(icon, stringResource(label), tint = Ink)
                    }
                }
            TextButton(onClick = { dx = -placement.offsetX * pitchX; dy = -placement.offsetY * pitchY }) { Text(stringResource(R.string.snap_to_grid)) }
            TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) }
            TextButton(enabled = valid, onClick = { onApply(column, row) }) { Text(stringResource(R.string.apply)) }
        }
        if (!valid) Text(stringResource(R.string.taken_by_apps), color = Color.White,
            modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .65f), RoundedCornerShape(FolioRadius.CARD.dp))
                .padding(horizontal = FolioSpace.COMFY.dp, vertical = FolioSpace.COMPACT.dp))
    }
}
