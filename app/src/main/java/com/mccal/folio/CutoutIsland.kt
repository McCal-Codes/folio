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
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
            cutout = islandCamera(view.rootWindowInsets?.displayCutout, view.display, density.density)
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
        // Every way out of here that is not "show this one" clears the island, or what was showing stays up for
        // good. Dismissing from the Everywhere overlay sets the shared event to null and nothing else, a filtered
        // kind or an event that expired in the background used to leave the previous card on screen until the next
        // event happened to arrive.
        val (event, at) = eventPair ?: run { eventVisible = null; return@LaunchedEffect }
        if (event.kind in eventsOff) { eventVisible = null; return@LaunchedEffect }
        val remaining = IslandEvents.showMs(view.context, event) - (System.currentTimeMillis() - at)
        if (remaining <= 0) { eventVisible = null; return@LaunchedEffect }
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
    // A ringing call card swiped away shrinks to the pill; the call keeps ringing and a tap opens the card again.
    var ringPutAway by remember { mutableStateOf<String?>(null) }
    // A later call can reuse the same notification key, so the marker goes once that call is no longer ringing.
    LaunchedEffect(activity) { if ((activity as? IslandActivity.Call)?.incoming != true) ringPutAway = null }
    androidx.activity.compose.BackHandler(expanded) { expanded = false }
    // A brief event or a ringing call takes the island over; the card must not come back by itself when it ends (IM-9).
    val takeover = eventVisible != null
    LaunchedEffect(takeover) { expanded = expandedAfterTakeover(expanded, takeover) }
    val callRinging = (activity as? IslandActivity.Call)?.incoming == true
    LaunchedEffect(callRinging) { expanded = expandedAfterTakeover(expanded, callRinging) }
    val reduceMotion = LocalReduceMotion.current

    val context = androidx.compose.ui.platform.LocalContext.current
    val wide = islandWideWindow(windowWidth, density.density, androidx.compose.ui.platform.LocalConfiguration.current.classScale)
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
    val layoutVersion by IslandLayoutChanges.version.collectAsStateWithLifecycle()
    var custom by remember(wide, landscape, layoutVersion) { mutableStateOf(IslandPosition.load(context, wide, landscape)) }
    val extraWidth = remember(wide, landscape, layoutVersion) { IslandSize.load(context, wide, landscape) }
    val nudge = remember(wide, landscape, layoutVersion) { IslandNudge.load(context, wide, landscape) }
    var dragOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    with(density) {
        val camera = cutout?.ltrb()
        val cameraGeometry = islandCameraGeometry(camera, windowWidth, windowHeight, density.density)
        // Where the camera really is: the snap target for a drag, even when the pill is drawn at top center instead.
        val realCamera = islandGeometry(camera, windowWidth, density.density)
        val geometry = islandPlacement(camera, custom, windowWidth, windowHeight, density.density, extraWidth, nudge)
        val pillH = geometry.pillH.dp
        val centerX = geometry.centerXPx
        val live = (content as? IslandContent.Live)?.activity
        // A new message opens straight into a small card (like an iPhone banner coming out of the island).
        val ringing = (live as? IslandActivity.Call)?.takeIf { it.incoming && it.key != ringPutAway }
        val open = (expanded && live != null) || message != null || notice != null || ringing != null
        // A notice fits its text and keeps clear of the screen edges and a bent hinge; other cards keep their width.
        val hinge = LocalHinge.current?.takeIf { it.active }
        val placement = notice?.let { n ->
            noticePlacement(windowWidth.toDp().value, windowHeight.toDp().value, centerX.toDp().value, geometry.top,
                noticeWantWidth(n.text.length, n.action != null),
                hinge?.spans.orEmpty().map { it.first.toDp().value..it.last.toDp().value }, hinge?.vertical ?: true)
        }
        val cardW = placement?.width?.dp ?: 340.dp.coerceAtMost(windowWidth.toDp() - 16.dp)
        // One shape morphs between pill and card: width, corner radius and height all spring together,
        // anchored to the camera like the real Dynamic Island.
        val morph = if (reduceMotion) snap() else FolioMotion.spring<Dp>(.74f to Spring.StiffnessMediumLow)
        val width by animateDpAsState(if (open) cardW else geometry.widthFor(content).dp, morph, label = "island-width")
        val corner by animateDpAsState(if (open) 34.dp else pillH / 2, morph, label = "island-corner")
        // Always a clear margin from the screen edges, like the gap around iPhone's island.
        val left = if (placement != null && open) noticeLeft(placement, centerX.toDp().value, width.value).dp.toPx()
            else islandLeftPx(centerX, width.toPx(), windowWidth, density.density)
        // No floor here: the edge gap is the shared placement's call, so Home and the island over apps match (#394).
        val top = geometry.top.dp

        val toggles = message == null && live != null
        // Said from what is really showing: a ringing call or a notice opens the card by itself and has nothing to toggle.
        val a11y = islandToggleA11y(toggles, open, forced = ringing != null || notice != null)
        val toggleLabel = a11y?.takeIf { it.hasAction }?.let { stringResource(if (it.expanded) R.string.island_collapse else R.string.island_expand) }
        val stateLabel = a11y?.let { stringResource(if (it.expanded) R.string.island_state_expanded else R.string.island_state_collapsed) }
        val toggle = { if (a11y?.hasAction == true) expanded = !expanded }
        // A tap anywhere else on Home puts the card away. The tap is consumed here, like a sheet's scrim, so it never
        // also opens whatever was under it (IM-9/IM-10).
        if (islandCardTapOutsideCloses(expanded, live != null, message != null, notice != null, ringing != null))
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { expanded = false } }.clearAndSetSemantics { }.testTag("island-scrim"))
        Box(Modifier.offset { IntOffset((left + dragOffset.x).roundToInt(), (top.toPx() + dragOffset.y).roundToInt()) }.width(width)
            .then(if (placement != null && open) Modifier.heightIn(max = placement.maxHeight.dp) else Modifier)
            .graphicsLayer { val s = if (dragging) 1.06f else 1f; scaleX = s; scaleY = s }
            // Long-press and drag to move it; dropping near the camera snaps back to the camera.
            .pointerInput(wide, landscape, windowWidth) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragging = true; expanded = false; haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) },
                    onDragCancel = { dragging = false; dragOffset = androidx.compose.ui.geometry.Offset.Zero },
                    onDragEnd = {
                        dragging = false
                        // The drawn spot includes the saved nudge, which is stored on its own: take it out so the drop does not count it twice.
                        val newCenterX = (geometry.centerXPx - nudge.dxDp * density.density + dragOffset.x).coerceIn(0f, windowWidth.toFloat())
                        val newTopDp = (geometry.top - nudge.dyDp + dragOffset.y / density.density).coerceAtLeast(4f)
                        val nearCamera = cutout != null && islandSnapsToCamera(newCenterX, newTopDp, realCamera, density.density)
                        custom = if (nearCamera) null else IslandPosition(newCenterX / windowWidth, newTopDp)
                            .clamped(windowWidth, windowHeight, density.density, cameraGeometry.pillH)
                        IslandPosition.save(context, wide, landscape, custom)
                        dragOffset = androidx.compose.ui.geometry.Offset.Zero
                    },
                ) { change, amount -> change.consume(); dragOffset += amount }
            }
            .then(if (reduceMotion) Modifier else Modifier.animateContentSize(FolioMotion.spring(FolioMotion.Appear)))
            .clip(RoundedCornerShape(corner)).background(Color.Black)
            .clickable(remember { MutableInteractionSource() }, null, onClickLabel = toggleLabel) { toggle() }
            // Polite, so a notice or a new activity is read when it appears rather than only when found (A11Y-7).
            .semantics {
                contentDescription = describe(content, islandStrings); liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                stateLabel?.let { stateDescription = it }
            }
            .testTag("cutout-island")) {
            androidx.compose.animation.AnimatedContent(open, label = "island-content",
                transitionSpec = {
                    if (reduceMotion) fadeIn(tween(FolioMotion.FADE_MS)) togetherWith fadeOut(tween(FolioMotion.FADE_MS))
                    else fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(90))
                }) { showCard ->
                if (showCard && notice != null) NoticeCardContent(notice, topInset = pillH) { eventVisible = null; IslandEvents.dismiss() }
                else if (showCard && message != null) MessageCardContent(message, replying, onReply = { replying = true },
                    onOpen = { replying = false; eventVisible = null; IslandListenerService.openKey(context, message.key, message.packageName) },
                    onDone = { replying = false; eventVisible = null },
                    onDismiss = { replying = false; eventVisible = null; IslandEvents.dismiss() })
                else if (showCard && live != null) {
                    val ringingNow = live is IslandActivity.Call && live.incoming
                    Box(Modifier.swipeUpToHide(ringingNow) { expanded = false; ringPutAway = (live as IslandActivity.Call).key }) {
                        ExpandedCardContent(live, onOpen = { expanded = false; onOpen(live) })
                    }
                }
                else Box(Modifier.width(geometry.widthFor(content).dp).height(pillH)) { IslandPillContent(content, geometry.wrapW.dp, pillH) }
            }
        }
        // The pill is only 34 dp tall: its touch area reaches 14 dp lower without changing what is drawn (I-10).
        if (toggles && !open) Box(Modifier.offset { IntOffset((left + dragOffset.x).roundToInt(), (top.toPx() + pillH.toPx() + dragOffset.y).roundToInt()) }
            .size(width, ISLAND_TOUCH_EXTRA.dp).clickable(remember { MutableInteractionSource() }, null) { toggle() }.clearAndSetSemantics { })
    }
}

