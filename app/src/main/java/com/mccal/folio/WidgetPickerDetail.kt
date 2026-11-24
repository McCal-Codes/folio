package com.mccal.folio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp

/**
 * [content] laid out at [real], the size a widget has on Home, then drawn [scale] times as big (or small), so a preview shows what Home will show
 * rather than a thumbnail laid out for a different box. This takes up the scaled size.
 */
@Composable
internal fun ScaledPreview(real: WidgetContentSize, scale: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    Layout(content, modifier) { measurables, _ ->
        val width = with(density) { real.widthDp.dp.roundToPx() }
        val height = with(density) { real.heightDp.dp.roundToPx() }
        val placeables = measurables.map { it.measure(Constraints.fixed(width, height)) }
        layout((width * scale).roundToInt(), (height * scale).roundToInt()) {
            placeables.forEach { it.placeWithLayer(0, 0) { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) } }
        }
    }
}

/** The most sizes the picker's size switch offers at once. */
private const val MAX_SIZE_OPTIONS = 5

/**
 * The sizes a widget offers in the picker's size switch: its own preferred, smallest and largest, and Apple's three (Small 2 × 2, Medium 4 × 2,
 * Large 4 × 4) where the widget accepts them, smallest first. A widget that cannot be resized offers only its own size, so the switch is not shown.
 * [fallback] is the size of a widget with no limits of its own (Folio's Clock and Date).
 */
internal fun widgetSizeOptions(constraints: WidgetSpanConstraints?, fallback: WidgetSpan): List<WidgetSpan> {
    if (constraints == null) return listOf(fallback)
    val wanted = listOf(constraints.preferred, constraints.minimum, constraints.maximum, WidgetSpan(2, 2), WidgetSpan(4, 2), WidgetSpan(4, 4))
    return wanted.filter { constraints.accepts(it) }.distinct().take(MAX_SIZE_OPTIONS)
        .sortedWith(compareBy({ it.width * it.height }, { it.width }))
}

/** The name a size goes by: the same three words the gallery cards use. */
internal fun widgetSizeNameRes(span: WidgetSpan): Int = when {
    span.width <= 2 && span.height <= 2 -> R.string.small
    span.height <= 2 -> R.string.medium
    else -> R.string.large
}

/** The top-left cell a [span] may have on a page of [rows] rows, nearest to ([col], [row]): the widget never leaves the page. */
internal fun mapClampAnchor(col: Int, row: Int, span: WidgetSpan, rows: Int): Pair<Int, Int> =
    col.coerceIn(0, (GRID_COLUMNS - span.width).coerceAtLeast(0)) to row.coerceIn(0, (rows - span.height).coerceAtLeast(0))

/** The size a widget anchored at ([col], [row]) takes when its corner is dragged to cell ([toCol], [toRow]): the nearest one it accepts. */
internal fun mapResize(constraints: WidgetSpanConstraints, col: Int, row: Int, toCol: Int, toRow: Int, rows: Int): WidgetSpan {
    val maxWidth = maxOf(constraints.minimum.width, minOf(constraints.maximum.width, GRID_COLUMNS - col))
    val maxHeight = maxOf(constraints.minimum.height, minOf(constraints.maximum.height, rows - row))
    return WidgetSpan((toCol - col + 1).coerceIn(constraints.minimum.width, maxWidth),
        (toRow - row + 1).coerceIn(constraints.minimum.height, maxHeight))
}

/**
 * What the picker's second step needs to know about Home, so it can draw a page and say whether a widget fits: read from the live layout each
 * time it is asked, never copied. Indexes are Home cell indexes ([homeCellIndex]); [taken] is a page's local cells that apps, folders or other
 * widgets cover.
 */
internal class PickerPlacement(
    val firstPage: Int,
    val lastPage: Int,
    /** Where this widget goes unless the person moves it: the cell the picker was opened from, or the first one. */
    val startIndex: Int,
    val rows: (page: Int) -> Int,
    val taken: (page: Int) -> Set<Int>,
    val fits: (index: Int, span: WidgetSpan) -> Boolean,
    /** The first free cell for [span] on [page] alone, or null. */
    val freeOnPage: (page: Int, span: WidgetSpan) -> Int?,
    /** The first free cell for [span], looking at [page] first, then the others; null when no page has room. */
    val firstFree: (span: WidgetSpan, page: Int) -> Int?,
    /** How big the widget really is on Home at [span] when its top row is [row], so the preview can be drawn at that size and scaled up. */
    val contentSize: (span: WidgetSpan, row: Int) -> WidgetContentSize,
)

