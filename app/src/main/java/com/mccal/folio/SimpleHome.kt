package com.mccal.folio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Battery2Bar
import androidx.compose.material.icons.rounded.Battery4Bar
import androidx.compose.material.icons.rounded.Battery6Bar
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

/**
 * Simple Home in place of Folio's while Simple Mode is on: LauncherScreen's one branch point asks this first and draws
 * nothing else when it returns true. Shown whenever Simple is on, whatever [FeatureGate.SIMPLE_MODE] says, so a phone
 * already in Simple always keeps its way out; the gate is where Simple is turned on (SimpleSettings.kt).
 */
@Composable
internal fun simpleHomeShown(state: LauncherState, model: LauncherModel, homeRequests: Int, onLaunch: (AppEntry) -> Unit): Boolean {
    SimpleQuiet.active = state.simple != null
    val setup = state.simple ?: return false
    if (SimpleQuiet.allSettings.value) { SimpleAllSettingsWatch(homeRequests); return false }
    SimpleWindow { SimpleHome(state, setup, model, homeRequests, onLaunch) }
    return true
}

/**
 * While a helper has Folio's full Settings open from Helper Settings, Folio's own Home is underneath it. Simple comes
 * back as soon as Settings closes, the Home button is pressed or Folio leaves the screen (or if Settings never opened).
 */
@Composable
private fun SimpleAllSettingsWatch(homeRequests: Int) {
    val back = { SimpleQuiet.allSettings.value = false }
    val startRequests = remember { homeRequests }
    LaunchedEffect(homeRequests) { if (homeRequests != startRequests) back() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { back() }
    LaunchedEffect(Unit) {
        var opened = false
        val watch = launch {
            androidx.compose.runtime.snapshotFlow { LauncherSheetsOpen.intValue }.collect { open ->
                if (open > 0) opened = true else if (opened) back()
            }
        }
        kotlinx.coroutines.delay(SIMPLE_SETTINGS_WAIT_MS)
        if (!opened) { watch.cancel(); back() }
    }
}

private const val SIMPLE_SETTINGS_WAIT_MS = 5_000L

/** The Simple screens. Home and More Apps are the person's; the rest are the helper's, behind the screen lock. */
internal enum class SimpleScreen(val parent: SimpleScreen?) {
    HOME(null), MORE(HOME), SETTINGS(HOME), TEXT_SIZE(SETTINGS), EDIT(SETTINGS), ADD(EDIT), REORDER(EDIT), REMOVE(EDIT),
}

/**
 * Simple's own full-screen window. On the Fold8 a full-screen surface drawn in Home's own window is cut short at the top
 * and bottom, which no insets setting fixes, so Simple sits in a window of its own, as Folio's other full-screen
 * surfaces do. No dim and no blur behind it: Simple is opaque. Android's status bar stays, so signal, Wi-Fi and battery
 * are always in sight (a call that fails has a visible reason), with Simple's content padded below it.
 */
@Composable
private fun SimpleWindow(content: @Composable () -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false,
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val dark = LocalDuoPalette.current == DarkDuoPalette
        LaunchedEffect(view, dark) {
            (view.parent as? DialogWindowProvider)?.window?.let { w ->
                w.setDimAmount(0f)
                w.setWindowAnimations(0)
                androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).apply {
                    show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                    isAppearanceLightStatusBars = !dark
                }
            }
        }
        content()
    }
}

// ---------- look ----------

/** Simple's colors: solid surfaces only, light or dark with Folio's appearance. */
@Immutable
internal data class SimpleColors(
    val background: Color, val tile: Color, val raised: Color, val line: Color,
    val text: Color, val secondary: Color, val accent: Color, val onAccent: Color, val danger: Color,
)

@Composable
internal fun simpleColors(): SimpleColors {
    val accent = LocalAccent.current.fill
    return if (LocalDuoPalette.current == DarkDuoPalette)
        SimpleColors(Color.Black, FolioColors.SecondaryBackground, FolioColors.SheetSurface, FolioColors.Gray,
            Color.White, Color.White.copy(alpha = .62f), accent, Color.White, FolioColors.Red)
    else SimpleColors(FolioColors.LightBackground, Color.White, Color.White, FolioColors.Gray,
        Color.Black, Color.Black.copy(alpha = .62f), accent, Color.White, FolioColors.RedLight)
}

internal val LocalSimpleColors = staticCompositionLocalOf<SimpleColors?> { null }

/** Simple's Text Size, on top of Android's font size. It changes Folio only. */
private val LocalSimpleScale = staticCompositionLocalOf { 1f }

