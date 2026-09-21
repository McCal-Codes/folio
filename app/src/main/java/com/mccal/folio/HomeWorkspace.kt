@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.mccal.folio

import androidx.compose.ui.res.stringResource
import android.appwidget.AppWidgetProviderInfo
import android.os.UserManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun ExpandedWorkspace(
    nativePager: androidx.compose.foundation.pager.PagerState,
    motion: WorkspacePageMotion,
    firstHome: Int,
    leftPageContent: @Composable (Modifier) -> Unit,
    /** iPad-style Today View kept beside Home in place of the unfolded-only page. */
    besideContent: (@Composable (Modifier) -> Unit)? = null,
    visibleHomePages: Int,
    panelWidth: Dp,
    contentHeight: Dp,
    bottomSpace: Dp,
    geometry: HomeGeometry,
    state: LauncherState,
    previewSlots: List<String?>,
    previewLeadingSlots: List<String?>,
    previewWidgetPlacements: List<WidgetPlacement>,
    appsById: Map<String, AppEntry>,
    widgets: WidgetController,
    drag: HomeDragState,
    target: DropTarget?,
    insertionTarget: DropTarget?,
    libraryQuery: String,
    onLibraryQuery: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit,
    onPinned: (String, Boolean) -> Unit,
    onTurnOnWork: (Long) -> Unit,
    onActions: (AppEntry) -> Unit,
    onWidget: (Int) -> Unit,
    onFolder: (String) -> Unit,
    onEmptyWidget: (Int) -> Unit,
    onMove: (String, Int) -> Unit = { _, _ -> },
    /** Back swipe progress over the App Library (predictive back), 0–1. */
    libraryBack: () -> Float = { 0f },
    onRefresh: () -> Unit,
) {
    val density = LocalDensity.current
    val viewportWidth = motion.pageWidth
    val stride = motion.homeStride
    val initialHomeOrigin = with(density) { panelWidth.toPx() }
    val homePaneWidth = with(density) { (geometry.gridWidth + 16f).dp.toPx() }
    val stateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val visibleHomes by remember(nativePager, motion, firstHome, visibleHomePages, initialHomeOrigin, homePaneWidth) {
        derivedStateOf(structuralEqualityPolicy()) {
            val physicalPosition = nativePager.currentPage + nativePager.currentPageOffsetFraction
            val scroll = motion.offset(physicalPosition)
            val intersectingHomes = (0 until visibleHomePages).filter { page ->
                val start = initialHomeOrigin + page * stride
                start + homePaneWidth > scroll && start < scroll + viewportWidth
            }
            val nearestLogicalPage = nativePager.currentPage - firstHome
            // While Discover is current, keep the initial Home pair cached. Otherwise Home 2
            // is recreated midway through the first native exit and provider inflation can
            // block the gesture frame even though that pane began offscreen.
            val retentionAnchor = nearestLogicalPage.coerceAtLeast(0)
            (intersectingHomes + (retentionAnchor - 1..retentionAnchor + 1))
                .filter { it in 0 until visibleHomePages }.distinct().sorted()
        }
    }
    val place: Modifier.(Float) -> Modifier = { x ->
        offset {
            val physicalPosition = nativePager.currentPage + nativePager.currentPageOffsetFraction
            IntOffset((x - motion.offset(physicalPosition)).roundToInt(), 0)
        }
    }
    val showDiscover by remember(nativePager, firstHome) {
        derivedStateOf(structuralEqualityPolicy()) {
            firstHome > 0 && nativePager.currentPage + nativePager.currentPageOffsetFraction <= firstHome + .25f
        }
    }
    val leadingX = initialHomeOrigin - stride
    val showLeading by remember(nativePager, motion, firstHome, panelWidth, leadingX, homePaneWidth) {
        derivedStateOf(structuralEqualityPolicy()) {
            val physicalPosition = nativePager.currentPage + nativePager.currentPageOffsetFraction
            val scroll = motion.offset(physicalPosition)
            panelWidth.value > 0f && physicalPosition - firstHome < 1f &&
                leadingX - scroll + homePaneWidth > 0f
        }
    }
    val libraryPhysicalPage = firstHome + visibleHomePages
    val showLibrary by remember(nativePager, libraryPhysicalPage) {
        derivedStateOf(structuralEqualityPolicy()) {
            nativePager.currentPage + nativePager.currentPageOffsetFraction >= libraryPhysicalPage - 1.25f
        }
    }

    Box(Modifier.fillMaxSize().clipToBounds().testTag("expanded-workspace")) {
        if (showDiscover) {
            key("discover-pane") {
                Box(Modifier.place(-viewportWidth).fillMaxSize()) { leftPageContent(Modifier.fillMaxSize()) }
            }
        }

        if (showLeading) {
            key("expanded-leading-home") {
                Box(Modifier.place(leadingX).width((geometry.gridWidth + 16f).dp).fillMaxHeight()
                    .testTag("expanded-leading-home")) {
                    if (besideContent != null) besideContent(Modifier.fillMaxSize()) else HomePagePane(
                        -1, state, previewSlots, previewLeadingSlots, previewWidgetPlacements, appsById, geometry, contentHeight, bottomSpace,
                        widgets, drag, target, insertionTarget, showLargeWidget = true,
                        onLaunch = onLaunchFrom, onActions = onActions, onWidget = onWidget,
                        onFolder = onFolder, onEmptyWidget = onEmptyWidget, onMove = onMove, onRefresh = onRefresh,
                        modifier = Modifier,
                    )
                }
            }
        }

        visibleHomes.forEach { page ->
            key("expanded-home-$page") {
                stateHolder.SaveableStateProvider("expanded-home-$page") {
                    Box(Modifier.place(initialHomeOrigin + page * stride)
                        .width((geometry.gridWidth + 16f).dp).fillMaxHeight()) {
                        HomePagePane(
                            page, state, previewSlots, previewLeadingSlots, previewWidgetPlacements, appsById, geometry, contentHeight, bottomSpace,
                            widgets, drag, target, insertionTarget, showLargeWidget = page > 0,
                            onLaunch = onLaunchFrom, onActions = onActions, onWidget = onWidget,
                            onFolder = onFolder,
                            onEmptyWidget = onEmptyWidget,
                            onMove = onMove,
                            onRefresh = onRefresh,
                        )
                    }
                }
            }
        }

        if (showLibrary) {
            key("library-pane") {
                Box(Modifier.place((visibleHomePages - 1) * stride + viewportWidth).fillMaxSize()) {
                    AppLibrary(state, libraryQuery, onLibraryQuery, onLaunch, onPinned,
                        onActions = onActions,
                        modifier = Modifier.fillMaxSize()
                            .graphicsLayer { val b = libraryBack(); scaleX = 1f - .14f * b; scaleY = scaleX; alpha = 1f - .35f * b; translationX = size.width * .08f * b }
                            .padding(start = 16.dp, top = 16.dp, bottom = bottomSpace)
                            .testTag("library-page"),
                        drag = drag, page = visibleHomePages, onLaunchFrom = onLaunchFrom, onTurnOnWork = onTurnOnWork)
                }
            }
        }
    }
}

