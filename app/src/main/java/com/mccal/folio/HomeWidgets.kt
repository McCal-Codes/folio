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
import androidx.lifecycle.repeatOnLifecycle
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
internal fun GlassCard(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val look = LocalGlassLook.current
    Surface(modifier.fillMaxSize().clip(RoundedCornerShape(FolioRadius.PANEL.dp)).clickable(onClick = onClick),
        color = Glass.copy(alpha = look.widget), shape = RoundedCornerShape(FolioRadius.PANEL.dp),
        border = if (look.outline > 0f) androidx.compose.foundation.BorderStroke(1.dp, look.outlineColor) else null) {
        // Like iOS widgets, the whole card scales with its size, so a narrower column (the Today View beside
        // Home in portrait) shrinks the text instead of clipping it.
        BoxWithConstraints {
            val scale = (minOf(maxWidth, maxHeight) / 150.dp).coerceIn(.6f, 1f)
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(density.density * scale, density.fontScale)) {
                Column(Modifier.fillMaxSize().padding(FolioSpace.LARGE.dp), verticalArrangement = Arrangement.SpaceBetween, content = content)
            }
        }
    }
}

@Composable
internal fun currentTime(): LocalDateTime {
    val tick by rememberMinuteTick()
    return displayNow(tick)
}

