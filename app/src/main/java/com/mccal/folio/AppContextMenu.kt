package com.mccal.folio

import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Last on-screen bounds of each app icon, so a long-press menu can lift the icon in place. */
internal object IconBounds {
    private val bounds = HashMap<String, Rect>()
    fun update(id: String, rect: Rect) { if (!rect.isEmpty) bounds[id] = Rect(rect) }
    fun of(id: String): Rect? = bounds[id]
}

internal data class QuickAction(val label: String, val icon: Bitmap?, val info: ShortcutInfo)

/** The app's own shortcuts (Folio can read them as the default Home app). Call off the main thread. */
internal fun loadQuickActions(context: android.content.Context, app: AppEntry, limit: Int = 4): List<QuickAction> = runCatching {
    val apps = context.getSystemService(LauncherApps::class.java)
    if (!apps.hasShortcutHostPermission()) return@runCatching emptyList()
    val query = LauncherApps.ShortcutQuery().setPackage(app.component.packageName).setActivity(app.component)
        .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC)
    apps.getShortcuts(query, app.user).orEmpty().filter { it.isEnabled }.sortedBy { it.rank }.take(limit).map { info ->
        QuickAction((info.shortLabel ?: info.longLabel ?: "").toString(),
            runCatching { apps.getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi)?.toBitmap(96, 96) }.getOrNull(), info)
    }
}.getOrDefault(emptyList())

/**
 * iPhone-style long-press menu: the icon lifts where it is, Home blurs behind, and a compact menu
 * appears next to it with the app's own quick actions first, then Folio's actions.
 */
