@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.mccal.folio

import androidx.compose.ui.res.pluralStringResource
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
internal fun DockAppColumn(
    savedDock: List<String?>,
    previewDock: List<String?>,
    appsById: Map<String, AppEntry>,
    rowHeight: Float,
    iconSize: Float,
    drag: HomeDragState,
    target: DropTarget?,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onChoose: (Int) -> Unit,
    magnify: Boolean = false,
    leftHanded: Boolean = false,
    /** Lay the dock out left to right (the bottom bar) instead of top to bottom (the side rail). */
    horizontal: Boolean = false,
) {
    val chooseDockLabel = stringResource(R.string.choose_dock_app)
    val usedRecentlyLabel = stringResource(R.string.used_recently)
    val draggedId = drag.source?.appId
    // One slot along the dock's axis; rowHeight is the pitch either way.
    fun Modifier.slot(index: Int) = if (horizontal) fillMaxHeight().width(rowHeight.dp).offset(x = (rowHeight * index).dp)
        else fillMaxWidth().height(rowHeight.dp).offset(y = (rowHeight * index).dp)
    val edit = LocalHomeEdit.current
    val dockTarget = (target as? DropTarget.Dock)?.index
    val source = drag.source?.target as? DropTarget.Dock
    val draggedPreviewIndex = previewDock.indexOf(draggedId)
    val hiddenIndex = when {
        !drag.active || !drag.moved -> null
        dockTarget != null -> draggedPreviewIndex.takeIf { it >= 0 }
        source != null && target !is DropTarget.Home -> draggedPreviewIndex.takeIf { it >= 0 }
        else -> null
    }
    val dimDragged = drag.active && !drag.moved && source != null
    val launchBounds = remember(savedDock.size) { List(savedDock.size) { android.graphics.Rect() } }
    val interactions = remember(savedDock.size) { List(savedDock.size) { MutableInteractionSource() } }
    val slotScales = savedDock.indices.map { index ->
        val pressed by interactions[index].collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) .92f else 1f, label = "dock press $index")
        scale
    }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { rowHeight.dp.toPx() }
    // Harbor-style magnification: icons swell under the finger as it slides along the dock (touches pass through).
    var touchY by remember { mutableStateOf<Float?>(null) }
    val haptic = LocalHapticFeedback.current
    fun along(position: Offset) = if (horizontal) position.x else position.y
    Box((if (horizontal) Modifier.fillMaxHeight().width((rowHeight * savedDock.size).dp) else Modifier.fillMaxWidth().height((rowHeight * savedDock.size).dp))
        .then(if (!magnify) Modifier else Modifier.pointerInput(horizontal) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            touchY = along(down.position)
            var lastRow = (along(down.position) / rowHeightPx).toInt()
            while (true) {
                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                touchY = along(change.position)
                val row = (along(change.position) / rowHeightPx).toInt()
                if (row != lastRow) { lastRow = row; haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) }
            }
            touchY = null
        }
    })) {
        savedDock.indices.forEach { index ->
            val cell = DropTarget.Dock(index)
            val savedApp = appsById[savedDock[index]]
            val previewId = previewDock.getOrNull(index)
            val highlighted = drag.active && target == cell
            val gap = hiddenIndex == index
            Box(Modifier.slot(index)
                .background(if (highlighted) Color.White.copy(alpha = .3f) else Color.Transparent, RoundedCornerShape(FolioRadius.GROUP.dp)),
                contentAlignment = Alignment.Center) {
                when {
                    gap -> Box(Modifier.size(iconSize.dp).testTag("drag-gap-dock-$index")
                        .background(Glass.copy(alpha = .16f), RoundedCornerShape(FolioRadius.CARD.dp))
                        .border(2.dp, Color.White.copy(alpha = .55f), RoundedCornerShape(FolioRadius.CARD.dp)))
                    previewId == null -> Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            Box(Modifier.slot(index)
                .testTag("dock-slot-$index").dropRegion(drag, cell, savedApp?.id)
                .semantics(mergeDescendants = true) { contentDescription = savedApp?.label ?: "Choose dock app ${index + 1}" }
                .combinedClickable(interactionSource = interactions[index], indication = LocalIndication.current, role = Role.Button, onClick = {
                    if (savedApp != null) { if (!edit.active) onLaunch(savedApp, launchBounds[index]) } else onChoose(index)
                }, onLongClick = null)
                .semantics { onLongClick(chooseDockLabel) { onChoose(index); true } })
        }

        val ids = (savedDock + previewDock).filterNotNull().distinct()
        ids.forEach { id ->
            val savedIndex = savedDock.indexOf(id)
            val previewIndex = previewDock.indexOf(id)
            val renderIndex = previewIndex.takeIf { it >= 0 } ?: savedIndex.takeIf { it >= 0 } ?: return@forEach
            val app = appsById[id] ?: return@forEach
            key(id) {
                val animatedOffset by animateIntOffsetAsState(
                    if (horizontal) IntOffset((renderIndex * rowHeightPx).roundToInt(), 0) else IntOffset(0, (renderIndex * rowHeightPx).roundToInt()),
                    animationSpec = if (drag.active) androidx.compose.animation.core.spring(visibilityThreshold = IntOffset(1, 1))
                        else androidx.compose.animation.core.snap(), label = "dock insertion $id")
                val visible = previewIndex >= 0 && renderIndex != hiddenIndex
                val opacity by animateFloatAsState(
                    if (!visible) 0f else if (dimDragged && id == draggedId) .28f else 1f,
                    label = "dock insertion visibility $id",
                )
                Box(Modifier.offset { animatedOffset }.then(if (horizontal) Modifier.fillMaxHeight().width(rowHeight.dp) else Modifier.fillMaxWidth().height(rowHeight.dp)).alpha(opacity)
                    .testTag("dock-app-$id"), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(iconSize.dp).testTag("dock-icon-$id")
                        .onGloballyPositioned { if (savedIndex >= 0) { launchBounds[savedIndex].set(it.boundsInWindow().toAndroidBounds()); IconBounds.update(id, launchBounds[savedIndex]) } }
                        .jiggle(id)) {
                        val magnification by animateFloatAsState(touchY?.let { y ->
                            val center = (renderIndex + .5f) * rowHeightPx
                            1f + .38f * (1f - kotlin.math.abs(center - y) / (rowHeightPx * 1.5f)).coerceAtLeast(0f)
                        } ?: 1f, androidx.compose.animation.core.spring(dampingRatio = .75f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium), label = "dock magnify $id")
                        AppIcon(app, null, Modifier.fillMaxSize().graphicsLayer {
                            val s = slotScales[renderIndex] * magnification; scaleX = s; scaleY = s
                            // Grow toward the screen, away from the edge the dock sits on.
                            transformOrigin = if (horizontal) androidx.compose.ui.graphics.TransformOrigin(.5f, 1f)
                                else androidx.compose.ui.graphics.TransformOrigin(if (leftHanded) 0f else 1f, .5f)
                        }, shape = RoundedCornerShape(11.dp))
                        if (edit.active && savedIndex >= 0) JiggleRemoveButton(stringResource(R.string.remove_from_dock, app.label), inset = 6.dp) { edit.onRemove(DropTarget.Dock(savedIndex)) }
                    }
                    // Recent-app dot (Beta): below the icon in a horizontal dock, on the screen side of a side dock.
                    if (!edit.active && app.packageName in LocalRecentPackages.current) Box(Modifier
                        .align(if (horizontal) Alignment.BottomCenter else if (leftHanded) Alignment.CenterEnd else Alignment.CenterStart)
                        .size(5.dp).background(Color.White.copy(alpha = .8f), CircleShape)
                        .semantics { contentDescription = usedRecentlyLabel })
                }
            }
        }
    }
}

