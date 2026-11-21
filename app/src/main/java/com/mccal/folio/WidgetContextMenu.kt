package com.mccal.folio

import android.graphics.Rect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Last on-screen bounds of each Home widget, by slot, so holding one can open its menu beside it (as [IconBounds] does for icons). */
internal object WidgetBounds {
    private val bounds = HashMap<Int, Rect>()
    fun update(slot: Int, rect: Rect) { if (!rect.isEmpty) bounds[slot] = Rect(rect) }
    fun of(slot: Int): Rect? = bounds[slot]
}

/** The rows of the widget menu's first level, in order. Edit Widget exists only where the widget has something to edit. */
internal enum class WidgetMenuAction { EDIT_WIDGET, SIZE, REPLACE, STACK, EDIT_HOME, MORE, REMOVE }

internal fun widgetMenuActions(canEdit: Boolean): List<WidgetMenuAction> = buildList {
    if (canEdit) add(WidgetMenuAction.EDIT_WIDGET)
    add(WidgetMenuAction.SIZE); add(WidgetMenuAction.REPLACE); add(WidgetMenuAction.STACK)
    add(WidgetMenuAction.EDIT_HOME); add(WidgetMenuAction.MORE); add(WidgetMenuAction.REMOVE)
}

/** What sits behind More: the options a widget menu keeps off its first level, shown only where they apply. */
internal enum class WidgetMoreEntry { CUSTOM_SIZE, MOVE_TO_PAGE, PLACE_FREELY, EXPAND, REMOVE_PHOTO, EDIT_STACK }

internal fun widgetMoreEntries(homePages: Int, canPlace: Boolean, canExpand: Boolean, canRemovePhoto: Boolean, stacked: Boolean): List<WidgetMoreEntry> = buildList {
    add(WidgetMoreEntry.CUSTOM_SIZE)
    if (homePages > 1) add(WidgetMoreEntry.MOVE_TO_PAGE)
    if (canPlace) add(WidgetMoreEntry.PLACE_FREELY)
    if (canExpand) add(WidgetMoreEntry.EXPAND)
    if (canRemovePhoto) add(WidgetMoreEntry.REMOVE_PHOTO)
    if (stacked) add(WidgetMoreEntry.EDIT_STACK)
}

internal enum class WidgetShape { SMALL, MEDIUM, LARGE, EXTRA_LARGE, TINY, ROW, COLUMN, CURRENT }

/** One row of the Size submenu: [selected] is the checkmark, [enabled] is false for a size this widget cannot be. */
internal data class WidgetSizeChoice(val shape: WidgetShape, val width: Int, val height: Int, val enabled: Boolean, val selected: Boolean)

/**
 * The Size submenu's rows: Small, Medium and Large always (dimmed when the widget cannot take that size), then Extra Large
 * (4 × 6, iOS 27's largest) and the one-cell, row and column shapes only where its limits and the page allow them, and the checkmark on the size it has now. A size set by hand that
 * matches none of them gets a checked row of its own, so the mark never goes missing.
 */
internal fun widgetSizeChoices(spanX: Int, spanY: Int, constraintsKnown: Boolean, fits: (Int, Int) -> Boolean): List<WidgetSizeChoice> {
    fun current(w: Int, h: Int) = spanX == w && spanY == h
    val named = listOf(Triple(WidgetShape.SMALL, 2, 2), Triple(WidgetShape.MEDIUM, 4, 2), Triple(WidgetShape.LARGE, 4, 4))
        .map { (shape, w, h) -> WidgetSizeChoice(shape, w, h, enabled = fits(w, h) || current(w, h), selected = current(w, h)) }
    val extras = listOf(Triple(WidgetShape.EXTRA_LARGE, 4, 6), Triple(WidgetShape.TINY, 1, 1), Triple(WidgetShape.ROW, GRID_COLUMNS, 1), Triple(WidgetShape.COLUMN, 1, 4))
        .filter { (_, w, h) -> constraintsKnown && fits(w, h) || current(w, h) }
        .map { (shape, w, h) -> WidgetSizeChoice(shape, w, h, enabled = true, selected = current(w, h)) }
    val all = named + extras
    return if (all.any { it.selected }) all else all + WidgetSizeChoice(WidgetShape.CURRENT, spanX, spanY, enabled = true, selected = true)
}