@Composable
internal fun AppContextMenu(
    app: AppEntry, onHome: Boolean, hidden: Boolean,
    /** The Focus locking Home editing, if any: editing rows are replaced by a note. */
    lockedBy: String? = null,
    onDismiss: () -> Unit, onMove: () -> Unit, onAddOrRemove: () -> Unit, onCreateFolder: () -> Unit, hasFolders: Boolean = false,
    onWidgets: (() -> Unit)?, onToggleHidden: () -> Unit, onInfo: () -> Unit, onRename: () -> Unit,
    /** Choose the apps tucked behind this icon (Icon Stacks); null where stacks don't apply. */
    onStack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val appear = remember { Animatable(0f) }
    var more by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appear.animateTo(1f, MotionSpeed.spring(.72f, Spring.StiffnessMediumLow)) }
    DisposableEffect(Unit) { LauncherSheetsOpen.intValue++; onDispose { LauncherSheetsOpen.intValue-- } }

    val actions by produceState(emptyList<QuickAction>(), app.id) { if (!app.isShortcut) value = withContext(Dispatchers.IO) { loadQuickActions(context, app) } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
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
        BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionOnScreen() }
            .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }.background(Color.Black.copy(alpha = .28f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)) {
            val screenW = with(density) { maxWidth.toPx() }
            val screenH = with(density) { maxHeight.toPx() }
            val icon = IconBounds.of(app.id) ?: Rect((screenW / 2 - 80).toInt(), (screenH / 3).toInt(), (screenW / 2 + 80).toInt(), (screenH / 3 + 160).toInt())
            val iconLeft = icon.left - origin.x
            val iconTop = icon.top - origin.y
            val iconSize = icon.width().toFloat()

            // Lifted icon, exactly where it was.
            AppIcon(app, null, Modifier.offset { IntOffset(iconLeft.roundToInt(), iconTop.roundToInt()) }
                .size(with(density) { iconSize.toDp() })
                .graphicsLayer { val s = 1f + .1f * appear.value; scaleX = s; scaleY = s }
                , shape = RoundedCornerShape(with(density) { (iconSize * .24f).toDp() }))

            // Menu below the icon, or above when there's no room; aligned to the icon, kept on screen.
            val menuW = with(density) { 260.dp.toPx() }
            val gap = with(density) { 14.dp.toPx() }
            val safeTop = with(density) { 56.dp.toPx() }      // clear of the camera and island
            val safeBottom = with(density) { 32.dp.toPx() }
            val spaceBelow = screenH - (iconTop + iconSize * 1.1f + gap) - safeBottom
            val spaceAbove = iconTop - gap - safeTop
            // Folio's own rows: Edit Home Screen, Remove/Add, More, plus Clear Badge when the app has one.
            val folioRows = 3 + if ((LocalBadgeCounts.current[app.packageName] ?: 0) > 0 && LocalIconLook.current.badges != BadgeStyle.OFF && lockedBy == null) 1 else 0
            val estimatedH = with(density) { (49.dp * (actions.size + folioRows) + 8.dp).toPx() }
            // Prefer below (like iOS) when it fits; otherwise whichever side has more room, scrolling if needed.
            val below = estimatedH <= spaceBelow || spaceBelow >= spaceAbove
            val maxMenuH = with(density) { (if (below) spaceBelow else spaceAbove).coerceAtLeast(120f).toDp() }
            // Keep Folio's own rows (Edit, Remove, More) visible without scrolling: drop app quick actions that don't fit.
            val rowPx = with(density) { 49.dp.toPx() }
            // Opening More swaps the app's quick actions for Folio's extra rows, so the menu doesn't need to scroll.
            val shownActions = if (more) emptyList() else actions.take((((if (below) spaceBelow else spaceAbove) - with(density) { 8.dp.toPx() }) / rowPx - folioRows).toInt().coerceAtLeast(0))
            val menuLeft = (iconLeft + iconSize / 2 - menuW / 2).coerceIn(gap, screenW - menuW - gap)
            val origX = ((iconLeft + iconSize / 2 - menuLeft) / menuW).coerceIn(0f, 1f)
            // Positioned from the measured menu height: the dialog can be shorter than the screen (navigation bar),
            // so aligning to its bottom edge made the menu overlap the lifted icon.
            var menuH by remember { mutableIntStateOf(0) }
            val lift = iconSize * .05f
            Column(Modifier.offset {
                    IntOffset(menuLeft.roundToInt(),
                        if (below) (iconTop + iconSize + lift + gap).roundToInt() else (iconTop - lift - gap - menuH).roundToInt())
                }
                .onSizeChanged { menuH = it.height }
                .width(260.dp)
                .heightIn(max = maxMenuH)
                .graphicsLayer {
                    val s = .7f + .3f * appear.value; scaleX = s; scaleY = s
                    transformOrigin = TransformOrigin(origX, if (below) 0f else 1f)
                }
                .clip(RoundedCornerShape(18.dp)).background(Color(0xFF2A2A2E).copy(alpha = .96f))
                .border(FolioGlass.edge, RoundedCornerShape(18.dp))
                .clickable(remember { MutableInteractionSource() }, null) {}
                .fadingVerticalScroll()) {
                shownActions.forEachIndexed { i, action ->
                    MenuRow(action.label, bitmap = action.icon) {
                        onDismiss()
                        runCatching { context.getSystemService(LauncherApps::class.java).startShortcut(action.info, null, null) }
                    }
                    if (i == shownActions.lastIndex) Box(Modifier.fillMaxWidth().height(8.dp).background(Color.Black.copy(alpha = .25f)))
                    else MenuDivider()
                }
                if (lockedBy != null) {
                    Text(stringResource(R.string.home_editing_is_off_while_1_is_on, lockedBy), color = Color.White.copy(alpha = .55f), fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    MenuDivider()
                } else {
                MenuRow(stringResource(R.string.edit_home_screen), Icons.Rounded.AppRegistration) { onMove() }
                MenuDivider()
                if ((LocalBadgeCounts.current[app.packageName] ?: 0) > 0 && LocalIconLook.current.badges != BadgeStyle.OFF) {
                    val activity = androidx.activity.compose.LocalActivity.current as? MainActivity
                    MenuRow(stringResource(R.string.clear_badge), Icons.Rounded.NotificationsOff) {
                        activity?.let { BadgeClears.clear(app.packageName, it.latestNotifications) }; onDismiss()
                    }
                    MenuDivider()
                }
                // A shortcut exists only as this icon, so removing it deletes it (like iOS's "Delete Bookmark").
                MenuRow(when { app.isShortcut -> stringResource(R.string.delete_shortcut); onHome -> stringResource(R.string.remove_from_home); else -> stringResource(R.string.add_to_home) },
                    if (onHome || app.isShortcut) Icons.Rounded.RemoveCircleOutline else Icons.Rounded.AddCircleOutline,
                    destructive = onHome || app.isShortcut) { onAddOrRemove() }
                MenuDivider()
                }
                // iOS keeps context menus short: the less common actions sit behind "More".
                if (!more) MenuRow(stringResource(R.string.more), Icons.Rounded.MoreHoriz) { more = true }
                else {
                    if (lockedBy == null) MenuRow(if (hasFolders) stringResource(R.string.add_to_folder) else stringResource(R.string.create_folder), Icons.Rounded.CreateNewFolder) { onCreateFolder() }
                    onWidgets?.let { MenuDivider(); MenuRow(stringResource(R.string.widgets), Icons.Rounded.Widgets) { it() } }
                    onStack?.let { MenuDivider(); MenuRow(stringResource(R.string.stack_apps), Icons.Rounded.Layers) { it() } }
                    MenuDivider()
                    MenuRow(stringResource(R.string.rename), Icons.Rounded.DriveFileRenameOutline) { onRename() }
                    MenuDivider()
                    MenuRow(if (hidden) stringResource(R.string.show_in_app_library) else stringResource(R.string.hide_from_app_library), if (hidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff) { onToggleHidden() }
                    MenuDivider()
                    MenuRow(if (app.isShortcut) stringResource(R.string.info_for_app) else stringResource(R.string.app_info), Icons.Rounded.Info) { onInfo() }
                }
            }
        }
    }
}