/**
 * Whether a card opened with a tap stays open when something takes the island over. A brief event or a ringing call
 * shows its own card; when it ends the island goes back to the pill instead of reopening the old card (IM-9).
 */
internal fun expandedAfterTakeover(expanded: Boolean, takeover: Boolean): Boolean = expanded && !takeover

/** Whether a tap outside the card puts it away: only a card the user opened, never a message, notice or ringing call (IM-10). */
internal fun islandCardTapOutsideCloses(expanded: Boolean, hasLive: Boolean, message: Boolean, notice: Boolean, ringing: Boolean): Boolean =
    expanded && hasLive && !message && !notice && !ringing

/** What TalkBack says about the island's tap target: the state, and whether activating it does anything. */
internal data class IslandToggleA11y(val expanded: Boolean, val hasAction: Boolean)

/**
 * [open] is whether a card is showing. A [forced] card (a ringing call, a notice) opened by itself, so it reads as
 * expanded with no toggle action. Null when the island has nothing to toggle.
 */
internal fun islandToggleA11y(toggles: Boolean, open: Boolean, forced: Boolean): IslandToggleA11y? = when {
    !toggles -> null
    forced -> IslandToggleA11y(expanded = true, hasAction = false)
    else -> IslandToggleA11y(expanded = open, hasAction = true)
}