@Composable
internal fun HomePagePane(
    page: Int,
    state: LauncherState,
    previewSlots: List<String?>,
    previewLeadingSlots: List<String?>,
    previewWidgetPlacements: List<WidgetPlacement>,
    appsById: Map<String, AppEntry>,
    geometry: HomeGeometry,
    contentHeight: Dp,
    bottomSpace: Dp,
    widgets: WidgetController,
    drag: HomeDragState,
    target: DropTarget?,
    insertionTarget: DropTarget?,
    showLargeWidget: Boolean,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onActions: (AppEntry) -> Unit,
    onWidget: (Int) -> Unit,
    onFolder: (String) -> Unit,
    onEmptyWidget: (Int) -> Unit = {},
    /** Moves an app or folder by a number of cells (TalkBack actions and Alt+arrow keys; no dragging needed). */
    onMove: (String, Int) -> Unit = { _, _ -> },
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val homeOptionsLabel = stringResource(R.string.home_options)
    val homeScroll = rememberScrollState()
    var paneBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val pageStart = homeCellIndex(page, 0)
    val backgroundTarget = (pageStart until pageStart + HOME_CELLS).firstOrNull { index ->
        homeCellShown(index, geometry.appRows) && state.layout.slotAt(index) == null && state.widgetPlacements.none { index in it.coveredIndices() }
    } ?: pageStart
    val verticalEdge = with(LocalDensity.current) { 42.dp.toPx() }
    LaunchedEffect(drag.active, page, paneBounds) {
        while (drag.active) {
            val pointer = drag.pointer
            val amount = when {
                !paneBounds.contains(pointer) -> 0f
                pointer.y < paneBounds.top + verticalEdge && homeScroll.canScrollBackward -> -18f
                pointer.y > paneBounds.bottom - verticalEdge && homeScroll.canScrollForward -> 18f
                else -> 0f
            }
            if (amount != 0f) homeScroll.scrollBy(amount)
            delay(16)
        }
    }
    val edit = LocalHomeEdit.current
    val context = LocalContext.current
    val doubleTapAction = FolioAction.entries.firstOrNull { it.name == state.triggerActions[FolioTrigger.DOUBLE_TAP.name] } ?: FolioAction.NONE
    Box(modifier.testTag("home-page-$page")
        // Jiggle mode: a tap on empty space (not on an icon, which handles its own taps) finishes editing.
        .pointerInput(edit.active, doubleTapAction, backgroundTarget, drag.active) {
            // Like iPhone, a long-press anywhere on empty Home (below the grid too) starts jiggle mode,
            // and a second one opens the Home options.
            val longPress: (Offset) -> Unit = { if (!drag.active) onEmptyWidget(backgroundTarget) }
            when {
                edit.active -> detectTapGestures(onTap = { edit.stop() }, onLongPress = longPress)
                // Activator-style double-tap on empty Home.
                doubleTapAction != FolioAction.NONE -> detectTapGestures(onDoubleTap = { FolioActions.run(context, doubleTapAction) }, onLongPress = longPress)
                else -> detectTapGestures(onLongPress = longPress)
            }
        }
        .semantics {
            onLongClick(homeOptionsLabel) {
                if (!drag.active) onEmptyWidget(backgroundTarget)
                !drag.active
            }
        }
        .onGloballyPositioned { paneBounds = it.boundsInRoot() }
        .width((geometry.gridWidth + 16f).dp)
        .height((contentHeight - bottomSpace).coerceAtLeast(0.dp))) {
        Box(Modifier.width(16.dp).fillMaxHeight().testTag("home-options-margin-$page")
            .pointerInput(backgroundTarget, drag.active) {
                detectTapGestures(onLongPress = {
                    if (!drag.active) onEmptyWidget(backgroundTarget)
                })
            })
        // Jiggle mode: room under the + / Edit / Done bar so the top row's remove buttons never crowd it.
        // Frozen while something is held: sliding the grid under a finger would change where it drops.
        var roomWanted by remember { mutableStateOf(edit.active) }
        if (!drag.active) roomWanted = edit.active
        // Only as much room as the Edit bar actually needs above this page's first row; pushing the whole grid down by a
        // fixed amount cut off the bottom row's labels on screens that already had space at the top.
        // Unfolded, the bar sits in the space above the widget row, so the grid doesn't move at all (like iPad).
        val editRoom by animateDpAsState(if (roomWanted && !geometry.expanded) (JIGGLE_BAR_BOTTOM - geometry.contentTop.dp).coerceAtLeast(0.dp) else 0.dp, label = "jiggle room")
        Column(Modifier.offset(x = 16.dp).width(geometry.gridWidth.dp).fillMaxHeight()
            .verticalScroll(homeScroll).padding(top = geometry.contentTop.dp + editRoom, bottom = 8.dp)) {
            val (pageIcon, pageLabels) = (state.pageStyles[page] ?: PageStyle()).apply(geometry, state.labels)
            SharedHomeGrid(page, state.homeSlots, state.leadingSlots, previewSlots, previewLeadingSlots, previewWidgetPlacements,
                appsById, geometry.copy(iconSize = pageIcon), pageLabels, widgets, drag, target,
                folders = state.folders, onLaunch = onLaunch, onActions = onActions, onWidget = onWidget,
                onFolder = onFolder, onEmptyWidget = onEmptyWidget, onMove = onMove,
                onEmptyDoubleTap = if (doubleTapAction == FolioAction.NONE) null else ({ FolioActions.run(context, doubleTapAction) }))
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            if (state.error != null) Text(state.error, color = Color.White,
                modifier = Modifier.clickable(onClick = onRefresh).padding(12.dp))
        }
    }
}