internal fun <T> List<T>.slicePage(range: IntRange): List<T> =
    if (isEmpty() || range.first >= size) emptyList() else subList(range.first, minOf(range.last + 1, size))

@Composable
internal fun FolderTile(folder: FolderEntry, apps: Map<String, AppEntry>, size: Float, labels: Boolean,
    drag: HomeDragState, page: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val counts0 = LocalBadgeCounts.current
    val unread = folder.appIds.mapNotNull { apps[it]?.packageName }.distinct().sumOf { counts0[it] ?: 0 }
    val folderApps = pluralStringResource(R.plurals.folder_apps, folder.appIds.size, folder.title, folder.appIds.size)
    val folderLabel = if (unread > 0) pluralStringResource(R.plurals.folder_unread, unread, folderApps, unread) else folderApps
    Column(modifier.clickable(onClick = onClick).semantics(mergeDescendants = true) {
        contentDescription = folderLabel
    }, horizontalAlignment = Alignment.CenterHorizontally) {
        val tint = LocalFolderColors.current[folder.id]?.let { Color(it) }
        val bounds = remember { android.graphics.Rect() }
        Box(Modifier.size(size.dp)
            .dropRegion(drag, DropTarget.Folder(folder.id), page = page, folderId = folder.id)
            .onGloballyPositioned { bounds.set(it.boundsInWindow().toAndroidBounds()); IconBounds.update(folder.id, bounds) }
            .jiggle(folder.id)) {
            // Like iOS: a folder's badge is the total of its apps' badges (each app counted once).
            val look = LocalIconLook.current
            val counts = LocalBadgeCounts.current
            val total = if (look.badges == BadgeStyle.OFF) 0
                else folder.appIds.mapNotNull { apps[it]?.packageName }.distinct().sumOf { counts[it] ?: 0 }
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape((size * .24f).dp))
                .background(tint?.copy(alpha = .78f) ?: Glass.copy(alpha = .72f)).border(1.dp, Color.White.copy(alpha = .55f), RoundedCornerShape((size * .24f).dp))
                .testTag("folder-drop-${folder.id}")) {
            folder.appIds.take(4).forEachIndexed { index, id ->
                apps[id]?.let { app ->
                    AppIcon(app, null, Modifier.align(when (index) {
                        0 -> Alignment.TopStart; 1 -> Alignment.TopEnd; 2 -> Alignment.BottomStart; else -> Alignment.BottomEnd
                    }).padding(5.dp).size((size * .38f).dp).clip(RoundedCornerShape(6.dp)))
                }
            }
            }
            if (total > 0) IconBadge(total, look.badges, look.badgeColor.fixed?.let { Color(it) } ?: BadgeRed, look.badgeLook, look.badgeSize.scale)
        }
        if (labels) Text(folder.title, color = LocalHomeInk.current.primary, fontSize = LocalLabelSize.current.sp.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = FolioSpace.TINY.dp))
    }
}