/** Where the picker's page map draws: one cell's width, the gaps, and the row heights (the two top rows are half-height, as on Home). */
private data class MapGeometry(val cellWidth: Float, val gap: Float, val rowHeight: Float, val rows: Int) {
    private val shortRow get() = rowHeight * .6f
    fun heightOf(row: Int) = if (row < 2) shortRow else rowHeight
    fun left(col: Int) = col * (cellWidth + gap)
    fun top(row: Int): Float { var y = 0f; for (r in 0 until row) y += heightOf(r) + gap; return y }
    val totalHeight get() = top(rows) - gap
    fun spanWidth(cols: Int) = cols * cellWidth + (cols - 1) * gap
    fun spanHeight(row: Int, count: Int) = top(row + count) - top(row) - gap
    fun colAt(x: Float) = (x / (cellWidth + gap)).toInt().coerceIn(0, GRID_COLUMNS - 1)
    fun rowAt(y: Float): Int { var r = 0; while (r + 1 < rows && top(r + 1) <= y) r++; return r }
}

private data class HeldWidget(val col: Int, val row: Int, val span: WidgetSpan, val fits: Boolean)

/**
 * One Home page, drawn small: grey for what is already there, blue for the widget. Touch the blue block and drag to move it (the cells under it
 * snap to the finger), drag the white corner to resize it to the nearest size it supports, or tap a cell to put it there. A drop that would cover
 * a taken cell turns red and, on release, the widget goes back; nothing is moved out of its way. Where a drag is not possible, the four move
 * actions on this view do the same one cell at a time.
 */
