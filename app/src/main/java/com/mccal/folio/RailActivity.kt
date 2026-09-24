package com.mccal.folio

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.basicMarquee
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.Layout

/**
 * iPhone Duo's side-rail Dynamic Island: a live activity (now playing, a call, a timer, navigation, progress)
 * grows the rail downward under the status, and leaves again when it ends. Tapping Now Playing expands it
 * vertically along the rail (artwork, title, position, controls stacked); other activities open their app.
 *
 * Drawn like Apple's island: pure black with no outline, concentric corners (inner radius = outer radius minus
 * the inset), and spring motion for arriving, leaving and expanding.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun RailLiveActivity(activity: IslandActivity?, width: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    // Keep showing the last activity while the rail shrinks away.
    var shown by remember { mutableStateOf(activity) }
    if (activity != null) shown = activity
    LaunchedEffect(activity == null, activity?.packageName) { if (activity == null || activity !is IslandActivity.Media) expanded = false }
    val reduceMotion = LocalReduceMotion.current
    val bouncy = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntSize>(dampingRatio = .72f, stiffness = 420f)
    AnimatedVisibility(activity != null,
        enter = if (reduceMotion) fadeIn() else expandVertically(bouncy, expandFrom = Alignment.Top) + fadeIn(),
        exit = if (reduceMotion) fadeOut() else shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()) {
        val current = shown ?: return@AnimatedVisibility
        val inset = 10.dp
        val outer = width / 2
        val glyph = (width - inset * 2).coerceIn(28.dp, 48.dp)
        val pressed = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val isPressed by pressed.collectIsPressedAsState()
        val scale by androidx.compose.animation.core.animateFloatAsState(if (isPressed) .94f else 1f,
            androidx.compose.animation.core.spring(dampingRatio = .6f, stiffness = 700f), label = "rail island press")
        val open = { IslandListenerService.open(context, current) }
        Column(Modifier.width(width).graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(8.dp, RoundedCornerShape(outer), ambientColor = Color.Black, spotColor = Color.Black)
            .clip(RoundedCornerShape(outer)).background(Color.Black)
            // Grows and shrinks along the rail with the same spring as the island.
            .animateContentSize(if (reduceMotion) androidx.compose.animation.core.snap() else bouncy)
            .clickable(pressed, null, onClickLabel = if (current is IslandActivity.Media) (if (expanded) "Collapse" else "Expand") else "Open ${current.title}") {
                if (current is IslandActivity.Media) expanded = !expanded else open()
            }
            .padding(inset).testTag("rail-live-activity")
            .semantics { contentDescription = current.title },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
            when (current) {
                is IslandActivity.Media -> {
                    val accent = rememberAccent(current.art)?.let { mixColor(it, Color.White, .25f) } ?: IslandGreen
                    // Artwork with corners concentric to the capsule; in the expanded rail it opens the app.
                    (current.art ?: current.icon)?.let {
                        androidx.compose.foundation.Image(it.asImageBitmap(), current.title,
                            Modifier.size(glyph).clip(RoundedCornerShape((outer - inset).coerceAtMost(glyph * .3f)))
                                .then(if (expanded) Modifier.clickable(onClickLabel = "Open ${current.title}", onClick = open) else Modifier),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    }
                    if (!expanded) Box(Modifier.padding(bottom = FolioSpace.HAIR.dp).graphicsLayer { scaleX = 1.25f; scaleY = 1.25f }) { Bars(current.playing, accent) }
                    else RailNowPlaying(current, accent, glyph)
                }
                // The island's row layout (icon + timer) is too wide for the rail: stack it.
                is IslandActivity.Call -> {
                    if (current.incoming) LeadingGlyph(IslandContent.Live(current), glyph)
                    else CircleGlyph(Icons.Rounded.Call, IslandGreen, glyph)
                    if (!current.incoming) Chronometer(remember(current.key) { current.since ?: System.currentTimeMillis() }, false, IslandGreen, 12.sp)
                }
                is IslandActivity.Navigation -> {
                    LeadingGlyph(IslandContent.Live(current), glyph)
                    Text(current.subtitle ?: current.title, color = Color.White, fontSize = 10.sp, maxLines = 2, lineHeight = 12.sp,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
                is IslandActivity.Timer -> {
                    LeadingGlyph(IslandContent.Live(current), glyph)
                    Chronometer(current.base, current.countDown, IslandOrange, 12.sp)
                }
                is IslandActivity.Progress -> {
                    LeadingGlyph(IslandContent.Live(current), glyph)
                    Ring(current.fraction, 20.dp)
                }
            }
        }
    }
}

/** The expanded rail's Now Playing: scrolling title and artist, playback position, and controls stacked down the rail. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun RailNowPlaying(media: IslandActivity.Media, accent: Color, width: androidx.compose.ui.unit.Dp) {
    val controller = runCatching { media.controller }.getOrNull()
    // Position and length straight from the media session, ticking while it plays.
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(media.playing) { while (media.playing) { now = android.os.SystemClock.elapsedRealtime(); kotlinx.coroutines.delay(500) } }
    val playback = controller?.playbackState
    val duration = controller?.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 }
    val position = playback?.let { p ->
        val elapsed = if (p.state == android.media.session.PlaybackState.STATE_PLAYING) ((now - p.lastPositionUpdateTime) * p.playbackSpeed).toLong() else 0L
        (p.position + elapsed).coerceIn(0L, duration ?: Long.MAX_VALUE)
    }
    Column(Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FolioSpace.TINY.dp)) {
        // Too narrow for a full title: it scrolls, like a marquee on the island.
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FolioSpace.HAIR.dp)) {
            Text(media.title, color = Color.White, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 900))
            media.subtitle?.let { Text(it, color = Color.White.copy(alpha = .6f), fontSize = 10.sp, maxLines = 1,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1400)) }
        }
        if (duration != null && position != null) Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = .22f))
            .semantics { contentDescription = "${formatClock(position)} of ${formatClock(duration)}" }) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f)).background(accent))
        }
        val controls = controller?.transportControls
        RailControl(Icons.Rounded.FastRewind, "Previous", 22.dp) { controls?.skipToPrevious() }
        RailControl(if (media.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (media.playing) "Pause" else "Play", 32.dp) {
            if (media.playing) controls?.pause() else controls?.play()
        }
        RailControl(Icons.Rounded.FastForward, "Next", 22.dp) { controls?.skipToNext() }
    }
}

@Composable
private fun RailControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(Modifier.size(width = 48.dp, height = 40.dp).clip(CircleShape).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(size))
    }
}

private fun formatClock(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
}

/** The edge the camera is nearest when that's a side edge (-1 left, +1 right); null when it's the top or bottom. */
internal fun cameraSideEdge(camera: android.graphics.Rect?, windowWidth: Int, windowHeight: Int): Int? =
    camera?.let { cameraSideEdge(it.left, it.top, it.right, it.bottom, windowWidth, windowHeight) }