@Composable
internal fun AppTile(app: AppEntry, size: Float, labels: Boolean, modifier: Modifier = Modifier, onClick: (android.graphics.Rect) -> Unit, onLongClick: () -> Unit,
    onRemove: (() -> Unit)? = null) {
    val appOptionsLabel = stringResource(R.string.app_options)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .88f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = .55f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium), label = "app press")
    // No size animation: after folding, the cover's icons must appear at their own size on the first frame.
    val iconSize = size.dp
    val bounds = remember { android.graphics.Rect() }
    val openPanel = LocalAppPanel.current
    Column(modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) { contentDescription = app.label }
        .iconSwipes(openPanel?.let { { it(app) } }, if (app.id in LocalStackedApps.current) LocalIconStack.current?.let { { it(app) } } else null)
        .clickable(interactionSource = interaction, indication = null,
            role = Role.Button, onClick = { onClick(bounds) })
        .semantics { onLongClick(appOptionsLabel) { onLongClick(); true } }.padding(horizontal = FolioSpace.HAIR.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        // Bounds are read outside the wiggle layer so jiggling doesn't report a new position every frame.
        Box(Modifier.size(iconSize).onGloballyPositioned { bounds.set(it.boundsInWindow().toAndroidBounds()); IconBounds.update(app.id, bounds) }
            .jiggle(app.id)) {
            if (app.id in LocalStackedApps.current) StackPeek(iconSize)
            AppIcon(app, null, Modifier.fillMaxSize()
                .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (pressed) .82f else 1f }, shape = RoundedCornerShape((size * .24f).dp))
            if (onRemove != null) JiggleRemoveButton(stringResource(R.string.remove_from_home_2, app.label), onRemove = onRemove)
        }
        val ink = LocalHomeInk.current
        if (labels) Row(Modifier.padding(top = FolioSpace.TINY.dp), verticalAlignment = Alignment.CenterVertically) {
            NewAppDot(app.packageName)
            Text(app.label, color = ink.primary, fontSize = LocalLabelSize.current.sp.sp, lineHeight = LocalLabelSize.current.lineSp.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = TextStyle(shadow = ink.labelShadow))
        }
    }
}