/** How far below the 34 dp pill its touch area reaches. */
internal const val ISLAND_TOUCH_EXTRA = 14

/** Where the user dragged the island on one screen: horizontal center as a fraction of width, top in dp. */
internal data class IslandPosition(val xFraction: Float, val topDp: Float) {
    /**
     * Kept inside the window, with the whole pill visible. A drag used to save whatever it ended at, and the top
     * had no upper bound, so a pill dragged to the bottom edge was saved there and came back under the search pill
     * or off the screen, with nothing left to grab. The saved string is also clamped on the way in, so a value from
     * an older build or a different window size cannot place the pill where it cannot be reached.
     */
    fun clamped(windowWidthPx: Int, windowHeightPx: Int, density: Float, pillHDp: Float): IslandPosition {
        val heightDp = windowHeightPx / density
        val maxTop = (heightDp - pillHDp - ISLAND_EDGE_GAP).coerceAtLeast(ISLAND_EDGE_GAP)
        return IslandPosition(xFraction.coerceIn(0f, 1f), topDp.coerceIn(ISLAND_EDGE_GAP, maxTop))
    }

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
            IslandLayoutChanges.changed()
        }
        fun reset(context: android.content.Context) {
            context.getSharedPreferences("folio", 0).edit().apply {
                for (wide in listOf(true, false)) for (landscape in listOf(true, false)) remove(key(wide, landscape))
            }.apply()
            IslandLayoutChanges.changed()
        }
    }
}

