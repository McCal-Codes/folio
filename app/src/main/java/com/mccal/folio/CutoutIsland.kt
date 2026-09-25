package com.mccal.folio

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import kotlinx.coroutines.flow.first
import android.graphics.Rect
import android.view.ViewTreeObserver
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** What the compact pill currently shows: a live activity, or a brief system event on top of it. */
internal sealed interface IslandContent {
    data class Live(val activity: IslandActivity) : IslandContent
    data class Event(val event: IslandEvent) : IslandContent
}

/**
 * Dynamic Island centered on the real camera cutout of the current display. The pill is always
 * symmetric around the camera; tapping opens a separate card underneath so the pill never moves.
 */
@Composable
internal fun CutoutIsland(activity: IslandActivity?, eventsOff: Set<String> = emptySet(), onOpen: (IslandActivity) -> Unit) {
    val islandStrings = androidx.compose.ui.platform.LocalContext.current.strings()
    val view = LocalView.current
    val density = LocalDensity.current

    // Track the cutout and window width live: after a fold the insets arrive after the first layout.
    var cutout by remember { mutableStateOf<Rect?>(null) }
    var windowWidth by remember { mutableIntStateOf(view.width) }
    var windowHeight by remember { mutableIntStateOf(view.height) }
    DisposableEffect(view) {
        val update = ViewTreeObserver.OnGlobalLayoutListener {
            windowWidth = view.rootView.width
            windowHeight = view.rootView.height
            // Reported cutout first; otherwise a known under-display camera (e.g. Galaxy Z Fold8 inner screen).
            cutout = view.rootWindowInsets?.displayCutout?.boundingRects?.filter { !it.isEmpty }?.minByOrNull { it.top }?.let(::Rect)
                ?: CameraArea.hiddenCamera(view.display)
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(update)
        update.onGlobalLayout()
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(update) }
    }

    val eventPair by IslandEvents.latest.collectAsStateWithLifecycle()
    var eventVisible by remember { mutableStateOf<IslandEvent?>(null) }
    // While typing a quick reply the message stays put; nothing new replaces it.
    var replying by remember { mutableStateOf(false) }
    LaunchedEffect(eventPair) {
        if (replying) return@LaunchedEffect
        val (event, at) = eventPair ?: return@LaunchedEffect
        if (event.kind in eventsOff) return@LaunchedEffect
        val remaining = IslandEvents.showMs(event) - (System.currentTimeMillis() - at)
        if (remaining <= 0) return@LaunchedEffect
        eventVisible = event; delay(remaining)
        snapshotFlow { replying }.first { r -> !r }
        eventVisible = null
    }
    val message = eventVisible as? IslandEvent.Message
    // Only a reply in progress owns Back; a passing message card must not swallow Back for the rest of Home.
    androidx.activity.compose.BackHandler(message != null && replying) { replying = false; eventVisible = null }
    val notice = eventVisible as? IslandEvent.Notice
    val content: IslandContent? = eventVisible?.let { IslandContent.Event(it) } ?: activity?.let { IslandContent.Live(it) }
    var expanded by remember(activity?.packageName) { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(expanded) { expanded = false }

    val context = androidx.compose.ui.platform.LocalContext.current
    val wide = with(density) { windowWidth.toDp() }.value * androidx.compose.ui.platform.LocalConfiguration.current.classScale >= 600f
    val landscape = windowWidth > windowHeight
    // Camera on a side edge (a screen turned sideways): the island stands upright around it, like iPhone Duo's.
    val side = cameraSideEdge(cutout, windowWidth, windowHeight)
    val upright = side != null && IslandPosition.load(context, wide, landscape) == null
    // Folio's own notices come here instead of a toast while this island can show them.
    DisposableEffect(upright) {
        if (!upright) IslandEvents.noticeIslands++
        onDispose { if (!upright) IslandEvents.noticeIslands-- }
    }
    if (content == null || windowWidth <= 0) return
    if (upright) {
        VerticalIsland(content, cutout!!, side!!, windowWidth, windowHeight, onOpen = onOpen,
            onMessage = { m -> eventVisible = null; IslandListenerService.openKey(context, m.key, m.packageName) })
        return
    }
    // A spot the user dragged the island to on this screen and orientation (needed on the inner screen, whose
    // under-display camera isn't reported by Android). Null = wrap the reported camera cutout.
    var custom by remember(wide, landscape) { mutableStateOf(IslandPosition.load(context, wide, landscape)) }
    var dragOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    with(density) {
        val cameraGeometry = islandGeometry(cutout, windowWidth, density.density)
        val geometry = custom?.let { islandGeometryAt(it.xFraction * windowWidth, it.topDp, windowWidth, density.density) } ?: cameraGeometry
        val camW = geometry.camW.dp
        val pillH = geometry.pillH.dp
        val centerX = geometry.centerXPx
        val live = (content as? IslandContent.Live)?.activity
        // A new message opens straight into a small card (like an iPhone banner coming out of the island).
        val ringing = (live as? IslandActivity.Call)?.takeIf { it.incoming }
        val open = (expanded && live != null) || message != null || notice != null || ringing != null
        val cardW = (if (notice != null) 300.dp else 340.dp).coerceAtMost(windowWidth.toDp() - 16.dp)
        // One shape morphs between pill and card: width, corner radius and height all spring together,
        // anchored to the camera like the real Dynamic Island.
        val morph = spring<Dp>(dampingRatio = .74f, stiffness = Spring.StiffnessMediumLow)
        val width by animateDpAsState(if (open) cardW else geometry.widthFor(content).dp, morph, label = "island-width")
        val corner by animateDpAsState(if (open) 34.dp else pillH / 2, morph, label = "island-corner")
        // Always a clear margin from the screen edges, like the gap around iPhone's island.
        val edge = ISLAND_SIDE_MARGIN.dp.toPx()
        val left = (centerX - width.toPx() / 2f).coerceIn(edge, maxOf(edge, windowWidth - width.toPx() - edge))
        val top = geometry.top.coerceAtLeast(ISLAND_EDGE_GAP).dp

        Box(Modifier.offset { IntOffset((left + dragOffset.x).roundToInt(), (top.toPx() + dragOffset.y).roundToInt()) }.width(width)
            .graphicsLayer { val s = if (dragging) 1.06f else 1f; scaleX = s; scaleY = s }
            // Long-press and drag to move it; dropping near the camera snaps back to the camera.
            .pointerInput(wide, landscape, windowWidth) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragging = true; expanded = false; haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) },
                    onDragCancel = { dragging = false; dragOffset = androidx.compose.ui.geometry.Offset.Zero },
                    onDragEnd = {
                        dragging = false
                        val newCenterX = (geometry.centerXPx + dragOffset.x).coerceIn(0f, windowWidth.toFloat())
                        val newTopDp = (geometry.top + dragOffset.y / density.density).coerceAtLeast(4f)
                        val nearCamera = kotlin.math.abs(newCenterX - cameraGeometry.centerXPx) < 56.dp.toPx() &&
                            kotlin.math.abs(newTopDp - cameraGeometry.top) < 40f && cutout != null
                        custom = if (nearCamera) null else IslandPosition(newCenterX / windowWidth, newTopDp)
                        IslandPosition.save(context, wide, landscape, custom)
                        dragOffset = androidx.compose.ui.geometry.Offset.Zero
                    },
                ) { change, amount -> change.consume(); dragOffset += amount }
            }
            .animateContentSize(spring(dampingRatio = .78f, stiffness = Spring.StiffnessMediumLow))
            .clip(RoundedCornerShape(corner)).background(Color.Black)
            .clickable(remember { MutableInteractionSource() }, null) { if (message == null && live != null) expanded = !expanded }
            // Polite, so a notice or a new activity is read when it appears rather than only when found (A11Y-7).
            .semantics { contentDescription = describe(content, islandStrings); liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }
            .testTag("cutout-island")) {
            androidx.compose.animation.AnimatedContent(open, label = "island-content",
                transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(90)) }) { showCard ->
                if (showCard && notice != null) NoticeCardContent(notice) { eventVisible = null; IslandEvents.dismiss() }
                else if (showCard && message != null) MessageCardContent(message, replying, onReply = { replying = true },
                    onOpen = { replying = false; eventVisible = null; IslandListenerService.openKey(context, message.key, message.packageName) },
                    onDone = { replying = false; eventVisible = null },
                    onDismiss = { replying = false; eventVisible = null; IslandEvents.dismiss() })
                else if (showCard && live != null) ExpandedCardContent(live, onOpen = { expanded = false; onOpen(live) })
                else Box(Modifier.width(geometry.widthFor(content).dp).height(pillH)) { IslandPillContent(content, camW, pillH) }
            }
        }
    }
}

