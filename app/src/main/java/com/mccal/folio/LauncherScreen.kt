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
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.roundToIntRect
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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

internal val Ink: Color
    @Composable get() = LocalDuoPalette.current.ink
internal val Glass: Color
    @Composable get() = LocalDuoPalette.current.glass

@Composable
fun DuoTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    val palette = if (dark) DarkDuoPalette else LightDuoPalette
    CompositionLocalProvider(LocalDuoPalette provides palette) {
        MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF9BC5D7), onPrimary = Color(0xFF12303D),
            surface = Color(0xFF17272E), onSurface = palette.ink, secondary = Color(0xFFD1BE98),
            secondaryContainer = Color(0xFF314852), onSecondaryContainer = palette.ink)
        else lightColorScheme(primary = Color(0xFF30596D), onPrimary = Color.White,
            surface = Color(0xFFF4F7F8), onSurface = palette.ink, secondary = Color(0xFF84775F),
            secondaryContainer = Color(0xFFDCE8ED), onSecondaryContainer = palette.ink), content = content)
    }
}


@Composable
fun LauncherScreen(
    state: LauncherState, model: LauncherModel, widgets: WidgetController, homeRequests: Int,
    onLaunch: (AppEntry) -> Unit, onMakeDefault: () -> Unit, onAppInfo: (AppEntry) -> Unit,
    isDefaultHome: Boolean, deviceStatus: DeviceStatus, onStatusMode: (Boolean) -> Unit, onWallpaperPreview: () -> Unit,
    onDiscover: () -> Unit = {}, searchRequests: Int = 0, settingsRequests: Int = 0,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit = { app, _ -> onLaunch(app) },
    onGoogleSearch: (android.graphics.Rect?) -> Boolean = { false },
    appearance: AppearanceState = AppearanceState(),
    onAppearanceMode: (AppearanceMode) -> Unit = {},
    onAppearanceManual: (String, Double, Double) -> Unit = { _, _, _ -> },
    onAppearanceDeviceLocation: () -> Unit = {},
    onAppearanceClear: () -> Unit = {},
    showFirstRun: Boolean = false,
    onFinishFirstRun: () -> Unit = {},
    onShadeSetup: () -> Unit = {},
    onShowWelcome: () -> Unit = {},
    onShowWhatsNew: () -> Unit = {},
) {
    var sheet by rememberSaveable { mutableStateOf("") }
    var dockSlot by rememberSaveable { mutableIntStateOf(0) }
    var widgetSlot by rememberSaveable { mutableIntStateOf(0) }
    var widgetTargetIndex by rememberSaveable { mutableIntStateOf(Int.MIN_VALUE) }
    var widgetExactTarget by rememberSaveable { mutableStateOf(false) }
    /** Set while the widget picker is adding to the Smart Stack at this placement slot. */
    var stackTargetSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    /** Set while the widget picker is adding to the Today View. */
    var todayAdd by rememberSaveable { mutableStateOf(false) }
    var widgetPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var widgetProfileSerial by rememberSaveable { mutableStateOf<Long?>(null) }
    var widgetSession by remember { mutableStateOf<WidgetPickerSession?>(null) }
    var widgetPlacementMessage by remember { mutableStateOf<String?>(null) }
    var emptyCellIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var resizeSlot by remember { mutableStateOf<Int?>(null) }
    var resizeWidth by rememberSaveable { mutableIntStateOf(1) }
    var resizeHeight by rememberSaveable { mutableIntStateOf(1) }
    var resizeConstraints by remember { mutableStateOf<WidgetSpanConstraints?>(null) }
    var resizePitchX by remember { mutableFloatStateOf(1f) }
    var resizePitchY by remember { mutableFloatStateOf(1f) }
    var resizeTopPitch by remember { mutableFloatStateOf(1f) }
    var resizeAppPitch by remember { mutableFloatStateOf(1f) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameAppId by rememberSaveable { mutableStateOf<String?>(null) }
    var panelAppId by rememberSaveable { mutableStateOf<String?>(null) }
    var stackAppId by rememberSaveable { mutableStateOf<String?>(null) }
    var stackEditId by rememberSaveable { mutableStateOf<String?>(null) }
    var customizationPage by rememberSaveable { mutableStateOf(CustomizationPage.OVERVIEW) }
    LaunchedEffect(sheet) {
        if (sheet != "widgets") { stackTargetSlot = null; todayAdd = false }
    }
    var openFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var createFolderFirstId by rememberSaveable { mutableStateOf<String?>(null) }
    var savedPage by rememberSaveable { mutableIntStateOf(0) }
    var lastHomePage by rememberSaveable { mutableIntStateOf(0) }
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    var pinQuery by rememberSaveable { mutableStateOf("") }
    val launcherActivity = androidx.activity.compose.LocalActivity.current as MainActivity
    val launcherRootView = LocalView.current.rootView
    DisposableEffect(sheet == "widgets") {
        val active = sheet == "widgets"
        if (active) LiveDiscover.setExternalResultPending(launcherActivity, "main", "widget-picker", true)
        onDispose { if (active) LiveDiscover.setExternalResultPending(launcherActivity, "main", "widget-picker", false) }
    }
    val appsById = remember(state.apps) { state.apps.associateBy { it.id } }
    val drag = remember { HomeDragState() }
    val folderOwnsInput = openFolderId != null || drag.source?.folderId != null
    DisposableEffect(folderOwnsInput) {
        if (folderOwnsInput) LiveDiscover.setExternalResultPending(launcherActivity, "main", "folder-panel", true)
        onDispose { if (folderOwnsInput) LiveDiscover.setExternalResultPending(launcherActivity, "main", "folder-panel", false) }
    }
    val haptic = LocalHapticFeedback.current
    val homeEdit = remember { HomeEditMode() }
    homeEdit.onRemove = { target -> if (target is DropTarget.Widget) widgets.remove(target.index) else model.removePlacement(target) }
    // While a Focus hides Home pages, editing is locked; trying explains why instead.
    val focusLock = LocalFocusLock.current
    var lockNotice by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusLock) { if (focusLock != null) homeEdit.stop() }
    // Long-press on empty Home starts jiggle mode (iPhone); a second long-press opens the Home options.
    val onEmptyLongPress: (Int) -> Unit = { index ->
        if (focusLock != null) { haptic.performHapticFeedback(HapticFeedbackType.Reject); lockNotice++ }
        else if (homeEdit.active) emptyCellIndex = index
        else { homeEdit.lastEmptyIndex = index; haptic.performHapticFeedback(HapticFeedbackType.LongPress); homeEdit.start() }
    }
    val homePages = state.homePages
    val pendingNewPage = widgets.pendingPlacement?.page == homePages
    val visibleHomePages = homePages + if (drag.active || widgetSession != null || pendingNewPage) 1 else 0
    var expandedWorkspace by remember { mutableStateOf(false) }
    /** Where the jiggle bar's Edit button is, so its menu opens right under it. */
    var editPillBounds by remember { mutableStateOf<androidx.compose.ui.unit.IntRect?>(null) }
    // The page left of Home is Folio's Today View, Google Discover when chosen and available, or nothing at all.
    val todayMode = state.leftPage == "TODAY"
    val discoverMode = state.leftPage == "DISCOVER"
    // Only Discover hosted beside Home reads the recorded Home layer.
    val hostedDiscover = discoverMode && DiscoverBounds.available
    val currentTodayMode by rememberUpdatedState(todayMode)
    val firstHome = if (todayMode || (discoverMode && DiscoverBounds.available)) 1 else 0
    DisposableEffect(discoverMode) {
        // Only Discover mode starts Google's hidden feed window (any other choice closes one that's running).
        if (!discoverMode) LiveDiscover.setExternalResultPending(launcherActivity, "main", "today-view", true)
        onDispose { if (!discoverMode) LiveDiscover.setExternalResultPending(launcherActivity, "main", "today-view", false) }
    }
    val pageCount = visibleHomePages + 1
    val nativePager = rememberPagerState(initialPage = savedPage.coerceIn(-firstHome, pageCount - 1) + firstHome, pageCount = { pageCount + firstHome })
    val pager = remember(nativePager) { LauncherPager(nativePager, firstHome) }
    fun leaveTemporaryWidgetPage() {
        val persistedPages = model.state.value.homePages
        if (pager.currentPage >= persistedPages)
            pager.requestScrollToPage((persistedPages - 1).coerceAtLeast(0))
    }
    var priorPendingPlacement by remember { mutableStateOf<WidgetPlacement?>(null) }
    LaunchedEffect(widgets.pendingPlacement, state.layout) {
        val pending = widgets.pendingPlacement
        if (pending != null) priorPendingPlacement = pending
        else priorPendingPlacement?.let { prior ->
            if (model.placement(prior.slot) == null && prior.page >= homePages) leaveTemporaryWidgetPage()
            priorPendingPlacement = null
        }
    }
    val pageGestures = remember(nativePager) { PageGestureLimits(nativePager) }
    SideEffect { pageGestures.editing = drag.active || widgetSession != null || resizeSlot != null; LiveDiscover.allowNativeOpen = pager.currentPage == 0 && !drag.active && widgetSession == null && resizeSlot == null }
    val pageFling = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(nativePager, pagerSnapDistance = pageGestures,
        snapAnimationSpec = MotionSpeed.spring(1f, androidx.compose.animation.core.Spring.StiffnessMediumLow * 1.2f))
    var nativeMotion by remember { mutableStateOf(false) }
    DisposableEffect(nativePager) {
        val callback: (Float) -> Unit = { progress ->
            val scrolling = nativePager.isScrollInProgress
            if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_callback_received",
                "progress=$progress scrolling=$scrolling nativeMotion=$nativeMotion current=${nativePager.currentPage} offset=${nativePager.currentPageOffsetFraction}")
            if (!scrolling || nativeMotion) {
                val priorNativeMotion = nativeMotion
                nativeMotion = progress > 0f && progress < 1f
                val position = 1f - progress
                val page = position.roundToInt()
                if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_callback_accepted",
                    "progress=$progress nativeMotion=$priorNativeMotion->$nativeMotion requestPage=$page requestOffset=${position - page}")
                nativePager.requestScrollToPage(page, position - page)
            } else if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_callback_rejected",
                "progress=$progress reason=compose_scrolling nativeMotion=$nativeMotion")
        }
        LiveDiscover.onNativeProgress = callback
        onDispose { if (LiveDiscover.onNativeProgress === callback) LiveDiscover.onNativeProgress = null }
    }
    LaunchedEffect(nativePager) {
        snapshotFlow { Triple((1f - nativePager.currentPage - nativePager.currentPageOffsetFraction).coerceIn(0f, 1f), nativePager.isScrollInProgress, nativeMotion) to (nativePager.targetPage < firstHome) }
            .collect { (motion, towardFeed) ->
                val (progress, scrolling, native) = motion
                if (firstHome > 0 && !currentTodayMode) {
                    if (DuoMotionTrace.enabled) DuoMotionTrace.event("pager_observer",
                        "progress=$progress scrolling=$scrolling nativeMotion=$native towardFeed=$towardFeed")
                    if (scrolling) {
                        if (nativeMotion && DuoMotionTrace.enabled) DuoMotionTrace.event("native_owner_cleared",
                            "reason=compose_scrolling progress=$progress")
                        nativeMotion = false
                        LiveDiscover.page(progress, true, towardFeed)
                    } else if (!native) LiveDiscover.page(progress, false)
                }
            }
    }
    val scope = rememberCoroutineScope()
    DisposableEffect(pager) {
        val callback = { scope.launch { pager.animateScrollToPage(0) }; Unit }
        LiveDiscover.onHomeRequest = callback
        onDispose { if (LiveDiscover.onHomeRequest === callback) LiveDiscover.onHomeRequest = null }
    }
    var previousHomePages by remember { mutableIntStateOf(homePages) }
    var previousEditRevision by remember { mutableIntStateOf(state.editRevision) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(pager, homePages) {
        snapshotFlow { pager.settledPage to drag.active }.distinctUntilChanged().collect { (page, moving) ->
            if (!moving) { savedPage = page; if (page in 0 until homePages) lastHomePage = page }
        }
    }
    LaunchedEffect(homePages, state.editRevision) {
        if (homePages != previousHomePages && !drag.active) {
            // Pin edits in the library keep the library selected; a completed drop stays on home.
            if (state.editRevision == previousEditRevision) {
                if (pager.currentPage == previousHomePages) pager.scrollToPage(homePages)
                else if (pager.currentPage >= pageCount) pager.scrollToPage(homePages - 1)
            } else if (pager.currentPage >= homePages) pager.scrollToPage(homePages - 1)
        }
        previousHomePages = homePages
        previousEditRevision = state.editRevision
    }
    LaunchedEffect(pager.settledPage) { if (pager.settledPage != homePages) focus.clearFocus() }
    LaunchedEffect(pager.settledPage, visibleHomePages) { if (pager.settledPage !in 0 until visibleHomePages) homeEdit.stop() }
    LaunchedEffect(state.verticalStatus) { onStatusMode(state.verticalStatus) }
    LaunchedEffect(homeRequests) { if (homeRequests > 0) {
        // An app can pause Home after the destination is visible but before its settle completes.
        // A Focus with its own Home page brings Home back there, like iOS Focus pages.
        val active = state.focusModes.firstOrNull { it.id == state.activeFocus }
        val page = (focusLock?.let { FocusPages.openPage(active, it.realPages) } ?: FocusModes.homePage(active, homePages))
            ?: pager.currentPage.takeIf { it in 0 until homePages }
            ?: lastHomePage.coerceIn(0, homePages - 1)
        drag.clear(); widgetSession = null; resizeSlot = null; sheet = ""; widgetPackage = null
        widgetExactTarget = false; widgetPlacementMessage = null; selectedId = null
        openFolderId = null; createFolderFirstId = null; emptyCellIndex = null; homeEdit.stop()
        focus.clearFocus(); keyboard?.hide()
        pager.animateScrollToPage(page)
    } }
    // Turning on a Focus with a Home page goes straight there.
    LaunchedEffect(state.activeFocus) {
        val active = state.focusModes.firstOrNull { it.id == state.activeFocus }
        (focusLock?.let { FocusPages.openPage(active, it.realPages) } ?: FocusModes.homePage(active, homePages))?.let { pager.animateScrollToPage(it) }
    }
    LaunchedEffect(settingsRequests) { if (settingsRequests > 0) {
        drag.clear(); widgetSession = null; resizeSlot = null; selectedId = null; homeEdit.stop()
        if (SoftwareUpdate.openRequested) { SoftwareUpdate.openRequested = false; customizationPage = CustomizationPage.SOFTWARE_UPDATE }
        SettingsLink.page?.let { customizationPage = it; SettingsLink.page = null }
        sheet = "settings"
    } }
    // Saved layout damaged, or apps failed to load: say so instead of quietly showing an empty Home.
    var problemDismissed by rememberSaveable(state.error) { mutableStateOf(false) }
    state.error?.takeIf { !problemDismissed && sheet.isEmpty() }?.let { message ->
        if (model.layoutDamaged) AlertDialog(onDismissRequest = { problemDismissed = true },
            title = { Text(stringResource(R.string.your_home_layout_couldn_t_be_loaded)) },
            text = { Text("Folio kept your saved layout untouched and is showing a basic Home for now. Restore a backup or an earlier layout, or start fresh (Folio keeps a copy of the old one).") },
            confirmButton = { TextButton(onClick = { SettingsLink.page = CustomizationPage.BACKUP; customizationPage = CustomizationPage.BACKUP; sheet = "settings" }) { Text(stringResource(R.string.restore_2)) } },
            dismissButton = { Row {
                TextButton(onClick = { problemDismissed = true }) { Text(stringResource(R.string.not_now)) }
                TextButton(onClick = { model.resetDamagedLayout() }) { Text(stringResource(R.string.start_fresh), color = FolioColors.Red) }
            } })
        else AlertDialog(onDismissRequest = { problemDismissed = true },
            title = { Text(stringResource(R.string.apps_couldn_t_be_loaded)) },
            text = { Text(message.removeSuffix(" Tap to retry.")) },
            confirmButton = { TextButton(onClick = { problemDismissed = true; model.refresh() }) { Text(stringResource(R.string.try_again)) } },
            dismissButton = { TextButton(onClick = { problemDismissed = true }) { Text(stringResource(R.string.not_now)) } })
    }
    LaunchedEffect(searchRequests) { if (searchRequests > 0) { drag.clear(); widgetSession = null; resizeSlot = null; sheet = ""; widgetPackage = null; widgetExactTarget = false; selectedId = null
        if (!state.googleSearch || !onGoogleSearch(null)) pager.animateScrollToPage(homePages)
    } }
    val widgetPickerBack = {
        if (widgetSession != null) {
            leaveTemporaryWidgetPage(); widgetSession = null; widgetPlacementMessage = null
        } else {
            sheet = ""; widgetPackage = null; widgetExactTarget = false; widgetPlacementMessage = null
        }
    }
    BackHandler(enabled = sheet == "widgets") { widgetPickerBack() }
    // Off while Spotlight or a top panel is open, so Back always closes those first, whatever order the handlers registered in.
    // App Library, predictive back: the library eases back as you swipe and returns to Home when you let go.
    var libraryBack by remember { mutableFloatStateOf(0f) }
    val libraryBackActive = sheet.isEmpty() && !launcherActivity.spotlightVisible.value && launcherActivity.topPanel.value == null &&
        pager.currentPage == visibleHomePages && resizeSlot == null && !drag.active && selectedId == null && !homeEdit.active
    PredictiveBack(enabled = libraryBackActive, onProgress = { libraryBack = it }, onCancel = { libraryBack = 0f },
        onBack = { focus.clearFocus(); scope.launch { pager.animateScrollToPage(0); libraryBack = 0f } })
    BackHandler(enabled = sheet.isEmpty() && !launcherActivity.spotlightVisible.value && launcherActivity.topPanel.value == null && !libraryBackActive) { if (resizeSlot != null) resizeSlot = null else if (drag.active) {
        val destination = if (drag.source?.target is DropTarget.Library) homePages else drag.originPage.coerceAtMost(homePages - 1)
        drag.clear(); scope.launch { pager.scrollToPage(destination) }
    } else if (selectedId != null) selectedId = null else if (homeEdit.active) homeEdit.stop()
        // Previewing (Folio isn't the Home app yet): Back on the first page leaves, like any other app.
        else if (!isDefaultHome && pager.currentPage == 0) launcherActivity.moveTaskToBack(true)
        else { focus.clearFocus(); scope.launch { pager.animateScrollToPage(0) } } }
    val openDiscover = { if (firstHome > 0) scope.launch { pager.animateScrollToPage(-1) } else if (discoverMode) onDiscover(); Unit }
    val openLibrary = { scope.launch { pager.animateScrollToPage(homePages) }; Unit }
    val todayContent: @Composable (Modifier) -> Unit = { pageModifier ->
        TodayView(state, widgets, pageModifier,
            onSearch = { launcherActivity.openSpotlight() }, onLaunch = onLaunch,
            onAddWidget = { todayAdd = true; widgetPackage = null; widgetProfileSerial = null; widgetExactTarget = false; sheet = "widgets" },
            onRemove = model::removeTodayWidget, onMove = model::moveTodayWidget)
    }
    val leftPageContent: @Composable (Modifier) -> Unit = { pageModifier ->
        when {
            !todayMode -> DiscoverContent(pageModifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp))
            expandedWorkspace && state.todayUnfolded != "PAGE" -> Box(pageModifier)
            else -> todayContent(pageModifier)
        }
    }

    val dragWindowPage = if (expandedWorkspace && (drag.active || widgetSession != null)) pager.settledPage else pager.currentPage
    val eligibleDragPages = remember(expandedWorkspace, dragWindowPage, visibleHomePages) {
        if (expandedWorkspace && dragWindowPage in 0 until visibleHomePages) {
            setOfNotNull((dragWindowPage - 1).takeIf { it >= -1 }, dragWindowPage)
        } else setOf(dragWindowPage)
    }
    val rawTarget = if (drag.active) drag.destination(drag.pointer, eligibleDragPages)?.target else null
    val target = if (rawTarget is DropTarget.Home && drag.source?.target is DropTarget.Widget) {
        val slot = (drag.source!!.target as DropTarget.Widget).index
        model.placement(slot)?.let {
            DropTarget.Home(adjustedWidgetDropIndex(rawTarget.index, it, drag.source!!.bounds, drag.origin))
        } ?: rawTarget
    } else rawTarget
    val blockedDock = drag.moved && target is DropTarget.Dock &&
        if (drag.source?.folderId != null) state.dock.none { it == null }
        else drag.source?.appId?.let { !canPlaceInDock(state.layout, it) } == true
    val insertionTarget = target.takeIf { drag.moved && !blockedDock }
    LaunchedEffect(drag.active, drag.moved) {
        val source = drag.source
        val id = source?.appId
        if (focusLock != null && drag.active && drag.moved) { drag.clear(); selectedId = null; lockNotice++; return@LaunchedEffect }
        if (drag.active && drag.moved && id != null && selectedId == id) { selectedId = null; homeEdit.start() }
        // Dragging out of the App Library heads to Home only once the app actually moves (holding just shows the menu).
        if (drag.active && drag.moved && source?.target is DropTarget.Library) {
            withFrameNanos { }
            pager.scrollToPage(lastHomePage.coerceIn(0, homePages - 1))
        }
    }
    // A light tick each time the dragged item snaps to a new spot.
    LaunchedEffect(insertionTarget) { if (insertionTarget != null && drag.moved) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    val widgetRawTarget = widgetSession?.let { session -> drag.regions.values.firstOrNull {
        it.target is DropTarget.Home && it.page in eligibleDragPages && it.bounds.contains(session.pointer)
    }?.target as? DropTarget.Home }
    // More rows: automatic placement uses the rows every page shows; a page already drawn taller (apps placed lower
    // on the other screen) also takes drops in those rows.
    val homeAppRows = state.homeAppRows
    val visibleRows = visibleHomeRows(homeAppRows)
    fun pageRows(page: Int) = shownHomeRows(homeAppRows, state.layout.slotsForPage(page), state.widgetPlacements.filter { it.page == page })
    fun draftAt(index: Int, span: WidgetSpan, slot: Int) = widgetCandidate(state.layout, slot, index, span.width, span.height, pageRows(homeCellPage(index)))
    val widgetDraft = widgetSession?.let { session -> session.candidate ?: widgetRawTarget?.let { cell ->
        draftAt(session.targetIndex ?: cell.index, session.span, session.slot)
    } ?: session.targetIndex?.let { draftAt(it, session.span, session.slot) } }
    val dropHomePage = if (pager.currentPage >= visibleHomePages)
        lastHomePage.coerceIn(0, homePages - 1) else pager.currentPage.coerceIn(0, homePages)
    val previewLayout = remember(state.layout, drag.source, insertionTarget, drag.moved, homeAppRows) {
        val id = drag.source?.appId
        when {
            id != null && insertionTarget is DropTarget.Home -> dropApp(state.layout, id, insertionTarget, homeAppRows)
            id != null && insertionTarget is DropTarget.Dock -> dropApp(state.layout, id, insertionTarget, homeAppRows)
            drag.source?.target is DropTarget.Widget && insertionTarget is DropTarget.Home ->
                moveWidget(state.layout, (drag.source!!.target as DropTarget.Widget).index, insertionTarget.index)
            else -> state.layout
        }
    }
    val edgeWidth = with(LocalDensity.current) { 30.dp.toPx() }
    val edgePointer = widgetSession?.takeIf { it.dragging }?.pointer ?: drag.pointer
    val edgeActive = (drag.active && drag.moved) || widgetSession?.dragging == true
    val edge = if (!edgeActive) 0 else dragEdgeDirection(edgePointer, drag.rootBounds, edgeWidth)
    LaunchedEffect(edgeActive, edge) {
        if (edge != 0) while (drag.active || widgetSession?.dragging == true) {
            delay(650)
            val next = (pager.currentPage + edge).coerceIn(0, homePages)
            if ((!drag.active && widgetSession?.dragging != true) || next == pager.currentPage) break
            // Do not key this effect on currentPage: it changes halfway through the
            // animation and would cancel the turn before the inner grid is visible.
            // Once the hold commits a turn, finish its animation while the finger moves
            // into the incoming page. Leaving the edge cancels only the next hold timer.
            scope.launch { pager.animateScrollToPage(next) }.join()
        }
    }
    fun finishDrag(cancelled: Boolean) {
        val source = drag.source ?: return
        val moved = drag.moved
        val rawDestination = if (moved && !cancelled) drag.destination(drag.pointer, eligibleDragPages)?.target else null
        val destination = if (rawDestination is DropTarget.Home && source.target is DropTarget.Widget) {
            model.placement(source.target.index)?.let {
                DropTarget.Home(adjustedWidgetDropIndex(rawDestination.index, it, source.bounds, drag.origin))
            }
                ?: rawDestination
        } else rawDestination
        val changed = when {
            source.folderId != null && destination is DropTarget.Folder ->
                model.addAppToFolder(destination.id, source.appId ?: "")
            source.folderId != null && destination != null && source.appId != null ->
                model.removeAppFromFolder(source.folderId, source.appId, destination)
            destination == DropTarget.Remove -> model.removePlacement(source.target)
            destination is DropTarget.Home && source.target is DropTarget.Widget -> model.moveWidgetTo(source.target.index, destination.index)
            destination != null && source.appId != null -> model.applyDrop(source.appId, destination)
            else -> false
        }
        if (moved && !cancelled && changed) haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
        // Like iPhone, dragging something on Home leaves Home in jiggle mode.
        if (moved && !cancelled && source.target !is DropTarget.Library && source.folderId == null) homeEdit.start()
        val returnToLibrary = source.target is DropTarget.Library && source.folderId == null && !changed
        val destinationHomePage = (destination as? DropTarget.Home)?.index?.let(::homeCellPage)
        val currentWindow = pager.settledPage.coerceIn(0, visibleHomePages - 1)
        val page = when (destination) {
            is DropTarget.Home -> if (expandedWorkspace && homeCellPage(destination.index) in eligibleDragPages) currentWindow else destinationHomePage!!
            is DropTarget.Dock -> dropHomePage
            is DropTarget.Widget -> 0
            else -> if (source.target is DropTarget.Library) pager.currentPage else drag.originPage
        }
        scope.launch {
            // Let a new home page compose before removing the temporary drop page.
            withFrameNanos { }
            drag.clear()
            withFrameNanos { }
            pager.scrollToPage(if (returnToLibrary) model.state.value.homePages else page.coerceIn(0, model.state.value.homePages - 1))
            if (!moved && !cancelled) {
                // A held dock app already shows its menu; only an empty slot opens the app chooser.
                if (source.target is DropTarget.Dock) { if (source.appId == null) { dockSlot = source.target.index; sheet = "dock" } else selectedId = source.appId }
                else if (source.target is DropTarget.Widget) { if (focusLock != null) lockNotice++ else { widgetSlot = source.target.index; sheet = "widgetActions" } }
                else if (source.appId?.let(::isFolderId) == true) openFolderId = source.appId
                else if (source.folderId == null) selectedId = source.appId
            }
        }
    }

    val homeLayer = rememberGraphicsLayer()
    DisposableEffect(homeLayer) {
        homeLayer.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
        LiveDiscover.homeLayer = homeLayer
        onDispose { if (LiveDiscover.homeLayer === homeLayer) LiveDiscover.homeLayer = null }
    }
    Box(Modifier.fillMaxSize().graphicsLayer {
        // The feed frame reuses the pager's render nodes in another window. Give Main
        // a complete render target so cross-window damage cannot erase stationary controls.
        // Only Google Discover's hosted feed needs it; with Today View an extra offscreen pass just costs frames.
        compositingStrategy = if (!hostedDiscover) androidx.compose.ui.graphics.CompositingStrategy.Auto
            else androidx.compose.ui.graphics.CompositingStrategy.Offscreen
    }.onSizeChanged { LiveDiscover.fullSize = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }.testTag("launcher-root").homeDragInput(drag,
        enabled = sheet.isEmpty() && !showFirstRun && selectedId == null && resizeSlot == null && pager.currentPage >= 0,
        page = pager.currentPage, eligiblePages = eligibleDragPages, onStart = {
            focus.clearFocus(); keyboard?.hide(); haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            // iPhone: holding an app shows its menu right away (no Android-style pick-up). Moving while still
            // holding dismisses the menu, picks the app up and starts jiggle mode (see the effect below).
            drag.source?.let { src ->
                if (!homeEdit.active && src.appId != null && !isFolderId(src.appId) && src.folderId == null && src.target !is DropTarget.Widget)
                    selectedId = src.appId
            }
            if (drag.source?.folderId != null) openFolderId = null
        },
        onFinish = { cancelled -> finishDrag(cancelled) }, immediate = homeEdit.active)
        .twoFingerSwipeDown(FolioAction.entries.firstOrNull { it.name == state.triggerActions[FolioTrigger.TWO_FINGER_DOWN.name] }
            ?.takeIf { it != FolioAction.NONE && sheet.isEmpty() && !homeEdit.active }) { FolioActions.run(launcherActivity, it) }) { ProvideJiggle(homeEdit) {
        val panelWide = androidx.compose.ui.platform.LocalConfiguration.current.fitsRegularHomeLayout()
        val tone = LocalWallpaperTone.current
        val homeInk = homeInkFor(state.homeInk, tone.prefersDarkText)
        val basePalette = LocalDuoPalette.current
        val tintAmount = state.glassTintAmount
        val tinted = if (tintAmount > 0f) remember(basePalette, tone.primary, tintAmount) { basePalette.copy(glass = tintedGlass(basePalette.glass, tone.primary, tintAmount)) } else basePalette
        // Reduce Transparency: nearly solid glass must still contrast with the text on it, so it's dark under white text
        // and light under dark text (whatever the appearance), keeping a little of the wallpaper tint.
        val palette = if (LocalSolidGlass.current) tinted.copy(glass = tintedGlass(
            if (homeInk.dark) Color(0xFFF2F2F7) else FolioColors.SecondaryBackground, tone.primary, tintAmount * .5f)) else tinted
        val homeApps = remember(state.apps, state.hiddenApps) { HomeApps(state.apps.filter { it.id !in state.hiddenApps && it.available }) { onLaunchFrom(it, null) } }
        CompositionLocalProvider(LocalWidgetStacks provides state.widgetStacks, LocalStackRotate provides state.stackRotate, LocalHomeApps provides homeApps,
            LocalHomeInk provides homeInk, LocalDuoPalette provides palette,
            // Remembered so every icon isn't recomposed each time Home recomposes (a new lambda changes the local).
            LocalStackedApps provides state.iconStacks.keys,
            LocalIconStack provides remember(homeEdit.active, haptic) {
                if (homeEdit.active) null else { app: AppEntry -> haptic.performHapticFeedback(HapticFeedbackType.ContextClick); stackAppId = app.id }
            },
            LocalAppPanel provides remember(state.appPanels, state.featureScopes, homeEdit.active, haptic, panelWide) {
                val panelsOn = FeatureScopes.on(state.featureScopes, "appPanels", state.appPanels, screenFor(panelWide))
                if (panelsOn && !homeEdit.active) { app: AppEntry -> haptic.performHapticFeedback(HapticFeedbackType.ContextClick); panelAppId = app.id } else null
            }) {
        if (!state.systemWallpaper) DuneWallpaper()
        else if (state.wallpaperMotion) SystemWallpaperParallax(nativePager)
        // iOS "dark appearance dims wallpaper".
        val dim by androidx.compose.animation.core.animateFloatAsState(if (state.dimWallpaperDark && appearance.dark) .3f else 0f, label = "wallpaper dim")
        if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        // Home never moves for the keyboard: including IME insets here re-measured the whole grid on every
        // frame of the keyboard animation (Spotlight/search jank). Sheets that need it use imePadding themselves.
        var homeBoxTop by remember { mutableFloatStateOf(0f) }
        val hinge = LocalHinge.current
        BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { homeBoxTop = it.positionInWindow().y }
            .windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets.ime).union(rememberHiddenCameraInsets())
            // Short windows run the rail the full height, so keep the upright island's strip clear there. Regular-size
            // windows (unfolded portrait) keep Home centered: the island sits below the status and beside the dock bar.
            .union(rememberSideIslandInsets(state.island && androidx.compose.ui.platform.LocalConfiguration.current.let {
                !it.fitsRegularHomeLayout() })))) {
            // Tier C: a tiny cover screen gets the focused Home instead of a full page shrunk past tappable sizes.
            // Settings and first-run setup still open over the regular screen below.
            if (isMicroWindow(maxWidth.value, maxHeight.value) && sheet.isEmpty() && !showFirstRun) {
                val microApps = remember(state.dock, state.homeSlots, appsById) {
                    (state.dock + state.homeSlots).filterNotNull().distinct().mapNotNull(appsById::get)
                }
                MicroHome(microApps, deviceStatus, maxWidth, maxHeight, onLaunch = onLaunch,
                    onNotifications = { launcherActivity.openSystemShade(ShadePanel.NOTIFICATIONS) },
                    onSearch = { launcherActivity.openSpotlight() }, onSettings = { sheet = "settings" })
                return@BoxWithConstraints
            }
            val classScale = androidx.compose.ui.platform.LocalConfiguration.current.classScale
            val wide = maxWidth.value * classScale >= 650f && maxHeight.value * classScale >= HOME_REGULAR_MIN_HEIGHT_DP
            val preset = if (wide) state.expanded else state.compact
            val density = LocalDensity.current
            val inLibrary = pager.currentPage == visibleHomePages
            var statusHeight by remember { mutableFloatStateOf(0f) }
            val geometry = homeGeometry(maxWidth.value, maxHeight.value, preset, state.labels,
                statusHeight = if (state.verticalStatus) statusHeight + 22f else 0f,
                labelHeight = with(density) { LocalLabelSize.current.lineSp.sp.toDp().value } + 6f, inLibrary = inLibrary,
                homeBottomSpace = if (isDefaultHome) 44f else 88f,
                // The rail's round search/back controls only show without the search pill or on Discover.
                railControls = !state.searchPill || pager.currentPage < 0, classScale = classScale, appRows = homeAppRows,
                foldAtCenter = hinge?.vertical == true, fillSpace = state.homeRows == 0)
            // Half folded like a laptop: the status (information) stays above the hinge and the dock (controls) goes
            // below it, like Folio's other fold-aware panels; the dock scrolls if the lower half is short.
            val tableHinge = hinge?.takeIf { it.active && !it.vertical }
            val hingeTop = tableHinge?.let { with(density) { (it.startPx - homeBoxTop).toDp().value } }
            val hingeBottom = tableHinge?.let { with(density) { (it.endPx - homeBoxTop).toDp().value } }
            val statusTopShown = if (hingeTop != null && geometry.statusTop + statusHeight > hingeTop - 8f)
                maxOf(16f, hingeTop - 8f - statusHeight) else geometry.statusTop
            val dockTopShown = if (hingeBottom != null && !geometry.horizontalDock && geometry.dockTop < hingeBottom + 8f)
                hingeBottom + 8f else geometry.dockTop
            val dockHeightShown = if (dockTopShown != geometry.dockTop)
                minOf(geometry.dockHeight, (maxHeight.value - dockTopShown - 12f).coerceAtLeast(56f)) else geometry.dockHeight
            // More rows (Automatic): remember how many rows fit this screen, so both screens can show the same number.
            // Only the full-screen Home counts: not split-screen or pop-up windows, short windows, or two-column pages
            // (micro covers never get here).
            val measuresRows = !geometry.splitColumns && maxHeight.value * classScale >= HOME_REGULAR_MIN_HEIGHT_DP &&
                !launcherActivity.isInMultiWindowMode
            LaunchedEffect(measuresRows, wide, geometry.fitAppRows) { if (measuresRows) model.recordHomeFit(wide, geometry.fitAppRows) }
            SideEffect {
                resizePitchX = with(density) { geometry.cellWidth.dp.toPx() }
                resizePitchY = with(density) { minOf((geometry.widgetHeight + 18f) / 2f, geometry.rowHeight).dp.toPx() }
                resizeTopPitch = with(density) { ((geometry.widgetHeight + 18f) / 2f).dp.toPx() }
                resizeAppPitch = with(density) { geometry.rowHeight.dp.toPx() }
            }
            LaunchedEffect(geometry.gridWidth, geometry.widgetHeight, geometry.rowHeight) { resizeSlot = null }
            SideEffect { expandedWorkspace = geometry.expanded }
            // Unfolded with Today View beside Home (or off), there's nothing to the left of Home: spring back.
            val noLeftPageUnfolded = todayMode && geometry.expanded && state.todayUnfolded != "PAGE"
            // Stop the swipe itself (not just spring back): the Today View is already on screen beside Home.
            SideEffect { pageGestures.minPage = if (noLeftPageUnfolded) firstHome else 0 }
            LaunchedEffect(noLeftPageUnfolded, pager.settledPage) {
                if (noLeftPageUnfolded && pager.settledPage < 0) pager.animateScrollToPage(0)
            }
            LaunchedEffect(geometry.expanded) {
                if (!geometry.expanded) {
                    val sessionTargetsLeading = widgetSession?.let { session ->
                        session.candidate?.page == -1 || session.targetIndex?.let(::homeCellPage) == -1
                    } == true
                    val savedTargetLeading = widgetTargetIndex != Int.MIN_VALUE && homeCellPage(widgetTargetIndex) == -1
                    if (sessionTargetsLeading || savedTargetLeading) {
                        widgetSession = null
                        widgetTargetIndex = Int.MIN_VALUE
                        widgetExactTarget = false
                        widgetPackage = null
                        widgetProfileSerial = null
                        widgetPlacementMessage = null
                        sheet = ""
                    }
                    val dragTouchesLeading = drag.source?.page == -1 ||
                        ((target as? DropTarget.Home)?.index?.let(::homeCellPage) == -1)
                    if (dragTouchesLeading) {
                        drag.clear()
                    }
                }
            }
            val contentHeight = maxHeight
            val contentWidth = maxWidth
            val panelWidth = maxWidth - geometry.homeWidth.dp
            // A phone-sized screen keeps the status Side Bar beside Home even with the dock at the bottom.
            val pagerWidth = if (geometry.horizontalDock && !geometry.dockBesideRail) maxWidth else maxWidth - preset.dockWidth.dp - 28.dp
            val leftColumnOrigin = (maxWidth / 2f - geometry.gridWidth.dp) / 2f - 16.dp
            val homeStride = panelWidth - leftColumnOrigin
            val bottomSpace = (if (isDefaultHome) 44.dp else 88.dp) + if (geometry.horizontalDock) (geometry.dockBarHeight + 16f).dp else 0.dp
            val workspaceMotion = if (geometry.expanded) remember(firstHome, visibleHomePages, pagerWidth, homeStride, density) {
                WorkspacePageMotion(firstHome, visibleHomePages, with(density) { pagerWidth.toPx() }, with(density) { homeStride.toPx() })
            } else null
            val dockScroll = rememberScrollState()
            var gestureOriginInRoot by remember { mutableStateOf(Offset.Zero) }
            var gestureOriginInWindow by remember { mutableStateOf(Offset.Zero) }
            var scrubberBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
            val pagerInputEnabled = pager.currentPage in -firstHome..visibleHomePages && !drag.active &&
                widgetSession == null && resizeSlot == null && sheet.isEmpty() && !showFirstRun && selectedId == null &&
                openFolderId == null && emptyCellIndex == null && createFolderFirstId == null &&
                launcherActivity.backups.preview == null && !launcherActivity.backups.pickerPending &&
                !launcherActivity.backgrounds.pickerPending && widgets.setupStatus == null &&
                widgets.reconfigureWidgetId == null
            val libraryPullZone = with(density) { 40.dp.toPx() }
            Box(Modifier.fillMaxSize().onGloballyPositioned {
                gestureOriginInRoot = it.boundsInRoot().topLeft
                gestureOriginInWindow = it.boundsInWindow().topLeft
            }.onePageGestures(
                nativePager,
                pageGestures,
                motion = workspaceMotion,
                enabled = pagerInputEnabled,
                // Positive IDs are provider-owned Android views. Leave their vertical
                // stream untouched so scrollable widgets retain native gesture handling.
                // A dock that is already scrolled, or magnifies under the finger, also gets first use of a downward drag.
                canStartDownwardSwipe = { point ->
                    // App Library: like iOS, Notification Center and Control Center pull down from the top edge; lower
                    // down, a downward drag scrolls the library.
                    if (pager.currentPage == visibleHomePages) point.y < libraryPullZone
                    else if (pager.currentPage !in 0 until visibleHomePages) false else {
                        val region = drag.hit(point + gestureOriginInRoot, eligibleDragPages)
                        val rootOnScreen = IntArray(2).also(launcherRootView::getLocationOnScreen)
                        val screenPoint = point + gestureOriginInWindow +
                            Offset(rootOnScreen[0].toFloat(), rootOnScreen[1].toFloat())
                        // Sliding along the dock magnifies it (Harbor), so it never opens Spotlight.
                        !(region?.target is DropTarget.Dock && (dockScroll.value > 0 ||
                            FeatureScopes.on(state.featureScopes, "dockMagnify", state.dockMagnify, screenFor(wide)))) &&
                            !((region?.target as? DropTarget.Widget)?.index?.let { state.widgetStacks[it]?.isNotEmpty() } == true) &&
                            // A swipe down on a stacked icon opens its stack, not Spotlight.
                            region?.appId?.let { it in state.iconStacks } != true &&
                            !nativeWidgetConsumesVerticalGesture(launcherRootView, screenPoint)
                    }
                },
                canStartGesture = { point -> geometry.expanded || homePages < 2 || !state.pageScrub || !scrubberBounds.contains(point + gestureOriginInRoot) },
                onDownwardSwipe = { panel ->
                    // The App Library has its own search field, so a pull from its top middle does nothing.
                    if (panel == ShadePanel.SEARCH) {
                        if (pager.currentPage != visibleHomePages) when (state.swipeDownHome) {
                            "SPOTLIGHT" -> launcherActivity.openSpotlight()
                            // Notification Center, or Android's own shade when Folio's panels are off.
                            "NOTIFICATIONS" -> launcherActivity.openSystemShade(ShadePanel.NOTIFICATIONS)
                        }
                    }
                    else launcherActivity.openSystemShade(panel)
                },
                onLeadingOverscroll = if (firstHome == 0 && discoverMode) onDiscover else null,
            )) {
            val pagerModifier = Modifier.align(if (state.leftHanded) Alignment.TopEnd else Alignment.TopStart)
                .fillMaxHeight().width(pagerWidth)
                .drawWithContent {
                    // The recorded Home layer only feeds Google Discover's frame; Today View and None draw directly.
                    if (!hostedDiscover) drawContent()
                    else {
                        homeLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(homeLayer)
                    }
                    LiveDiscover.host.get()?.invalidateFrame()
                }.testTag("app-pager")
                .discoverSwipe(discoverMode && firstHome == 0 && pager.currentPage == 0 && !drag.active && sheet.isEmpty() &&
                    !showFirstRun && selectedId == null, onDiscover)
                .onGloballyPositioned {
                    if (firstHome > 0 && !todayMode) {
                        val bounds = it.boundsInWindow()
                        LiveDiscover.pagerOrigin = bounds.topLeft
                        val padding = 32 * density.density
                        LiveDiscover.prepare(launcherActivity,
                            android.graphics.Rect((bounds.left + padding).toInt(), (bounds.top + padding).toInt(),
                                (bounds.right - 16 * density.density).toInt(), (bounds.bottom - padding).toInt()), bounds.width)
                    }
                }
                .semantics { stateDescription = if (pager.currentPage == -1) launcherActivity.getString(R.string.discover) else if (pager.currentPage == visibleHomePages) launcherActivity.getString(R.string.all_apps) else "Home page ${pager.currentPage + 1} of $visibleHomePages" }
            if (geometry.expanded) {
                Box(pagerModifier) {
                    // PagerState remains the source of truth for native Discover progress,
                    // snapping, accessibility state, and programmatic page requests.
                    HorizontalPager(nativePager, Modifier.fillMaxSize(), userScrollEnabled = false,
                        key = { if (it < firstHome) "discover" else if (it - firstHome == visibleHomePages) "library" else "home-${it - firstHome}" }) { }
                    ExpandedWorkspace(
                        nativePager = nativePager, motion = workspaceMotion!!, firstHome = firstHome,
                        visibleHomePages = visibleHomePages, panelWidth = panelWidth,
                        contentHeight = contentHeight, bottomSpace = bottomSpace, geometry = geometry,
                        state = state, previewSlots = previewLayout.slots, previewLeadingSlots = previewLayout.leadingSlots,
                        previewWidgetPlacements = previewLayout.widgetPlacements, appsById = appsById,
                        widgets = widgets, drag = drag, target = target, insertionTarget = insertionTarget,
                        libraryQuery = libraryQuery, onLibraryQuery = { libraryQuery = it },
                        onLaunch = onLaunch, onLaunchFrom = onLaunchFrom, onPinned = model::setPinned,
                        onTurnOnWork = { model.turnOnWork(it) },
                        onActions = { selectedId = it.id }, onWidget = { if (focusLock != null) lockNotice++ else { widgetSlot = it; sheet = "widgetActions" } },
                        onFolder = { openFolderId = it },
                        onEmptyWidget = onEmptyLongPress,
                        onMove = { id, offset -> if (focusLock != null) lockNotice++ else model.move(id, offset) },
                        onRefresh = model::refresh,
                        libraryBack = { libraryBack },
                        leftPageContent = leftPageContent,
                        besideContent = if (todayMode && state.todayUnfolded == "BESIDE") todayContent else null,
                    )
                }
            } else {
                HorizontalPager(nativePager, pagerModifier,
                    // Keep adjacent Home panes attached so ordinary back-and-forth paging does
                    // not synchronously inflate provider RemoteViews inside the gesture frame.
                    // Discover is two physical positions before Home 2. Retain both Home
                    // neighbors to avoid reinflating Home 2's RemoteViews during native exit.
                    beyondViewportPageCount = if (firstHome > 0) 2 else 1,
                    userScrollEnabled = !drag.active && resizeSlot == null, flingBehavior = pageFling,
                    key = { if (it < firstHome) "discover" else if (it - firstHome == visibleHomePages) "library" else "home-${it - firstHome}" }) { physicalPage ->
                    val page = physicalPage - firstHome
                    if (page == -1) {
                        leftPageContent(Modifier.fillMaxSize())
                    } else if (page == visibleHomePages) {
                        AppLibrary(state, libraryQuery, { libraryQuery = it }, onLaunch, model::setPinned,
                            // Unfolded portrait: the Side Bar's status capsule sits in the top corner, so the library keeps
                            // the same side margin Home does instead of running underneath it.
                            onActions = { selectedId = it.id }, modifier = Modifier.fillMaxSize()
                                .graphicsLayer { val b = libraryBack; scaleX = 1f - .14f * b; scaleY = scaleX; alpha = 1f - .35f * b; translationX = size.width * .08f * b }
                                .padding(top = 16.dp, bottom = bottomSpace)
                                .padding(libraryEdges(geometry.horizontalDock && !geometry.dockBesideRail && state.verticalStatus, preset.dockWidth, state.leftHanded)).testTag("library-page"),
                            drag = drag, page = visibleHomePages, onLaunchFrom = onLaunchFrom, onTurnOnWork = { model.turnOnWork(it) })
                    } else {
                        // Centered beside the rail when the grid is narrower than the space (short, wide windows).
                        Row(Modifier.fillMaxSize().testTag("home-surface"), horizontalArrangement = Arrangement.Center) {
                            HomePagePane(page, state, previewLayout.slots, previewLayout.leadingSlots, previewLayout.widgetPlacements, appsById, geometry, contentHeight,
                                bottomSpace, widgets, drag, target, insertionTarget, showLargeWidget = false,
                                onLaunch = onLaunchFrom, onActions = { selectedId = it.id },
                                onWidget = { if (focusLock != null) lockNotice++ else { widgetSlot = it; sheet = "widgetActions" } },
                                onFolder = { openFolderId = it },
                                onEmptyWidget = onEmptyLongPress,
                                onMove = { id, offset -> if (focusLock != null) lockNotice++ else model.move(id, offset) },
                                onRefresh = model::refresh)
                        }
                    }
                }
            }
            if (state.verticalStatus) StatusRail(deviceStatus,
                Modifier.align(railTop(state.leftHanded)).railEdge(state.leftHanded, 12.dp).offset(y = statusTopShown.dp)
                    .width(preset.dockWidth.dp).onSizeChanged {
                        // The whole rail, location slot included: the dock goes below all of it.
                        statusHeight = with(density) { it.height.toDp().value }
                    },
                compact = contentHeight < 500.dp, iconSize = dockIconSize(geometry.iconSize).dp, style = state.statusStyle,
                focus = state.focusModes.firstOrNull { it.id == state.activeFocus },
                // Live activities grow the rail under the status; the dock below moves with the measured height.
                island = if (state.island && state.railActivities) ({
                    RailLiveActivity(IslandListenerService.activity.collectAsState().value
                        ?.takeUnless { it is IslandActivity.Call && "CALL" in state.islandEventsOff }, preset.dockWidth.dp)
                }) else null)
            // Background and border without clipping, so Harbor-style magnified icons can grow past the rail.
            // Portrait unfolded (iPhone Duo): a horizontal dock bar centered along the bottom, above the page controls.
            val dockPitch = geometry.dockPitch
            val dockBarWidth = (dockPitch * state.dock.size + 16f).dp
            Box((if (geometry.horizontalDock) (if (hinge?.active == true && hinge.vertical)
                    // Half folded like a book: the bar sits centered on the trailing half, off the hinge.
                    Modifier.align(Alignment.BottomEnd).padding(end = ((contentWidth / 2 - dockBarWidth) / 2).coerceAtLeast(0.dp))
                else if (geometry.dockBesideRail)
                    // Centered under the grid, which sits beside the status Side Bar.
                    Modifier.align(if (state.leftHanded) Alignment.BottomEnd else Alignment.BottomStart)
                        .padding(start = if (state.leftHanded) 0.dp else ((pagerWidth + 16.dp - dockBarWidth) / 2).coerceAtLeast(0.dp),
                            end = if (state.leftHanded) ((pagerWidth + 16.dp - dockBarWidth) / 2).coerceAtLeast(0.dp) else 0.dp)
                    else Modifier.align(Alignment.BottomCenter))
                    .padding(bottom = (if (isDefaultHome) 44 else 88).dp + 8.dp)
                    .width(dockBarWidth).height(geometry.dockBarHeight.dp)
                else Modifier.align(railTop(state.leftHanded)).railEdge(state.leftHanded, 12.dp).offset(y = dockTopShown.dp)
                    .width(preset.dockWidth.dp).height(dockHeightShown.dp)).graphicsLayer {
                    // Composite the stationary dock independently of the shared pager layer (not while magnifying: it would clip).
                    compositingStrategy = if (state.dockMagnify) androidx.compose.ui.graphics.CompositingStrategy.Auto
                        else androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                }.background(Glass.copy(alpha = state.statusStyle.railGlass), RoundedCornerShape(30.dp))
                .border(1.dp, LocalGlassLook.current.outlineColor, RoundedCornerShape(30.dp)).testTag("dock")) {
                Column(if (geometry.horizontalDock) Modifier.fillMaxSize().padding(horizontal = 8.dp) else Modifier.padding(vertical = 8.dp).verticalScroll(dockScroll)) {
                    DockAppColumn(state.dock, previewLayout.dock, appsById, if (geometry.horizontalDock) dockPitch else geometry.dockRowHeight,
                        dockIconSize(geometry.iconSize), drag, insertionTarget,
                        onLaunch = onLaunchFrom, onChoose = { dockSlot = it; sheet = "dock" },
                        magnify = FeatureScopes.on(state.featureScopes, "dockMagnify", state.dockMagnify, screenFor(wide)) &&
                            !LocalReduceMotion.current, leftHanded = state.leftHanded, horizontal = geometry.horizontalDock)
                }
            }
            Column(Modifier.align(if (state.leftHanded) Alignment.BottomEnd else Alignment.BottomStart).width(pagerWidth)
                .padding(start = if (state.leftHanded) 0.dp else 16.dp, end = if (state.leftHanded) 16.dp else 0.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!isDefaultHome && !homeEdit.active && !drag.active) PreviewBar(onUseAsHome = { sheet = ""; onMakeDefault() },
                    onExit = { launcherActivity.moveTaskToBack(true) })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    // Only when there is a page to the left of Home: with Today View and Discover both off, the
                    // button led nowhere (reported on r/GalaxyFold, 18 Sep 2026).
                    if (!drag.active && (firstHome > 0 || discoverMode)) IconButton(onClick = openDiscover, Modifier.size(32.dp).testTag("discover-page-link")) {
                        Icon(Icons.Rounded.Explore, "Discover", tint = Color.White.copy(alpha = .65f), modifier = Modifier.size(17.dp))
                    }
                    // iOS: a "Search" capsule where the page dots are; the dots come back while paging or editing.
                    // iOS: drag sideways along the Search pill or the dots to scrub through Home pages, a tick per page.
                    var scrubbing by remember { mutableStateOf(false) }
                    val scrubStep = with(density) { 34.dp.toPx() }
                    val showSearchPill = state.searchPill && !homeEdit.active && !drag.active && !scrubbing &&
                        !nativePager.isScrollInProgress && pager.currentPage in 0 until homePages
                    androidx.compose.animation.AnimatedContent(showSearchPill, label = "search pill",
                        // Cover screen only: unfolded, Home already shows two pages side by side.
                        modifier = if (geometry.expanded || homePages < 2 || !state.pageScrub) Modifier else Modifier.onGloballyPositioned { scrubberBounds = it.boundsInRoot() }.pointerInput(homePages) {
                            var startPage = 0
                            var travel = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { scrubbing = true; travel = 0f; startPage = pager.currentPage.coerceIn(0, homePages - 1) },
                                onDragEnd = { scrubbing = false }, onDragCancel = { scrubbing = false },
                            ) { change, amount ->
                                change.consume()
                                travel += amount
                                val page = (startPage + (travel / scrubStep).roundToInt()).coerceIn(0, homePages - 1)
                                if (page != pager.currentPage) {
                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    scope.launch { pager.scrollToPage(page) }
                                }
                            }
                        }.semantics { contentDescription = "Page scrubber" },
                        transitionSpec = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) togetherWith
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) },
                        contentAlignment = Alignment.Center) { pill ->
                        if (pill) HomeSearchPill { if (!state.googleSearch || !onGoogleSearch(null)) launcherActivity.openSpotlight() }
                        else Row(Modifier.height(30.dp).background(if (scrubbing) Color.White.copy(alpha = .18f) else Color.Transparent, CircleShape)
                            .padding(horizontal = if (scrubbing) 6.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (visibleHomePages <= 6) repeat(visibleHomePages) { index ->
                            Box(Modifier.size(28.dp).clip(CircleShape).clickable { scope.launch { pager.animateScrollToPage(index) } }
                                .semantics { contentDescription = if (index == homePages) "New home page" else "Home page ${index + 1}" }, contentAlignment = Alignment.Center) {
                                if (index == homePages) Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                else Box(Modifier.size(if (index == pager.currentPage) 6.dp else 4.dp).background(if (index == pager.currentPage) LocalHomeInk.current.primary else LocalHomeInk.current.faint, CircleShape))
                            }
                        } else Text("${minOf(pager.currentPage + 1, homePages)} / $homePages", color = Color.White, fontSize = 12.sp)
                        }
                    }
                    IconButton(onClick = openLibrary, Modifier.size(32.dp).testTag("library-page-link")) {
                        Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, "All apps page", tint = Color.White.copy(alpha = if (pager.currentPage == homePages) 1f else .6f), modifier = Modifier.size(17.dp))
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(homeEdit.active && sheet.isEmpty(),
                // Unfolded, the bar sits in the free strip at the bottom of the Home half beside the page dots, so it neither
                // covers the widget row nor pushes the grid; folded, it's at the top, clear of the Dynamic Island.
                Modifier.align(when {
                    geometry.expanded -> if (state.leftHanded) Alignment.BottomStart else Alignment.BottomEnd
                    state.leftHanded -> Alignment.TopEnd
                    else -> Alignment.TopStart
                }).width(pagerWidth),
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { if (geometry.expanded) it / 2 else -it / 2 },
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { if (geometry.expanded) it / 2 else -it / 2 }) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp,
                    top = if (geometry.expanded) 0.dp else if (state.island) JIGGLE_BAR_ISLAND_GAP else 2.dp).testTag("jiggle-bar"),
                    horizontalArrangement = if (geometry.expanded) Arrangement.spacedBy(8.dp, Alignment.End) else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically) {
                    val editPage = pager.currentPage.coerceIn(0, homePages - 1)
                    JigglePill("", Icons.Rounded.Add, description = "Add widget") {
                        widgetSlot = model.nextWidgetSlot(); widgetTargetIndex = homeCellIndex(editPage, 0); widgetExactTarget = false
                        widgetPackage = null; widgetProfileSerial = null; sheet = "widgets"
                    }
                    JigglePill(stringResource(R.string.edit), modifier = Modifier.onGloballyPositioned { editPillBounds = it.boundsInWindow().roundToIntRect() }) {
                        emptyCellIndex = homeEdit.lastEmptyIndex?.takeIf { homeCellPage(it) == editPage } ?: homeCellIndex(editPage, 0)
                    }
                    // Unfolded, keep all three together at the top right instead of spread across two pages.
                    if (!geometry.expanded) Spacer(Modifier.weight(1f))
                    JigglePill(stringResource(R.string.done), emphasized = true) { haptic.performHapticFeedback(HapticFeedbackType.Confirm); homeEdit.stop() }
                }
            }
            if (!inLibrary && !drag.active) Column(Modifier.align(railBottom(state.leftHanded)).railEdge(state.leftHanded, 12.dp).padding(bottom = 6.dp)
                .width(preset.dockWidth.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val controlSize = dockIconSize(geometry.iconSize).dp
                if (pager.currentPage == -1) CircleControl(Icons.Rounded.ArrowForward, "Back to home", "discover-home", controlSize) { scope.launch { pager.animateScrollToPage(0) } }
                val searchBounds = remember { android.graphics.Rect() }
                if (!state.searchPill || pager.currentPage !in 0 until homePages) Box(Modifier.onGloballyPositioned { searchBounds.set(it.boundsInWindow().toAndroidBounds()) }) {
                    CircleControl(Icons.Rounded.Search, if (state.googleSearch) "Search Google" else "Search apps", "search", controlSize) {
                        if (!state.googleSearch || !onGoogleSearch(searchBounds)) launcherActivity.openSpotlight()
                    }
                }
            }
            if (sheet.isNotEmpty() && sheet != "widgets") OwnMethod {
                val activeCustomizationPage = if (sheet == "settings:wallpaper") CustomizationPage.WALLPAPER else customizationPage
                ModalBottomSheet(onDismissRequest = {
                    sheet = ""; widgetPackage = null; widgetExactTarget = false
                }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
                    containerColor = MaterialTheme.colorScheme.surface, fullScreen = sheet.startsWith("settings")) {
                    ModalDialogBackHandler {
                        if ((sheet == "settings" || sheet == "settings:wallpaper") &&
                            activeCustomizationPage != CustomizationPage.OVERVIEW) {
                            customizationPage = activeCustomizationPage.parent
                            sheet = "settings"
                        } else {
                            sheet = ""; widgetPackage = null; widgetExactTarget = false
                        }
                    }
                    when (sheet) {
                        "dock" -> AppPicker(state.apps, dockSlot,
                            onSelect = {
                                if (canPlaceInDock(state.layout, it.id)) {
                                    model.applyDrop(it.id, DropTarget.Dock(dockSlot)); sheet = ""
                                }
                            },
                            onClear = { model.removePlacement(DropTarget.Dock(dockSlot)) },
                            onLongClick = { selectedId = it.id; sheet = "" },
                            canSelect = { canPlaceInDock(state.layout, it.id) },
                            blockedHint = if (state.dock.none { it == null }) stringResource(R.string.dock_full_move_an_app_out_first) else null)
                        "pins" -> Column(Modifier.fillMaxHeight(.9f).imePadding()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { sheet = "" }) { Text(stringResource(R.string.done)) }
                            }
                            AppLibrary(state, pinQuery, { pinQuery = it }, onLaunch, model::setPinned,
                                onActions = { selectedId = it.id; sheet = "" }, editing = true, modifier = Modifier.weight(1f).fillMaxWidth(),
                                onTurnOnWork = { model.turnOnWork(it) })
                        }
                        "settings", "settings:wallpaper" -> CustomizationSheet(state, wide, model, isDefaultHome,
                            page = activeCustomizationPage, onPage = { customizationPage = it; sheet = "settings" },
                            onMakeDefault = { sheet = ""; onMakeDefault() },
                            onClose = { sheet = "" }, onEditPins = { sheet = "pins" },
                            onWidget = { widgetSlot = it; widgetPackage = null; widgetProfileSerial = null; widgetExactTarget = false; sheet = "widgets" },
                            onAddWidget = { page -> widgetSlot = model.nextWidgetSlot(); widgetTargetIndex = page * HOME_CELLS; widgetPackage = null; widgetProfileSerial = null; widgetExactTarget = false; sheet = "widgets" },
                            onRemoveWidget = widgets::remove,
                            onExportLayout = { sheet = ""; launcherActivity.backups.startExport() },
                            onSaveLayoutToFolder = { launcherActivity.backups.saveToFolioFolder(it) },
                            onImportLayout = { sheet = ""; launcherActivity.backups.startImport() },
                            appearance = appearance, onAppearanceMode = onAppearanceMode,
                            onAppearanceManual = onAppearanceManual, onAppearanceDeviceLocation = onAppearanceDeviceLocation,
                            onAppearanceClear = onAppearanceClear,
                            onShadeSetup = { sheet = ""; onShadeSetup() },
                            onShowWelcome = { sheet = ""; onShowWelcome() },
                            onShowWhatsNew = { sheet = ""; onShowWhatsNew() },
                            backgrounds = launcherActivity.backgrounds,
                            onWallpaperPreview = { sheet = ""; onWallpaperPreview() }, homePage = pager.currentPage.coerceIn(0, homePages - 1))
                        "widgetActions" -> model.placement(widgetSlot)?.let { placement ->
                            val topPitch = (geometry.widgetHeight + 18f) / 2f
                            val gridSizing = WidgetGridSizing(GRID_COLUMNS, pageRows(placement.page).coerceAtLeast(visibleRows), geometry.cellWidth,
                                minOf(topPitch, geometry.rowHeight), maxOf(topPitch, geometry.rowHeight), 10f, 18f,
                                topRowHeightDp = topPitch, appRowHeightDp = geometry.rowHeight)
                            val constraints = widgets.manager.getAppWidgetInfo(placement.id)?.let { widgets.sizing(it, gridSizing) }
                            WidgetActions(placement, constraints, rows = pageRows(placement.page),
                                stackCards = model.stackCards(placement.slot), stackLabel = { widgetLabel(launcherActivity, it, widgets) },
                                stackRotate = state.stackRotate, onStackRotate = model::setStackRotate,
                                onAddToStack = {
                                    stackTargetSlot = placement.slot; widgetSlot = placement.slot
                                    widgetPackage = null; widgetProfileSerial = null; widgetExactTarget = false; sheet = "widgets"
                                },
                                onRemoveFromStack = { model.removeFromStack(placement.slot, it) },
                                onShowFirstInStack = { model.showFirstInStack(placement.slot, it) },
                                canConfigure = widgets.canReconfigure(placement.id),
                                onConfigure = { widgets.reconfigure(placement.id); sheet = "" },
                                isValid = { x, y -> (x == placement.spanX && y == placement.spanY) || resizeWidget(state.layout, widgetSlot, x, y) != state.layout },
                                onResize = { x, y -> model.resizeWidget(widgetSlot, x, y) },
                                onStartResize = { x, y ->
                                    resizeSlot = widgetSlot; resizeWidth = x; resizeHeight = y
                                    resizeConstraints = constraints; sheet = ""
                                },
                                onMoveToPage = { page ->
                                    (0 until HOME_CELLS).firstOrNull { local ->
                                        widgetCandidate(state.layout, placement.slot, page * HOME_CELLS + local,
                                            placement.spanX, placement.spanY, visibleRows) != null
                                    }?.let { model.moveWidgetTo(placement.slot, page * HOME_CELLS + it) } == true
                                }, homePages = homePages,
                                onReplace = {
                                    widgetPackage = null
                                    widgetProfileSerial = widgets.manager.getAppWidgetInfo(placement.id)?.profile?.let {
                                        launcherActivity.getSystemService(UserManager::class.java).getSerialNumberForUser(it)
                                    }?.takeIf { it >= 0 }
                                    widgetExactTarget = false; sheet = "widgets"
                                },
                                onRemove = { widgets.remove(widgetSlot); sheet = "" },
                                onClose = { sheet = "" })
                        }
                    }
                }
            }
            // iOS-style notice when editing is locked by a Focus.
            FocusLockNotice(lockNotice, focusLock?.mode, Modifier.align(Alignment.TopCenter))
            if (showFirstRun) {
                // Full-screen, iOS Setup Assistant style; Back steps back, Skip Setup or Get Started finishes.
                ModalBottomSheet(onDismissRequest = onFinishFirstRun, modifier = Modifier.testTag("first-run-setup"), fullScreen = true) {
                    Onboarding(isDefaultHome, onMakeDefault, onShadeSetup,
                        systemWallpaper = state.systemWallpaper,
                        onWallpaper = { value ->
                            if (value != state.systemWallpaper) { model.setSystemWallpaper(value); launcherActivity.applyWallpaperWindow(value) }
                        },
                        onFinish = onFinishFirstRun, state = state, model = model)
                }
            }
            if (sheet == "widgets") OwnMethod {
                val catalogProfiles = remember(state.profiles) { state.profiles.filter { it.isPersonal || it.isWork } }
                val selectedProfile = catalogProfiles.firstOrNull { it.userSerial == widgetProfileSerial }
                    ?: catalogProfiles.firstOrNull { it.isPersonal } ?: AppProfile(0, stringResource(R.string.personal), true, false, false, true, true)
                val userManager = remember(launcherActivity) { launcherActivity.getSystemService(UserManager::class.java) }
                val providers = remember(widgetPackage, selectedProfile, sheet, state.apps) {
                    val user = userManager.getUserForSerialNumber(selectedProfile.userSerial)
                    if (user == null || !selectedProfile.available || !selectedProfile.unlocked || selectedProfile.quiet) emptyList()
                    else runCatching { widgetPackage?.let { widgets.providersForPackage(it, user) }
                        ?: widgets.providers(user) }.getOrDefault(emptyList()).filter { provider ->
                        provider.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 &&
                            provider.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER == 0
                    }
                }
                val catalog by produceState<List<WidgetCatalogEntry>?>(null, providers, selectedProfile.userSerial, sheet) {
                    value = withContext(Dispatchers.IO) { widgetCatalog(launcherActivity, providers, selectedProfile) }
                }
                val topPitch = (geometry.widgetHeight + 18f) / 2f
                val pickerSizing = remember(geometry, visibleRows) { WidgetGridSizing(GRID_COLUMNS, visibleRows,
                    geometry.cellWidth, minOf(topPitch, geometry.rowHeight),
                    maxOf(topPitch, geometry.rowHeight), 10f, 18f,
                    topRowHeightDp = topPitch, appRowHeightDp = geometry.rowHeight) }
                val footprint: (AppWidgetProviderInfo) -> WidgetSpan? = { provider ->
                    widgets.sizing(provider, pickerSizing)?.takeIf { it.minimumFitsGrid }?.preferred
                }
                VisualWidgetPicker(catalog, catalogProfiles.ifEmpty { listOf(selectedProfile) }, selectedProfile,
                    onSelectProfile = { widgetProfileSerial = it.userSerial; widgetPlacementMessage = null },
                    onTurnOnWork = { model.turnOnWork(it) }, hiddenForDrag = widgetSession != null,
                    footprint = footprint,
                    onBack = widgetPickerBack,
                    onTap = tap@{ provider ->
                        if (todayAdd) {
                            val span = widgets.sizing(provider, pickerSizing)?.preferred
                            widgets.addToToday(provider, span?.let { TodaySize.forSpan(it.width, it.height) } ?: TodaySize.MEDIUM, pickerSizing)
                            todayAdd = false; sheet = ""; widgetPackage = null
                            return@tap
                        }
                        stackTargetSlot?.let { stackSlot ->
                            val placement = model.placement(stackSlot)
                            val min = widgets.sizing(provider, pickerSizing)?.minimum
                            if (placement == null || (min != null && (min.width > placement.spanX || min.height > placement.spanY))) {
                                widgetPlacementMessage = launcherActivity.getString(R.string.this_widget_needs_a_bigger_space_than_th)
                            } else {
                                widgets.addToStack(stackSlot, provider, pickerSizing)
                                stackTargetSlot = null; sheet = ""; widgetPackage = null; widgetPlacementMessage = null
                            }
                            return@tap
                        }
                        footprint(provider)?.let { preferredSpan ->
                            val existing = model.placement(widgetSlot)
                            val constraints = widgets.sizing(provider, pickerSizing)
                            val span = existing?.let { placement ->
                                WidgetSpan(placement.spanX, placement.spanY).takeIf {
                                    constraints != null && it.width in constraints.minimum.width..constraints.maximum.width &&
                                        it.height in constraints.minimum.height..constraints.maximum.height
                                }
                            } ?: preferredSpan
                            val special = existing?.takeIf { it.row + it.spanY > GRID_ROWS }
                            if (special != null) {
                                widgetSession = WidgetPickerSession(provider, widgetSlot,
                                    WidgetSpan(special.spanX, special.spanY), Offset.Zero,
                                    dragging = false, candidate = special)
                                widgetPlacementMessage = null
                                scope.launch { pager.scrollToPage(special.page.coerceAtLeast(0).coerceAtMost(homePages - 1)) }
                                return@let
                            }
                            val requestedIndex = existing?.let { homeCellIndex(it.page, it.row * GRID_COLUMNS + it.column) }
                                ?: widgetTargetIndex.takeUnless { it == Int.MIN_VALUE } ?: 0
                            val requestedPage = homeCellPage(requestedIndex).coerceIn(if (expandedWorkspace) -1 else 0, homePages)
                            val availablePages = (if (expandedWorkspace) -1 else 0)..homePages
                            val autoPages = (listOf(requestedPage) + availablePages.filter { it != requestedPage })
                            val freeIndex = if (existing != null || widgetExactTarget) requestedIndex.takeIf {
                                draftAt(it, span, widgetSlot) != null
                            } else autoPages.asSequence().flatMap { page ->
                                (0 until HOME_CELLS).asSequence().map { homeCellIndex(page, it) }
                            }.firstOrNull { widgetCandidate(state.layout, widgetSlot, it, span.width, span.height, visibleRows) != null }
                            val targetIndex = freeIndex ?: requestedIndex
                            widgetSession = WidgetPickerSession(provider, widgetSlot, span, Offset.Zero,
                                dragging = false, targetIndex = targetIndex)
                            widgetPlacementMessage = if (freeIndex == null)
                                launcherActivity.getString(R.string.there_isn_t_room_for_this_size_choose_an) else null
                            scope.launch { pager.scrollToPage(homeCellPage(targetIndex).coerceIn(0, homePages)) }
                        }
                    },
                    onBuiltin = builtin@{ builtinId ->
                        if (todayAdd) { model.addTodayWidget(builtinId, TodaySize.SMALL); todayAdd = false; sheet = ""; return@builtin }
                        stackTargetSlot?.let { stackSlot ->
                            model.addToStack(stackSlot, builtinId); stackTargetSlot = null; sheet = ""; widgetPackage = null
                            return@builtin
                        }
                        val existing = model.placement(widgetSlot)
                        val special = existing?.takeIf { it.row + it.spanY > GRID_ROWS }
                        // Big Clock starts as wide as the page, like the Lock Screen clock; the others are small.
                        val span = existing?.let { WidgetSpan(it.spanX, it.spanY) } ?: WidgetSpan(if (builtinId == BIG_CLOCK_WIDGET) 4 else 2, 2)
                        if (special != null) {
                            widgetSession = WidgetPickerSession(null, widgetSlot, span, Offset.Zero,
                                dragging = false, candidate = special, builtinId = builtinId)
                            widgetPlacementMessage = null
                            scope.launch { pager.scrollToPage(special.page.coerceAtLeast(0).coerceAtMost(homePages - 1)) }
                            return@builtin
                        }
                        val requested = existing?.let {
                            homeCellIndex(it.page, it.row * GRID_COLUMNS + it.column)
                        } ?: widgetTargetIndex.takeUnless { it == Int.MIN_VALUE } ?: 0
                        val requestedPage = homeCellPage(requested).coerceIn(if (expandedWorkspace) -1 else 0, homePages)
                        val availablePages = (if (expandedWorkspace) -1 else 0)..homePages
                        val exact = model.placement(widgetSlot) != null || widgetExactTarget
                        val candidates = if (exact) sequenceOf(requested)
                            else (listOf(requestedPage) + availablePages.filter { it != requestedPage }).asSequence()
                                .flatMap { page -> (0 until HOME_CELLS).asSequence().map { homeCellIndex(page, it) } }
                        val free = candidates.firstOrNull {
                            widgetCandidate(state.layout, widgetSlot, it, span.width, span.height, if (exact) pageRows(homeCellPage(it)) else visibleRows) != null
                        }
                        widgetSession = WidgetPickerSession(null, widgetSlot, span, Offset.Zero,
                            dragging = false, targetIndex = free ?: requested, builtinId = builtinId)
                        widgetPlacementMessage = if (free == null)
                            launcherActivity.getString(R.string.there_isn_t_room_for_this_card_choose_an) else null
                        scope.launch { pager.scrollToPage(homeCellPage(free ?: requested).coerceIn(0, homePages)) }
                    },
                    onDragStart = { provider, point ->
                        if (stackTargetSlot == null && !todayAdd) footprint(provider)?.let { span ->
                            widgetSession = WidgetPickerSession(provider, widgetSlot, span, point, dragging = true)
                            widgetPlacementMessage = null
                            scope.launch { pager.scrollToPage(lastHomePage.coerceIn(0, homePages - 1)) }
                        }
                    },
                    onDrag = { point -> widgetSession = widgetSession?.copy(pointer = point) },
                    onDrop = {
                        val session = widgetSession
                        if (session != null && widgetDraft != null) {
                            session.provider?.let { widgets.add(widgetDraft, it, pickerSizing) }
                                ?: session.builtinId?.let { widgets.setBuiltin(widgetDraft.copy(id = it)) }
                            widgetSession = null; sheet = ""; widgetPackage = null
                        } else {
                            leaveTemporaryWidgetPage(); widgetSession = null
                            widgetPlacementMessage = launcherActivity.getString(R.string.there_isn_t_room_there_try_another_space)
                        }
                    },
                    onCancelDrag = {
                        if (widgetSession != null) {
                            leaveTemporaryWidgetPage(); widgetSession = null
                        }
                    })
                widgetSession?.let { session ->
                    val placementDensity = LocalDensity.current
                    val sessionEntry = session.provider?.let { selected -> catalog?.firstOrNull {
                        it.provider.provider == selected.provider && it.provider.profile == selected.profile } }
                    // Legacy overflow replacements are locked to their existing view
                    // bounds and may begin below the canonical six-row grid. They have
                    // no Home-cell address; specialAnchor below is their visual anchor.
                    val candidateIndex = widgetDraft?.takeIf { session.candidate == null }
                        ?.let { homeCellIndex(it.page, it.row * GRID_COLUMNS + it.column) }
                    val visualIndex = candidateIndex ?: widgetRawTarget?.index ?: session.targetIndex
                    val specialAnchor = session.candidate?.let { drag.regions[DropTarget.Widget(session.slot)]?.bounds }
                    val anchor = specialAnchor ?: visualIndex?.let { drag.regions[DropTarget.Home(it)]?.bounds }
                    Box(Modifier.fillMaxSize().testTag("widget-placement-mode")
                        .then(if (!session.dragging && session.candidate == null) Modifier.pointerInput(session.slot, session.span) {
                            detectTapGestures { local ->
                                val point = local + drag.rootOrigin
                                val cell = drag.regions.values.firstOrNull {
                                    it.target is DropTarget.Home && it.page in eligibleDragPages && it.bounds.contains(point)
                                }?.target as? DropTarget.Home
                                cell?.let { widgetSession = session.copy(pointer = point, targetIndex = it.index) }
                            }
                        } else Modifier)) {
                        Row(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.folioSafeTop).padding(top = 8.dp)
                            .background(Glass.copy(alpha = .97f), RoundedCornerShape(22.dp))
                            .testTag("widget-placement-toolbar"), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = widgetPickerBack) { Text(stringResource(R.string.back_to_widgets)) }
                            if (session.candidate != null) Text(stringResource(R.string.replace_here), color = Ink,
                                modifier = Modifier.testTag("widget-replacement-locked"))
                            val targetPage = homeCellPage(session.targetIndex ?: 0)
                            if (!session.dragging && session.candidate == null) IconButton(
                                enabled = targetPage > if (expandedWorkspace) -1 else 0, onClick = {
                                val local = homeCellLocal(session.targetIndex ?: 0)
                                val page = targetPage - 1
                                widgetSession = session.copy(targetIndex = homeCellIndex(page, local))
                                scope.launch { pager.animateScrollToPage(page.coerceAtLeast(0)) }
                            }) { Icon(Icons.Rounded.ChevronLeft, "Previous home page") }
                            Text("${session.span.width} × ${session.span.height}", color = Ink)
                            if (!session.dragging && session.candidate == null) IconButton(enabled = targetPage < homePages, onClick = {
                                val local = homeCellLocal(session.targetIndex ?: 0)
                                val page = (targetPage + 1).coerceAtMost(homePages)
                                widgetSession = session.copy(targetIndex = homeCellIndex(page, local))
                                scope.launch { pager.animateScrollToPage(page.coerceAtLeast(0)) }
                            }) { Icon(Icons.Rounded.ChevronRight, "Next home page") }
                            if (!session.dragging) TextButton(enabled = widgetDraft != null, onClick = {
                                widgetDraft?.let { draft ->
                                    val contentSize = specialAnchor?.let { bounds -> with(placementDensity) {
                                        WidgetContentSize(bounds.width.toDp().value, bounds.height.toDp().value)
                                    } }
                                    session.provider?.let { widgets.add(draft, it, pickerSizing, contentSize) }
                                        ?: session.builtinId?.let { widgets.setBuiltin(draft.copy(id = it)) }
                                    widgetSession = null; sheet = ""; widgetPackage = null
                                }
                            }, modifier = Modifier.testTag("widget-placement-apply")) { Text(stringResource(R.string.place)) }
                            TextButton(onClick = { leaveTemporaryWidgetPage(); widgetSession = null; sheet = ""; widgetPackage = null },
                                modifier = Modifier.testTag("widget-placement-cancel")) { Text(stringResource(R.string.cancel)) }
                        }
                        if (anchor != null) {
                            val density = LocalDensity.current
                            val cellWidthPx = with(density) { geometry.cellWidth.dp.toPx() }
                            fun pickerRowTop(row: Int): Float = if (row <= 2) row * with(density) { topPitch.dp.toPx() }
                                else with(density) { (geometry.widgetHeight + 18f + (row - 2) * geometry.rowHeight).dp.toPx() }
                            val candidateRow = homeCellLocal(visualIndex ?: 0) / GRID_COLUMNS
                            val previewWidth = specialAnchor?.let { with(density) { it.width.toDp() } }
                                ?: with(density) { (cellWidthPx * session.span.width - 10.dp.toPx()).toDp() }
                            val previewHeight = specialAnchor?.let { with(density) { it.height.toDp() } }
                                ?: with(density) { (pickerRowTop(candidateRow + session.span.height) -
                                    pickerRowTop(candidateRow) - 18.dp.toPx()).coerceAtLeast(48.dp.toPx()).toDp() }
                            val previewX = if (specialAnchor != null) anchor.left
                                else anchor.left + with(density) { 5.dp.toPx() }
                            Surface(Modifier.offset { IntOffset(previewX.roundToInt(), anchor.top.roundToInt()) }
                                .size(previewWidth, previewHeight).testTag("widget-placement-preview")
                                .semantics { stateDescription = if (widgetDraft != null) launcherActivity.getString(R.string.ready_to_place) else launcherActivity.getString(R.string.no_room_here) },
                                color = if (widgetDraft != null) Glass.copy(alpha = .82f) else Color(0xFFE7B6B6).copy(alpha = .9f),
                                shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(3.dp,
                                    if (widgetDraft != null) Color.White else Color(0xFFFF6B6B))) {
                                Box(Modifier.fillMaxSize()) {
                                    if (sessionEntry != null) WidgetProviderPreview(sessionEntry, session.span,
                                        Modifier.fillMaxSize().padding(5.dp).clip(RoundedCornerShape(18.dp)))
                                    else Column(Modifier.align(Alignment.Center).padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(session.provider?.loadLabel(launcherActivity.packageManager)?.toString()
                                            ?: when (session.builtinId) {
                                                CLOCK_WIDGET -> stringResource(R.string.clock)
                                                DATE_WIDGET -> stringResource(R.string.date)
                                                UP_NEXT_WIDGET -> stringResource(R.string.up_next)
                                                SUGGESTIONS_WIDGET -> stringResource(R.string.suggestions)
                                                BIG_CLOCK_WIDGET -> stringResource(R.string.big_clock)
                                                else -> stringResource(R.string.widget_panel)
                                            }, color = Ink,
                                            textAlign = TextAlign.Center)
                                        Text("${session.span.width} × ${session.span.height}", color = Ink)
                                    }
                                    if (widgetDraft == null) Box(Modifier.matchParentSize()
                                        .background(Color(0xFFB83B3B).copy(alpha = .34f)), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.no_room_here), color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        } else if (session.dragging) {
                            Surface(Modifier.offset { IntOffset((session.pointer.x - 90.dp.toPx()).roundToInt(),
                                (session.pointer.y - 60.dp.toPx()).roundToInt()) }.size(180.dp, 120.dp)
                                .testTag("widget-placement-preview").semantics { stateDescription = "No room here" },
                                color = Color(0xFFE7B6B6).copy(alpha = .9f), shape = RoundedCornerShape(24.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (sessionEntry != null) WidgetProviderPreview(sessionEntry, session.span,
                                        Modifier.fillMaxSize().padding(5.dp).clip(RoundedCornerShape(18.dp)))
                                    Box(Modifier.matchParentSize().background(Color(0xFFB83B3B).copy(alpha = .34f)),
                                        contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_room_here), color = Color.White) }
                                }
                            }
                        }
                    }
                }
                widgetPlacementMessage?.let { message ->
                    Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp),
                        color = Glass, shape = RoundedCornerShape(18.dp)) { Text(message, Modifier.padding(16.dp), color = Ink) }
                }
            }
        }
        if (drag.active) {
            if (drag.moved) {
                if (pager.currentPage > 0) Box(Modifier.align(Alignment.CenterStart).width(6.dp).height(112.dp)
                    .background(Color.White.copy(alpha = if (edge < 0) .9f else .3f), RoundedCornerShape(6.dp)).testTag("drag-edge-left"))
                if (pager.currentPage < homePages) Box(Modifier.align(Alignment.CenterEnd).width(6.dp).height(112.dp)
                    .background(Color.White.copy(alpha = if (edge > 0) .9f else .3f), RoundedCornerShape(6.dp)).testTag("drag-edge-right"))
            }
            appsById[drag.source?.appId]?.let { app ->
                val size = 66.dp
                val px = with(LocalDensity.current) { size.toPx() }
                AppIcon(app, "Moving ${app.label}", Modifier
                    .offset { IntOffset((drag.pointer.x - drag.rootOrigin.x - px / 2).roundToInt(), (drag.pointer.y - drag.rootOrigin.y - px * .65f).roundToInt()) }
                    .size(size).shadow(16.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).testTag("drag-ghost"))
            }
            drag.source?.appId?.let { state.layout.folder(it) }?.let { folder ->
                Surface(Modifier.offset { IntOffset((drag.pointer.x - drag.rootOrigin.x - 42.dp.toPx()).roundToInt(),
                    (drag.pointer.y - drag.rootOrigin.y - 52.dp.toPx()).roundToInt()) }.size(84.dp)
                    .shadow(16.dp, RoundedCornerShape(20.dp)).testTag("folder-drag-ghost"),
                    color = Glass.copy(alpha = .96f), shape = RoundedCornerShape(20.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(folder.title, color = Ink, textAlign = TextAlign.Center) }
                }
            }
            drag.source?.widgetId?.let { id ->
                val width = 144.dp; val height = 108.dp
                val x = with(LocalDensity.current) { width.toPx() }
                val y = with(LocalDensity.current) { height.toPx() }
                Surface(Modifier.offset { IntOffset((drag.pointer.x - x / 2).roundToInt(), (drag.pointer.y - y * .65f).roundToInt()) }
                    .size(width, height).shadow(16.dp, RoundedCornerShape(24.dp)).testTag("drag-ghost"),
                    color = Glass.copy(alpha = .95f), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Widgets, null, tint = Ink)
                        Spacer(Modifier.height(8.dp))
                        Text(remember(id, widgets) { widgetLabel(launcherActivity, id, widgets) }, color = Ink, maxLines = 2, textAlign = TextAlign.Center)
                    }
                }
            }
            if (blockedDock) Surface(
                Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.folioSafeTop)
                    .padding(top = 10.dp, start = 20.dp, end = 100.dp),
                color = Glass.copy(alpha = .96f), shape = RoundedCornerShape(18.dp)
            ) {
                Text(stringResource(R.string.dock_full_move_an_app_out_first),
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = Ink, fontSize = 13.sp)
            }
            if (drag.moved && drag.source?.target !is DropTarget.Library &&
                drag.source?.appId?.let(::isFolderId) != true) Surface(
                // Keep removal in the right-side control area that is vacated during a drag.
                // A centered target overlaps the expanded workspace's right-hand first cell.
                Modifier.align(railBottom(state.leftHanded)).navigationBarsPadding().railEdge(state.leftHanded, 12.dp).padding(bottom = 12.dp)
                    .width((if (expandedWorkspace) state.expanded else state.compact).dockWidth.dp).height(64.dp)
                    .dropRegion(drag, DropTarget.Remove).testTag("remove-drop-target"),
                color = if (target == DropTarget.Remove) Color(0xFFB33B3B) else Glass.copy(alpha = .96f), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxSize().padding(vertical = 6.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.DeleteOutline, null)
                    Text(stringResource(R.string.remove), fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        resizeSlot?.let { slot ->
            val placement = model.placement(slot)
            val bounds = drag.regions[DropTarget.Widget(slot)]?.bounds
            if (placement != null && bounds != null) {
                val minW = resizeConstraints?.minimum?.width ?: 2
                val minH = resizeConstraints?.minimum?.height ?: 2
                val maxW = minOf(GRID_COLUMNS - placement.column, resizeConstraints?.maximum?.width ?: GRID_COLUMNS)
                val maxH = minOf(pageRows(placement.page) - placement.row, resizeConstraints?.maximum?.height ?: GRID_ROWS)
                val feasible = placement.page >= -1 && placement.row in 0 until GRID_ROWS &&
                    !(placement.id >= 0 && resizeConstraints == null) && minW <= maxW && minH <= maxH
                val candidate = resizeWidget(state.layout, slot, resizeWidth, resizeHeight)
                val valid = feasible && ((resizeWidth == placement.spanX && resizeHeight == placement.spanY) || candidate != state.layout)
                val widthPx = (bounds.width + (resizeWidth - placement.spanX) * resizePitchX).coerceAtLeast(resizePitchX)
                val density = LocalDensity.current
                fun resizeRowTop(row: Int) = if (row <= 2) row * resizeTopPitch else 2 * resizeTopPitch + (row - 2) * resizeAppPitch
                val heightPx = (resizeRowTop(placement.row + resizeHeight) - resizeRowTop(placement.row) -
                    with(density) { 18.dp.toPx() }).coerceAtLeast(resizePitchY)
                Box(Modifier.offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                    .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
                    .border(3.dp, if (valid) Color.White else Color(0xFFFF6B6B), RoundedCornerShape(24.dp))
                    .testTag("widget-resize-preview-$slot")) {
                    Box(Modifier.align(Alignment.BottomEnd).offset(12.dp, 12.dp).size(44.dp)
                        .background(if (valid) Color.White else Color(0xFFFF6B6B), CircleShape)
                        .testTag("widget-resize-handle-$slot")
                        .pointerInput(slot, resizeConstraints) {
                            var dx = 0f; var dy = 0f; var startWidth = resizeWidth; var startHeight = resizeHeight
                            detectDragGestures(onDragStart = {
                                dx = 0f; dy = 0f; startWidth = resizeWidth; startHeight = resizeHeight
                            }, onDrag = { change, amount ->
                                change.consume(); dx += amount.x; dy += amount.y
                                if (feasible && resizeConstraints?.canResizeHorizontally != false)
                                    resizeWidth = (startWidth + (dx / resizePitchX).roundToInt()).coerceIn(minW, maxW)
                                if (feasible && resizeConstraints?.canResizeVertically != false)
                                    resizeHeight = (startHeight + (dy / resizePitchY).roundToInt()).coerceIn(minH, maxH)
                            })
                        }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.OpenInFull, "Drag to resize widget", tint = Ink, modifier = Modifier.size(22.dp))
                    }
                    Row(Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                        .background(Glass.copy(alpha = .96f), RoundedCornerShape(20.dp))) {
                        TextButton(onClick = { resizeSlot = null }) { Text(stringResource(R.string.cancel)) }
                        TextButton(enabled = valid, onClick = {
                            model.resizeWidget(slot, resizeWidth, resizeHeight); resizeSlot = null
                        }) { Text(stringResource(R.string.apply)) }
                    }
                    if (!feasible) Text(stringResource(R.string.move_this_widget_into_the_six_row_grid_b),
                        color = Color.White, modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = .65f)).padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
        }
        appsById[stackAppId]?.let { anchor ->
            val stacked = state.iconStacks[anchor.id].orEmpty().mapNotNull(appsById::get)
            if (stacked.isEmpty()) LaunchedEffect(anchor.id) { stackAppId = null }
            else IconStackFan(anchor, stacked, onDismiss = { stackAppId = null }) { stackAppId = null; onLaunchFrom(it, IconBounds.of(anchor.id)) }
        }
        appsById[stackEditId]?.let { anchor ->
            ModalBottomSheet(onDismissRequest = { stackEditId = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                IconStackEditor(anchor, state.apps.filter { it.id !in state.hiddenApps }, state.iconStacks[anchor.id].orEmpty(),
                    onToggle = { model.toggleStackApp(anchor.id, it) }, onDone = { stackEditId = null })
            }
        }
        appsById[panelAppId]?.let { app ->
            AppPanel(app, onDismiss = { panelAppId = null }, onOpen = { panelAppId = null; onLaunchFrom(app, IconBounds.of(app.id)) })
        }
        appsById[selectedId]?.let { app ->
            val pinned = state.layout.indexOfShortcut(app.id) != null
            val packageName = app.packageName
            val hasWidgets = packageName.isNotEmpty() && runCatching {
                widgets.providersForPackage(packageName, app.user)
            }.getOrDefault(emptyList()).isNotEmpty()
            val openWidgetsFor: (() -> Unit)? = if (hasWidgets) {{
                val page = lastHomePage.coerceIn(0, homePages - 1)
                widgetTargetIndex = homeCellIndex(page, 0); widgetExactTarget = false
                widgetSlot = model.nextWidgetSlot(); widgetPackage = packageName
                widgetProfileSerial = app.userSerial; selectedId = null; sheet = "widgets"
            }} else null
            // iPhone-style menu next to the icon; "Edit Home Screen" starts jiggle mode for moving.
            AppContextMenu(app, onHome = pinned, hidden = app.id in state.hiddenApps,
                lockedBy = focusLock?.mode?.name,
                onDismiss = { selectedId = null }, onMove = { selectedId = null; homeEdit.start() },
                onAddOrRemove = { if (app.isShortcut) model.deleteShortcut(app) else model.setPinned(app.id, !pinned); selectedId = null },
                onCreateFolder = { createFolderFirstId = app.id; selectedId = null }, hasFolders = state.folders.any { app.id !in it.appIds },
                onWidgets = openWidgetsFor,
                onToggleHidden = { model.setHidden(app.id, app.id !in state.hiddenApps); selectedId = null },
                onInfo = { onAppInfo(app); selectedId = null },
                onRename = { renameAppId = app.id; selectedId = null },
                onStack = if (pinned) {{ stackEditId = app.id; selectedId = null }} else null)
        }
        appsById[renameAppId]?.let { app ->
            RenameAppAlert(app, onDismiss = { renameAppId = null }, onRename = { model.renameApp(app.id, it); renameAppId = null })
        }
        emptyCellIndex?.let { index ->
            HomeEditMenu(anchor = editPillBounds.takeIf { homeEdit.active }, onDismiss = { emptyCellIndex = null },
                onWidgets = {
                    widgetTargetIndex = index; widgetExactTarget = true; widgetSlot = model.nextWidgetSlot(); widgetPackage = null; widgetProfileSerial = null
                    sheet = "widgets"
                }, onWallpaper = { sheet = "settings:wallpaper" },
                onCustomize = { sheet = "settings" },
                onAddPage = { val page = model.addPage(); if (page >= 0) scope.launch { pager.animateScrollToPage(page) } },
                onRemovePage = if (homeCellPage(index) == homePages - 1 && homePages > 1 && state.layout.contentPageCount < homePages) {{
                    if (model.removeLastEmptyPage()) scope.launch { pager.animateScrollToPage(homePages - 2) }
                }} else null)
        }
        createFolderFirstId?.let { firstId ->
            val first = appsById[firstId]
            // Existing folders first (the only other way in is dragging onto one), then a new folder with another app.
            val folders = state.folders.filter { firstId !in it.appIds }
            AlertDialog(onDismissRequest = { createFolderFirstId = null },
                title = { Text(if (folders.isEmpty()) "Create folder with ${first?.label ?: "app"}" else "Add ${first?.label ?: "app"} to a folder") },
                text = { LazyColumn(Modifier.heightIn(max = 420.dp).testTag("folder-app-picker")) {
                    items(folders, key = { it.id }) { folder ->
                        TextButton(onClick = { model.addAppToFolder(folder.id, firstId); createFolderFirstId = null },
                            modifier = Modifier.fillMaxWidth().testTag("folder-add-${folder.id}")) {
                            Icon(Icons.Rounded.Folder, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
                            Text("${folder.title} (${folder.appIds.size})", Modifier.weight(1f))
                        }
                    }
                    if (folders.isNotEmpty()) item("new-folder-header") {
                        Text(stringResource(R.string.new_folder_with), style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp))
                    }
                    items(state.apps.filter { it.id != firstId && it.available }, key = { it.id }) { second ->
                        TextButton(onClick = {
                            val preferredPage = state.layout.indexOfShortcut(firstId)?.let(::homeCellPage)
                                ?.takeIf { it >= 0 || expandedWorkspace } ?: lastHomePage.coerceIn(0, homePages - 1)
                            val blocked = state.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
                            val targetIndex = (0 until HOME_CELLS).map { homeCellIndex(preferredPage, it) }
                                .firstOrNull { it !in blocked && homeCellShown(it, homeAppRows) && state.layout.slotAt(it) in listOf(null, firstId, second.id) }
                                ?: state.layout.indexOfShortcut(firstId)
                            if (targetIndex != null) model.createFolder(firstId, second.id, targetIndex)
                            createFolderFirstId = null
                        }, modifier = Modifier.fillMaxWidth().testTag("folder-app-${second.id}")) {
                            Text(second.label, Modifier.fillMaxWidth())
                        }
                    }
                } }, confirmButton = { TextButton(onClick = { createFolderFirstId = null }) { Text(stringResource(R.string.cancel)) } })
        }
        openFolderId?.let { id ->
            state.folders.firstOrNull { it.id == id }?.let { folder ->
                val blocked = state.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
                val destinationPages = (if (expandedWorkspace) listOf(-1) else emptyList()) + (0 until homePages)
                val homeDestinations = destinationPages.mapNotNull { destinationPage ->
                    (0 until HOME_CELLS).map { homeCellIndex(destinationPage, it) }
                        .firstOrNull { it !in blocked && homeCellShown(it, homeAppRows) && state.layout.slotAt(it) == null }
                }
                FolderPanel(folder, appsById, drag, pager.currentPage, homeDestinations,
                    dockVacancies = state.dock.indices.filter { state.dock[it] == null },
                    onDismiss = { openFolderId = null }, onRename = { model.renameFolder(id, it) },
                    color = state.folderColors[id], onColor = { model.setFolderColor(id, it) },
                    onLaunch = onLaunchFrom,
                    onMoveOut = { appId, destination ->
                        if (model.removeAppFromFolder(id, appId, destination)) openFolderId = model.folder(id)?.id
                    })
            } ?: LaunchedEffect(id) { openFolderId = null }
        }
        launcherActivity.backups.preview?.let { preview ->
            LayoutRestorePreview(preview, onRestore = {
                launcherActivity.backups.applyImport(); sheet = ""
            }, onCancel = launcherActivity.backups::cancelImport)
        }
        if (launcherActivity.backups.pickerPending) AlertDialog(onDismissRequest = {},
            title = { Text(stringResource(R.string.layout_document)) },
            text = { Text(stringResource(R.string.the_system_document_picker_is_still_open)) },
            confirmButton = { TextButton(onClick = { launcherActivity.backups.resumePendingPicker() },
                modifier = Modifier.testTag("backup-picker-resume")) { Text(stringResource(R.string.resume)) } },
            dismissButton = { TextButton(onClick = launcherActivity.backups::cancelImport,
                modifier = Modifier.testTag("backup-picker-cancel")) { Text(stringResource(R.string.cancel)) } })
        if (launcherActivity.backgrounds.pickerPending && !launcherActivity.backgrounds.loading) AlertDialog(
            onDismissRequest = {}, title = { Text(stringResource(R.string.background_photo)) },
            text = { Text(stringResource(R.string.the_photo_picker_was_interrupted_resume)) },
            confirmButton = { TextButton(onClick = launcherActivity.backgrounds::choosePhoto,
                modifier = Modifier.testTag("background-picker-resume")) { Text(stringResource(R.string.resume)) } },
            dismissButton = { TextButton(onClick = launcherActivity.backgrounds::cancelPendingSelection,
                modifier = Modifier.testTag("background-picker-cancel")) { Text(stringResource(R.string.cancel)) } })
        (launcherActivity.backups.errorMessage ?: launcherActivity.backups.successMessage)?.let { message ->
            AlertDialog(onDismissRequest = launcherActivity.backups::clearMessage,
                title = { Text(if (launcherActivity.backups.errorMessage != null) stringResource(R.string.layout_backup_problem) else stringResource(R.string.layout_backup)) },
                text = { Text(message) }, confirmButton = { TextButton(onClick = launcherActivity.backups::clearMessage) { Text(stringResource(R.string.ok)) } })
        }
        widgets.failureMessage?.let { message ->
            AlertDialog(onDismissRequest = widgets::clearFailure, title = { Text(stringResource(R.string.widget_not_added)) },
                text = { Text(message, Modifier.testTag("widget-bind-error")) },
                confirmButton = { TextButton(onClick = widgets::clearFailure) { Text(stringResource(R.string.ok)) } })
        }
        if (widgets.pendingPlacement != null && widgets.setupStatus != null) {
            AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.finish_widget_setup)) },
                text = { Text(stringResource(R.string.the_widget_is_waiting_at_its_chosen_spot)) },
                confirmButton = { TextButton(onClick = widgets::finishPendingSetup,
                    modifier = Modifier.semantics { contentDescription = "Continue widget setup" }) { Text(stringResource(R.string.finish_setup)) } },
                dismissButton = { TextButton(onClick = { leaveTemporaryWidgetPage(); widgets.cancelPendingSetup() },
                    modifier = Modifier.semantics { contentDescription = "Cancel widget setup" }) { Text(stringResource(R.string.cancel)) } })
        }
        widgets.reconfigureWidgetId?.let {
            AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.widget_settings)) },
                text = { Text(stringResource(R.string.widget_settings_were_interrupted_resume)) },
                confirmButton = { TextButton(onClick = widgets::finishPendingReconfigure,
                    modifier = Modifier.testTag("widget-reconfigure-resume")) { Text(stringResource(R.string.resume)) } },
                dismissButton = { TextButton(onClick = widgets::cancelPendingReconfigure,
                    modifier = Modifier.testTag("widget-reconfigure-cancel")) { Text(stringResource(R.string.cancel)) } })
        }
        }
    } } }
}