@Composable
internal fun ClockCard(onClick: () -> Unit) {
    val clockWidgetTapToReplaceLabel = stringResource(R.string.clock_widget_tap_to_replace)
    val time = currentTime()
    val format = if (android.text.format.DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm"
    GlassCard(onClick = onClick) {
        Text(stringResource(R.string.local_time), color = LocalHomeInk.current.secondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .6.sp,
            modifier = Modifier.semantics { contentDescription = clockWidgetTapToReplaceLabel })
        Text(time.format(DateTimeFormatter.ofPattern(format)), color = LocalHomeInk.current.primary, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
        Text(time.format(DateTimeFormatter.ofPattern(if (format == "HH:mm") "EEE" else "a · EEE")), color = LocalHomeInk.current.secondary, fontSize = FolioType.GROUP_LABEL.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun DateCard(onClick: () -> Unit) {
    val date = currentTime()
    GlassCard(onClick = onClick) {
        Text(date.format(DateTimeFormatter.ofPattern("EEEE")).uppercase(), color = LocalHomeInk.current.secondary, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = .6.sp, maxLines = 1)
        Text(date.dayOfMonth.toString(), color = LocalHomeInk.current.primary, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, lineHeight = 46.sp)
        Text(date.format(DateTimeFormatter.ofPattern("MMMM")), color = LocalHomeInk.current.secondary, fontSize = FolioType.GROUP_LABEL.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun ExpandedCard(onClick: () -> Unit) {
    val date = currentTime()
    GlassCard(onClick = onClick) {
        Column {
            Text(date.format(DateTimeFormatter.ofPattern("EEEE")), color = LocalHomeInk.current.primary, fontSize = 22.sp)
            Text(date.format(DateTimeFormatter.ofPattern(stringResource(R.string.mmmm_d))), color = LocalHomeInk.current.secondary, fontSize = 16.sp)
        }
        Column {
            Icon(Icons.Rounded.Widgets, null, tint = LocalHomeInk.current.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.a_little_more_room), color = LocalHomeInk.current.primary, fontSize = FolioType.TITLE.sp, lineHeight = 32.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.add_a_calendar_photos_or_another_widget), color = LocalHomeInk.current.secondary, fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))
            FolioButton(stringResource(R.string.add_widget), onClick, style = FolioButtonStyle.TONAL, icon = Icons.Rounded.Add)
        }
    }
}

@Composable
internal fun WidgetSlot(id: Int, slot: Int, controller: WidgetController, modifier: Modifier, onAdd: () -> Unit, fallback: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var restoreMessage by remember(slot) { mutableStateOf<String?>(null) }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(FolioRadius.PANEL.dp)).testTag("widget-slot-$slot")) {
        val displayedContentSize = WidgetContentSize(maxWidth.value, maxHeight.value)
        if (id == NEEDS_BINDING_WIDGET) {
            val restore = controller.restoreDescriptor(slot)
            Surface(Modifier.fillMaxSize().testTag("widget-restore-$slot"), color = Glass.copy(alpha = .88f),
                shape = RoundedCornerShape(FolioRadius.PANEL.dp), border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = .7f))) {
                Column(Modifier.padding(FolioSpace.MEDIUM.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(restore?.title ?: stringResource(R.string.saved_widget), color = Ink, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Text(restore?.profileLabel ?: stringResource(R.string.unavailable_profile), color = Ink.copy(alpha = .72f),
                        style = MaterialTheme.typography.bodySmall)
                    restoreMessage?.let { Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
                    Row {
                        TextButton(onClick = {
                            if (!controller.rebindRestoredWidget(slot, contentSize = displayedContentSize))
                                restoreMessage = context.getString(R.string.that_provider_or_profile_isn_t_available)
                        },
                            modifier = Modifier.testTag("widget-restore-reconnect-$slot")) { Text(stringResource(R.string.reconnect)) }
                        TextButton(onClick = onAdd, modifier = Modifier.testTag("widget-restore-replace-$slot")) { Text(stringResource(R.string.replace)) }
                    }
                }
            }
            return@BoxWithConstraints
        }
        val info = remember(id) { if (id >= 0) controller.manager.getAppWidgetInfo(id) else null }
        if (info == null) fallback()
        else {
            key(id) {
                AndroidView(factory = { context -> controller.host.createView(context, id, info) },
                    modifier = Modifier.fillMaxSize())
            }
        }
    }
}

internal fun widgetLabel(context: android.content.Context, id: Int, controller: WidgetController) = when (id) {
    CLOCK_WIDGET -> context.getString(R.string.clock)
    DATE_WIDGET -> context.getString(R.string.date)
    UP_NEXT_WIDGET -> context.getString(R.string.up_next)
    SUGGESTIONS_WIDGET -> context.getString(R.string.suggestions)
    BIG_CLOCK_WIDGET -> context.getString(R.string.big_clock)
    INFO_WIDGET -> context.getString(R.string.widget_panel)
    EMPTY_WIDGET -> context.getString(R.string.add_widget)
    else -> controller.label(id)
}

@Composable
internal fun MovableWidget(id: Int, slot: Int, controller: WidgetController, drag: HomeDragState,
    target: DropTarget?, modifier: Modifier, page: Int, onAdd: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cell = DropTarget.Widget(slot)
    val edit = LocalHomeEdit.current
    // Built-in cards wiggle; provider widgets (Android views) only get the remove button, since moving
    // a hosted view every frame would re-lay it out constantly.
    val cards = WidgetStacks.cards(id, LocalWidgetStacks.current[slot])
    Box(modifier.dropRegion(drag, cell, page = page, widgetId = id)) {
        val chrome = Modifier.fillMaxSize().then(if (id < 0) Modifier.jiggle("widget-$slot", .35f) else Modifier)
            .alpha(if (drag.source?.target == cell) .3f else 1f)
            .border(if (drag.active && target == cell) 2.dp else 0.dp,
                if (drag.active && target == cell) Color.White else Color.Transparent, RoundedCornerShape(FolioRadius.PANEL.dp))
            .semantics { onLongClick(context.getString(R.string.move_or_replace_widget)) { onAdd(); true } }
        if (cards.size > 1) SmartStack(cards, slot, controller, chrome, onAdd)
        else WidgetSlot(id, slot, controller, chrome, onAdd) { BuiltinWidgetCard(id, slot, onAdd) }
    if (edit.active && id != EMPTY_WIDGET && id != INFO_WIDGET) JiggleRemoveButton(stringResource(R.string.remove_widget)) { edit.onRemove(cell) }
    }
}

@Composable
internal fun BuiltinWidgetCard(id: Int, slot: Int, onAdd: () -> Unit) {
    when (id) {
        CLOCK_WIDGET -> ClockCard(onAdd)
        DATE_WIDGET -> DateCard(onAdd)
        UP_NEXT_WIDGET -> UpNextCard(onAdd)
        SUGGESTIONS_WIDGET -> SuggestionsCard(onAdd)
        BIG_CLOCK_WIDGET -> BigClockCard(onAdd)
        INFO_WIDGET -> if (slot % 3 == 2) ExpandedCard(onAdd) else GlassCard(onClick = onAdd) {
            Icon(Icons.Rounded.Widgets, null, tint = Color.White, modifier = Modifier.size(28.dp))
            Text(stringResource(R.string.your_widgets), color = Color.White, fontSize = FolioType.SUBHEAD.sp, maxLines = 1)
            Text(stringResource(R.string.tap_to_choose), color = Color.White.copy(alpha = .8f), fontSize = FolioType.GROUP_LABEL.sp)
        }
        else -> Surface(Modifier.fillMaxSize().clickable(onClick = onAdd), color = Glass.copy(alpha = .18f),
            shape = RoundedCornerShape(FolioRadius.PANEL.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .25f))) {
            Column(Modifier.padding(FolioSpace.MEDIUM.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Add, null, tint = Color.White)
                Text(if (id >= 0) stringResource(R.string.widget_unavailable) else stringResource(R.string.add_widget), color = Color.White, fontSize = FolioType.GROUP_LABEL.sp)
            }
        }
    }
}

/**
 * iOS Smart Stack: swipe up or down between the widgets in one spot. Dots on the side show while flipping;
 * with Smart Rotate on, the stack moves to the most relevant widget while Home is open.
 */
@Composable
internal fun SmartStack(cards: List<Int>, slot: Int, controller: WidgetController, modifier: Modifier, onAdd: () -> Unit) {
    val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { cards.size })
    val rotate = LocalStackRotate.current
    val haptic = LocalHapticFeedback.current
    var lastSettled by remember { mutableIntStateOf(0) }
    LaunchedEffect(pager.settledPage) {
        if (pager.settledPage != lastSettled) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        lastSettled = pager.settledPage
    }
    // Smart Rotate, like iOS: every 15 minutes the stack moves to the widget that matters now: an event starting
    // within the hour for Up Next, or an app you usually open around this time for its widget.
    val context = androidx.compose.ui.platform.LocalContext.current
    val homeApps = LocalHomeApps.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(rotate, cards, lifecycleOwner) {
        // Only while Home is in front: a stack that rotates behind another app burns a read of usage stats and the
        // calendar for a screen nobody is looking at (DYN-15).
        if (rotate) lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) { while (true) {
            delay(15 * 60_000L)
            val relevance = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val apps = Suggestions.packageRelevance(context, homeApps.apps)
                val soon = UpNext.events(context, limit = 1).firstOrNull()?.let { !it.allDay && it.begin - System.currentTimeMillis() in 0..60 * 60_000L } == true
                cards.map { id -> when {
                    id == UP_NEXT_WIDGET -> if (soon) 5.0 else 0.0
                    id >= 0 -> controller.manager.getAppWidgetInfo(id)?.provider?.packageName?.let { apps[it] } ?: 0.0
                    else -> 0.0
                } }
            }
            val pick = Suggestions.smartStackPick(relevance, pager.currentPage)
            if (pick != null && !pager.isScrollInProgress) pager.animateScrollToPage(pick)
        }
    } }
    var dotsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(pager.isScrollInProgress) { if (pager.isScrollInProgress) dotsVisible = true else { delay(1_200); dotsVisible = false } }
    Box(modifier) {
        androidx.compose.foundation.pager.VerticalPager(pager, Modifier.fillMaxSize().clip(RoundedCornerShape(FolioRadius.PANEL.dp)),
            key = { cards[it] }, beyondViewportPageCount = 0) { page ->
            val card = cards[page]
            WidgetSlot(card, slot, controller, Modifier.fillMaxSize(), onAdd) { BuiltinWidgetCard(card, slot, onAdd) }
        }
        val dotsAlpha by animateFloatAsState(if (dotsVisible) 1f else 0f, label = "stack dots")
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 5.dp).alpha(dotsAlpha)
            .background(Color.Black.copy(alpha = .28f), RoundedCornerShape(50)).padding(horizontal = 3.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(FolioSpace.TINY.dp)) {
            repeat(cards.size) { index ->
                Box(Modifier.size(5.dp).background(Color.White.copy(alpha = if (index == pager.currentPage) 1f else .4f), CircleShape))
            }
        }
    }
}