internal fun cameraSideEdge(left: Int, top: Int, right: Int, bottom: Int, windowWidth: Int, windowHeight: Int): Int? {
    if (windowWidth <= 0 || windowHeight <= 0) return null
    val toLeft = left; val toRight = windowWidth - right
    val toTop = top; val toBottom = windowHeight - bottom
    val nearest = minOf(toLeft, toRight, toTop, toBottom)
    return when {
        toTop == nearest || toBottom == nearest -> null
        toLeft == nearest -> -1
        else -> 1
    }
}

/**
 * The Dynamic Island standing upright around a camera on a side edge: what leads sits above the camera, what
 * trails sits below, and the hole stays in the gap between. It keeps a margin from the edge and lives in the
 * strip Home already keeps clear for the camera, so it doesn't cover the rail. Now Playing expands along the edge
 * toward the side with more room; other activities open their app.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun VerticalIsland(content: IslandContent, camera: android.graphics.Rect, side: Int, windowWidth: Int, windowHeight: Int,
    onOpen: (IslandActivity) -> Unit, onMessage: (IslandEvent.Message) -> Unit) {
    val islandStrings = androidx.compose.ui.platform.LocalContext.current.strings()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val live = (content as? IslandContent.Live)?.activity
    val media = live as? IslandActivity.Media
    val call = live as? IslandActivity.Call
    val callControls = call != null && (call.canAnswer || call.canDecline || call.canHangUp || call.canMute || call.canSpeaker)
    // A ringing call opens straight into its answer and decline buttons, like the island's incoming-call card.
    var expanded by remember(media?.packageName, call?.key, call?.incoming) { mutableStateOf(call?.incoming == true && callControls) }
    androidx.activity.compose.BackHandler(expanded) { expanded = false }
    val reduceMotion = LocalReduceMotion.current
    val bouncy = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntSize>(dampingRatio = .72f, stiffness = 420f)
    with(density) {
        val hole = minOf(camera.width(), camera.height()).toDp()
        val edge = SIDE_ISLAND_EDGE
        val thickness = sideIslandThickness(hole)
        // Tall windows (inner portrait) expand the island along the edge. Short ones (the cover turned sideways) don't
        // have the height, so the island stays slim and its controls open in a card beside the rail instead.
        val room = maxOf(camera.centerY(), windowHeight - camera.centerY()).toDp()
        val alongEdge = room >= 330.dp
        val grown = expanded && alongEdge
        val card = expanded && !alongEdge
        val width by androidx.compose.animation.core.animateDpAsState(if (grown) 76.dp else thickness,
            androidx.compose.animation.core.spring(dampingRatio = .74f, stiffness = 420f), label = "vertical island width")
        val glyph = thickness - 14.dp
        val gapHalf = hole / 2 + 4.dp
        val growUp = camera.centerY() > windowHeight / 2
        // Collapsed: the same inset as around the camera. Expanded: the growing end clears its rounded corner.
        val padFar = if (grown) 22.dp else 7.dp
        val padTop = if (growUp) padFar else 7.dp
        val padBottom = if (growUp) 7.dp else padFar
        val accent = media?.let { rememberAccent(it.art)?.let { a -> mixColor(a, Color.White, .25f) } } ?: IslandGreen
        val pressed = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val isPressed by pressed.collectIsPressedAsState()
        val scale by androidx.compose.animation.core.animateFloatAsState(if (isPressed) .95f else 1f,
            androidx.compose.animation.core.spring(dampingRatio = .6f, stiffness = 700f), label = "vertical island press")
        val expandedStack: @Composable () -> Unit = {
            if (grown && media != null) RailNowPlaying(media, accent, 76.dp - 20.dp)
            if (grown && call != null && callControls) RailCallButtons(call)
        }
        val leading: @Composable () -> Unit = {
            Column(Modifier.animateContentSize(if (reduceMotion) androidx.compose.animation.core.snap() else bouncy),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                if (growUp) expandedStack()
                when {
                    media != null -> (media.art ?: media.icon)?.let {
                        val art by androidx.compose.animation.core.animateDpAsState(if (grown) 76.dp - 20.dp else glyph,
                            androidx.compose.animation.core.spring(dampingRatio = .74f, stiffness = 420f), label = "vertical island art")
                        androidx.compose.foundation.Image(it.asImageBitmap(), media.title, Modifier.size(art).clip(RoundedCornerShape(art * .24f)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    }
                    live is IslandActivity.Call && !live.incoming -> CircleGlyph(Icons.Rounded.Call, IslandGreen, glyph)
                    // Events carry a word beside their icon on the wide pill; standing upright there's only room for the icon.
                    content is IslandContent.Event && content.event !is IslandEvent.Message -> when (val e = content.event) {
                        is IslandEvent.Charging -> CircleGlyph(Icons.Rounded.Bolt, IslandGreen, glyph)
                        is IslandEvent.Silent -> CircleGlyph(if (e.on) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive,
                            if (e.on) FolioColors.Red else Color.White, glyph)
                        is IslandEvent.Focus -> CircleGlyph(Icons.Rounded.DarkMode, FolioColors.Indigo, glyph)
                        is IslandEvent.Notice -> CircleGlyph(Icons.Rounded.Info, Color.White, glyph)
                        else -> CircleGlyph(Icons.Rounded.Headphones, IslandBlue, glyph)
                    }
                    else -> LeadingGlyph(content, glyph)
                }
            }
        }
        val trailing: @Composable () -> Unit = {
            Column(Modifier.animateContentSize(if (reduceMotion) androidx.compose.animation.core.snap() else bouncy),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                when (live) {
                    is IslandActivity.Media -> Bars(live.playing, accent)
                    is IslandActivity.Call -> if (!live.incoming) Chronometer(remember(live.key) { live.since ?: System.currentTimeMillis() }, false, IslandGreen, 10.sp)
                    is IslandActivity.Timer -> Chronometer(live.base, live.countDown, IslandOrange, 10.sp)
                    is IslandActivity.Progress -> Ring(live.fraction, 18.dp)
                    is IslandActivity.Navigation, null -> when (val e = (content as? IslandContent.Event)?.event) {
                        is IslandEvent.Charging -> Text("${e.level ?: ""}%", color = IslandGreen, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        is IslandEvent.Silent -> Text(if (e.on) "On" else "Off", color = Color.White, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        is IslandEvent.Focus -> Text(if (e.on) "On" else "Off", color = Color.White, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        is IslandEvent.Message -> e.appIcon?.takeIf { e.avatar != null }?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null, Modifier.size(18.dp).clip(RoundedCornerShape(5.dp))) }
                        else -> Unit
                    }
                }
                if (!growUp) expandedStack()
            }
        }
        val onTap = {
            when {
                media != null || callControls -> expanded = !expanded
                live != null -> onOpen(live)
                else -> ((content as? IslandContent.Event)?.event as? IslandEvent.Message)?.let(onMessage)
            }
        }
        val cardGrow = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(card) {
            if (card) { if (reduceMotion) cardGrow.snapTo(1f) else cardGrow.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = .74f, stiffness = 520f)) }
            else cardGrow.snapTo(0f)
        }
        androidx.compose.ui.layout.Layout(content = {
            // Tapping anywhere else closes the card, as with the island's expanded card.
            Box(if (card) Modifier.clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { expanded = false } else Modifier)
            Box(Modifier.graphicsLayer {
                val g = cardGrow.value
                alpha = g.coerceIn(0f, 1f); scaleX = .6f + .4f * g; scaleY = .6f + .4f * g
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (side > 0) 1f else 0f, .5f)
            }.shadow(16.dp, RoundedCornerShape(34.dp)).clip(RoundedCornerShape(34.dp)).background(Color.Black).testTag("vertical-island-card")) {
                if (card && live != null) ExpandedCardContent(live) { expanded = false; onOpen(live) }
            }
            Box(Modifier.shadow(8.dp, RoundedCornerShape(width / 2)).clip(RoundedCornerShape(width / 2)).background(Color.Black)
                .clickable(pressed, null, onClickLabel = if (media != null || callControls) (if (expanded) "Collapse" else "Expand") else describe(content, islandStrings)) { onTap() }
                .semantics { contentDescription = describe(content, islandStrings) }.testTag("vertical-island"))
            leading()
            trailing()
        }, modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale }) { measurables, constraints ->
            val w = width.roundToPx()
            val loose = androidx.compose.ui.unit.Constraints(maxWidth = w, maxHeight = constraints.maxHeight)
            val scrim = measurables[0].measure(if (card) androidx.compose.ui.unit.Constraints.fixed(constraints.maxWidth, constraints.maxHeight)
                else androidx.compose.ui.unit.Constraints.fixed(0, 0))
            val cardWidth = minOf(340.dp.roundToPx(), windowWidth / 2)
            val cardPlaceable = measurables[1].measure(if (card) androidx.compose.ui.unit.Constraints(minWidth = cardWidth, maxWidth = cardWidth,
                maxHeight = windowHeight - 24.dp.roundToPx()) else androidx.compose.ui.unit.Constraints.fixed(0, 0))
            val bgMeasurable = measurables[2]
            val above = measurables[3].measure(loose)
            val below = measurables[4].measure(loose)
            val cy = camera.centerY()
            val top = cy - gapHalf.roundToPx() - above.height - padTop.roundToPx()
            val bottom = cy + gapHalf.roundToPx() + below.height + padBottom.roundToPx()
            val bg = bgMeasurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, (bottom - top).coerceAtLeast(w)))
            // Hug the camera's edge with a margin, never past the screen edge.
            val left = if (side > 0) windowWidth - edge.roundToPx() - w else edge.roundToPx()
            layout(constraints.maxWidth, constraints.maxHeight) {
                scrim.place(0, 0)
                if (card) {
                    // Beside the island, past the side rail, centered on the camera and kept on screen.
                    val clear = (edge + thickness + RAIL_CLEARANCE).roundToPx()
                    val cardX = if (side > 0) windowWidth - clear - cardWidth else clear
                    val cardY = (cy - cardPlaceable.height / 2).coerceIn(12.dp.roundToPx(), maxOf(12.dp.roundToPx(), windowHeight - cardPlaceable.height - 12.dp.roundToPx()))
                    cardPlaceable.place(cardX, cardY)
                }
                bg.place(left, top)
                above.place(left + (w - above.width) / 2, cy - gapHalf.roundToPx() - above.height)
                below.place(left + (w - below.width) / 2, cy + gapHalf.roundToPx())
            }
        }
    }
}

/** Call controls stacked down the rail: decline and answer while ringing; mute, end and speaker during the call. */
@Composable
private fun RailCallButtons(call: IslandActivity.Call) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun act(kind: CallControls.Kind) { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm); IslandListenerService.callAction(context, call.key, kind) }
    val red = FolioColors.Red
    val grey = Color.White.copy(alpha = .22f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp)) {
        if (call.incoming) {
            if (call.canAnswer) RailCallButton(Icons.Rounded.Call, "Accept", IslandGreen) { act(CallControls.Kind.ANSWER) }
            if (call.canDecline) RailCallButton(Icons.Rounded.CallEnd, "Decline", red) { act(CallControls.Kind.DECLINE) }
        } else {
            if (call.canMute) RailCallButton(Icons.Rounded.MicOff, "Mute", grey) { act(CallControls.Kind.MUTE) }
            if (call.canSpeaker) RailCallButton(Icons.AutoMirrored.Rounded.VolumeUp, "Speaker", grey) { act(CallControls.Kind.SPEAKER) }
            if (call.canHangUp) RailCallButton(Icons.Rounded.CallEnd, "End", red) { act(CallControls.Kind.HANG_UP) }
        }
    }
}

