package com.mccal.folio

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.material.icons.rounded.AddCircle
import kotlinx.coroutines.launch

@Composable
internal fun FolderPanel(
    folder: FolderEntry, apps: Map<String, AppEntry>, drag: HomeDragState, page: Int,
    homeDestinations: List<Int>, dockVacancies: List<Int>, onDismiss: () -> Unit,
    onRename: (String) -> Unit, onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onMoveOut: (String, DropTarget) -> Unit,
    color: Long? = null, onColor: (Long?) -> Unit = {}, onAddApps: (() -> Unit)? = null,
    size: FolderSize? = null, onSize: (FolderSize?) -> Unit = {},
) {
    var title by rememberSaveable(folder.id) { mutableStateOf(folder.title) }
    // Zoom in from the folder's tile on Home and back into it on close, like iPhone folders.
    val appear = remember(folder.id) { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember(folder.id) { mutableStateOf(false) }
    val close: () -> Unit = {
        if (!closing) { closing = true; scope.launch {
            appear.animateTo(0f, FolioMotion.spring(FolioMotion.Firm)); onDismiss()
        } }
    }
    val tile = remember(folder.id) { IconBounds.of(folder.id) }
    var panelBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    // Predictive back: the folder shrinks toward its icon as you swipe, and closes (or springs back) when you let go.
    PredictiveBack(enabled = !closing, onProgress = { p -> scope.launch { appear.snapTo(1f - .35f * p) } },
        onCancel = { scope.launch { appear.animateTo(1f, FolioMotion.spring(FolioMotion.Quick)) } }, onBack = close)
    DisposableEffect(folder.id) { onDispose { if (title.isNotBlank() && title != folder.title) onRename(title) } }
    DisposableEffect(drag, folder.id) {
        drag.activeSourceScope = folder.id
        onDispose { if (drag.activeSourceScope == folder.id) drag.activeSourceScope = null }
    }
    LaunchedEffect(folder.id) { appear.animateTo(1f, MotionSpeed.spring(.78f, androidx.compose.animation.core.Spring.StiffnessMediumLow)) }
    val folderLook = LocalFolderLook.current
    // Mirrors Configuration.fitsRegularHomeLayout() (SizeClass.kt): the same signal every other screen already
    // uses to tell the cover screen apart from the unfolded inner screen, so a resized folder stays hinge-safe -
    // never past the inner screen's own ceiling, and never past the cover screen's narrower one.
    val config = LocalConfiguration.current
    val regular = remember(config) { config.fitsRegularHomeLayout() }
    val minFolderW = 280f; val minFolderH = 220f
    val maxFolderW = if (regular) 640f else 520f
    val maxFolderH = if (regular) 620f else 560f
    val autoFolderW = (config.screenWidthDp * .86f).coerceIn(minFolderW, maxFolderW)
    // -1 means "not sized yet": the real auto height depends on the title/swatches/Add Apps header's own measured
    // height (it can wrap, grow with text scale, etc.) plus the real inset-adjusted window below, not a flat
    // fraction of the screen - a flat fraction is what used to leave the card with almost no bottom margin, its
    // rounded corners reading as hard edges because there was no visible gap left to round into.
    var folderW by remember(folder.id) { mutableFloatStateOf(size?.width?.coerceIn(minFolderW, maxFolderW) ?: -1f) }
    var folderH by remember(folder.id) { mutableFloatStateOf(size?.height?.coerceIn(minFolderH, maxFolderH) ?: -1f) }
    val density = LocalDensity.current.density
    // A real Dialog (its own window), not an inline overlay in MainActivity's shared window: measured directly
    // (Compose onGloballyPositioned on this scrim), the shared window's content stopped 104 dp short of the true
    // top and 39 dp short of the bottom here specifically - a genuine measure constraint, immune to
    // enableEdgeToEdge, setDecorFitsSystemWindows, layoutInDisplayCutoutMode and even the legacy
    // FLAG_LAYOUT_NO_LIMITS, all tried and measured with zero effect. AppContextMenu.kt and WidgetContextMenu.kt
    // already sidestep this the same way, with their own window and their own decorFitsSystemWindows = false.
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false)) {
        val view = LocalView.current
        LaunchedEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.let { w ->
                w.setDimAmount(0f)
                androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).apply {
                    systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                }
            }
        }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }.background(FolioGlass.scrim)
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClickLabel = stringResource(R.string.close_folder),
            onClick = close,
        )
        .testTag("folder-panel"),
        contentAlignment = Alignment.Center) {
        // The scrim above stays full-bleed under the island/camera; only the centered content needs to clear it -
        // same split TopPanels.kt uses. Without this, a short folder centers high enough to land under the island.
        // imePadding lives here too, not on the scrim above: it was shrinking the scrim's own painted bounds on
        // some OEM builds (Samsung's inset reporting folds the status bar into the ime-adjacent region while a
        // text field exists in the tree, even unfocused), leaving a lighter, un-scrimmed strip at the very top.
        BoxWithConstraints(Modifier.windowInsetsPadding(WindowInsets.folioSafeTop).navigationBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
        var headerHeightPx by remember { mutableFloatStateOf(0f) }
        val marginDp = FolioSpace.XL.dp
        // The header (title, swatches, Add Apps) can wrap or grow with text scale, so its real measured height,
        // not a guess, is what the card's own height budgets around - the same margin above and below either side.
        val availableH = (maxHeight - with(LocalDensity.current) { headerHeightPx.toDp() } - marginDp * 2)
            .coerceAtLeast(minFolderH.dp)
        val effectiveMaxFolderH = minOf(maxFolderH.dp, availableH).value
        if (folderW < 0f) folderW = autoFolderW
        if (folderH < 0f) folderH = (effectiveMaxFolderH * (560f / maxFolderH)).coerceIn(minFolderH, effectiveMaxFolderH)
        // A fold/unfold while open moves the ceiling live, and so does the header's own height (e.g. Reduce
        // Motion toggling text scale); follow both instead of leaving the card sitting where it no longer fits.
        LaunchedEffect(minFolderW, maxFolderW, minFolderH, effectiveMaxFolderH) {
            folderW = folderW.coerceIn(minFolderW, maxFolderW); folderH = folderH.coerceIn(minFolderH, effectiveMaxFolderH)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier
            .onGloballyPositioned { panelBounds = it.boundsInWindow() }
            .graphicsLayer {
                val p = appear.value
                if (tile != null && panelBounds.width > 0f) {
                    val start = (tile.width() / panelBounds.width).coerceIn(.08f, 1f)
                    val s = start + (1f - start) * p; scaleX = s; scaleY = s
                    translationX = (tile.exactCenterX() - panelBounds.center.x) * (1f - p)
                    translationY = (tile.exactCenterY() - panelBounds.center.y) * (1f - p)
                    alpha = (p * 1.8f).coerceIn(0f, 1f)
                } else { val s = .86f + .14f * p; scaleX = s; scaleY = s }
            }) {
        // Just the title and a small options button: color and Add Apps used to sit inline below it always
        // visible, which was a lot to look at before you'd even opened an app. Both now live in one popup off
        // the button, the same anchored-menu pattern FolderChild's own "..." button already uses below.
        var folderMenu by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = FolioSpace.COMFY.dp)
                .onGloballyPositioned { headerHeightPx = it.size.height.toFloat() }) {
            Spacer(Modifier.size(FolioTouch.MIN.dp))
            androidx.compose.foundation.text.BasicTextField(title, { title = it },
                Modifier.weight(1f, fill = false).widthIn(max = 320.dp).testTag("folder-name"), singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (title.isNotBlank()) onRename(title) }))
            Box {
                IconButton(onClick = { folderMenu = true }, Modifier.size(FolioTouch.MIN.dp).testTag("folder-options")) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.folder_options), tint = Color.White)
                }
                FolioMenuPopup(folderMenu, onDismiss = { folderMenu = false }, tag = "folder-options") {
                    // Folder tint: none + a few iOS-like colors. Scrolls rather than assuming eight swatches fit
                    // the menu's own 280 dp max width.
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = FolioSpace.MEDIUM.dp, vertical = FolioSpace.SNUG.dp)) {
                        (listOf<Long?>(null) + FolderSwatches).forEach { swatch ->
                            val selected = swatch == color
                            Box(Modifier.width(40.dp).height(FolioTouch.MIN.dp)
                                .clickable(onClickLabel = stringResource(if (swatch == null) R.string.no_folder_color else R.string.folder_color)) { onColor(swatch) },
                                contentAlignment = Alignment.Center) {
                                Box(Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(swatch?.let { Color(it) } ?: Color.White.copy(alpha = .18f))
                                    .then(if (selected) Modifier.border(2.5.dp, Color.White, androidx.compose.foundation.shape.CircleShape) else Modifier))
                            }
                        }
                    }
                    if (onAddApps != null) {
                        MenuDivider()
                        MenuRow(stringResource(R.string.add_apps), icon = Icons.Rounded.AddCircle, tag = "folder-add-apps") {
                            folderMenu = false; onAddApps()
                        }
                    }
                }
            }
        }
        Box {
        Surface(Modifier.width(folderW.dp).height(folderH.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .testTag("folder-panel-content"),
            color = when (folderLook.background) {
                FolderBackground.GLASS -> FolioGlass.card
                FolderBackground.SOLID -> FolioColors.SecondaryBackground
                FolderBackground.CLEAR -> Color.Transparent
            }, contentColor = Color.White, shape = RoundedCornerShape(38.dp),
            border = if (folderLook.background == FolderBackground.CLEAR) null else FolioGlass.edge) {
            Column(Modifier.padding(FolioSpace.XL.dp)) {
                val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                LazyVerticalGrid(if (folderLook.columns > 0) FolderColumns(folderLook.columns) else GridCells.Adaptive(84.dp), Modifier.fillMaxWidth().weight(1f).edgeFade(gridState), state = gridState,
                    contentPadding = PaddingValues(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp),
                    verticalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp)) {
                    items(folder.appIds, key = { it }) { appId ->
                        apps[appId]?.let { app -> FolderChild(app, folder.id, drag, page, homeDestinations, dockVacancies,
                            onLaunch = onLaunch, onMoveOut = onMoveOut) }
                    }
                }
            }
        }
        // Resize, the same corner-drag gesture as a Home widget (LauncherScreen.kt's resize handle) - free-form
        // in dp rather than snapped to grid cells, since a folder doesn't live on Home's app grid. Committed once
        // on release, like the widget gesture, not on every frame; the box itself already tracks live during drag.
        Box(Modifier.align(Alignment.BottomEnd).size(FolioTouch.MIN.dp)
            .pointerInput(folder.id, minFolderW, maxFolderW, minFolderH, maxFolderH) {
                detectDragGestures(
                    onDragEnd = { onSize(FolderSize(folderW, folderH)) },
                    onDragCancel = { onSize(FolderSize(folderW, folderH)) },
                ) { change, dragAmount ->
                    change.consume()
                    folderW = (folderW + dragAmount.x / density).coerceIn(minFolderW, maxFolderW)
                    folderH = (folderH + dragAmount.y / density).coerceIn(minFolderH, maxFolderH)
                }
            }
            .testTag("folder-resize-handle"), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.OpenInFull, stringResource(R.string.resize_folder),
                tint = Color.White.copy(alpha = .85f), modifier = Modifier.size(18.dp))
        }
        }
        }
        }
    }
    }
}

