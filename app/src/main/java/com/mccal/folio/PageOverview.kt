package com.mccal.folio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.width
import androidx.compose.ui.window.DialogProperties

/** The most pages Home can have (the model stops adding here). */
private const val MAX_PAGES = 20

/** What the overview needs to draw one page: its cells, its widgets, whether it is hidden. Plain data, so a miniature costs nothing to build. */
private class PageSketch(val page: Int, val cells: List<String?>, val widgets: List<WidgetPlacement>, val hidden: Boolean)

/**
 * iOS's page editing screen: every Home page as a small picture in a grid, a check circle under each (ticked shows the page on Home,
 * unticked hides it, and its apps stay where they are), a long press and drag to move a page, and Done. A hidden page stays here,
 * dimmed, so it can be shown again.
 *
 * The pictures are drawn from the layout's cells and widgets (rounded tiles and blocks), never from a live render of Home, so ten pages
 * cost ten small canvases. TalkBack moves a page with the "Move page earlier / later" actions, which is the way to reorder without a
 * drag (A11Y-6); every change is announced in a polite live region.
 *
 * [currentPage] is the real page Home is on. [onSetHidden] answers whether the change was allowed (the last page showing cannot be hidden).
 */
@Composable
internal fun PageOverview(
    layout: HomeLayout,
    hidden: Set<Int>,
    appRows: Int,
    currentPage: Int,
    canUndo: Boolean,
    onMove: (from: Int, to: Int) -> Unit,
    onSetHidden: (page: Int, hidden: Boolean) -> Boolean,
    onAddPage: () -> Int,
    onRemoveLast: () -> Boolean,
    onUndo: () -> Unit,
    onOpen: (page: Int) -> Unit,
    onDone: () -> Unit,
) {
    RegisterOpenWindow(LauncherSheets)
    val solid = LocalSolidGlass.current
    val exit = rememberOverlayExit()
    val dismiss = { exit.close(onDone) }
    // What was just done, shown and read aloud under the title; Undo clears it, since the thing it described is undone.
    var status by remember { mutableStateOf("") }
    FolioDialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        FolioDialogWindow(dim = 0f)
        // Reduce Motion: the page fades in where it is instead of rising into place (A11Y-14). Nothing here pulses or flashes.
        val appear = rememberEntrance(FolioMotion.pick(old = 1f to 520f, new = FolioMotion.Sheet), scenario = PerfScenario.SHEET)
        val fade = rememberReducedFade()
        androidx.compose.runtime.CompositionLocalProvider(LocalOverlayExit provides exit) {
            MaterialTheme(colorScheme = FolioSheetColors, typography = MaterialTheme.typography) {
                val sketches = remember(layout, hidden, appRows) { sketchPages(layout, hidden) }
                val rows = remember(sketches, appRows) { sketches.maxOf { shownHomeRows(appRows, it.cells, it.widgets) } }
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) * fade.value * OverlayExits.alpha(exit.progress.value, fades = true, reduce = exit.reduce) }
                    .background(FolderBackdrop.scrim(solid))) {
                    FoldAvoidingBox(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = FolioSpace.XL.dp)) {
                        // Undo and Done sit at the window's own edges, so on a wide screen Done is still where a thumb expects it; only the
                        // title and the pages are held to a readable width in the middle.
                        Column(Modifier.fillMaxSize().testTag("page-overview"), horizontalAlignment = Alignment.CenterHorizontally) {
                            OverviewBar(canUndo, { onUndo(); status = "" }, dismiss)
                            Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                                OverviewTitle()
                                OverviewGrid(sketches, rows, currentPage, status, { status = it }, onMove, onSetHidden, onAddPage, onRemoveLast,
                                    onOpen = { page -> exit.close { onOpen(page) } }, solid)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewBar(canUndo: Boolean, onUndo: () -> Unit, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = FolioSpace.LARGE.dp), verticalAlignment = Alignment.CenterVertically) {
        if (canUndo) OverviewPill(stringResource(R.string.undo), Icons.AutoMirrored.Rounded.Undo, "page-overview-undo", onUndo)
        Spacer(Modifier.weight(1f))
        OverviewPill(stringResource(R.string.done), null, "page-overview-done", onDone, emphasized = true)
    }
}

@Composable
private fun OverviewTitle() {
    Column(Modifier.fillMaxWidth().padding(top = FolioSpace.SMALL.dp, bottom = FolioSpace.SMALL.dp)) {
        Text(stringResource(R.string.page_overview), color = Color.White, fontSize = FolioType.TITLE3.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.page_overview_hint), color = FolioGlass.secondary, fontSize = FolioType.FOOTNOTE.sp,
            modifier = Modifier.padding(top = FolioSpace.TINY.dp))
    }
}