@Composable
private fun RailCallButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(color).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

private val SIDE_ISLAND_EDGE = 6.dp
/** Room left for the side rail (widest dock plus its gaps) between the island and a card opened beside it. */
private val RAIL_CLEARANCE = 4.dp + 12.dp + 84.dp + 14.dp
private fun sideIslandThickness(hole: androidx.compose.ui.unit.Dp) = maxOf(hole + 14.dp, 40.dp)

/**
 * Room Home keeps on the camera's side edge for the upright island, so the rail and grid never sit under it.
 * Always reserved while the island is on (not only while something plays), so Home doesn't shift when music starts.
 */
@Composable
internal fun rememberSideIslandInsets(enabled: Boolean): androidx.compose.foundation.layout.WindowInsets {
    val view = androidx.compose.ui.platform.LocalView.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    var layoutTick by remember { mutableIntStateOf(0) }
    DisposableEffect(view) {
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener { layoutTick++ }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    val camera = remember(layoutTick, config.orientation, config.screenWidthDp) {
        view.rootWindowInsets?.displayCutout?.boundingRects?.filter { !it.isEmpty }?.minByOrNull { it.top }
            ?: CameraArea.hiddenCamera(view.display)
    }
    val w = view.rootView.width; val h = view.rootView.height
    val side = if (enabled) cameraSideEdge(camera, w, h) else null
    return remember(side, camera, w, h) {
        if (side == null || camera == null) androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
        else with(density) {
            val reach = (SIDE_ISLAND_EDGE + sideIslandThickness(minOf(camera.width(), camera.height()).toDp()) + 4.dp).roundToPx()
            if (side > 0) androidx.compose.foundation.layout.WindowInsets(right = reach) else androidx.compose.foundation.layout.WindowInsets(left = reach)
        }
    }
}