/** Where the user dragged the island on one screen: horizontal center as a fraction of width, top in dp. */
internal data class IslandPosition(val xFraction: Float, val topDp: Float) {
    companion object {
        // Saved per screen and orientation: a spot dragged to in landscape means nothing once the screen turns.
        // (The original keys were the natural orientations: unfolded landscape, cover portrait.)
        private fun key(wide: Boolean, landscape: Boolean) = when {
            wide -> if (landscape) "island_pos_inner" else "island_pos_inner_portrait"
            else -> if (landscape) "island_pos_cover_landscape" else "island_pos_cover"
        }
        fun load(context: android.content.Context, wide: Boolean, landscape: Boolean): IslandPosition? =
            context.getSharedPreferences("folio", 0).getString(key(wide, landscape), null)?.split(',')?.let { parts ->
                runCatching { IslandPosition(parts[0].toFloat(), parts[1].toFloat()) }.getOrNull()
            }
        fun save(context: android.content.Context, wide: Boolean, landscape: Boolean, position: IslandPosition?) {
            context.getSharedPreferences("folio", 0).edit().apply {
                if (position == null) remove(key(wide, landscape)) else putString(key(wide, landscape), "${position.xFraction},${position.topDp}")
            }.apply()
        }
        fun reset(context: android.content.Context) {
            context.getSharedPreferences("folio", 0).edit().apply {
                for (wide in listOf(true, false)) for (landscape in listOf(true, false)) remove(key(wide, landscape))
            }.apply()
        }
    }
}