/** How fast a size grows with bigger text: labels and body grow most, the clock and titles least, so 200% still fits. */
internal object SimpleGrowth {
    const val LABEL = .75f
    const val TITLE = .25f
    const val SMALL = 1f
}

/**
 * A text size of [base] at 100%, grown like Android 14's nonlinear font scaling: big text grows less than small text.
 * Worked out from Android's font size and Simple's Text Size together and given in exact dp, so Android's own
 * nonlinear curve doesn't apply a second time.
 */
@Composable
internal fun simpleText(base: Float, growth: Float = SimpleGrowth.LABEL): TextUnit {
    val factor = simpleGrowth(growth)
    return with(LocalDensity.current) { (base * factor).dp.toSp() }
}

/** How much bigger than at 100% something that grows at [growth] is now, for sizing a control around its words. */
@Composable
internal fun simpleGrowth(growth: Float): Float = 1f + (LocalDensity.current.fontScale * LocalSimpleScale.current - 1f) * growth

/** A short fade as a screen appears, and nothing else moves. It snaps in place if frames stop, so nothing stays invisible. */
@Composable
private fun rememberSimpleFade(): State<Float> {
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(FolioMotion.FADE_MS)) }
    DisposableEffect(Unit) {
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main.immediate)
        scope.launch { kotlinx.coroutines.delay(SIMPLE_FADE_TIMEOUT_MS); if (fade.value != 1f) fade.snapTo(1f) }
        onDispose { scope.cancel() }
    }
    return fade.asState()
}

private const val SIMPLE_FADE_TIMEOUT_MS = 600L

/** Every Simple surface: Simple's colors and text size, opaque and filling its window. */
@Composable
internal fun SimpleSurface(textSize: SimpleTextSize, content: @Composable () -> Unit) {
    val colors = simpleColors()
    CompositionLocalProvider(LocalSimpleColors provides colors, LocalSimpleScale provides textSize.scale, content = content)
}

internal val simpleTheme: SimpleColors @Composable get() = LocalSimpleColors.current ?: simpleColors()

// ---------- Simple Home ----------

@Composable
private fun SimpleHome(state: LauncherState, setup: SimpleSetup, model: LauncherModel, homeRequests: Int, onLaunch: (AppEntry) -> Unit) {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(SimpleScreen.HOME) }
    var hint by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    // How many apps Home had room for, so More Apps lists the rest. Written while Home lays out, read only by More Apps.
    val shownOnHome = remember { mutableIntStateOf(SIMPLE_MAX_APPS) }
    // The Home button, or Folio going to the background: back to Home, so Helper Settings never stays open.
    LaunchedEffect(homeRequests) { screen = SimpleScreen.HOME; hint = false }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { screen = SimpleScreen.HOME; hint = false; holding = false }
    // Back, like Home, just returns to Simple Home. On Home it does nothing, as on any Home.
    BackHandler { hint = false; screen = SimpleScreen.HOME }
    val byId = remember(state.apps) { state.apps.associateBy { it.id } }
    val apps = setup.apps.mapNotNull { byId[it] }
    val callId by produceState<String?>(null, state.apps) {
        value = withContext(Dispatchers.IO) { runCatching { simpleRoleApps(context, state.apps).call }.getOrNull() }
    }
    val callLabel = stringResource(R.string.simple_call)
    val label: (AppEntry) -> String = { if (it.id == callId) callLabel else it.label }
    val openHelper = { unlockHelper(context, onOpen = { screen = SimpleScreen.SETTINGS }, onNoLock = { holding = true }) }
    SimpleSurface(setup.textSize) {
        val colors = simpleTheme
        Box(Modifier.fillMaxSize().background(colors.background)) {
            key(screen) {
                val fade by rememberSimpleFade()
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade }) {
                    when (screen) {
                        SimpleScreen.HOME -> SimpleHomeScreen(apps, setup.style, label, shownOnHome,
                            onLaunch = { hint = false; onLaunch(it) }, onHold = { hint = true },
                            onMore = { hint = false; screen = SimpleScreen.MORE }, onHelper = { hint = false; openHelper() })
                        SimpleScreen.MORE -> SimpleMoreApps(apps.drop(shownOnHome.intValue), label, onLaunch = { hint = false; onLaunch(it) },
                            onHold = { hint = true }, onBack = { hint = false; screen = SimpleScreen.HOME })
                        else -> SimpleHelperPages(screen, state, setup, model, label, go = { screen = it })
                    }
                }
            }
            if (hint && (screen == SimpleScreen.HOME || screen == SimpleScreen.MORE)) SimpleHint(Modifier.align(Alignment.BottomCenter)) { hint = false }
        }
        if (holding) SimpleHoldDialog(onCancel = { holding = false }, onOpen = { holding = false; screen = SimpleScreen.SETTINGS })
    }
}

