package com.mccal.folio

import androidx.compose.ui.res.stringResource
import android.content.pm.LauncherApps
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** Opens an app's quick panel (provided by LauncherScreen; null when the gesture is off). */
internal val LocalAppPanel = staticCompositionLocalOf<((AppEntry) -> Unit)?> { null }

/**
 * Velox-style gesture (idea from Velox by Phillip Tennen): a quick upward flick on an app icon opens its panel.
 * Only claims clearly vertical upward flicks, so taps, long-presses and page swipes behave as before.
 */
internal fun Modifier.swipeUpForPanel(onSwipeUp: (() -> Unit)?): Modifier = if (onSwipeUp == null) this else pointerInput(onSwipeUp) {
    val threshold = 28.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed || change.isConsumed) break
            val d = change.position - down.position
            if (-d.y > threshold && abs(d.x) < -d.y * .6f) { change.consume(); onSwipeUp(); break }
            if (d.y > threshold || abs(d.x) > threshold) break
        }
    }
}

/** iOS-style app panel above the icon: shortcuts, the app's latest notifications and its music controls. */
@Composable
internal fun AppPanel(app: AppEntry, onDismiss: () -> Unit, onOpen: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = .74f, stiffness = Spring.StiffnessMediumLow)) }
    DisposableEffect(Unit) { LauncherSheetsOpen.intValue++; onDispose { LauncherSheetsOpen.intValue-- } }
    val actions by produceState(emptyList<QuickAction>(), app.id) { value = withContext(Dispatchers.IO) { loadQuickActions(context, app, 4) } }
    val notifications = IslandListenerService.notifications.collectAsState().value.filter { it.packageName == app.component.packageName }.take(3)
    val media = (IslandListenerService.activity.collectAsState().value as? IslandActivity.Media)?.takeIf { it.packageName == app.component.packageName }

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
            .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }.background(Color.Black.copy(alpha = .3f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)) {
            val screenW = with(density) { maxWidth.toPx() }
            val icon = IconBounds.of(app.id)
            val panelW = with(density) { minOf(320.dp, maxWidth - 24.dp).toPx() }
            val gap = with(density) { 12.dp.toPx() }
            var panelH by remember { mutableIntStateOf(0) }
            val iconCenterX = icon?.let { it.exactCenterX() - origin.x } ?: screenW / 2
            val left = (iconCenterX - panelW / 2).coerceIn(gap, screenW - panelW - gap)
            val iconTop = icon?.let { it.top - origin.y } ?: with(density) { maxHeight.toPx() } / 2
            // Above the icon (like Velox) unless there isn't room, then below it.
            val above = iconTop - gap - panelH > with(density) { 56.dp.toPx() }
            val top = if (above) iconTop - gap - panelH else (icon?.let { it.bottom - origin.y } ?: iconTop) + gap
            Column(Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }.width(with(density) { panelW.toDp() })
                .onSizeChanged { panelH = it.height }
                .graphicsLayer {
                    val s = .85f + .15f * appear.value; scaleX = s; scaleY = s
                    transformOrigin = TransformOrigin(((iconCenterX - left) / panelW).coerceIn(0f, 1f), if (above) 1f else 0f)
                }
                .clip(RoundedCornerShape(24.dp)).background(Color(0xFF232326).copy(alpha = .97f)).border(FolioGlass.edge, RoundedCornerShape(24.dp))
                .clickable(remember { MutableInteractionSource() }, null) {}.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app, null, Modifier.size(36.dp), shape = RoundedCornerShape(9.dp), badge = false)
                    Spacer(Modifier.width(10.dp))
                    Text(app.label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.open), color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(CircleShape).background(Color.White).clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 6.dp))
                }
                media?.let { m ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .08f)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        (m.art ?: m.icon)?.let { Image(it.asImageBitmap(), null, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            m.subtitle?.let { Text(it, color = Color.White.copy(alpha = .6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        val t = runCatching { m.controller.transportControls }.getOrNull()
                        Icon(Icons.Rounded.FastRewind, "Previous", tint = Color.White, modifier = Modifier.size(30.dp).clip(CircleShape).clickable { t?.skipToPrevious() })
                        Icon(if (m.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", tint = Color.White,
                            modifier = Modifier.size(34.dp).clip(CircleShape).clickable { if (m.playing) t?.pause() else t?.play() })
                        Icon(Icons.Rounded.FastForward, "Next", tint = Color.White, modifier = Modifier.size(30.dp).clip(CircleShape).clickable { t?.skipToNext() })
                    }
                }
                if (notifications.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .08f))) {
                    notifications.forEachIndexed { index, item ->
                        if (index > 0) MenuDivider()
                        Column(Modifier.fillMaxWidth().clickable { onDismiss(); IslandListenerService.openNotification(context, item) }
                            .padding(horizontal = 12.dp, vertical = 9.dp)) {
                            Text(item.title ?: item.appLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            item.text?.let { Text(it, color = Color.White.copy(alpha = .7f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
                if (actions.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .08f))) {
                    actions.forEachIndexed { index, action ->
                        if (index > 0) MenuDivider()
                        MenuRow(action.label, bitmap = action.icon) {
                            onDismiss()
                            runCatching { context.getSystemService(LauncherApps::class.java).startShortcut(action.info, null, null) }
                        }
                    }
                }
                if (media == null && notifications.isEmpty() && actions.isEmpty())
                    Text(stringResource(R.string.no_shortcuts_or_notifications_for_1, app.label), color = Color.White.copy(alpha = .6f), fontSize = 13.sp)
            }
        }
    }
}