@Composable
internal fun MenuRow(label: String, icon: ImageVector? = null, bitmap: Bitmap? = null, destructive: Boolean = false, onClick: () -> Unit) {
    val tint = if (destructive) FolioColors.Red else Color.White
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = tint, fontSize = 16.sp, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        when {
            bitmap != null -> Image(bitmap.asImageBitmap(), null, Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)))
            icon != null -> Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun MenuDivider() = HorizontalDivider(color = Color.White.copy(alpha = .1f), thickness = .5.dp)

/** Rename an app: the typed name replaces the label everywhere, and an empty field puts Android's name back. */
@Composable
internal fun RenameAppAlert(app: AppEntry, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember(app.id) { mutableStateOf(if (app.label == app.systemLabel) "" else app.label) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(app.id) { runCatching { focus.requestFocus() } }
    // The alert follows the system's light or dark theme, so the field takes its colors from the dialog
    // rather than assuming white on dark.
    val ink = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    androidx.compose.material3.AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_app)) },
        text = {
            Column {
                Text(stringResource(R.string.leave_it_empty_to_use_s_again, app.systemLabel), fontSize = 13.sp)
                androidx.compose.foundation.text.BasicTextField(name, { name = it.takeAppName() },
                    Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(ink.copy(alpha = .08f)).padding(horizontal = 10.dp, vertical = 10.dp)
                        .focusRequester(focus).testTag("app-name"),
                    singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = ink, fontSize = 17.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(ink),
                    decorationBox = { field ->
                        Box {
                            // The app's own name as a hint, so an empty field doesn't look like a blank row.
                            if (name.isEmpty()) Text(app.systemLabel, color = ink.copy(alpha = .4f), fontSize = 17.sp)
                            field()
                        }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onRename(name) }))
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onRename(name) }) { Text(stringResource(R.string.done)) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