/** A capsule button (iOS's Done and Undo pills). 48 dp tall so a finger has all of it (A11Y-1); the filled one is the single accent on the page. */
@Composable
private fun OverviewPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, tag: String, onClick: () -> Unit, emphasized: Boolean = false) {
    val accent = LocalAccent.current
    Row(Modifier.heightIn(min = FolioTouch.MIN.dp).clip(CircleShape)
        .background(if (emphasized) accent.fill else FolioGlass.raised)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = FolioSpace.XL.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        icon?.let { Icon(it, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(FolioSpace.SNUG.dp)) }
        Text(label, color = Color.White, fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OverviewGrid(sketches: List<PageSketch>, rows: Int, currentPage: Int, status: String, setStatus: (String) -> Unit, onMove: (Int, Int) -> Unit,
    onSetHidden: (Int, Boolean) -> Boolean, onAddPage: () -> Int, onRemoveLast: () -> Boolean, onOpen: (Int) -> Unit, solid: Boolean) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val count = sketches.size
    val bounds = remember { mutableStateMapOf<Int, Rect>() }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var target by remember { mutableStateOf<Int?>(null) }
    val keepOne = stringResource(R.string.page_overview_keep_one)
    val currentOnMove by rememberUpdatedState(onMove)

    fun targetAt(point: Offset): Int? = bounds.entries.firstOrNull { it.value.contains(point) }?.key
        ?: bounds.entries.minByOrNull { (it.value.center - point).getDistanceSquared() }?.key

    val scroll = rememberScrollState()
    var viewport by remember { mutableStateOf(Rect.Zero) }
    // With more pages than fit, a page carried near the top or bottom edge scrolls the grid (faster the nearer the edge) and stays under the finger.
    // It moves while the finger is still, so it runs on its own clock; the drag only sets where the page is.
    LaunchedEffect(dragging) {
        val page = dragging ?: return@LaunchedEffect
        val edge = with(density) { 72.dp.toPx() }
        val fastest = with(density) { 16.dp.toPx() }
        while (true) {
            withFrameNanos { }
            val center = (bounds[page]?.center ?: break) + dragOffset
            val next = targetAt(center)
            if (next != target) { target = next; haptic.perform(FolioHaptic.Step) }
            val toTop = center.y - viewport.top
            val toBottom = viewport.bottom - center.y
            val delta = when {
                toTop < edge -> -fastest * ((edge - toTop) / edge).coerceIn(0f, 1f)
                toBottom < edge -> fastest * ((edge - toBottom) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (delta != 0f) {
                val used = scroll.scrollBy(delta)
                // The page's place in the grid moved by what was scrolled; its picture follows so it stays under the finger.
                if (used != 0f) dragOffset += Offset(0f, used)
            }
        }
    }
    // After a keyboard move the focus goes with the page to its new place, so the next key press moves the same page again.
    val focusers = remember(count) { List(count) { FocusRequester() } }
    var refocus by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(refocus, sketches) {
        val to = refocus ?: return@LaunchedEffect
        runCatching { focusers.getOrNull(to)?.requestFocus() }
        refocus = null
    }

    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { viewport = it.boundsInRoot() }) {
        val columns = (maxWidth / 104.dp).toInt().coerceIn(2, 5)
        val gap = FolioSpace.LARGE.dp
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = FolioSpace.HUGE.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
            // A polite live region: what was just done is read aloud and shown (A11Y-7), never a toast.
            // Its line is always there, so the grid never jumps down when the first message arrives.
            Text(status, color = FolioGlass.secondary, fontSize = FolioType.FOOTNOTE.sp,
                modifier = Modifier.heightIn(min = 20.dp).semantics { liveRegion = LiveRegionMode.Polite })
            // The last cell is "Add Page" (until Home has the most pages it can), so adding is where pages are managed.
            val cells: List<PageSketch?> = if (count < MAX_PAGES) sketches + listOf(null) else sketches
            val added = stringResource(R.string.page_added, count + 1)
            val removedText = stringResource(R.string.page_removed, count)
            cells.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { sketch ->
                        if (sketch == null) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                val addLabel = stringResource(R.string.add_page)
                                Box(Modifier.fillMaxWidth().aspectRatio(GRID_COLUMNS / (rows * 1.15f))
                                    .clip(RoundedCornerShape(FolioRadius.CARD.dp))
                                    .background(FolioGlass.card.copy(alpha = if (solid) 1f else FolioGlass.card.alpha * .5f))
                                    .border(FolioGlass.edge, RoundedCornerShape(FolioRadius.CARD.dp))
                                    .clickable(role = Role.Button, onClickLabel = addLabel) {
                                        if (onAddPage() >= 0) { haptic.perform(FolioHaptic.Commit); setStatus(added) } else haptic.perform(FolioHaptic.Refuse)
                                    }
                                    .semantics { contentDescription = addLabel }.testTag("page-overview-add"), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(32.dp))
                                }
                                // Same height as a page's check row, so the add card lines up with the pages beside it.
                                Text(addLabel, color = FolioGlass.secondary, fontSize = FolioType.CAPTION.sp, textAlign = TextAlign.Center,
                                    modifier = Modifier.heightIn(min = FolioTouch.MIN.dp).padding(top = FolioSpace.SMALL.dp))
                            }
                            return@forEach
                        }
                        val page = sketch.page
                        val label = stringResource(R.string.home_page, page + 1)
                        val shownText = stringResource(if (sketch.hidden) R.string.page_overview_hidden else R.string.page_overview_shown)
                        val earlier = stringResource(R.string.move_page_earlier)
                        val later = stringResource(R.string.move_page_later)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            val isDragging = dragging == page
                            val aspect = GRID_COLUMNS / (rows * 1.15f)
                            Box(Modifier.fillMaxWidth().aspectRatio(aspect)
                                // Not boundsInRoot(): that is cut off by the scrolling area, so a page partly or wholly scrolled out of sight would
                                // report a smaller (or empty) box and the drag would aim at the wrong page.
                                .onGloballyPositioned { bounds[page] = Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat())) }
                                .graphicsLayer {
                                    if (isDragging) { translationX = dragOffset.x; translationY = dragOffset.y; scaleX = 1.06f; scaleY = 1.06f; shadowElevation = 12.dp.toPx() }
                                }
                                .clip(RoundedCornerShape(FolioRadius.CARD.dp))
                                .background(FolioGlass.card.copy(alpha = if (solid) 1f else FolioGlass.card.alpha))
                                .border(
                                    when {
                                        target == page && dragging != null && !isDragging -> BorderStroke(2.dp, LocalAccent.current.ink)
                                        page == currentPage -> BorderStroke(1.5.dp, Color.White.copy(alpha = .55f))
                                        else -> FolioGlass.edge
                                    }, RoundedCornerShape(FolioRadius.CARD.dp))
                                .graphicsLayer { alpha = if (sketch.hidden && !isDragging) .5f else 1f }
                                .pointerInput(page) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { dragging = page; dragOffset = Offset.Zero; target = page; haptic.perform(FolioHaptic.PickedUp) },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount
                                            val center = bounds[page]?.center ?: return@detectDragGesturesAfterLongPress
                                            val next = targetAt(center + dragOffset)
                                            if (next != target) { target = next; haptic.perform(FolioHaptic.Step) }
                                        },
                                        onDragEnd = {
                                            val from = page; val to = target
                                            dragging = null; target = null; dragOffset = Offset.Zero
                                            if (to != null && to != from) { currentOnMove(from, to); haptic.perform(FolioHaptic.Commit); setStatus("") }
                                        },
                                        onDragCancel = { dragging = null; target = null; dragOffset = Offset.Zero },
                                    )
                                }
                                // Alt with an arrow key moves the focused page one place (the same keys as moving an icon on Home).
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown || !event.isAltPressed) return@onPreviewKeyEvent false
                                    val to = when (event.key) {
                                        Key.DirectionLeft, Key.DirectionUp -> HomePages.stepTarget(page, count, toEnd = false)
                                        Key.DirectionRight, Key.DirectionDown -> HomePages.stepTarget(page, count, toEnd = true)
                                        else -> return@onPreviewKeyEvent false
                                    }
                                    if (to != null) {
                                        currentOnMove(page, to); refocus = to; haptic.perform(FolioHaptic.Commit)
                                        setStatus(context.getString(R.string.page_moved_to_position, to + 1, count))
                                    }
                                    true
                                }
                                .focusRequester(focusers[page])
                                .clickable(enabled = !sketch.hidden, role = Role.Button, onClickLabel = label) { onOpen(page) }
                                .semantics {
                                    contentDescription = label
                                    stateDescription = shownText
                                    customActions = buildList {
                                        HomePages.stepTarget(page, count, toEnd = false)?.let { to ->
                                            add(CustomAccessibilityAction(earlier) { currentOnMove(page, to); setStatus(context.getString(R.string.page_moved_to_position, to + 1, count)); true })
                                        }
                                        HomePages.stepTarget(page, count, toEnd = true)?.let { to ->
                                            add(CustomAccessibilityAction(later) { currentOnMove(page, to); setStatus(context.getString(R.string.page_moved_to_position, to + 1, count)); true })
                                        }
                                    }
                                }.testTag("page-thumb-$page")) {
                                PageMiniature(sketch, rows, Modifier.fillMaxSize().padding(FolioSpace.SNUG.dp))
                                // The last page, when it is empty, can be removed: a minus badge, as in iOS's editing mode.
                                if (page == count - 1 && count > 1 && sketch.cells.all { it == null } && sketch.widgets.isEmpty()) {
                                    val removeLabel = stringResource(R.string.remove_this_empty_page)
                                    Box(Modifier.align(Alignment.TopStart).size(FolioTouch.MIN.dp)
                                        .clickable(role = Role.Button, onClickLabel = removeLabel) {
                                            if (onRemoveLast()) { haptic.perform(FolioHaptic.Commit); setStatus(removedText) } else { haptic.perform(FolioHaptic.Refuse); setStatus(keepOne) }
                                        }.semantics { contentDescription = removeLabel }.testTag("page-remove-$page"), contentAlignment = Alignment.Center) {
                                        Box(Modifier.size(24.dp).clip(CircleShape).background(FolioGlass.raised), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.Remove, null, tint = FolioColors.RedOnDark, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                if (sketch.cells.all { it == null } && sketch.widgets.isEmpty()) {
                                    Text(stringResource(R.string.page_overview_empty), color = FolioGlass.secondary, fontSize = FolioType.CAPTION.sp, textAlign = TextAlign.Center,
                                        modifier = Modifier.align(Alignment.Center).padding(FolioSpace.SMALL.dp))
                                }
                            }
                            // The check circle: ticked shows the page, unticked hides it. A 24 dp circle inside a 48 dp touch box (A11Y-1).
                            val shownNow = !sketch.hidden
                            val shownStatus = stringResource(R.string.page_shown_on_home, page + 1)
                            val hiddenStatus = stringResource(R.string.page_hidden_from_home, page + 1)
                            Box(Modifier.size(FolioTouch.MIN.dp).toggleable(value = shownNow, role = Role.Checkbox) { wantShown ->
                                if (onSetHidden(page, !wantShown)) { haptic.perform(if (wantShown) FolioHaptic.ToggleOn else FolioHaptic.ToggleOff); setStatus(if (wantShown) shownStatus else hiddenStatus) }
                                else { haptic.perform(FolioHaptic.Refuse); setStatus(keepOne) }
                            }.semantics { contentDescription = label; stateDescription = shownText }.testTag("page-check-$page"), contentAlignment = Alignment.Center) {
                                CheckCircle(shownNow)
                            }
                        }
                    }
                    // A short last row keeps its cells the same width as the rows above it.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CheckCircle(on: Boolean) {
    val accent = LocalAccent.current
    // The shape differs, not only the color: a filled circle with a tick against an empty ring (A11Y-10).
    Box(Modifier.size(24.dp).clip(CircleShape).background(if (on) accent.fill else Color.Transparent)
        .border(BorderStroke(1.5.dp, if (on) accent.fill else Color.White.copy(alpha = .7f)), CircleShape), contentAlignment = Alignment.Center) {
        if (on) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

/** A page drawn small: its widgets as rounded blocks and its apps as rounded tiles, on the same four-column grid Home uses. */
@Composable
private fun PageMiniature(sketch: PageSketch, rows: Int, modifier: Modifier) {
    Canvas(modifier) {
        val colW = size.width / GRID_COLUMNS
        val rowH = size.height / rows
        val tile = minOf(colW, rowH) * .66f
        sketch.widgets.forEach { w ->
            if (w.row >= rows) return@forEach
            val spanY = minOf(w.spanY, rows - w.row)
            drawRoundRect(Color.White.copy(alpha = .3f), Offset(w.column * colW + colW * .06f, w.row * rowH + rowH * .06f),
                Size(w.spanX * colW - colW * .12f, spanY * rowH - rowH * .12f), CornerRadius(tile * .3f))
        }
        sketch.cells.forEachIndexed { local, id ->
            if (id == null || local / GRID_COLUMNS >= rows) return@forEachIndexed
            val cx = (local % GRID_COLUMNS + .5f) * colW
            val cy = (local / GRID_COLUMNS + .5f) * rowH
            // An app tile's corner is about a quarter of its size (DES-8a).
            drawRoundRect(Color.White.copy(alpha = .62f), Offset(cx - tile / 2, cy - tile / 2), Size(tile, tile), CornerRadius(tile * .26f))
        }
    }
}

private fun sketchPages(layout: HomeLayout, hidden: Set<Int>): List<PageSketch> {
    val hiddenNow = HomePages.sanitizeHidden(hidden, layout.pageCount)
    return (0 until layout.pageCount).map { page ->
        PageSketch(page, layout.slotsForPage(page), layout.widgetPlacements.filter { it.page == page }, page in hiddenNow)
    }
}