@Composable
internal fun WidgetPageMap(
    rows: Int,
    taken: Set<Int>,
    anchor: Int,
    span: WidgetSpan,
    constraints: WidgetSpanConstraints?,
    fits: (local: Int, span: WidgetSpan) -> Boolean,
    onCommit: (local: Int, span: WidgetSpan) -> Unit,
    onRefused: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val accent = LocalAccent.current
    val resizable = constraints != null && constraints.minimum != constraints.maximum
    val left = stringResource(R.string.widget_map_move_left)
    val right = stringResource(R.string.widget_map_move_right)
    val up = stringResource(R.string.widget_map_move_up)
    val down = stringResource(R.string.widget_map_move_down)
    // Where the widget is now, read after the description, so a screen reader hears the result of a move action and not only the actions.
    val position = stringResource(R.string.widget_map_state, anchor % GRID_COLUMNS + 1, anchor / GRID_COLUMNS + 1, span.width, span.height)
    val latestFits by rememberUpdatedState(fits)
    val latestCommit by rememberUpdatedState(onCommit)
    val latestRefused by rememberUpdatedState(onRefused)
    val latestAnchor by rememberUpdatedState(anchor)
    val latestSpan by rememberUpdatedState(span)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = with(density) { 6.dp.toPx() }
        // The resize handle sits on the widget's corner, half outside the cells; this margin keeps it inside the area that takes touches.
        val pad = with(density) { 20.dp.toPx() }
        val padOffset = Offset(pad, pad)
        val geometry = MapGeometry((with(density) { maxWidth.toPx() } - 2 * pad - gap * (GRID_COLUMNS - 1)) / GRID_COLUMNS, gap,
            with(density) { 40.dp.toPx() }, rows)
        val handleRadius = with(density) { 10.dp.toPx() }
        val reach = with(density) { 28.dp.toPx() }
        var held by remember { mutableStateOf<HeldWidget?>(null) }
        fun nudge(dCol: Int, dRow: Int): Boolean {
            val (c, r) = mapClampAnchor(anchor % GRID_COLUMNS + dCol, anchor / GRID_COLUMNS + dRow, span, rows)
            if (fits(r * GRID_COLUMNS + c, span)) onCommit(r * GRID_COLUMNS + c, span) else onRefused()
            return true
        }
        val shown = held ?: HeldWidget(anchor % GRID_COLUMNS, anchor / GRID_COLUMNS, span, true)
        val cellColor = Color.White.copy(alpha = .10f)
        val takenColor = Color.White.copy(alpha = .26f)
        val good = accent.fill.copy(alpha = .86f)
        val bad = FolioColors.InvalidDrop.copy(alpha = .36f)
        Canvas(Modifier.fillMaxWidth().height(with(density) { (geometry.totalHeight + 2 * pad).toDp() }).testTag("widget-detail-map")
            .semantics {
                contentDescription = description
                stateDescription = position
                customActions = listOf(
                    CustomAccessibilityAction(left) { nudge(-1, 0) }, CustomAccessibilityAction(right) { nudge(1, 0) },
                    CustomAccessibilityAction(up) { nudge(0, -1) }, CustomAccessibilityAction(down) { nudge(0, 1) })
            }
            .pointerInput(geometry, constraints) {
                var resizing = false
                var grabCol = 0
                var grabRow = 0
                detectDragGestures(
                    onDragStart = { raw ->
                        val point = raw - padOffset
                        val col0 = latestAnchor % GRID_COLUMNS
                        val row0 = latestAnchor / GRID_COLUMNS
                        val corner = Offset(geometry.left(col0) + geometry.spanWidth(latestSpan.width),
                            geometry.top(row0) + geometry.spanHeight(row0, latestSpan.height))
                        resizing = resizable && (point - corner).getDistance() <= reach
                        val col = geometry.colAt(point.x)
                        val row = geometry.rowAt(point.y)
                        val inside = col in col0 until col0 + latestSpan.width && row in row0 until row0 + latestSpan.height
                        grabCol = if (inside && !resizing) col - col0 else 0
                        grabRow = if (inside && !resizing) row - row0 else 0
                        held = HeldWidget(col0, row0, latestSpan, true)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val point = change.position - padOffset
                        val col = geometry.colAt(point.x)
                        val row = geometry.rowAt(point.y)
                        val current = held ?: return@detectDragGestures
                        held = if (resizing && constraints != null) {
                            val next = mapResize(constraints, current.col, current.row, col, row, rows)
                            HeldWidget(current.col, current.row, next, latestFits(current.row * GRID_COLUMNS + current.col, next))
                        } else {
                            val (c, r) = mapClampAnchor(col - grabCol, row - grabRow, current.span, rows)
                            HeldWidget(c, r, current.span, latestFits(r * GRID_COLUMNS + c, current.span))
                        }
                    },
                    onDragEnd = {
                        val end = held
                        held = null
                        if (end != null && end.fits) latestCommit(end.row * GRID_COLUMNS + end.col, end.span)
                        else if (end != null) latestRefused()
                    },
                    onDragCancel = { held = null })
            }
            .pointerInput(geometry) {
                detectTapGestures { raw ->
                    val point = raw - padOffset
                    val (c, r) = mapClampAnchor(geometry.colAt(point.x), geometry.rowAt(point.y), latestSpan, rows)
                    if (latestFits(r * GRID_COLUMNS + c, latestSpan)) latestCommit(r * GRID_COLUMNS + c, latestSpan) else latestRefused()
                }
            }) {
            translate(pad, pad) {
            val radius = CornerRadius(6.dp.toPx())
            for (r in 0 until rows) for (c in 0 until GRID_COLUMNS) {
                drawRoundRect(if (r * GRID_COLUMNS + c in taken) takenColor else cellColor,
                    Offset(geometry.left(c), geometry.top(r)), Size(geometry.cellWidth, geometry.heightOf(r)), radius)
            }
            val topLeft = Offset(geometry.left(shown.col), geometry.top(shown.row))
            val size = Size(geometry.spanWidth(shown.span.width), geometry.spanHeight(shown.row, shown.span.height))
            drawRoundRect(if (shown.fits) good else bad, topLeft, size, CornerRadius(8.dp.toPx()))
            drawRoundRect(if (shown.fits) accent.ink else FolioColors.InvalidDrop, topLeft, size, CornerRadius(8.dp.toPx()),
                style = Stroke(2.dp.toPx()))
            if (resizable) {
                val corner = Offset(topLeft.x + size.width, topLeft.y + size.height)
                drawCircle(Color.White, handleRadius, corner)
                drawCircle(if (shown.fits) accent.ink else FolioColors.InvalidDrop, handleRadius, corner, style = Stroke(2.dp.toPx()))
            }
            }
        }
    }
}