/**
 * Home: a big clock, the date and battery, the helper's apps and a quiet way into Helper Settings. It never scrolls:
 * full size when everything fits; else a one-line header and 76 dp list rows; else as many apps as fit and More Apps
 * for the rest. Only when even one app and More Apps don't fit (very big text in a split-screen window) does it scroll
 * rather than drop the clock or shrink a target. Unfolded or on its side, the clock and Helper Settings sit on the left
 * and the apps on the right, either side of the fold.
 */
@Composable
private fun SimpleHomeScreen(apps: List<AppEntry>, style: SimpleStyle, label: (AppEntry) -> String, shownOnHome: androidx.compose.runtime.MutableIntState,
    onLaunch: (AppEntry) -> Unit, onHold: () -> Unit, onMore: () -> Unit, onHelper: () -> Unit) {
    val hinge = LocalHinge.current
    val density = LocalDensity.current
    val context = LocalContext.current
    // A swipe down opens Android's own notification shade, as on any phone, through Folio's gestures service when it
    // is on (Android's status bar still comes down from the top edge when it isn't). A swipe up does nothing.
    val pull = with(density) { 64.dp.toPx() }
    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) {
        var travel = 0f
        detectVerticalDragGestures(onDragStart = { travel = 0f }) { _, dy ->
            travel += dy
            if (travel > pull) { travel = Float.NEGATIVE_INFINITY; SystemShadeAccessibilityService.open(context, ShadePanel.NOTIFICATIONS) }
        }
    }.simpleInsets().padding(start = FolioSpace.LARGE.dp, end = FolioSpace.LARGE.dp,
        top = FolioSpace.MEDIUM.dp, bottom = FolioSpace.LARGE.dp)) {
        val wide = maxWidth >= 700.dp || maxWidth > maxHeight
        val hingeGap = hinge?.takeIf { it.vertical }?.let { with(density) { (it.endPx - it.startPx).toDp() } } ?: FolioSpace.XXL.dp
        val columnGap = hingeGap + FolioSpace.HUGE.dp
        val fitHeight = constraints.maxHeight
        val scroll = rememberScrollState()
        SubcomposeLayout(Modifier.fillMaxWidth().verticalScroll(scroll, enabled = scroll.maxValue > 0)) { c ->
            val width = c.maxWidth
            val gapPx = columnGap.roundToPx()
            val columnWidth = if (wide) ((width - gapPx) / 2).coerceAtLeast(0) else width
            fun measureAll(compact: Boolean, shown: Int): List<androidx.compose.ui.layout.Placeable> {
                val items: List<AppEntry?> = apps.take(shown) + if (shown < apps.size) listOf(null) else emptyList()
                val loose = Constraints(maxWidth = columnWidth)
                val head = subcompose("head-$compact") { SimpleHeader(compact) }.map { it.measure(loose) }
                val block = subcompose("apps-$compact-$shown") {
                    SimpleAppsBlock(items, style, compact, label, onLaunch, onHold, onMore)
                }.map { it.measure(Constraints(minWidth = columnWidth, maxWidth = columnWidth)) }
                val foot = subcompose("foot-$compact") { SimpleFoot(compact, onHelper) }.map { it.measure(Constraints(minWidth = columnWidth, maxWidth = columnWidth)) }
                return listOf(head.single(), block.single(), foot.single())
            }
            fun gap(compact: Boolean) = (if (compact) FolioSpace.MEDIUM else FolioSpace.XL).dp.roundToPx()
            fun height(parts: List<androidx.compose.ui.layout.Placeable>, compact: Boolean): Int {
                val (head, block, foot) = parts
                return if (wide) maxOf(head.height + gap(compact) + foot.height, block.height)
                else head.height + gap(compact) + block.height + gap(compact) + foot.height
            }
            val n = apps.size
            val tries = buildList {
                add(false to n); add(true to n)
                // One row becomes More Apps, so each step down shows one app fewer and More Apps in its place.
                for (shown in (n - 2) downTo 1) add(true to shown)
            }
            var chosen = tries.first()
            var parts = measureAll(chosen.first, chosen.second)
            for (attempt in tries.drop(1)) {
                if (height(parts, chosen.first) <= fitHeight) break
                chosen = attempt
                parts = measureAll(attempt.first, attempt.second)
            }
            shownOnHome.intValue = chosen.second
            val compact = chosen.first
            val (head, block, foot) = parts
            val total = maxOf(height(parts, compact), fitHeight)
            layout(width, total) {
                head.placeRelative(0, 0)
                if (wide) {
                    block.placeRelative(columnWidth + gapPx, 0)
                    foot.placeRelative(0, (total - foot.height).coerceAtLeast(head.height + gap(compact)))
                } else {
                    block.placeRelative(0, head.height + gap(compact))
                    foot.placeRelative(0, head.height + gap(compact) + block.height + gap(compact))
                }
            }
        }
    }
}