/** Tells a running island that its saved spot or size changed, so Adjust island shows on Home behind it at once. */
internal object IslandLayoutChanges {
    val version = kotlinx.coroutines.flow.MutableStateFlow(0)
    fun changed() { version.value += 1 }
}

/**
 * How much wider than the measured camera the person wants the island to wrap, in dp, per screen and orientation
 * like [IslandPosition]. A phone set to a smaller minimum width, or one that reports its camera smaller than the real
 * hole, can leave the pill narrower than the lens area; this is the way to say so. Zero is the automatic fit.
 */
internal object IslandSize {
    /** One nudge, in dp. */
    const val STEP_DP = 2f
    private fun key(wide: Boolean, landscape: Boolean) = when {
        wide -> if (landscape) "island_extra_inner" else "island_extra_inner_portrait"
        else -> if (landscape) "island_extra_cover_landscape" else "island_extra_cover"
    }
    /** Never negative and never more than the room beside the camera allows ([IslandGeometry.maxExtra]). */
    fun clamp(extraDp: Float, geometry: IslandGeometry): Float = if (extraDp.isNaN()) 0f else extraDp.coerceIn(0f, geometry.maxExtra)
    fun load(context: android.content.Context, wide: Boolean, landscape: Boolean): Float =
        context.getSharedPreferences("folio", 0).getFloat(key(wide, landscape), 0f).takeIf { !it.isNaN() && it > 0f } ?: 0f
    fun save(context: android.content.Context, wide: Boolean, landscape: Boolean, extraDp: Float) {
        context.getSharedPreferences("folio", 0).edit().apply {
            if (extraDp <= 0f || extraDp.isNaN()) remove(key(wide, landscape)) else putFloat(key(wide, landscape), extraDp)
        }.apply()
        IslandLayoutChanges.changed()
    }
    fun reset(context: android.content.Context) {
        context.getSharedPreferences("folio", 0).edit().apply {
            for (wide in listOf(true, false)) for (landscape in listOf(true, false)) remove(key(wide, landscape))
        }.apply()
        IslandLayoutChanges.changed()
    }
    /**
     * What "Fit to camera" asks for: the width Android reports for the camera, when it is wider than the lens outline
     * the island wraps by default ([cameraRect]). Null when the report is not wider, so there is nothing to fit.
     */
    fun fitExtra(reportedWidthDp: Float?, geometry: IslandGeometry): Float? =
        reportedWidthDp?.let { (it - geometry.camW).coerceIn(0f, geometry.maxExtra) }?.takeIf { it >= 1f }
}

/** A fine shift of the island from where it sits, in dp: Adjust island's arrows. Zero is where the camera (or a dragged spot) puts it. */
internal data class IslandNudge(val dxDp: Float, val dyDp: Float) {
    fun plus(dx: Float, dy: Float) = IslandNudge((dxDp + dx).coerceIn(-NUDGE_LIMIT_DP, NUDGE_LIMIT_DP), (dyDp + dy).coerceIn(-NUDGE_LIMIT_DP, NUDGE_LIMIT_DP))
    companion object {
        val None = IslandNudge(0f, 0f)
        /** Far enough to line the pill up with a mis-reported camera, never far enough to lose it. */
        const val NUDGE_LIMIT_DP = 48f
        private fun key(wide: Boolean, landscape: Boolean) = when {
            wide -> if (landscape) "island_nudge_inner" else "island_nudge_inner_portrait"
            else -> if (landscape) "island_nudge_cover_landscape" else "island_nudge_cover"
        }
        fun load(context: android.content.Context, wide: Boolean, landscape: Boolean): IslandNudge =
            context.getSharedPreferences("folio", 0).getString(key(wide, landscape), null)?.split(',')?.let { parts ->
                runCatching { IslandNudge(parts[0].toFloat(), parts[1].toFloat()).takeIf { !it.dxDp.isNaN() && !it.dyDp.isNaN() } }.getOrNull()
            }?.let { IslandNudge(it.dxDp.coerceIn(-NUDGE_LIMIT_DP, NUDGE_LIMIT_DP), it.dyDp.coerceIn(-NUDGE_LIMIT_DP, NUDGE_LIMIT_DP)) } ?: None
        fun save(context: android.content.Context, wide: Boolean, landscape: Boolean, nudge: IslandNudge) {
            context.getSharedPreferences("folio", 0).edit().apply {
                if (nudge == None) remove(key(wide, landscape)) else putString(key(wide, landscape), "${nudge.dxDp},${nudge.dyDp}")
            }.apply()
            IslandLayoutChanges.changed()
        }
        fun reset(context: android.content.Context) {
            context.getSharedPreferences("folio", 0).edit().apply {
                for (wide in listOf(true, false)) for (landscape in listOf(true, false)) remove(key(wide, landscape))
            }.apply()
            IslandLayoutChanges.changed()
        }
    }
}