/** Island placed at a free position (no camera inside). */
internal fun islandGeometryAt(centerXPx: Float, topDp: Float, windowWidthPx: Int, density: Float): IslandGeometry {
    val room = minOf(centerXPx, windowWidthPx - centerXPx) / density - ISLAND_SIDE_MARGIN
    val pillH = 34f
    return IslandGeometry(0f, 0f, centerXPx, topDp + pillH / 2, pillH, maxOf(room * 2, 80f), topDp)
}

/** Pure placement of the island around a camera cutout. Dp values except [centerXPx]. */
internal data class IslandGeometry(val camW: Float, val camH: Float, val centerXPx: Float, val centerY: Float, val pillH: Float, val maxW: Float,
    val top: Float = centerY - pillH / 2) {
    fun widthFor(content: IslandContent): Float = minOf(islandWantWidth(content, camW.dp).value, maxW).coerceAtLeast(camW + pillH)
}

internal fun islandGeometry(cutout: Rect?, windowWidthPx: Int, density: Float): IslandGeometry =
    if (cutout == null) islandGeometry(null as IntArray?, windowWidthPx, density)
    else islandGeometry(intArrayOf(cutout.left, cutout.top, cutout.right, cutout.bottom), windowWidthPx, density)

/** [cutoutLtrb] is left, top, right, bottom in px (or null when the display has no cutout). */
internal fun islandGeometry(cutoutLtrb: IntArray?, windowWidthPx: Int, density: Float): IslandGeometry {
    val camW = (cutoutLtrb?.let { it[2] - it[0] } ?: 0) / density
    val camH = (cutoutLtrb?.let { it[3] - it[1] } ?: 0) / density
    val centerXPx = cutoutLtrb?.let { (it[0] + it[2]) / 2f } ?: (windowWidthPx / 2f)
    val centerY = (cutoutLtrb?.let { (it[1] + it[3]) / 2f } ?: (18 * density)) / density
    // Keep a real gap from the screen edge (like iPhone), wrap the camera with a small margin, and
    // never be shorter than a comfortable pill.
    val camTop = (cutoutLtrb?.get(1) ?: (12 * density).toInt()) / density
    val camBottom = camTop + camH
    // The island always starts above the camera, even when the camera sits closer to the edge than the usual gap
    // (a punch-hole near the top, as on the Galaxy Z Fold7's inner screen); otherwise the lens peeks over it.
    val top = if (cutoutLtrb == null) ISLAND_EDGE_GAP
        else minOf(maxOf(ISLAND_EDGE_GAP, camTop - ISLAND_CAMERA_MARGIN), camTop - ISLAND_CAMERA_MIN_OVERLAP).coerceAtLeast(0f)
    val pillH = maxOf(camBottom + ISLAND_CAMERA_MARGIN - top, ISLAND_MIN_HEIGHT)
    // Width stays symmetric around the camera, capped by the room on the narrower side.
    val room = minOf(centerXPx, windowWidthPx - centerXPx) / density - ISLAND_SIDE_MARGIN
    return IslandGeometry(camW, camH, centerXPx, top + pillH / 2, pillH, room * 2, top)
}