/**
 * The picker's second step: one widget, large, with its description, a size switch, and (when it is going to a Home page) the page map where it
 * can be moved and resized before it is added. [onAdd] gets the Home cell index the map ended on and the chosen size; both are null/the default
 * when there is no map (the Today View, a stack), which add at the widget's own size as before.
 */
@Composable
internal fun WidgetDetail(
    title: String,
    subtitle: String,
    description: String,
    constraints: WidgetSpanConstraints?,
    defaultSpan: WidgetSpan,
    placement: PickerPlacement?,
    onBack: () -> Unit,
    onAdd: (index: Int?, span: WidgetSpan) -> Unit,
    /** Choosing the spot on Home itself, full size and on the real page, at the size picked here; shown only with a page map. */
    onPlace: ((span: WidgetSpan) -> Unit)? = null,
    preview: @Composable (WidgetSpan) -> Unit,
) {
    val options = remember(constraints, defaultSpan) { widgetSizeOptions(constraints, defaultSpan) }
    var span by remember { mutableStateOf(constraints?.preferred ?: defaultSpan) }
    fun settle(span: WidgetSpan, from: Int): Int = placement?.let { p ->
        if (p.fits(from, span)) from else p.firstFree(span, homeCellPage(from)) ?: from
    } ?: from
    val startedAt = remember { placement?.let { settle(span, it.startIndex) } ?: 0 }
    var index by remember { mutableIntStateOf(startedAt) }
    var refused by remember { mutableStateOf(false) }
    val ink = Color.White
    val secondary = FolioColors.SecondaryLabel
    val page = homeCellPage(index)
    val fitsNow = placement == null || placement.fits(index, span)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.folioSafeTop).navigationBarsPadding().padding(horizontal = 18.dp)
        .testTag("widget-detail"), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(top = FolioSpace.SNUG.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(FolioTouch.MIN.dp).clickable(onClickLabel = stringResource(R.string.back), role = Role.Button, onClick = onBack)
                .testTag("widget-detail-back"), contentAlignment = Alignment.Center) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = ink, modifier = Modifier.size(20.dp))
                }
            }
            Text(title, color = ink, fontSize = FolioType.TITLE3.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = FolioSpace.MEDIUM.dp))
        }
        Column(Modifier.weight(1f).widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
            Text(subtitle, color = secondary, fontSize = FolioType.SUBHEAD.sp, modifier = Modifier.padding(top = FolioSpace.SMALL.dp))
            if (description.isNotBlank()) Text(description, color = ink, fontSize = FolioType.CALLOUT.sp, modifier = Modifier.testTag("widget-detail-description"))
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val real = placement?.contentSize?.invoke(span, homeCellLocal(index) / GRID_COLUMNS)
                if (real != null) {
                    // Drawn at the size it has on Home and scaled up, so what you see here is what Home will show (a Suggestions card has room for
                    // four apps at 2 x 2 only when it is that size), not a thumbnail laid out for a different box.
                    // The page map is the part that is used, so at a large font the preview gives it room (A11Y-12): 240 dp tall at 100%, 120 dp at 200%.
                    val scale = minOf(maxWidth.value / real.widthDp, 240f / LocalDensity.current.fontScale.coerceAtLeast(1f) / real.heightDp, 2.4f)
                    ScaledPreview(real, scale, Modifier.clip(RoundedCornerShape((FolioRadius.PANEL * scale).dp)).testTag("widget-detail-preview")) { preview(span) }
                } else {
                    val ratio = (span.width.toFloat() / span.height).coerceIn(.6f, 2.4f)
                    val previewWidth = minOf(maxWidth, 168.dp * ratio)
                    Box(Modifier.width(previewWidth).aspectRatio(ratio).clip(RoundedCornerShape(FolioRadius.NOTIFICATION.dp)).testTag("widget-detail-preview")) { preview(span) }
                }
            }
            if (options.size > 1) {
                Text(stringResource(R.string.widget_detail_size), color = secondary, fontSize = FolioType.GROUP_LABEL.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                    options.forEach { option ->
                        val on = option == span
                        Column(Modifier.widthIn(min = 76.dp).heightIn(min = FolioTouch.MIN.dp).clip(RoundedCornerShape(FolioRadius.ROW.dp))
                            .background(if (on) Color.White else Color.White.copy(alpha = .14f))
                            .selectable(on, role = Role.RadioButton, onClick = {
                                span = option; refused = false; index = settle(option, index)
                            }).padding(horizontal = FolioSpace.MEDIUM.dp, vertical = FolioSpace.SMALL.dp)
                            .testTag("widget-detail-size-${option.width}x${option.height}"),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(stringResource(widgetSizeNameRes(option)), color = if (on) Color.Black else ink, fontSize = FolioType.COMPACT.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("${option.width} × ${option.height}", color = if (on) Color.Black.copy(alpha = .7f) else secondary,
                                fontSize = FolioType.GROUP_LABEL.sp, maxLines = 1)
                        }
                    }
                }
            }
            if (placement != null) {
                val pageName = if (page < 0) stringResource(R.string.widget_map_leading_page) else stringResource(R.string.widget_map_page, page + 1)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pageName, color = ink, fontSize = FolioType.CALLOUT.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.widget_map_hint), color = secondary, fontSize = FolioType.GROUP_LABEL.sp)
                    }
                    val move = { target: Int ->
                        val local = homeCellLocal(index)
                        val same = homeCellIndex(target, local)
                        index = if (placement.fits(same, span)) same else placement.freeOnPage(target, span) ?: same
                        refused = false
                    }
                    Box(Modifier.size(FolioTouch.MIN.dp).then(if (page > placement.firstPage) Modifier.clickable(
                        onClickLabel = stringResource(R.string.widget_map_previous_page), role = Role.Button) { move(page - 1) } else Modifier),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ChevronLeft, stringResource(R.string.widget_map_previous_page),
                            tint = if (page > placement.firstPage) ink else ink.copy(alpha = .3f))
                    }
                    Box(Modifier.size(FolioTouch.MIN.dp).then(if (page < placement.lastPage) Modifier.clickable(
                        onClickLabel = stringResource(R.string.widget_map_next_page), role = Role.Button) { move(page + 1) } else Modifier),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ChevronRight, stringResource(R.string.widget_map_next_page),
                            tint = if (page < placement.lastPage) ink else ink.copy(alpha = .3f))
                    }
                }
                // The page the picker was opened for may have no room at this size; then the map starts on the first page that has, and says why.
                val requestedPage = homeCellPage(placement.startIndex)
                val skipped = homeCellPage(startedAt) != requestedPage && page == homeCellPage(startedAt)
                val sentence = when {
                    refused || (!fitsNow && placement.firstFree(span, page) != null) -> stringResource(R.string.widget_map_taken)
                    !fitsNow -> stringResource(R.string.widget_map_no_room)
                    skipped -> stringResource(R.string.widget_map_started_elsewhere,
                        if (requestedPage < 0) stringResource(R.string.widget_map_leading_page) else stringResource(R.string.widget_map_page, requestedPage + 1), pageName)
                    else -> stringResource(R.string.widget_map_fits, pageName)
                }
                // Two lines are always reserved so the map does not jump when a longer sentence replaces a short one.
                Text(sentence, color = if (fitsNow && !refused) secondary else FolioColors.InvalidDrop, fontSize = FolioType.FOOTNOTE.sp,
                    textAlign = TextAlign.Start, minLines = 2,
                    // Polite live region (A11Y-7): "That spot is taken" is announced when a drop is refused, without the person having to look for it.
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("widget-detail-fit"))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    WidgetPageMap(placement.rows(page), placement.taken(page), homeCellLocal(index), span, constraints,
                        fits = { local, candidate -> placement.fits(homeCellIndex(page, local), candidate) },
                        onCommit = { local, next -> span = next; index = homeCellIndex(page, local); refused = false },
                        onRefused = { refused = true },
                        description = stringResource(R.string.widget_map_description, pageName), modifier = Modifier.widthIn(max = 420.dp))
                }
            }
        }
        FolioButton(stringResource(R.string.widget_detail_add), { onAdd(if (placement != null) index else null, span) },
            Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(top = FolioSpace.MEDIUM.dp, bottom = if (placement != null && onPlace != null) 0.dp else FolioSpace.MEDIUM.dp),
            tag = "widget-detail-add", enabled = fitsNow)
        if (placement != null && onPlace != null) FolioButton(stringResource(R.string.widget_detail_place), { onPlace(span) },
            Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(bottom = FolioSpace.SMALL.dp), style = FolioButtonStyle.PLAIN, tag = "widget-detail-place")
    }
}