@Composable
internal fun WidgetActions(
    placement: WidgetPlacement,
    constraints: WidgetSpanConstraints?,
    canConfigure: Boolean,
    onConfigure: () -> Unit,
    isValid: (Int, Int) -> Boolean,
    onResize: (Int, Int) -> Unit,
    onStartResize: (Int, Int) -> Unit,
    onMoveToPage: (Int) -> Boolean,
    homePages: Int,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
    stackCards: List<Int> = emptyList(),
    stackLabel: (Int) -> String? = { null },
    stackRotate: Boolean = true,
    onStackRotate: (Boolean) -> Unit = {},
    onAddToStack: () -> Unit = {},
    onRemoveFromStack: (Int) -> Unit = {},
    onShowFirstInStack: (Int) -> Unit = {},
    /** Rows the widget's page shows (More rows); sizes never reach past them. */
    rows: Int = GRID_ROWS,
) {
    val sheetMaxHeight = with(LocalDensity.current) {
        (LocalWindowInfo.current.containerSize.height * .88f).toDp()
    }
    var width by remember(placement.slot, placement.spanX) { mutableIntStateOf(placement.spanX) }
    var height by remember(placement.slot, placement.spanY) { mutableIntStateOf(placement.spanY) }
    var customSize by remember(placement.slot) { mutableStateOf(false) }
    val minWidth = constraints?.minimum?.width ?: 2
    val minHeight = constraints?.minimum?.height ?: 2
    val maxWidth = minOf(GRID_COLUMNS - placement.column, constraints?.maximum?.width ?: GRID_COLUMNS)
    val maxHeight = minOf(rows.coerceAtMost(GRID_ROWS) - placement.row, constraints?.maximum?.height ?: GRID_ROWS)
    val feasible = placement.page >= -1 && placement.row in 0 until GRID_ROWS &&
        !(placement.id >= 0 && constraints == null) && minWidth <= maxWidth && minHeight <= maxHeight
    val valid = feasible && isValid(width, height)
    fun fits(w: Int, h: Int) = feasible && w in minWidth..maxWidth && h in minHeight..maxHeight && isValid(w, h)
    val secondary = Color.White.copy(alpha = .6f)

    // iOS-style widget menu: quick sizes, widget actions, Smart Stack, and a red Remove at the bottom.
    Column(Modifier.fillMaxWidth().heightIn(max = sheetMaxHeight).verticalScroll(rememberScrollState())
        .padding(horizontal = FolioSpace.XL.dp, vertical = FolioSpace.TINY.dp),
        verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (stackCards.size > 1) stringResource(R.string.smart_stack) else stringResource(R.string.widget), Modifier.weight(1f), color = Color.White,
                fontSize = 22.sp, fontWeight = FontWeight.Bold)
            // The circle is still 32 dp; the tap is 48 (A11Y-1). The target is the box around it, not the circle.
            Box(Modifier.size(FolioTouch.MIN.dp).clickable(onClickLabel = stringResource(R.string.close_widget_options), onClick = onClose),
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.close_widget_options), tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        SheetGroupLabel(stringResource(R.string.size))
        SheetGroup {
            Row(Modifier.fillMaxWidth().padding(FolioSpace.MEDIUM.dp), horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                listOf(Triple(stringResource(R.string.small), 2, 2), Triple(stringResource(R.string.medium), 4, 2), Triple(stringResource(R.string.large), 4, 4)).forEach { (label, w, h) ->
                    val ok = fits(w, h) || (placement.spanX == w && placement.spanY == h)
                    IosChip(selected = placement.spanX == w && placement.spanY == h,
                        onClick = { if (ok && (placement.spanX != w || placement.spanY != h)) { onResize(w, h); onClose() } },
                        label = { Text(label, color = if (ok) Color.Unspecified else Color.White.copy(alpha = .3f)) },
                        modifier = Modifier.weight(1f).testTag("widget-size-${label.lowercase()}-${placement.slot}"))
                }
            }
            MenuDivider()
            MenuRow(stringResource(R.string.resize_on_home), Icons.Rounded.OpenInFull) { if (feasible) onStartResize(width, height) }
            MenuDivider()
            MenuRow(if (customSize) stringResource(R.string.hide_custom_size) else stringResource(R.string.custom_size), Icons.Rounded.Tune) { customSize = !customSize }
            if (customSize) Column(Modifier.padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.SMALL.dp), verticalArrangement = Arrangement.spacedBy(FolioSpace.TINY.dp)) {
                if (!feasible) Text(stringResource(R.string.move_this_widget_into_the_six_row_grid_b), color = FolioColors.Red, fontSize = FolioType.FOOTNOTE.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.width), Modifier.weight(1f), color = Color.White)
                    IconButton(enabled = constraints?.canResizeHorizontally != false,
                        onClick = { if (feasible) width = (width - 1).coerceAtLeast(minWidth) }) { Icon(Icons.Rounded.Remove, stringResource(R.string.decrease_widget_width), tint = Color.White) }
                    Text(pluralStringResource(R.plurals.columns, width, width), Modifier.width(88.dp), textAlign = TextAlign.Center, color = Color.White)
                    IconButton(enabled = constraints?.canResizeHorizontally != false,
                        onClick = { if (feasible) width = (width + 1).coerceAtMost(maxWidth) }) { Icon(Icons.Rounded.Add, stringResource(R.string.increase_widget_width), tint = Color.White) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.height), Modifier.weight(1f), color = Color.White)
                    IconButton(enabled = constraints?.canResizeVertically != false,
                        onClick = { if (feasible) height = (height - 1).coerceAtLeast(minHeight) }) { Icon(Icons.Rounded.Remove, stringResource(R.string.decrease_widget_height), tint = Color.White) }
                    Text(pluralStringResource(R.plurals.rows, height, height), Modifier.width(88.dp), textAlign = TextAlign.Center, color = Color.White)
                    IconButton(enabled = constraints?.canResizeVertically != false,
                        onClick = { if (feasible) height = (height + 1).coerceAtMost(maxHeight) }) { Icon(Icons.Rounded.Add, stringResource(R.string.increase_widget_height), tint = Color.White) }
                }
                if (!valid) Text(stringResource(R.string.that_size_overlaps_another_item_or_exten), color = secondary, fontSize = FolioType.FOOTNOTE.sp)
                IosChip(selected = valid, onClick = { if (valid) { onResize(width, height); onClose() } }, label = { Text(stringResource(R.string.apply_size)) },
                    modifier = Modifier.fillMaxWidth())
            }
        }

        SheetGroupLabel(stringResource(R.string.widget))
        SheetGroup {
            if (canConfigure) { MenuRow(stringResource(R.string.edit_widget), Icons.Rounded.Settings) { onConfigure() }; MenuDivider() }
            MenuRow(stringResource(R.string.replace_widget), Icons.Rounded.FindReplace) { onReplace() }
            if (homePages > 1) {
                MenuDivider()
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(FolioSpace.MEDIUM.dp), horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                    repeat(homePages) { page ->
                        IosChip(selected = placement.page == page, onClick = { onMoveToPage(page) },
                            label = { Text(stringResource(R.string.page_1, page + 1)) }, modifier = Modifier.testTag("widget-move-${placement.slot}-page-$page"))
                    }
                }
            }
        }

        SheetGroupLabel(stringResource(R.string.smart_stack))
        SheetGroup {
            MenuRow(if (stackCards.size > 1) stringResource(R.string.add_widget_to_stack) else stringResource(R.string.make_a_stack), Icons.Rounded.Layers) { onAddToStack() }
            if (stackCards.size > 1) {
                stackCards.forEachIndexed { index, card ->
                    MenuDivider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = FolioSpace.LARGE.dp), verticalAlignment = Alignment.CenterVertically) {
                        val cardName = stackLabel(card) ?: stringResource(R.string.widget)
                        Text("${index + 1}. $cardName", Modifier.weight(1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (index > 0) TextButton(onClick = { onShowFirstInStack(card) }) { Text(stringResource(R.string.show_first)) }
                        IconButton(onClick = { onRemoveFromStack(card) }) {
                            Icon(Icons.Rounded.RemoveCircleOutline, stringResource(R.string.remove_from_stack, cardName), tint = FolioColors.Red)
                        }
                    }
                }
                MenuDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = FolioSpace.LARGE.dp, end = FolioSpace.SMALL.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.smart_rotate), color = Color.White)
                        Text(stringResource(R.string.show_the_next_widget_every_30_minutes), color = secondary, fontSize = FolioType.FOOTNOTE.sp)
                    }
                    IosSwitch(stackRotate, onStackRotate)
                }
            }
        }
        if (stackCards.size > 1) Text(stringResource(R.string.swipe_up_or_down_on_the_stack_to_flip_be), color = secondary, fontSize = FolioType.FOOTNOTE.sp,
            modifier = Modifier.padding(start = FolioSpace.TINY.dp))

        SheetGroup(Modifier.padding(top = FolioSpace.SMALL.dp)) {
            MenuRow(if (stackCards.size > 1) stringResource(R.string.remove_stack) else stringResource(R.string.remove_widget_2), Icons.Rounded.RemoveCircleOutline, destructive = true) { onRemove() }
        }
        Spacer(Modifier.height(16.dp))
    }
}