// Keeps a block in its own compiled method. Home's content lambda outgrew 256 registers, and R8 then wrote a
// register into an 8-bit slot, so optimized builds failed ART verification at launch.
@Composable
private fun BoxScope.OwnMethod(content: @Composable BoxScope.() -> Unit) = content()

/** App Library side margins: Home's 16dp, plus room for the Side Bar's status capsule on whichever side it sits. */
private fun libraryEdges(statusInCorner: Boolean, dockWidth: Float, leftHanded: Boolean): PaddingValues {
    val rail = if (statusInCorner) (dockWidth + 28f).dp else 0.dp
    return if (leftHanded) PaddingValues(start = rail.coerceAtLeast(16.dp), end = if (statusInCorner) 16.dp else 0.dp)
    else PaddingValues(start = 16.dp, end = rail)
}

/** Shown while Folio is open as an app, before it's the Home app: says so, and offers both ways forward. */
@Composable
private fun PreviewBar(onUseAsHome: () -> Unit, onExit: () -> Unit) {
    val ink = LocalHomeInk.current
    Row(Modifier.padding(bottom = 6.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp))
        .background(Glass.copy(alpha = LocalGlassLook.current.widget)).border(1.dp, LocalGlassLook.current.outlineColor, RoundedCornerShape(24.dp))
        .padding(start = 16.dp, end = 4.dp).testTag("home-setup"), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.preview), color = ink.secondary, fontSize = 15.sp, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.use_as_home), color = FolioColors.Blue, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onUseAsHome)
                .heightIn(min = 48.dp).wrapContentHeight().padding(horizontal = 8.dp).testTag("preview-use-as-home"))
        IconButton(onClick = onExit, Modifier.size(48.dp).testTag("preview-exit")) {
            Icon(Icons.Rounded.Close, "Exit Preview", tint = ink.secondary, modifier = Modifier.size(18.dp))
        }
    }
}