/** The inside of the compact pill: glyph left of the camera, detail right of it. */
@Composable
internal fun IslandPillContent(content: IslandContent, camW: Dp, pillH: Dp) {
    // One inset on every side, like Apple's island: the leading glyph sits as far from the left end as from the
    // top and bottom, and the trailing glyph mirrors it on the right.
    val inset = (pillH * .17f).coerceIn(5.dp, 8.dp)
    Row(Modifier.fillMaxSize().padding(horizontal = inset), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { LeadingGlyph(content, pillH - inset * 2) }
        Spacer(Modifier.width(camW + 6.dp))
        Box(Modifier.weight(1f).padding(end = inset * .5f), contentAlignment = Alignment.CenterEnd) { TrailingGlyph(content, pillH - inset * 2 - 4.dp) }
    }
}

/** Desired pill width beside the camera for this content. */
internal fun islandWantWidth(content: IslandContent, camW: Dp): Dp = when (content) {
    is IslandContent.Event -> camW + 190.dp
    is IslandContent.Live -> when (content.activity) {
        is IslandActivity.Navigation, is IslandActivity.Timer, is IslandActivity.Call -> camW + 150.dp
        else -> camW + 96.dp
    }
}

/** What TalkBack reads for the island. */
internal fun describe(content: IslandContent, strings: Strings): String = when (content) {
    is IslandContent.Event -> when (val e = content.event) {
        is IslandEvent.Charging -> e.level?.let { strings.plural(R.plurals.island_charging_percent, it, it) } ?: strings.get(R.string.charging)
        is IslandEvent.Silent -> strings.get(if (e.on) R.string.silent_mode_on else R.string.silent_mode_off)
        is IslandEvent.Focus -> strings.get(if (e.on) R.string.do_not_disturb_on_2 else R.string.do_not_disturb_off_2)
        is IslandEvent.Bluetooth -> e.name?.let { strings.get(R.string.island_connected_to, it) } ?: strings.get(R.string.connected)
        is IslandEvent.Notice -> e.text
        is IslandEvent.Message -> when {
            e.alert && e.text != null -> strings.get(R.string.island_alert_text, e.appLabel, e.sender, e.text)
            e.alert -> strings.get(R.string.island_alert, e.appLabel, e.sender)
            e.text != null -> strings.get(R.string.island_message_text, e.sender, e.text)
            else -> strings.get(R.string.island_message, e.sender)
        }
    }
    is IslandContent.Live -> strings.get(R.string.island_live_details, content.activity.title)
}

@Composable
internal fun LeadingGlyph(content: IslandContent, size: Dp) {
    when (content) {
        is IslandContent.Event -> when (val e = content.event) {
            is IslandEvent.Charging -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bolt, null, tint = IslandGreen, modifier = Modifier.size(size * .8f))
                Text(stringResource(R.string.charging), color = Color.White, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold)
            }
            is IslandEvent.Silent -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (e.on) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive, null,
                    tint = if (e.on) Red else Color.White, modifier = Modifier.size(size * .75f))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.silent), color = Color.White, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold)
            }
            is IslandEvent.Focus -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DarkMode, null, tint = Purple, modifier = Modifier.size(size * .75f))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.focus), color = Color.White, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold)
            }
            is IslandEvent.Bluetooth -> Icon(if (e.speaker) Icons.Rounded.Speaker else Icons.Rounded.Headphones, null, tint = IslandBlue, modifier = Modifier.size(size * .8f))
            is IslandEvent.Message -> MessageAvatar(e, size)
            is IslandEvent.Notice -> e.appIcon?.let { Image(it.asImageBitmap(), null, Modifier.size(size).clip(RoundedCornerShape(size * .24f))) }
                ?: CircleGlyph(Icons.Rounded.Info, Color.White, size)
        }
        is IslandContent.Live -> when (val a = content.activity) {
            is IslandActivity.Call -> if (a.incoming) CallAvatar(a, size) else Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Call, null, tint = IslandGreen, modifier = Modifier.size(size * .7f))
                Spacer(Modifier.width(4.dp))
                Chronometer(remember(a.key) { a.since ?: System.currentTimeMillis() }, countDown = false, color = IslandGreen)
            }
            is IslandActivity.Timer -> CircleGlyph(Icons.Rounded.Timer, IslandOrange, size)
            is IslandActivity.Navigation -> CircleGlyph(Icons.Rounded.TurnRight, IslandBlue, size)
            else -> ((a as? IslandActivity.Media)?.art ?: a.icon)?.let { Image(it.asImageBitmap(), null, Modifier.size(size).clip(RoundedCornerShape(size * .28f)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
        }
    }
}