/** The camera width Android reports on the top edge, in dp, before [cameraRect] narrows it to the lens: the base of Fit to camera. */
internal fun islandReportedCameraWidthDp(cutout: android.view.DisplayCutout?, density: Float): Float? =
    cutout?.boundingRects?.filter { !it.isEmpty }?.minByOrNull { it.top }?.let { it.width() / density }

/** Island placed at a free position (no camera inside). */
internal fun islandGeometryAt(centerXPx: Float, topDp: Float, windowWidthPx: Int, density: Float): IslandGeometry {
    val room = minOf(centerXPx, windowWidthPx - centerXPx) / density - ISLAND_SIDE_MARGIN
    val pillH = 34f
    return IslandGeometry(0f, 0f, centerXPx, topDp + pillH / 2, pillH, maxOf(room * 2, 80f), topDp)
}

/**
 * The camera to wrap (#61): the cutout's own outline when it lies inside the reported rect and is shorter, else the
 * rect. On Android's punch-hole emulation the outline is the hole itself, so the island wraps the lens.
 *
 * The Galaxy Z Fold8 cover is different: it reports its camera as a strip from the top edge down past the lens
 * (589,0 - 659,104 px at 2.625) and its outline is that same rectangle, so there is no smaller hole to wrap. For such a
 * strip the island wraps a band of it instead, sized so the pill keeps the usual height ([cameraStripBand]).
 */
internal fun cameraRect(rect: Rect, outline: android.graphics.Path?, density: Float): Rect {
    val bounds = android.graphics.RectF().also { outline?.computeBounds(it, true) }
    val lens = if (outline == null || bounds.isEmpty) null
        else intArrayOf(bounds.left.toInt(), bounds.top.toInt(), kotlin.math.ceil(bounds.right).toInt(), kotlin.math.ceil(bounds.bottom).toInt())
    return cameraRect(intArrayOf(rect.left, rect.top, rect.right, rect.bottom), lens, density).let { Rect(it[0], it[1], it[2], it[3]) }
}

/** [rect] and [outline] are left, top, right, bottom in px. An outline that spans other cutouts too is not inside. */
internal fun cameraRect(rect: IntArray, outline: IntArray?, density: Float): IntArray {
    if (outline == null || outline[2] <= outline[0] || outline[3] <= outline[1]) return rect
    val inside = outline[0] >= rect[0] - OUTLINE_SLACK && outline[1] >= rect[1] - OUTLINE_SLACK &&
        outline[2] <= rect[2] + OUTLINE_SLACK && outline[3] <= rect[3] + OUTLINE_SLACK
    if (inside && outline[3] - outline[1] < rect[3] - rect[1] - OUTLINE_SLACK) return outline
    return if (isCameraStrip(rect, outline, density)) cameraStripBand(rect, density) else rect
}

/**
 * A camera reported as a strip with no smaller hole inside it: the outline's bounds are the rect (give or take
 * rounding), it starts at the top edge, it is taller than it is wide, and taller than the usual pill. Notches are
 * wider than tall and punch holes stand off the edge, so neither counts.
 */