private enum class WidgetMenuLevel(val parent: WidgetMenuLevel?) {
    MAIN(null), SIZE(MAIN), MORE(MAIN), CUSTOM_SIZE(MORE), PAGE(MORE), STACK(MORE)
}

/** Reduce Motion: the menu fades over this long instead of growing out of the widget. */
private const val WIDGET_MENU_FADE_MS = 150
private val MENU_WIDTH = 260.dp
private val MENU_ROW = 49.dp

/**
 * The widget's hold menu: an iOS-style popover beside the widget, in the same surface as the app icon's menu
 * ([AppContextMenu]). The rest of Home dims and the widget stays lit. The first level holds Edit Widget, Size, Replace
 * Widget, Make a Stack, Edit Home Screen and More, with Remove Widget in red pinned under them; Size, custom size,
 * pages and the stack's order open in place of the list, and Back (or the back button) goes up one level.
 * Reduce Motion fades instead of growing out of the widget.
 */
@Composable
internal fun WidgetContextMenu(
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
    onEditHome: () -> Unit,
    /** A built-in widget's own options (Big Clock's look, Photos' options); with [canConfigure] this is what Edit Widget opens. */
    onCustomize: (() -> Unit)? = null,
    /** Photos widget only: takes the photo showing out of the widget. */
    onRemovePhoto: (() -> Unit)? = null,
    onPlace: (() -> Unit)? = null,
    onExpand: (() -> Unit)? = null,
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
    val density = LocalDensity.current
    val still = LocalReduceMotion.current
    val appear = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf(WidgetMenuLevel.MAIN) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, if (still) tween(WIDGET_MENU_FADE_MS)
            else FolioMotion.spring(FolioMotion.pick(old = .72f to Spring.StiffnessMediumLow, new = FolioMotion.Menu)))
    }
    /** Tapping outside closes the menu back into the widget (a fade under Reduce Motion); choosing a row closes it at once. */
    val dismiss: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                appear.animateTo(0f, if (still) tween(WIDGET_MENU_FADE_MS) else FolioMotion.spring(FolioMotion.Firm))
                onClose()
            }
        }
    }
    // No RegisterOpenWindow(LauncherSheets): that blurs Home, and the widget this menu belongs to would blur with it. The scrim dims and leaves it lit.

    // Sizes, the same limits the sheet used to enforce.
    var width by remember(placement.slot, placement.spanX) { mutableIntStateOf(placement.spanX) }
    var height by remember(placement.slot, placement.spanY) { mutableIntStateOf(placement.spanY) }
    var pageFull by remember(placement.slot) { mutableStateOf(false) }
    val minWidth = constraints?.minimum?.width ?: 2
    val minHeight = constraints?.minimum?.height ?: 2
    val maxSpanW = minOf(GRID_COLUMNS - placement.column, constraints?.maximum?.width ?: GRID_COLUMNS)
    val maxSpanH = minOf(rows.coerceAtMost(GRID_ROWS) - placement.row, constraints?.maximum?.height ?: GRID_ROWS)
    val feasible = placement.page >= -1 && placement.row in 0 until GRID_ROWS &&
        !(placement.id >= 0 && constraints == null) && minWidth <= maxSpanW && minHeight <= maxSpanH
    val valid = feasible && isValid(width, height)
    fun fits(w: Int, h: Int) = feasible && w in minWidth..maxSpanW && h in minHeight..maxSpanH && isValid(w, h)
    val stacked = stackCards.size > 1
    val canEdit = canConfigure || onCustomize != null

    Dialog(onDismissRequest = { level.parent?.let { level = it } ?: dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
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
        var origin by remember { mutableStateOf(Offset.Zero) }
        val closeLabel = stringResource(R.string.close_widget_options)
        BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionOnScreen() }
            // Offscreen so the widget's hole below clears only this scrim, and the fade covers scrim and menu together.
            .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f); compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                drawRect(Color.Black.copy(alpha = .28f))
                WidgetBounds.of(placement.slot)?.let { r ->
                    drawRoundRect(Color.Black, Offset(r.left - origin.x, r.top - origin.y), Size(r.width().toFloat(), r.height().toFloat()),
                        CornerRadius(FolioRadius.PANEL.dp.toPx()), blendMode = BlendMode.Clear)
                }
            }
            .clickable(remember { MutableInteractionSource() }, null, onClickLabel = closeLabel, onClick = dismiss)) {
            val screenW = with(density) { this@BoxWithConstraints.maxWidth.toPx() }
            val screenH = with(density) { this@BoxWithConstraints.maxHeight.toPx() }
            val widget = WidgetBounds.of(placement.slot) ?: Rect((screenW / 2 - 160).toInt(), (screenH / 3).toInt(), (screenW / 2 + 160).toInt(), (screenH / 3 + 320).toInt())
            val wLeft = widget.left - origin.x
            val wTop = widget.top - origin.y
            val wRight = widget.right - origin.x
            val wBottom = widget.bottom - origin.y

            // Below the widget when the first level fits there, else above it, else over its lower part (a widget as tall as Home).
            val menuW = with(density) { MENU_WIDTH.toPx() }
            val gap = with(density) { 10.dp.toPx() }
            val safeTop = with(density) { 56.dp.toPx() }      // clear of the camera and island
            val safeBottom = with(density) { 32.dp.toPx() }
            val spaceBelow = screenH - (wBottom + gap) - safeBottom
            val spaceAbove = wTop - gap - safeTop
            val estimatedH = with(density) { (MENU_ROW * widgetMenuActions(canEdit).size + 8.dp).toPx() }
            val roomy = minOf(estimatedH, with(density) { 220.dp.toPx() })
            val placeBelow = estimatedH <= spaceBelow || (estimatedH > spaceAbove && spaceBelow >= spaceAbove && spaceBelow >= roomy)
            val placeAbove = !placeBelow && (estimatedH <= spaceAbove || spaceAbove >= roomy)
            val overBottom = minOf(wBottom, screenH - safeBottom) - gap
            val maxMenuPx = when { placeBelow -> spaceBelow; placeAbove -> spaceAbove; else -> overBottom - safeTop }
            val maxMenuH = with(density) { maxMenuPx.coerceAtLeast(120f).toDp() }
            val menuLeft = popupLeft((wLeft + wRight) / 2f, menuW, screenW, gap)
            val origX = (((wLeft + wRight) / 2f - menuLeft) / menuW).coerceIn(0f, 1f)
            // Positioned from the measured height: the level changes it, and a window shorter than the screen would put the menu on the widget.
            var menuH by remember { mutableIntStateOf(0) }

            Column(Modifier.offset {
                    IntOffset(menuLeft.roundToInt(), when {
                        placeBelow -> (wBottom + gap).roundToInt()
                        placeAbove -> (wTop - gap - menuH).roundToInt()
                        else -> (overBottom - menuH).roundToInt()
                    })
                }
                .onSizeChanged { menuH = it.height }
                .width(MENU_WIDTH)
                .heightIn(max = maxMenuH)
                .graphicsLayer {
                    if (!still) { val s = .7f + .3f * appear.value; scaleX = s; scaleY = s }
                    transformOrigin = TransformOrigin(origX, if (placeBelow) 0f else 1f)
                }
                .clip(RoundedCornerShape(FolioRadius.FLOATING.dp))
                // Opaque: with no blur behind (see above), a see-through surface let Home's icons read through the rows.
                .background(FolioColors.PopoverSurface)
                .border(FolioGlass.edge, RoundedCornerShape(FolioRadius.FLOATING.dp))
                .clickable(remember { MutableInteractionSource() }, null) {}) {
                // A fade between levels, never a flash: the old level leaves as the new one arrives.
                AnimatedContent(level, transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(90)) }, label = "widget menu level") { shown ->
                    Column(Modifier.heightIn(max = maxMenuH)) {
                        Column(Modifier.weight(1f, fill = false).fadingVerticalScroll()) {
                            when (shown) {
                                WidgetMenuLevel.MAIN -> widgetMenuActions(canEdit).filter { it != WidgetMenuAction.REMOVE }.forEachIndexed { index, action ->
                                    if (index > 0) MenuDivider()
                                    when (action) {
                                        WidgetMenuAction.EDIT_WIDGET -> MenuRow(stringResource(R.string.edit_widget), Icons.Rounded.Settings, maxLines = Int.MAX_VALUE,
                                            tag = "widget-menu-edit") { if (canConfigure) onConfigure() else onCustomize?.invoke() }
                                        WidgetMenuAction.SIZE -> MenuRow(stringResource(R.string.size), Icons.AutoMirrored.Rounded.KeyboardArrowRight, maxLines = Int.MAX_VALUE,
                                            tag = "widget-menu-size") { level = WidgetMenuLevel.SIZE }
                                        WidgetMenuAction.REPLACE -> MenuRow(stringResource(R.string.replace_widget), Icons.Rounded.FindReplace, maxLines = Int.MAX_VALUE,
                                            tag = "widget-menu-replace") { onReplace() }
                                        WidgetMenuAction.STACK -> MenuRow(stringResource(if (stacked) R.string.add_widget_to_stack else R.string.make_a_stack), Icons.Rounded.Layers,
                                            maxLines = Int.MAX_VALUE, tag = "widget-menu-stack") { onAddToStack() }
                                        WidgetMenuAction.EDIT_HOME -> MenuRow(stringResource(R.string.edit_home_screen), Icons.Rounded.AppRegistration, maxLines = Int.MAX_VALUE,
                                            tag = "widget-menu-edit-home") { onEditHome() }
                                        WidgetMenuAction.MORE -> MenuRow(stringResource(R.string.more), Icons.AutoMirrored.Rounded.KeyboardArrowRight, maxLines = Int.MAX_VALUE,
                                            tag = "widget-menu-more") { level = WidgetMenuLevel.MORE }
                                        WidgetMenuAction.REMOVE -> Unit
                                    }
                                }
                                WidgetMenuLevel.SIZE -> {
                                    WidgetMenuHeader(stringResource(R.string.size)) { level = WidgetMenuLevel.MAIN }
                                    widgetSizeChoices(placement.spanX, placement.spanY, constraints != null, ::fits).forEach { choice ->
                                        val label = when (choice.shape) {
                                            WidgetShape.SMALL -> stringResource(R.string.small)
                                            WidgetShape.MEDIUM -> stringResource(R.string.medium)
                                            WidgetShape.LARGE -> stringResource(R.string.large)
                                            WidgetShape.EXTRA_LARGE -> stringResource(R.string.widget_size_extra_large)
                                            WidgetShape.TINY -> stringResource(R.string.widget_shape_tiny)
                                            WidgetShape.ROW -> stringResource(R.string.widget_shape_row)
                                            WidgetShape.COLUMN -> stringResource(R.string.widget_shape_column)
                                            WidgetShape.CURRENT -> stringResource(R.string.widget_size_dimensions, choice.width, choice.height)
                                        }
                                        MenuRow(label, checked = choice.selected, enabled = choice.enabled, maxLines = Int.MAX_VALUE,
                                            tag = "widget-size-${choice.shape.name.lowercase()}-${placement.slot}") {
                                            if (!choice.selected) onResize(choice.width, choice.height)
                                            onClose()
                                        }
                                        MenuDivider()
                                    }
                                    MenuRow(stringResource(R.string.resize_on_home), Icons.Rounded.OpenInFull, enabled = feasible, maxLines = Int.MAX_VALUE,
                                        tag = "widget-menu-resize-on-home") { onStartResize(placement.spanX, placement.spanY) }
                                }
                                WidgetMenuLevel.MORE -> {
                                    WidgetMenuHeader(stringResource(R.string.more)) { level = WidgetMenuLevel.MAIN }
                                    val entries = widgetMoreEntries(homePages, onPlace != null, onExpand != null, onRemovePhoto != null, stacked)
                                    entries.forEachIndexed { index, entry ->
                                        if (index > 0) MenuDivider()
                                        when (entry) {
                                            WidgetMoreEntry.CUSTOM_SIZE -> MenuRow(stringResource(R.string.custom_size), Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                maxLines = Int.MAX_VALUE, tag = "widget-menu-custom-size") { level = WidgetMenuLevel.CUSTOM_SIZE }
                                            WidgetMoreEntry.MOVE_TO_PAGE -> MenuRow(stringResource(R.string.move_to_page), Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                maxLines = Int.MAX_VALUE, tag = "widget-menu-pages") { level = WidgetMenuLevel.PAGE }
                                            WidgetMoreEntry.PLACE_FREELY -> MenuRow(stringResource(R.string.place_freely), Icons.Rounded.OpenWith, maxLines = Int.MAX_VALUE) { onPlace?.invoke() }
                                            WidgetMoreEntry.EXPAND -> MenuRow(stringResource(R.string.expand_widget_temporarily), Icons.Rounded.OpenInFull, maxLines = Int.MAX_VALUE) { onExpand?.invoke() }
                                            WidgetMoreEntry.REMOVE_PHOTO -> MenuRow(stringResource(R.string.remove_this_photo), Icons.Rounded.HideImage, maxLines = Int.MAX_VALUE) { onRemovePhoto?.invoke() }
                                            WidgetMoreEntry.EDIT_STACK -> MenuRow(stringResource(R.string.edit_stack), Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                maxLines = Int.MAX_VALUE, tag = "widget-menu-edit-stack") { level = WidgetMenuLevel.STACK }
                                        }
                                    }
                                }
                                WidgetMenuLevel.CUSTOM_SIZE -> {
                                    WidgetMenuHeader(stringResource(R.string.custom_size)) { level = WidgetMenuLevel.MORE }
                                    if (!feasible) Text(stringResource(R.string.move_this_widget_into_the_six_row_grid_b), color = FolioColors.RedOnDark, fontSize = FolioType.FOOTNOTE.sp,
                                        modifier = Modifier.padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.SMALL.dp))
                                    SizeStepper(stringResource(R.string.width), pluralStringResource(R.plurals.columns, width, width),
                                        stringResource(R.string.decrease_widget_width), stringResource(R.string.increase_widget_width),
                                        enabled = constraints?.canResizeHorizontally != false && feasible,
                                        onMinus = { width = (width - 1).coerceAtLeast(minWidth) }, onPlus = { width = (width + 1).coerceAtMost(maxSpanW) })
                                    MenuDivider()
                                    SizeStepper(stringResource(R.string.height), pluralStringResource(R.plurals.rows, height, height),
                                        stringResource(R.string.decrease_widget_height), stringResource(R.string.increase_widget_height),
                                        enabled = constraints?.canResizeVertically != false && feasible,
                                        onMinus = { height = (height - 1).coerceAtLeast(minHeight) }, onPlus = { height = (height + 1).coerceAtMost(maxSpanH) })
                                    if (!valid) Text(stringResource(R.string.that_size_overlaps_another_item_or_exten), color = Color.White.copy(alpha = .6f), fontSize = FolioType.FOOTNOTE.sp,
                                        modifier = Modifier.padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.SMALL.dp))
                                    MenuDivider()
                                    MenuRow(stringResource(R.string.apply_size), enabled = valid, maxLines = Int.MAX_VALUE, tag = "widget-menu-apply-size") {
                                        if (valid) { onResize(width, height); onClose() }
                                    }
                                }
                                WidgetMenuLevel.PAGE -> {
                                    WidgetMenuHeader(stringResource(R.string.move_to_page)) { level = WidgetMenuLevel.MORE }
                                    repeat(homePages) { page ->
                                        MenuRow(stringResource(R.string.page_1, page + 1), checked = placement.page == page, maxLines = Int.MAX_VALUE,
                                            tag = "widget-move-${placement.slot}-page-$page") {
                                            if (placement.page == page) onClose() else if (onMoveToPage(page)) onClose() else pageFull = true
                                        }
                                        MenuDivider()
                                    }
                                    if (pageFull) Text(stringResource(R.string.widget_page_full), color = Color.White.copy(alpha = .6f), fontSize = FolioType.FOOTNOTE.sp,
                                        modifier = Modifier.padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.SMALL.dp))
                                }
                                WidgetMenuLevel.STACK -> {
                                    WidgetMenuHeader(stringResource(R.string.edit_stack)) { level = WidgetMenuLevel.MORE }
                                    stackCards.forEachIndexed { index, card ->
                                        if (index > 0) MenuDivider()
                                        val cardName = stackLabel(card) ?: stringResource(R.string.widget)
                                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = FolioSpace.LARGE.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("${index + 1}. $cardName", Modifier.weight(1f), color = Color.White, fontSize = FolioType.CALLOUT.sp)
                                            if (index > 0) Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { onShowFirstInStack(card) }
                                                .padding(horizontal = FolioSpace.SMALL.dp), contentAlignment = Alignment.Center) {
                                                Text(stringResource(R.string.show_first), color = LocalAccent.current.ink, fontSize = FolioType.FOOTNOTE.sp, textAlign = TextAlign.Center)
                                            }
                                            Box(Modifier.size(FolioTouch.MIN.dp).clickable(role = Role.Button, onClickLabel = stringResource(R.string.remove_from_stack, cardName)) { onRemoveFromStack(card) },
                                                contentAlignment = Alignment.Center) {
                                                Icon(Icons.Rounded.RemoveCircleOutline, stringResource(R.string.remove_from_stack, cardName), tint = FolioColors.RedOnDark, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                    MenuDivider()
                                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = FolioSpace.LARGE.dp, end = FolioSpace.MEDIUM.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f).padding(vertical = FolioSpace.SMALL.dp)) {
                                            Text(stringResource(R.string.smart_rotate), color = Color.White, fontSize = FolioType.CALLOUT.sp)
                                            Text(stringResource(R.string.show_the_next_widget_every_30_minutes), color = Color.White.copy(alpha = .6f), fontSize = FolioType.FOOTNOTE.sp)
                                        }
                                        IosSwitch(stackRotate, onStackRotate)
                                    }
                                    Text(stringResource(R.string.swipe_up_or_down_on_the_stack_to_flip_be), color = Color.White.copy(alpha = .6f), fontSize = FolioType.FOOTNOTE.sp,
                                        modifier = Modifier.padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.SMALL.dp))
                                }
                            }
                        }
                        // Pinned under the scrolling part, so on a short cover screen Remove never sits below the fold. Red is for this alone.
                        if (shown == WidgetMenuLevel.MAIN) {
                            Box(Modifier.fillMaxWidth().height(8.dp).background(Color.Black.copy(alpha = .25f)))
                            MenuRow(stringResource(if (stacked) R.string.remove_stack else R.string.remove_widget_2), Icons.Rounded.RemoveCircleOutline,
                                destructive = true, maxLines = Int.MAX_VALUE, tag = "widget-menu-remove") { onRemove() }
                        }
                    }
                }
            }
        }
    }
}