@Composable
internal fun TrailingGlyph(content: IslandContent, size: Dp) {
    when (content) {
        is IslandContent.Event -> when (val e = content.event) {
            is IslandEvent.Charging -> Text("${e.level ?: ""}%", color = IslandGreen, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold)
            is IslandEvent.Silent -> Text(if (e.on) "On" else "Off", color = if (e.on) Red else Color.White.copy(alpha = .7f), fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold)
            is IslandEvent.Focus -> Text(if (e.on) "On" else "Off", color = if (e.on) Purple else Color.White.copy(alpha = .7f), fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold)
            is IslandEvent.Bluetooth -> Text(e.name ?: stringResource(R.string.connected), color = Color.White, fontSize = FolioType.GROUP_LABEL.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // The app's icon beside the sender's photo; without a photo the icon is already on the left.
            is IslandEvent.Message -> e.appIcon?.takeIf { e.avatar != null }?.let { Image(it.asImageBitmap(), null, Modifier.size(size * .8f).clip(RoundedCornerShape(size * .22f))) }
            is IslandEvent.Notice -> Unit
        }
        is IslandContent.Live -> when (val a = content.activity) {
            is IslandActivity.Media -> Bars(a.playing, if (LocalTintOptions.current.media) rememberAccent(a.art)?.let { mixColor(it, Color.White, .25f) } ?: IslandGreen else IslandGreen)
            is IslandActivity.Progress -> Ring(a.fraction, size)
            is IslandActivity.Call -> Bars(playing = !a.incoming)
            is IslandActivity.Timer -> Chronometer(a.base, a.countDown, IslandOrange)
            is IslandActivity.Navigation -> Text(a.subtitle ?: a.title, color = Color.White, fontSize = FolioType.GROUP_LABEL.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Swipe up to put an island card away, like an iPhone banner: it follows the finger with some resistance and springs
 * back if let go early. The notification itself stays in Notification Center.
 */
private fun Modifier.swipeUpToHide(enabled: Boolean, onHide: () -> Unit): Modifier = composed {
    val pull = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    val hideAt = with(LocalDensity.current) { 24.dp.toPx() }
    val latestOnHide by rememberUpdatedState(onHide)
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        var travel = 0f
        detectVerticalDragGestures(
            onDragStart = { travel = 0f },
            onDragEnd = { if (travel < -hideAt) latestOnHide() else scope.launch { pull.animateTo(0f, spring(dampingRatio = .7f)) } },
            onDragCancel = { scope.launch { pull.animateTo(0f) } },
        ) { change, dy ->
            change.consume(); travel += dy
            scope.launch { pull.snapTo((travel * .5f).coerceIn(-hideAt * 3f, 0f)) }
        }
    }.graphicsLayer { translationY = pull.value; alpha = 1f - (-pull.value / (hideAt * 6f)) }
}

/** Folio's own brief feedback: the app's icon (or an info symbol) and one or two lines. Tap or swipe up to hide. */
@Composable
private fun NoticeCardContent(notice: IslandEvent.Notice, onHide: () -> Unit) {
    Row(Modifier.fillMaxWidth().swipeUpToHide(true, onHide).clickable(onClick = onHide)
        .padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.MEDIUM.dp), verticalAlignment = Alignment.CenterVertically) {
        notice.appIcon?.let { Image(it.asImageBitmap(), null, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))) }
            ?: Icon(Icons.Rounded.Info, null, tint = IslandBlue, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(notice.text, color = Color.White, fontSize = FolioType.SUBHEAD.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
    }
}

/** Sender photo (a notification's own picture for other apps), or the app icon when there isn't one. */
@Composable
private fun MessageAvatar(message: IslandEvent.Message, size: Dp) {
    val bitmap = message.avatar ?: message.appIcon
    // People are round, like iOS; other apps' pictures (album covers, photos, logos) keep a rounded square.
    val person = message.avatar != null && !message.alert
    if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.size(size).clip(if (person) CircleShape else RoundedCornerShape(size * .24f)),
        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    else Box(Modifier.size(size).clip(CircleShape).background(FolioColors.MenuSurface), contentAlignment = Alignment.Center) {
        Text(message.sender.take(1).uppercase(), color = Color.White, fontSize = (size.value * .42f).sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MessageCardContent(message: IslandEvent.Message, replying: Boolean, onReply: () -> Unit, onOpen: () -> Unit, onDone: () -> Unit,
    onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxWidth().swipeUpToHide(!replying, onDismiss)
        .padding(start = FolioSpace.LARGE.dp, end = FolioSpace.LARGE.dp, top = FolioSpace.COMFY.dp, bottom = FolioSpace.COMFY.dp),
        verticalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp)) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen)) {
            Box {
                MessageAvatar(message, 44.dp)
                if (message.avatar != null) message.appIcon?.let {
                    Image(it.asImageBitmap(), null, Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(20.dp).clip(RoundedCornerShape(5.dp)))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message.sender, color = Color.White, fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(message.appLabel, color = Color.White.copy(alpha = .5f), fontSize = FolioType.GROUP_LABEL.sp, maxLines = 1)
                }
                message.text?.let { Text(it, color = Color.White.copy(alpha = .85f), fontSize = 14.sp, maxLines = if (replying) 2 else 3,
                    overflow = TextOverflow.Ellipsis, lineHeight = 18.sp) }
            }
        }
        if (replying) QuickReplyField(message.sender.substringBefore(" · "), onSend = { IslandListenerService.reply(context, message.key, it) }, onDone = onDone)
        else if (message.canReply) Row(horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
            MessageActionPill(stringResource(R.string.reply), onReply)
        }
    }
}

@Composable
internal fun ExpandedCardContent(activity: IslandActivity, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = FolioSpace.LARGE.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen)) {
            if (activity is IslandActivity.Call) CallAvatar(activity, 44.dp)
            else ((activity as? IslandActivity.Media)?.art ?: activity.icon)?.let { Image(it.asImageBitmap(), null, Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(activity.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val subtitle = when (activity) {
                    is IslandActivity.Media -> activity.subtitle
                    is IslandActivity.Progress -> activity.subtitle
                    is IslandActivity.Navigation -> activity.subtitle
                    is IslandActivity.Call -> if (activity.incoming) stringResource(R.string.incoming_call) else null
                    else -> null
                }
                subtitle?.let { Text(it, color = Color.White.copy(alpha = .6f), fontSize = FolioType.FOOTNOTE.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            when (activity) {
                is IslandActivity.Call -> if (!activity.incoming) Chronometer(remember(activity.key) { activity.since ?: System.currentTimeMillis() }, false, IslandGreen, 20.sp)
                is IslandActivity.Timer -> Chronometer(activity.base, activity.countDown, IslandOrange, 20.sp)
                else -> Unit
            }
        }
        when (activity) {
            is IslandActivity.Media -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val t = activity.controller.transportControls
                Icon(Icons.Rounded.FastRewind, stringResource(R.string.previous), tint = Color.White, modifier = Modifier.minimumInteractiveComponentSize().size(34.dp).clip(CircleShape).clickable { t.skipToPrevious() })
                Icon(if (activity.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(R.string.play_or_pause), tint = Color.White,
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable { if (activity.playing) t.pause() else t.play() })
                Icon(Icons.Rounded.FastForward, stringResource(R.string.next), tint = Color.White, modifier = Modifier.minimumInteractiveComponentSize().size(34.dp).clip(CircleShape).clickable { t.skipToNext() })
            }
            is IslandActivity.Progress -> activity.fraction?.let { f ->
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = .2f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(IslandGreen))
                }
            }
            is IslandActivity.Call -> CallButtons(activity)
            else -> Unit
        }
    }
}

/** Caller photo, or the calling app's icon in a circle. */
@Composable
private fun CallAvatar(call: IslandActivity.Call, size: Dp) {
    val bitmap = call.avatar ?: call.icon
    if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.size(size).clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    else CircleGlyph(Icons.Rounded.Call, IslandGreen, size)
}

/** iPhone call controls: Decline/Accept while ringing; Mute, End and Speaker during the call. */
@Composable
private fun CallButtons(call: IslandActivity.Call) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun act(kind: CallControls.Kind) { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm); IslandListenerService.callAction(context, call.key, kind) }
    Row(Modifier.fillMaxWidth().padding(top = FolioSpace.TINY.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        if (call.incoming) {
            if (call.canDecline) CallButton(Icons.Rounded.CallEnd, stringResource(R.string.decline), Red) { act(CallControls.Kind.DECLINE) }
            if (call.canAnswer) CallButton(Icons.Rounded.Call, stringResource(R.string.accept), IslandGreen) { act(CallControls.Kind.ANSWER) }
        } else {
            if (call.canMute) CallButton(Icons.Rounded.MicOff, stringResource(R.string.mute), Color.White.copy(alpha = .22f)) { act(CallControls.Kind.MUTE) }
            if (call.canHangUp) CallButton(Icons.Rounded.CallEnd, "End", Red) { act(CallControls.Kind.HANG_UP) }
            if (call.canSpeaker) CallButton(Icons.AutoMirrored.Rounded.VolumeUp, stringResource(R.string.speaker), Color.White.copy(alpha = .22f)) { act(CallControls.Kind.SPEAKER) }
            if (!call.canHangUp && !call.canMute && !call.canSpeaker) Text(stringResource(R.string.tap_to_return_to_the_call), color = Color.White.copy(alpha = .6f), fontSize = FolioType.FOOTNOTE.sp)
        }
    }
}

