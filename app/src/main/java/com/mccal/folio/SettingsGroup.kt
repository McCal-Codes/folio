package com.mccal.folio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

private const val NOTE = "settings-note"

/**
 * An explanation inside a settings group. Like a footer in iOS Settings (and PreferenceLoader tweak panes), a note
 * that ends a group is drawn under the card instead of in it; a note between rows gets no dividers around it.
 */
@Composable
internal fun CardNote(text: String, modifier: Modifier = Modifier) {
    // Footnotes read as a block of small text, so they need more room between lines than a row label does.
    Text(text, modifier.layoutId(NOTE), style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Settings rows are laid out top to bottom; the card lays them out itself, so Column-only modifiers do nothing here. */
private object CardScope : ColumnScope {
    override fun Modifier.weight(weight: Float, fill: Boolean) = this
    override fun Modifier.align(alignment: Alignment.Horizontal) = this
    override fun Modifier.alignBy(alignmentLine: VerticalAlignmentLine) = this
    override fun Modifier.alignBy(alignmentLineBlock: (Measured) -> Int) = this
}

/**
 * The inset grouped card of iOS Settings: rows separated by hairlines, with trailing [CardNote]s as the footer.
 * Rows that draw nothing (a hidden option) take no divider.
 */
@Composable
internal fun GroupedCard(background: Color, divider: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    // Filled in by the layout pass and read while drawing, which always comes after it.
    val lines = remember { FloatArrayList() }
    val cardBottom = remember { floatArrayOf(0f) }
    Layout({ CardScope.content() }, modifier.drawBehind {
        val radius = 20.dp.toPx()
        if (cardBottom[0] > 0f) drawRoundRect(background, size = Size(size.width, cardBottom[0]), cornerRadius = CornerRadius(radius))
        val inset = 16.dp.toPx()
        val stroke = .5.dp.toPx().coerceAtLeast(1f)
        for (i in 0 until lines.size) drawLine(divider, Offset(inset, lines[i]), Offset(size.width, lines[i]), stroke)
    }) { measurables, constraints ->
        val side = 16.dp.roundToPx()
        val padV = 6.dp.roundToPx()
        val noteGap = 12.dp.roundToPx()
        val inner = Constraints(maxWidth = (constraints.maxWidth - 2 * side).coerceAtLeast(0))
        val placeables = measurables.map { it.measure(inner) }
        val isNote = measurables.map { it.layoutId == NOTE }
        val visible = placeables.map { it.height > 0 }
        // The footer: notes after the last visible row.
        val lastRow = placeables.indices.lastOrNull { visible[it] && !isNote[it] } ?: -1
        val ys = IntArray(placeables.size)
        lines.clear()
        var y = if (lastRow >= 0) padV else 0
        var previousRow = false
        for (i in 0..lastRow) {
            if (!visible[i]) continue
            // A note between rows: enough air above and below that it reads as a footnote, not a squeezed row.
            if (isNote[i]) { y += 6.dp.roundToPx(); ys[i] = y; y += placeables[i].height + 10.dp.roundToPx(); previousRow = false; continue }
            if (previousRow) lines.add(y.toFloat())
            ys[i] = y; y += placeables[i].height; previousRow = true
        }
        if (lastRow >= 0) y += padV
        cardBottom[0] = y.toFloat()
        var footer = false
        for (i in lastRow + 1 until placeables.size) {
            if (!visible[i]) continue
            y += if (footer) 8.dp.roundToPx() else if (lastRow >= 0) noteGap else 0
            ys[i] = y; y += placeables[i].height; footer = true
        }
        layout(constraints.maxWidth, y.coerceAtLeast(constraints.minHeight)) {
            placeables.forEachIndexed { i, p -> p.place(side, ys[i]) }
        }
    }
}

/** A small growable float list, so layout doesn't allocate a boxed list on every pass. */
private class FloatArrayList {
    private var data = FloatArray(8)
    var size = 0; private set
    operator fun get(i: Int) = data[i]
    fun clear() { size = 0 }
    fun add(v: Float) { if (size == data.size) data = data.copyOf(size * 2); data[size++] = v }
}

/** A blue action row, like a button cell in iOS Settings: the text lines up with the other rows' labels. */
@Composable
internal fun CardAction(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, destructive: Boolean = false, onClick: () -> Unit) {
    Text(label, color = Color(if (destructive) 0xFFFF453A else 0xFF0A84FF).copy(alpha = if (enabled) 1f else .4f), fontSize = 17.sp,
        modifier = modifier.heightIn(min = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick).wrapContentHeight(Alignment.CenterVertically))
}