@Composable
internal fun CircleControl(icon: ImageVector, label: String, tag: String, visualSize: Dp, action: () -> Unit) {
    IconButton(onClick = action, modifier = Modifier.size(visualSize.coerceAtLeast(48.dp)).testTag(tag)) {
        Box(Modifier.size(visualSize).testTag("$tag-visual").background(Glass.copy(alpha = .22f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = .25f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
internal fun SharedHomeGrid(
    page: Int,
    savedSlots: List<String?>,
    savedLeadingSlots: List<String?>,
    previewSlots: List<String?>,
    previewLeadingSlots: List<String?>,
    widgetPlacements: List<WidgetPlacement>,
    appsById: Map<String, AppEntry>,
    geometry: HomeGeometry,
    labels: Boolean,
    widgets: WidgetController,
    drag: HomeDragState,
    target: DropTarget?,
    folders: List<FolderEntry>,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onActions: (AppEntry) -> Unit,
    onWidget: (Int) -> Unit,
    onFolder: (String) -> Unit,
    onEmptyWidget: (Int) -> Unit,
    onMove: (String, Int) -> Unit = { _, _ -> },
    /** The Double Tap action, for empty cells: each cell takes its own taps, so the page behind never sees them. */
    onEmptyDoubleTap: (() -> Unit)? = null,
) {
    val rowHeight = geometry.rowHeight
    val iconSize = geometry.iconSize
    val edit = LocalHomeEdit.current
    val pageStart = homeCellIndex(page, 0)
    val pageRange = pageStart until pageStart + HOME_CELLS
    fun savedAt(index: Int) = if (page == -1) savedLeadingSlots.getOrNull(homeCellLocal(index)) else savedSlots.getOrNull(index)
    fun previewAt(index: Int) = if (page == -1) previewLeadingSlots.getOrNull(homeCellLocal(index)) else previewSlots.getOrNull(index)
    fun savedIndexOf(id: String) = if (page == -1) savedLeadingSlots.indexOf(id).takeIf { it >= 0 }?.let { homeCellIndex(-1, it) }
        else savedSlots.indexOf(id).takeIf { it >= 0 }
    fun previewIndexOf(id: String) = if (page == -1) previewLeadingSlots.indexOf(id).takeIf { it >= 0 }?.let { homeCellIndex(-1, it) }
        else previewSlots.indexOf(id).takeIf { it >= 0 }
    val draggedId = drag.source?.appId
    val homeTarget = (target as? DropTarget.Home)?.index
    val source = drag.source?.target as? DropTarget.Home
    // Positions are null when absent: on the unfolded-only page (-1) real indices are negative, so -1 is a real cell.
    val draggedPreviewIndex = draggedId?.let(::previewIndexOf)
    val hiddenIndex = when {
        !drag.active || !drag.moved -> null
        homeTarget != null -> draggedPreviewIndex
        source != null && target !is DropTarget.Dock -> draggedPreviewIndex
        else -> null
    }
    val dimDragged = drag.active && !drag.moved && source != null
    val pending = widgets.pendingPlacement?.takeIf { it.page == page }
    val pendingIsReplacement = pending != null && widgetPlacements.any { it.slot == pending.slot }
    val pageWidgets = widgetPlacements.filter { it.page == page } + listOfNotNull(pending?.takeUnless { pendingIsReplacement })
    // More rows: the rows Home shows, or more where apps or widgets already sit lower; cells past them aren't drawn.
    val shownRows = shownHomeRows(geometry.appRows, pageRange.map { previewAt(it) ?: savedAt(it) }, pageWidgets)
    // The retained overflow widget (stored under the grid) draws right after the shown rows.
    fun displayRow(row: Int) = if (row >= GRID_ROWS) shownRows + row - GRID_ROWS else row
    val widgetRows = pageWidgets.map { displayRow(it.row) to it.spanY }
    val renderedRows = maxOf(shownRows, widgetRows.maxOfOrNull { it.first + it.second } ?: 0)
    // Stacked, or two columns side by side in a short, wide window (see HomeCellLayout).
    val cells = remember(geometry, widgetRows) { HomeCellLayout.forPage(geometry, widgetRows) }
    // Half folded like a laptop (phone upright): rows that would sit in the fold spring down past it, like iPhone Duo.
    val hinge = LocalHinge.current?.takeIf { it.active && !it.vertical }
    val density = LocalDensity.current
    var gridTopDp by remember { mutableFloatStateOf(0f) }
    val fold = hinge?.let { h -> with(density) {
        foldDisplacement(cells, renderedRows, gridTopDp, h.startPx.toDp().value, h.endPx.toDp().value, widgetRows)
    } }
    val foldShift by animateFloatAsState(fold?.second ?: 0f, androidx.compose.animation.core.spring(dampingRatio = .85f, stiffness = 380f), label = "fold shift")
    val foldRow = fold?.first ?: Int.MAX_VALUE
    fun rowTop(row: Int) = cells.y(row) + if (row >= foldRow) foldShift else 0f
    Box(Modifier.fillMaxWidth().height((cells.height(renderedRows) + foldShift).dp)
        .onGloballyPositioned { gridTopDp = with(density) { it.boundsInWindow().top.toDp().value } }) {
        val cellWidth = cells.cellWidth.dp
        fun cellX(column: Int, row: Int) = cells.x(column, row).dp

        repeat(shownRows * GRID_COLUMNS) { localIndex ->
            val globalIndex = pageStart + localIndex
            val cell = DropTarget.Home(globalIndex)
            val savedId = savedAt(globalIndex)
            val savedApp = appsById[savedId]
            val savedFolder = folders.firstOrNull { it.id == savedId }
            val previewId = previewAt(globalIndex)
            val highlighted = drag.active && target == cell
            val gap = hiddenIndex == globalIndex
            val row = localIndex / GRID_COLUMNS
            val cellHeight = cells.spanHeight(row, 1)
            Box(Modifier.offset(x = cellX(localIndex % GRID_COLUMNS, row), y = rowTop(row).dp)
                .width(cellWidth).height(cellHeight.dp).testTag("home-cell-$globalIndex")
                .dropRegion(drag, cell, savedApp?.id ?: savedFolder?.id, page)
                // Keyboard and switch focus goes to the app or folder itself, not the empty cell behind it.
                .focusProperties { canFocus = false }
                .combinedClickable(onClick = { if (savedFolder != null) onFolder(savedFolder.id) else if (edit.active) edit.stop() },
                    onLongClick = { if (savedId == null && !drag.active) onEmptyWidget(globalIndex) },
                    // Only on an empty cell outside jiggle mode, so a tap on an app or folder isn't held back waiting
                    // for a second one.
                    onDoubleClick = onEmptyDoubleTap?.takeIf { savedId == null && !edit.active && !drag.active })
                .background(if (highlighted) Glass.copy(alpha = .25f) else Color.Transparent, RoundedCornerShape(16.dp))
                .border(if (highlighted) 2.dp else 0.dp,
                    if (highlighted) Color.White.copy(alpha = .8f) else Color.Transparent, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.TopCenter) {
                if (drag.active && drag.source?.appId != null && (gap || previewId == null)) Box(
                    Modifier.size(iconSize.dp).testTag(if (gap) "drag-gap-home-$globalIndex" else "empty-home-slot-$globalIndex")
                        .background(Glass.copy(alpha = if (gap) .16f else .08f), RoundedCornerShape(18.dp))
                        .border(if (gap) 2.dp else 1.dp, Color.White.copy(alpha = if (gap) .55f else .3f), RoundedCornerShape(18.dp)))
            }
        }

        val ids = (if (page == -1) savedLeadingSlots + previewLeadingSlots
            else savedSlots.slicePage(pageRange) + previewSlots.slicePage(pageRange)).filterNotNull().distinct()
        ids.forEach { id ->
            val savedIndex = savedIndexOf(id)
            val previewIndex = previewIndexOf(id)
            val renderIndex = previewIndex?.takeIf { it in pageRange } ?: savedIndex?.takeIf { it in pageRange } ?: return@forEach
            val app = appsById[id] ?: return@forEach
            key(id) {
                val localIndex = renderIndex - pageStart
                val row = localIndex / GRID_COLUMNS
                // Slide only while rearranging; a new screen size (folding) must place icons immediately.
                val animatedOffset by animateIntOffsetAsState(
                    with(density) { IntOffset(cellX(localIndex % GRID_COLUMNS, row).toPx().roundToInt(), rowTop(row).dp.toPx().roundToInt()) },
                    animationSpec = if (drag.active || edit.active) androidx.compose.animation.core.spring(visibilityThreshold = IntOffset(1, 1))
                        else androidx.compose.animation.core.snap(),
                    label = "home insertion $id",
                )
                val visible = previewIndex != null && previewIndex in pageRange && renderIndex != hiddenIndex
                val opacity by animateFloatAsState(
                    if (dimDragged && id == draggedId) .28f else 1f,
                    label = "home insertion visibility $id",
                )
                Box(Modifier.offset { animatedOffset }.width(cellWidth).height(rowHeight.dp)
                    .alpha(opacity).moveActions(id, page, onMove).testTag("home-app-$id"), contentAlignment = Alignment.TopCenter) {
                    if (visible) AppTile(app, iconSize, labels,
                        onClick = { if (!edit.active) onLaunch(app, it) }, onLongClick = { onActions(app) },
                        onRemove = if (edit.active && savedIndex != null) {{ edit.onRemove(DropTarget.Home(savedIndex)) }} else null)
                }
            }
        }
        folders.forEach { folder ->
            val savedIndex = savedIndexOf(folder.id)
            val previewIndex = previewIndexOf(folder.id)
            val renderIndex = previewIndex?.takeIf { it in pageRange } ?: savedIndex?.takeIf { it in pageRange } ?: return@forEach
            val localIndex = renderIndex - pageStart
            val row = localIndex / GRID_COLUMNS
            val x = cellX(localIndex % GRID_COLUMNS, row)
            val y = rowTop(row).dp
            FolderTile(folder, appsById, iconSize, labels, drag, page,
                Modifier.offset(x = x, y = y).width(cellWidth).height(rowHeight.dp)
                    .moveActions(folder.id, page, onMove).testTag("home-folder-${folder.id}"), onClick = { onFolder(folder.id) })
        }
        pageWidgets.forEach { placement ->
            key("widget-${placement.slot}") {
                val width = (cellWidth * placement.spanX - 10.dp).coerceAtLeast(1.dp)
                val row = displayRow(placement.row)
                val x = cellX(placement.column, row) + 5.dp
                val y = rowTop(row)
                val height = (cells.spanHeight(row, placement.spanY) - 18f).coerceAtLeast(48f)
                if (placement == pending) Surface(Modifier.offset(x = x, y = y.dp).width(width).height(height.dp)
                    .testTag("widget-pending-${placement.slot}").semantics(mergeDescendants = true) {
                        contentDescription = "Pending ${widgets.pendingProvider?.shortClassName ?: "widget"}"
                    }, color = Glass.copy(alpha = .72f),
                    shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(2.dp, Color.White)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.finish_widget_setup), color = Ink)
                    }
                } else MovableWidget(placement.id, placement.slot, widgets, drag, target,
                    Modifier.offset(x = x, y = y.dp).width(width).height(height.dp), page = page) { onWidget(placement.slot) }
            }
        }
    }
}

/** One way to move a Home item without dragging: what TalkBack offers, what it says after, and the cell offset. */
internal data class HomeMove(@androidx.annotation.StringRes val label: Int, @androidx.annotation.StringRes val moved: Int, val offset: Int)

/** Left, right, up, down, and the same cell on the next or previous page. */
internal fun homeMoveOffsets(page: Int): List<HomeMove> = buildList {
    add(HomeMove(R.string.move_left, R.string.moved_left, -1)); add(HomeMove(R.string.move_right, R.string.moved_right, 1))
    add(HomeMove(R.string.move_up, R.string.moved_up, -GRID_COLUMNS)); add(HomeMove(R.string.move_down, R.string.moved_down, GRID_COLUMNS))
    // The unfolded-only page has no neighbors to move to.
    if (page >= 0) {
        add(HomeMove(R.string.move_to_next_page, R.string.moved_to_next_page, HOME_CELLS))
        if (page > 0) add(HomeMove(R.string.move_to_previous_page, R.string.moved_to_previous_page, -HOME_CELLS))
    }
}

/**
 * Lets TalkBack and keyboard users rearrange Home: custom accessibility actions, and Alt+arrow keys (Alt+Page Up/Down
 * for pages) on the focused icon. Folio says where the item went.
 */
@Composable
private fun Modifier.moveActions(id: String, page: Int, onMove: (String, Int) -> Unit): Modifier {
    val view = androidx.compose.ui.platform.LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    fun perform(move: HomeMove) { onMove(id, move.offset); view.announceForAccessibility(context.getString(move.moved)) }
    val offsets = homeMoveOffsets(page)
    val labels = offsets.map { stringResource(it.label) }
    return semantics {
        customActions = offsets.mapIndexed { i, move -> androidx.compose.ui.semantics.CustomAccessibilityAction(labels[i]) { perform(move); true } }
    }.onPreviewKeyEvent { event ->
        if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown || !event.isAltPressed) return@onPreviewKeyEvent false
        val action = when (event.key) {
            androidx.compose.ui.input.key.Key.DirectionLeft -> offsets.firstOrNull { it.offset == -1 }
            androidx.compose.ui.input.key.Key.DirectionRight -> offsets.firstOrNull { it.offset == 1 }
            androidx.compose.ui.input.key.Key.DirectionUp -> offsets.firstOrNull { it.offset == -GRID_COLUMNS }
            androidx.compose.ui.input.key.Key.DirectionDown -> offsets.firstOrNull { it.offset == GRID_COLUMNS }
            androidx.compose.ui.input.key.Key.PageDown -> offsets.firstOrNull { it.offset == HOME_CELLS }
            androidx.compose.ui.input.key.Key.PageUp -> offsets.firstOrNull { it.offset == -HOME_CELLS }
            else -> null
        } ?: return@onPreviewKeyEvent false
        perform(action); true
    }
}