/** Room for Android's status bar and the camera at the top, and the navigation bar at the bottom. */
@Composable
private fun Modifier.simpleInsets(): Modifier = windowInsetsPadding(WindowInsets.folioSafeTop.only(WindowInsetsSides.Top)
    .union(WindowInsets.navigationBars).union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)))

/** The time, the date and the battery: the three things people check most. One line when Home is short of room. */
@Composable
private fun SimpleHeader(compact: Boolean) {
    val colors = simpleTheme
    val context = LocalContext.current
    val tick by rememberMinuteTick()
    val now = displayNow(tick)
    val status = LocalDeviceStatus.current
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val timePattern = remember(is24) { android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), if (is24) "Hm" else "hma") }
    val time = now.format(DateTimeFormatter.ofPattern(timePattern))
    val date = now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d)))
    val small = simpleText(if (compact) 20f else 22f)
    val battery: @Composable () -> Unit = {
        status.battery?.let { level ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(batteryIcon(level, status.charging), if (status.charging) stringResource(R.string.simple_charging) else null,
                    tint = colors.text, modifier = Modifier.size(if (compact) 26.dp else 30.dp))
                Spacer(Modifier.size(FolioSpace.SNUG.dp))
                Text(stringResource(R.string.simple_battery_percent, level), color = colors.text, fontSize = small)
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.TINY.dp).semantics(mergeDescendants = true) { heading() }) {
        if (compact) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(time, color = colors.text, fontSize = simpleText(34f, SimpleGrowth.TITLE), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                battery()
            }
            Text(date, color = colors.secondary, fontSize = small)
        } else {
            Text(time, color = colors.text, fontSize = simpleText(64f, SimpleGrowth.TITLE), fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().padding(top = FolioSpace.TINY.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(date, Modifier.weight(1f), color = colors.secondary, fontSize = small)
                Spacer(Modifier.size(FolioSpace.MEDIUM.dp))
                battery()
            }
        }
    }
}

private fun batteryIcon(level: Int, charging: Boolean): ImageVector = when {
    charging -> Icons.Rounded.BatteryChargingFull
    level >= 90 -> Icons.Rounded.BatteryFull
    level >= 60 -> Icons.Rounded.Battery6Bar
    level >= 35 -> Icons.Rounded.Battery4Bar
    level >= 15 -> Icons.Rounded.Battery2Bar
    else -> Icons.Rounded.BatteryAlert
}

/** Helper Settings, as a quiet outlined button under the apps, and the preview label. */
@Composable
private fun SimpleFoot(compact: Boolean, onHelper: () -> Unit) {
    val colors = simpleTheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (compact) FolioSpace.SNUG.dp else FolioSpace.SMALL.dp)) {
        SimpleButton(stringResource(R.string.simple_helper_settings), SimpleButtonKind.QUIET, Icons.Rounded.Lock, onClick = onHelper,
            textSize = if (compact) 20f else 22f)
        SimplePreviewLabel()
    }
}

/** Every Simple screen says it is a preview, until Simple ships in 0.7.2. */
@Composable
internal fun SimplePreviewLabel(modifier: Modifier = Modifier) {
    Text(stringResource(R.string.simple_preview_label), modifier.fillMaxWidth(), color = simpleTheme.secondary,
        fontSize = simpleText(13f, SimpleGrowth.SMALL), textAlign = TextAlign.Center)
}