@Composable
private fun CallButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(color).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Text(label, color = Color.White.copy(alpha = .7f), fontSize = 11.sp, modifier = Modifier.padding(top = FolioSpace.TINY.dp))
    }
}

@Composable
internal fun CircleGlyph(icon: ImageVector, color: Color, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = .22f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(size * .62f))
    }
}

@Composable
internal fun Chronometer(base: Long, countDown: Boolean, color: Color, fontSize: androidx.compose.ui.unit.TextUnit = 13.sp) {
    val now by rememberSecondTick()
    val seconds = ((if (countDown) base - now else now - base) / 1000).coerceAtLeast(0)
    val text = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%d:%02d".format(seconds / 60, seconds % 60)
    Text(text, color = color, fontSize = fontSize, fontWeight = FontWeight.SemiBold,
        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
}

@Composable
internal fun Bars(playing: Boolean, color: Color = IslandGreen) {
    // No infinite animation while paused: a paused session must not redraw forever.
    val phase = if (playing) {
        val transition = rememberInfiniteTransition(label = "island-bars")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "phase").value
    } else 0f
    Canvas(Modifier.size(width = 20.dp, height = 14.dp)) {
        val w = size.width / 7
        for (i in 0 until 4) {
            val wave = if (playing) (kotlin.math.sin(phase * 2 * Math.PI + i * 1.3).toFloat() + 1f) / 2f else .15f
            val h = size.height * (.3f + .7f * wave)
            drawLine(color, Offset(w * (1 + i * 1.7f), size.height / 2 + h / 2), Offset(w * (1 + i * 1.7f), size.height / 2 - h / 2),
                strokeWidth = w, cap = StrokeCap.Round)
        }
    }
}

@Composable
internal fun Ring(fraction: Float?, size: Dp) {
    Canvas(Modifier.size(size)) {
        val stroke = this.size.width * .14f
        val arc = Size(this.size.width - stroke, this.size.height - stroke)
        val o = Offset(stroke / 2, stroke / 2)
        drawArc(Color.White.copy(alpha = .2f), 0f, 360f, false, o, arc, style = Stroke(stroke))
        drawArc(IslandGreen, -90f, 360f * (fraction ?: .25f), false, o, arc, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

internal val IslandGreen = FolioColors.Green
internal val IslandOrange = FolioColors.Orange
private val Red = FolioColors.Red
private val Purple = FolioColors.Indigo
internal val IslandBlue = FolioColors.Blue

private const val ISLAND_EDGE_GAP = 8f
/** Smallest gap between the island and the left or right screen edge. */
private const val ISLAND_SIDE_MARGIN = 12f
private const val ISLAND_CAMERA_MARGIN = 5f
/** The least the island reaches past the camera's top edge. */
private const val ISLAND_CAMERA_MIN_OVERLAP = 2f
private const val ISLAND_MIN_HEIGHT = 34f