/** The chosen number of columns, but never cells too narrow for an icon and its name (small cover screens). */
private data class FolderColumns(val columns: Int) : GridCells {
    override fun androidx.compose.ui.unit.Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val count = columns.coerceAtMost(((availableSize + spacing) / (76.dp.roundToPx() + spacing)).coerceAtLeast(1))
        val cells = availableSize - spacing * (count - 1)
        return List(count) { cells / count + if (it < cells % count) 1 else 0 }
    }
}

@Composable
private fun FolderChild(
    app: AppEntry, folderId: String, drag: HomeDragState, page: Int,
    homeDestinations: List<Int>, dockVacancies: List<Int>,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit, onMoveOut: (String, DropTarget) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().testTag("folder-child-${app.id}"), color = Color.Transparent, contentColor = Color.White,
        shape = RoundedCornerShape(18.dp)) {
        Box {
            Column(Modifier.fillMaxWidth().dropRegion(drag, DropTarget.Library(app.id), app.id, page,
                folderId = folderId, scope = folderId).clickable(enabled = app.available) { onLaunch(app, null) }
                .padding(horizontal = FolioSpace.SNUG.dp, vertical = FolioSpace.COMPACT.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // No per-app badge dot here: it would land on the same corner as the "..." button below, and the
                // folder's own Home icon already shows the combined total (HomeTiles.kt) before you even open it.
                AppIcon(app, null, Modifier.size(58.dp), shape = RoundedCornerShape(FolioRadius.CARD.dp), badge = false)
                Text(app.label, Modifier.padding(top = FolioSpace.SNUG.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium)
                if (app.isWork || !app.available) Text(if (app.available) app.profileLabel else stringResource(R.string.text_1_s_unavailable, app.profileLabel),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { menu = true }, Modifier.align(Alignment.TopEnd).size(36.dp)
                .testTag("folder-options-${app.id}")) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.move_1_s, app.label)) }
            // Folio's menu: anchored under this button, growing from the corner it was opened at, and scrolling
            // when a layout has more pages than the window can show.
            FolioMenuPopup(menu, onDismiss = { menu = false }, tag = "folder-options-${app.id}") {
                homeDestinations.distinctBy(::homeCellPage).forEachIndexed { index, destination ->
                    val destinationPage = homeCellPage(destination)
                    val label = if (destinationPage == -1) stringResource(R.string.move_to_the_unfolded_only_page)
                        else stringResource(R.string.move_to_page_1_d, destinationPage + 1)
                    if (index > 0) MenuDivider()
                    MenuRow(label, tag = "folder-move-${app.id}-page-$destinationPage") {
                        menu = false; onMoveOut(app.id, DropTarget.Home(destination))
                    }
                }
                dockVacancies.firstOrNull()?.let { dock ->
                    MenuDivider()
                    MenuRow(stringResource(R.string.move_to_dock), tag = "folder-move-${app.id}-dock") {
                        menu = false; onMoveOut(app.id, DropTarget.Dock(dock))
                    }
                }
                MenuDivider()
                MenuRow(stringResource(R.string.remove_shortcut), destructive = true, tag = "folder-remove-${app.id}") {
                    menu = false; onMoveOut(app.id, DropTarget.Remove)
                }
            }
        }
    }
}

private val FolderSwatches = listOf(0xFFFF6B63, 0xFFFFA94D, 0xFFFFD84D, 0xFF63D98B, 0xFF4DB8FF, 0xFF8E7CFF, 0xFFFF7EB9)