internal fun isCameraStrip(rect: IntArray, outline: IntArray, density: Float): Boolean {
    val same = (0..3).all { kotlin.math.abs(outline[it] - rect[it]) <= OUTLINE_SLACK }
    val width = rect[2] - rect[0]; val height = rect[3] - rect[1]
    return same && rect[1] in 0..OUTLINE_SLACK && height > width && height / density > ISLAND_MIN_HEIGHT
}

/**
 * The part of a [isCameraStrip] strip the island wraps: as wide as the strip, and as tall as a camera that gives the
 * usual pill height. It sits on the strip's center, but never so high that the pill would come closer to the edge
 * than the usual gap, which Home keeps when it draws the island anyway; that way Home and the island over other apps
 * place it the same.
 */
internal fun cameraStripBand(rect: IntArray, density: Float): IntArray {
    val half = ISLAND_MIN_HEIGHT / 2 - ISLAND_CAMERA_MARGIN
    val center = maxOf((rect[1] + rect[3]) / 2f / density, ISLAND_EDGE_GAP + ISLAND_MIN_HEIGHT / 2)
    return intArrayOf(rect[0], kotlin.math.floor((center - half) * density).toInt(), rect[2], kotlin.math.floor((center + half) * density).toInt())
}

/** Pixels an outline may stick out of its rect and still count as inside (rounding). */
private const val OUTLINE_SLACK = 2

/** Pure placement of the island around a camera cutout. Dp values except [centerXPx]. */
internal data class IslandGeometry(val camW: Float, val camH: Float, val centerXPx: Float, val centerY: Float, val pillH: Float, val maxW: Float,
    val top: Float = centerY - pillH / 2,
    /** Width the person added to the camera area it wraps (Adjust island), in dp. Zero is the automatic fit. */
    val extraW: Float = 0f) {
    /** The camera width the pill is built around: the measured one plus what the person added. */
    val wrapW: Float get() = camW + extraW
    fun widthFor(content: IslandContent): Float = minOf(islandWantWidth(content, wrapW.dp).value, maxW).coerceAtLeast(wrapW + pillH)
    /** The most width that can be added: the pill must still fit the room beside the camera. */
    val maxExtra: Float get() = (maxW - (camW + pillH)).coerceAtLeast(0f)
}

internal fun Rect.ltrb(): IntArray = intArrayOf(left, top, right, bottom)

/** The camera the island wraps on this window: the reported cutout ([cameraRect]), else a known hidden camera. */
internal fun islandCamera(cutout: android.view.DisplayCutout?, display: android.view.Display?, density: Float): Rect? =
    cutout?.boundingRects?.filter { !it.isEmpty }?.minByOrNull { it.top }?.let { cameraRect(it, cutout.cutoutPath, density) }
        ?: CameraArea.hiddenCamera(display)

/**
 * Which saved spot a window uses ([IslandPosition.load]). Home and the island over apps share it, so a spot dragged
 * to on Home is the one used over apps on the same screen.
 */
internal fun islandWideWindow(windowWidthPx: Int, density: Float, classScale: Float): Boolean =
    windowWidthPx / density * classScale >= 600f

/**
 * Where the compact island goes on one window. Home and the island over other apps both place it with this, so it
 * sits at the same spot and height in both. [camera] is left, top, right, bottom in px after [cameraRect], or null;
 * [custom] is a spot the user dragged it to on this screen and orientation, kept on screen ([IslandPosition.clamped]).
 */
internal fun islandPlacement(camera: IntArray?, custom: IslandPosition?, windowWidthPx: Int, windowHeightPx: Int, density: Float,
    extraWidthDp: Float = 0f, nudge: IslandNudge = IslandNudge.None): IslandGeometry {
    val atCamera = islandCameraGeometry(camera, windowWidthPx, windowHeightPx, density)
    val placed = custom?.clamped(windowWidthPx, windowHeightPx, density, atCamera.pillH)
        ?.let { islandGeometryAt(it.xFraction * windowWidthPx, it.topDp, windowWidthPx, density) } ?: atCamera
    // A nudge shifts the pill as it is (wrapping the camera or free), so the wrap width and height are never lost to it.
    // Kept on screen: a pill already near the bottom must not be nudged off it (the same bottom bound as a dragged spot, IslandPosition.clamped).
    val lowest = maxOf((windowHeightPx / density - placed.pillH - ISLAND_EDGE_GAP).coerceAtLeast(0f), placed.top)
    val top = (placed.top + nudge.dyDp).coerceIn(0f, lowest)
    val centerX = (placed.centerXPx + nudge.dxDp * density).coerceIn(0f, windowWidthPx.toFloat())
    val moved = placed.copy(centerXPx = centerX, centerY = top + placed.pillH / 2, top = top)
    return moved.copy(extraW = IslandSize.clamp(extraWidthDp, placed))
}