/** A submenu's top row: a back chevron and the level's name, the way an iOS submenu opens. */
@Composable
private fun WidgetMenuHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onBack)
        .padding(start = FolioSpace.COMPACT.dp, end = FolioSpace.LARGE.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.back), tint = Color.White, modifier = Modifier.size(24.dp))
        Text(title, Modifier.weight(1f).semantics { heading() }, color = Color.White, fontSize = FolioType.CALLOUT.sp, fontWeight = FontWeight.SemiBold)
    }
    MenuDivider()
}

/** One dimension of the custom size: its name and value stacked (so large text wraps instead of clipping), and a minus and plus at 48 dp. */
@Composable
private fun SizeStepper(label: String, value: String, minusLabel: String, plusLabel: String, enabled: Boolean, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = FolioSpace.LARGE.dp, end = FolioSpace.SMALL.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = FolioSpace.SMALL.dp)) {
            Text(label, color = Color.White.copy(alpha = .6f), fontSize = FolioType.FOOTNOTE.sp)
            Text(value, color = Color.White, fontSize = FolioType.CALLOUT.sp)
        }
        StepperButton(Icons.Rounded.Remove, minusLabel, enabled, onMinus)
        StepperButton(Icons.Rounded.Add, plusLabel, enabled, onPlus)
    }
}

@Composable
private fun StepperButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(FolioTouch.MIN.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = if (enabled) .14f else .06f)), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White.copy(alpha = if (enabled) 1f else .35f), modifier = Modifier.size(18.dp))
        }
    }
}