/** The apps, as one list or two-column tiles, with More Apps in place of those Home has no room for (null). */
@Composable
private fun SimpleAppsBlock(items: List<AppEntry?>, style: SimpleStyle, compact: Boolean, label: (AppEntry) -> String,
    onLaunch: (AppEntry) -> Unit, onHold: () -> Unit, onMore: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Two tiles under 88 dp each won't do: a column too narrow for them gets one per row.
        val tiles = style == SimpleStyle.GRID && maxWidth >= 188.dp
        val gap = FolioSpace.MEDIUM.dp
        val item: @Composable (AppEntry?, Modifier) -> Unit = { app, modifier ->
            if (app != null) SimpleAppItem(app, label(app), tiles, compact, { onLaunch(app) }, onHold, modifier)
            else SimpleMoreItem(tiles, compact, onMore, onHold, modifier)
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
            if (!tiles) items.forEach { item(it, Modifier.fillMaxWidth()) }
            else items.chunked(2).forEach { pair -> SimpleTileRow(gap, pair.size) { index, modifier -> item(pair[index], modifier) } }
        }
    }
}

/**
 * Two tiles side by side, always the same height: the taller one's. Tiles hold app icons, and a live Clock or Calendar
 * icon can't be asked for its intrinsic size, so each tile is first measured in a copy that is never drawn and has no
 * semantics, then the real ones are laid out at the common height. A lone tile takes the left half.
 */
@Composable
private fun SimpleTileRow(gap: Dp, count: Int, tile: @Composable (Int, Modifier) -> Unit) {
    SubcomposeLayout(Modifier.fillMaxWidth()) { c ->
        val gapPx = gap.roundToPx()
        val width = ((c.maxWidth - gapPx) / 2).coerceAtLeast(0)
        val height = (0 until count).maxOfOrNull { index ->
            subcompose("size-$index") { tile(index, Modifier.clearAndSetSemantics {}) }.single()
                .measure(Constraints(minWidth = width, maxWidth = width)).height
        } ?: 0
        val tiles = (0 until count).map { index ->
            subcompose("tile-$index") { tile(index, Modifier) }.single().measure(Constraints.fixed(width, height))
        }
        layout(c.maxWidth, height) { tiles.forEachIndexed { index, placeable -> placeable.placeRelative(index * (width + gapPx), 0) } }
    }
}

/** One app on Home. The whole row or tile is the target. A long press moves nothing: it shows the calm Home-is-locked note. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SimpleAppItem(app: AppEntry, label: String, tile: Boolean, compact: Boolean, onClick: () -> Unit, onHold: () -> Unit, modifier: Modifier) {
    SimpleItemFrame(label, tile, compact, onClick, onHold, modifier) { size ->
        // No badge: nothing on Simple Home moves or changes on its own.
        AppIcon(app, null, Modifier.size(size), shape = RoundedCornerShape(FolioRadius.CARD.dp), badge = false)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SimpleMoreItem(tile: Boolean, compact: Boolean, onClick: () -> Unit, onHold: () -> Unit, modifier: Modifier) {
    SimpleItemFrame(stringResource(R.string.simple_more_apps), tile, compact, onClick, onHold, modifier) { size ->
        SimpleGlyph(Icons.Rounded.MoreHoriz, FolioColors.Gray, size)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SimpleItemFrame(label: String, tile: Boolean, compact: Boolean, onClick: () -> Unit, onHold: () -> Unit, modifier: Modifier,
    icon: @Composable (Dp) -> Unit) {
    val colors = simpleTheme
    val shape = RoundedCornerShape(FolioRadius.GROUPED_CARD.dp)
    val clickable = modifier.clip(shape).background(colors.tile)
        .combinedClickable(onClickLabel = stringResource(R.string.open_named, label), onLongClick = onHold, onClick = onClick)
        .semantics(mergeDescendants = true) {}
    if (tile) Column(clickable.heightIn(min = if (compact) 120.dp else 140.dp).padding(horizontal = FolioSpace.COMPACT.dp, vertical = FolioSpace.COMFY.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp, Alignment.CenterVertically)) {
        icon(64.dp)
        Text(label, color = colors.text, fontSize = simpleText(22f), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    } else Row(clickable.heightIn(min = if (compact) 76.dp else 88.dp)
        .padding(horizontal = if (compact) FolioSpace.COMFY.dp else FolioSpace.LARGE.dp, vertical = if (compact) FolioSpace.SMALL.dp else FolioSpace.MEDIUM.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FolioSpace.LARGE.dp)) {
        icon(if (compact) 52.dp else 56.dp)
        Text(label, Modifier.weight(1f), color = colors.text, fontSize = simpleText(24f), fontWeight = FontWeight.SemiBold)
    }
}

/** A plain symbol on a colored square, the size of an app icon, for Simple's own rows. */
@Composable
internal fun SimpleGlyph(icon: ImageVector, background: Color, size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(FolioRadius.CARD.dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * .58f))
    }
}