/**
 * The island wrapped around the camera. A camera that is not on the top edge (a screen turned sideways or upside
 * down) has no pill to sit in: wrapping it put the pill mid-edge and partly off screen, so the island goes to the top
 * center instead. Home stands an upright island at a side camera itself ([VerticalIsland]).
 */
internal fun islandCameraGeometry(camera: IntArray?, windowWidthPx: Int, windowHeightPx: Int, density: Float): IslandGeometry =
    islandGeometry(camera?.takeIf { cameraOnTopEdge(it, windowWidthPx, windowHeightPx) }, windowWidthPx, density)

/** Whether the top is the screen edge [camera] is nearest. An unknown window size counts as top. */
internal fun cameraOnTopEdge(camera: IntArray, windowWidthPx: Int, windowHeightPx: Int): Boolean {
    if (windowWidthPx <= 0 || windowHeightPx <= 0) return true
    return camera[1] <= minOf(camera[0], windowWidthPx - camera[2], windowHeightPx - camera[3])
}

/** Whether a pill dropped at [centerXPx], [topDp] is close enough to the real camera ([camera]) to snap back to it. */
internal fun islandSnapsToCamera(centerXPx: Float, topDp: Float, camera: IslandGeometry, density: Float): Boolean =
    kotlin.math.abs(centerXPx - camera.centerXPx) < 56f * density && kotlin.math.abs(topDp - camera.top) < 40f

/** Left of a pill [widthPx] wide centered on [centerXPx], kept the usual margin in from both screen edges. */
internal fun islandLeftPx(centerXPx: Float, widthPx: Float, windowWidthPx: Int, density: Float): Float {
    val edge = ISLAND_SIDE_MARGIN * density
    return (centerXPx - widthPx / 2f).coerceIn(edge, maxOf(edge, windowWidthPx - widthPx - edge))
}

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
    // (a punch-hole near the top, as on the Galaxy Z Fold7's inner screen); otherwise the lens peeks over it. A
    // cutout that starts at the edge itself (a notch, or a strip too short for [cameraStripBand]) cannot be started
    // above, so the island keeps the usual gap from the edge there.
    val atEdge = cutoutLtrb != null && cutoutLtrb[1] <= OUTLINE_SLACK
    val top = if (cutoutLtrb == null || atEdge) ISLAND_EDGE_GAP
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
            is IslandEvent.Silent -> Text(stringResource(if (e.on) R.string.state_on else R.string.state_off), color = if (e.on) Red else Color.White.copy(alpha = .7f), fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold)
            is IslandEvent.Focus -> Text(stringResource(if (e.on) R.string.state_on else R.string.state_off), color = if (e.on) Purple else Color.White.copy(alpha = .7f), fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold)
            is IslandEvent.Bluetooth -> Text(e.name ?: stringResource(R.string.connected), color = Color.White, fontSize = FolioType.GROUP_LABEL.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // The app's icon beside the sender's photo; without a photo the icon is already on the left.
            is IslandEvent.Message -> e.appIcon?.takeIf { e.avatar != null }?.let { Image(it.asImageBitmap(), null, Modifier.size(size * .8f).clip(RoundedCornerShape(size * .22f))) }
            is IslandEvent.Notice -> Unit
        }
        is IslandContent.Live -> when (val a = content.activity) {
            is IslandActivity.Media -> Bars(a.playing, if (LocalTintOptions.current.mediaIsland) rememberAccent(a.art)?.let { mixColor(it, Color.White, .25f) } ?: IslandGreen else IslandGreen)
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
            onDragEnd = { if (travel < -hideAt) latestOnHide() else scope.launch { pull.animateTo(0f, FolioMotion.spring(FolioMotion.Control)) } },
            onDragCancel = { scope.launch { pull.animateTo(0f) } },
        ) { change, dy ->
            change.consume(); travel += dy
            scope.launch { pull.snapTo((travel * .5f).coerceIn(-hideAt * 3f, 0f)) }
        }
    }.graphicsLayer { translationY = pull.value; alpha = 1f - (-pull.value / (hideAt * 6f)) }
}

