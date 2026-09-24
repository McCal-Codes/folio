package com.mccal.folio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Icon Stacks: apps tucked behind a Home icon. The icon still opens its app; swiping down on it fans the stack out. */
internal object IconStacks {
    const val MAX = 6

    fun toggle(stacks: Map<String, List<String>>, anchor: String, app: String): Map<String, List<String>> {
        if (app == anchor) return stacks
        val current = stacks[anchor].orEmpty()
        val next = if (app in current) current - app else (current + app).take(MAX)
        return if (next.isEmpty()) stacks - anchor else stacks + (anchor to next)
    }

    /** Stacks without apps that are gone, and without stacks left empty. */
    fun prune(stacks: Map<String, List<String>>, installed: Set<String>): Map<String, List<String>> =
        stacks.filterKeys { it in installed }.mapValues { (_, apps) -> apps.filter { it in installed } }.filterValues { it.isNotEmpty() }
}

/** What an icon's downward swipe does: open its stack (provided by Home; null when there's no stack or while editing). */
internal val LocalIconStack = staticCompositionLocalOf<((AppEntry) -> Unit)?> { null }
/** Anchors that have a stack, for the layered look behind their icons. */
internal val LocalStackedApps = staticCompositionLocalOf { emptySet<String>() }

/** Swipe up (app panel) or down (icon stack) on an icon; taps and long-presses pass through. */
internal fun Modifier.iconSwipes(onSwipeUp: (() -> Unit)?, onSwipeDown: (() -> Unit)?): Modifier =
    if (onSwipeUp == null && onSwipeDown == null) this else pointerInput(onSwipeUp, onSwipeDown) {
        val threshold = 28.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed || change.isConsumed) break
                val d = change.position - down.position
                if (onSwipeUp != null && -d.y > threshold && abs(d.x) < -d.y * .6f) { change.consume(); onSwipeUp(); break }
                if (onSwipeDown != null && d.y > threshold && abs(d.x) < d.y * .6f) { change.consume(); onSwipeDown(); break }
                if (abs(d.y) > threshold || abs(d.x) > threshold) break
            }
        }
    }

/** A card peeking out behind a stacked icon, like a small pile. */
@Composable
internal fun StackPeek(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).graphicsLayer { translationY = 4.dp.toPx(); scaleX = .86f; scaleY = .86f }
        .clip(RoundedCornerShape(size * .24f)).background(Color.White.copy(alpha = .28f)))
}

/**
 * The fan: the stack's apps drop out of the icon in a column (above it when there's no room below), each springing in a
 * moment after the last, with its name beside it. Tap one to open it; tap anywhere else to put them back.
 */
@Composable
internal fun IconStackFan(anchor: AppEntry, apps: List<AppEntry>, onDismiss: () -> Unit, onLaunch: (AppEntry) -> Unit) {
    val density = LocalDensity.current
    val reduceMotion = LocalReduceMotion.current
    val progress = remember(apps.size) { apps.map { Animatable(if (reduceMotion) 1f else 0f) } }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            progress.forEachIndexed { i, p ->
                launch { kotlinx.coroutines.delay(i * 35L); p.animateTo(1f, spring(dampingRatio = .68f, stiffness = 520f)) }
            }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        FolioDialogWindow(dim = 0f, blurRadiusDp = 16)
        var origin by remember { mutableStateOf(Offset.Zero) }
        BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionOnScreen() }
            .background(Color.Black.copy(alpha = .55f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss).testTag("icon-stack-fan")) {
            val icon = IconBounds.of(anchor.id)
            val size = icon?.width()?.toFloat() ?: with(density) { 60.dp.toPx() }
            val left = (icon?.left?.toFloat() ?: with(density) { maxWidth.toPx() / 2 }) - origin.x
            val top = (icon?.top?.toFloat() ?: with(density) { maxHeight.toPx() / 3 }) - origin.y
            val pitch = size + with(density) { 16.dp.toPx() }
            val below = top + size + pitch * apps.size < with(density) { maxHeight.toPx() - 24.dp.toPx() }
            // Names go on the side with more room.
            val labelsRight = left + size / 2 < with(density) { maxWidth.toPx() / 2 }
            AppIcon(anchor, null, Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }.size(with(density) { size.toDp() }),
                shape = RoundedCornerShape(with(density) { (size * .24f).toDp() }), badge = false)
            apps.forEachIndexed { i, app ->
                val p = progress[i]
                val targetY = if (below) top + pitch * (i + 1) else top - pitch * (i + 1)
                Row(Modifier.offset { IntOffset((if (labelsRight) left else left - with(density) { 180.dp.toPx() } ).roundToInt(),
                        (top + (targetY - top) * p.value).roundToInt()) }
                    .graphicsLayer { alpha = p.value.coerceIn(0f, 1f); val s = .6f + .4f * p.value; scaleX = s; scaleY = s }
                    .clip(RoundedCornerShape(16.dp)).clickable { onLaunch(app) }.testTag("icon-stack-app-${app.id}"),
                    verticalAlignment = Alignment.CenterVertically) {
                    val iconSize = with(density) { size.toDp() }
                    val label: @Composable () -> Unit = {
                        Box(Modifier.width(168.dp).padding(horizontal = 10.dp), contentAlignment = if (labelsRight) Alignment.CenterStart else Alignment.CenterEnd) {
                            Text(app.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(FolioColors.SecondaryBackground.copy(alpha = .9f)).padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                    if (!labelsRight) label()
                    AppIcon(app, null, Modifier.size(iconSize), shape = RoundedCornerShape(iconSize * .24f))
                    if (labelsRight) label()
                }
            }
        }
    }
}

/** Choose the apps in a stack: every app with a check, up to [IconStacks.MAX]. */
@Composable
internal fun IconStackEditor(anchor: AppEntry, apps: List<AppEntry>, chosen: List<String>, onToggle: (String) -> Unit, onDone: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) { apps.filter { it.id != anchor.id && !it.isWork && it.label.contains(query.trim(), true) } }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).padding(horizontal = 16.dp).testTag("icon-stack-editor")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(anchor, null, Modifier.size(36.dp), shape = RoundedCornerShape(9.dp), badge = false)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.stack_1, anchor.label), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.swipe_down_on_1_to_open_these_2_of_3, anchor.label, chosen.size, IconStacks.MAX), color = Color.White.copy(alpha = .6f), fontSize = 13.sp)
            }
            Text(stringResource(R.string.done), color = LocalAccent.current.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onDone).padding(8.dp))
        }
        IosSearchField(query, { query = it }, "Search apps", Modifier.padding(vertical = 12.dp))
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(Modifier.weight(1f).edgeFade(listState), state = listState) {
            items(shown, key = { it.id }) { app ->
                val on = app.id in chosen
                val full = !on && chosen.size >= IconStacks.MAX
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = !full) { onToggle(app.id) }
                    .graphicsLayer { alpha = if (full) .4f else 1f }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app, null, Modifier.size(40.dp), shape = RoundedCornerShape(10.dp), badge = false)
                    Text(app.label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Icon(if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, if (on) "In stack" else "Not in stack",
                        tint = if (on) LocalAccent.current.ink else Color.White.copy(alpha = .35f), modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