/** The apps Home had no room for, as a list, with the way back first. */
@Composable
private fun SimpleMoreApps(apps: List<AppEntry>, label: (AppEntry) -> String, onLaunch: (AppEntry) -> Unit, onHold: () -> Unit, onBack: () -> Unit) {
    SimplePage(primary = { SimpleButton(stringResource(R.string.simple_back_to_home), SimpleButtonKind.PRIMARY, onClick = onBack) },
        title = stringResource(R.string.simple_more_apps)) {
        apps.forEach { app -> SimpleAppItem(app, label(app), tile = false, compact = false, onClick = { onLaunch(app) }, onHold = onHold, modifier = Modifier.fillMaxWidth()) }
    }
}

/** What a long press on Home says: nothing moved, and where a helper changes it. Opaque, at the bottom, and read out once. */
@Composable
private fun SimpleHint(modifier: Modifier, onOk: () -> Unit) {
    val colors = simpleTheme
    val fade by rememberSimpleFade()
    Column(modifier.simpleInsets().padding(FolioSpace.MEDIUM.dp).widthIn(max = 440.dp).fillMaxWidth()
        .graphicsLayer { alpha = fade }
        .clip(RoundedCornerShape(FolioRadius.PANEL.dp)).background(colors.raised)
        .border(2.dp, colors.line, RoundedCornerShape(FolioRadius.PANEL.dp)).padding(FolioSpace.LARGE.dp)
        .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp)) {
        Text(stringResource(R.string.simple_home_stays_put), color = colors.text, fontSize = simpleText(22f), fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.simple_home_stays_put_detail), color = colors.text, fontSize = simpleText(19f))
        SimpleButton(stringResource(R.string.ok), SimpleButtonKind.PRIMARY, onClick = onOk)
    }
}

// ---------- shared pieces for Simple pages ----------

internal enum class SimpleButtonKind { PRIMARY, PLAIN, QUIET, DANGER }

/** A Simple button: 72 dp tall at least, with its words always showing. One primary button per screen. */
@Composable
internal fun SimpleButton(text: String, kind: SimpleButtonKind, icon: ImageVector? = null, modifier: Modifier = Modifier,
    textSize: Float = 22f, onClick: () -> Unit) {
    val colors = simpleTheme
    val shape = RoundedCornerShape(FolioRadius.GROUPED_CARD.dp)
    val background = when (kind) { SimpleButtonKind.PRIMARY -> colors.accent; SimpleButtonKind.QUIET -> Color.Transparent; else -> colors.tile }
    val ink = when (kind) { SimpleButtonKind.PRIMARY -> colors.onAccent; SimpleButtonKind.DANGER -> colors.danger; else -> colors.text }
    Row(modifier.fillMaxWidth().heightIn(min = 72.dp).clip(shape).background(background)
        .then(if (kind == SimpleButtonKind.QUIET) Modifier.border(2.dp, colors.line, shape) else Modifier)
        .simpleClickable(Role.Button, onClick)
        .padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.COMPACT.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, null, tint = ink, modifier = Modifier.size(28.dp)); Spacer(Modifier.size(FolioSpace.COMPACT.dp)) }
        Text(text, color = ink, fontSize = simpleText(textSize), fontWeight = if (kind == SimpleButtonKind.QUIET) FontWeight.Medium else FontWeight.SemiBold,
            textAlign = TextAlign.Center)
    }
}

internal fun Modifier.simpleClickable(role: Role, onClick: () -> Unit): Modifier = clickable(role = role, onClick = onClick)

/** A Simple page: its main button first, then the title, then what's on it, scrolling when it doesn't fit. */
@Composable
internal fun SimplePage(primary: @Composable () -> Unit, title: String, content: @Composable () -> Unit) {
    val colors = simpleTheme
    Box(Modifier.fillMaxSize().simpleInsets(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 620.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = FolioSpace.LARGE.dp, end = FolioSpace.LARGE.dp, top = FolioSpace.MEDIUM.dp, bottom = FolioSpace.LARGE.dp),
            verticalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
            primary()
            Text(title, Modifier.padding(start = FolioSpace.TINY.dp, end = FolioSpace.TINY.dp, top = FolioSpace.SMALL.dp).semantics { heading() },
                color = colors.text, fontSize = simpleText(34f, SimpleGrowth.TITLE), fontWeight = FontWeight.Bold)
            content()
            SimplePreviewLabel()
        }
    }
}