/** Folio's own brief feedback: the app's icon (or an info symbol) and one or two lines. Tap or swipe up to hide. */
@Composable
private fun NoticeCardContent(notice: IslandEvent.Notice, topInset: Dp, onHide: () -> Unit) {
    // The text starts below the camera row, never across the lens. At large text the button drops under the text.
    val large = androidx.compose.ui.platform.LocalConfiguration.current.fontScale >= 1.3f
    @Composable fun action() {
        val action = notice.action ?: return
        Text(action.label, color = IslandBlue, fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.heightIn(min = FolioTouch.MIN.dp).clickable { action.run(); onHide() }.padding(start = FolioSpace.MEDIUM.dp, end = FolioSpace.SMALL.dp)
                .wrapContentHeight(Alignment.CenterVertically).testTag("island-notice-action"))
    }
    @Composable fun body(modifier: Modifier) {
      Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        notice.appIcon?.let { Image(it.asImageBitmap(), null, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))) }
            ?: Icon(Icons.Rounded.Info, null, tint = IslandBlue, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        // Three lines at most, then an ellipsis: the card stays small and quiet. TalkBack reads all of it.
        Text(notice.text, color = Color.White, fontSize = FolioType.SUBHEAD.sp, maxLines = NOTICE_MAX_LINES, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp,
            modifier = Modifier.weight(1f))
        if (!large) action()
      }
    }
    Column(Modifier.fillMaxWidth().swipeUpToHide(true, onHide).clickable(onClick = onHide)
        .padding(start = FolioSpace.LARGE.dp, end = FolioSpace.LARGE.dp, top = topInset, bottom = FolioSpace.MEDIUM.dp)) {
        body(Modifier.fillMaxWidth())
        if (large) Box(Modifier.align(Alignment.End)) { action() }
    }
}

private const val NOTICE_MAX_LINES = 3

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
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.clip(RoundedCornerShape(FolioRadius.ROW.dp)).clickable(onClick = onOpen)) {
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
                message.text?.let { Text(it, color = Color.White.copy(alpha = .85f), fontSize = FolioType.COMPACT.sp, maxLines = if (replying) 2 else 3,
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(FolioRadius.ROW.dp)).clickable(onClick = onOpen)) {
            if (activity is IslandActivity.Call) CallAvatar(activity, 44.dp)
            else ((activity as? IslandActivity.Media)?.art ?: activity.icon)?.let { Image(it.asImageBitmap(), null, Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(activity.title, color = Color.White, fontSize = FolioType.CALLOUT.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                Icon(if (activity.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (activity.playing) R.string.pause else R.string.play), tint = Color.White,
                    modifier = Modifier.minimumInteractiveComponentSize().size(44.dp).clip(CircleShape).clickable { if (activity.playing) t.pause() else t.play() })
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
        Text(label, color = Color.White.copy(alpha = .7f), fontSize = FolioType.CAPTION.sp, modifier = Modifier.padding(top = FolioSpace.TINY.dp))
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
    // No infinite animation while paused, or with Reduce Motion on: then the bars stand still (I-9).
    val still = LocalReduceMotion.current
    val phase = if (playing && !still) {
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

internal const val ISLAND_EDGE_GAP = 8f
/** Smallest gap between the island and the left or right screen edge. */
private const val ISLAND_SIDE_MARGIN = 12f
private const val ISLAND_CAMERA_MARGIN = 5f
/** The least the island reaches past the camera's top edge. */
private const val ISLAND_CAMERA_MIN_OVERLAP = 2f
private const val ISLAND_MIN_HEIGHT = 34f
