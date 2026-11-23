package com.mccal.folio

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Control Center's editor on a wide window: two panes (FeatureGate.CC_TWO_PANE). On the left, the layout being edited, with its minus badges, held
 * moves and resize corners exactly as in the one-pane editor; on the right, "Add a Control" as a list docked beside it, one row for each control with
 * a plus button. It replaces the floating card the gallery used on a wide window (CcControlGallery), which covered or shrank the panel.
 *
 * Nothing here is a device: the split is worked out from the container's width and the hinges the window reports (ADP-1, ADP-11, ADP-12). Where a
 * vertical hinge cuts the container the divider is the hinge, so no control straddles the crease; with none, or when it does not leave both panes
 * room, a fixed share of the width. A window too narrow, a half-open book (its panel is one half) or a tabletop gets no split, and the gallery
 * floats as it did before.
 */

/** The narrowest container (in dp, times the class scale: ADP-2) that holds both panes: a panel of four columns, the gap and the list. */
internal const val CC_TWO_PANE_MIN_WIDTH_DP = 640f
/** The least width the panel (the layout pane) is given: four columns of about 60 dp (over the 48 dp touch size), their gaps and the panel's own padding. */
internal const val CC_TWO_PANE_PANEL_MIN_DP = 320f
/** The least and the most width the list (the add pane) is given; on a hinge it takes the room beside the crease up to the most. */
internal const val CC_TWO_PANE_LIST_MIN_DP = 280f
internal const val CC_TWO_PANE_LIST_MAX_DP = 400f
/** With no hinge to follow the list is this share of the container, kept between the least and the most. */
internal const val CC_TWO_PANE_LIST_SHARE = .36f
/** The gap between the panes when nothing sets it, and the clear space kept each side of a hinge (the same 12 dp as [FoldAvoidingBox]). */
internal const val CC_TWO_PANE_GAP_DP = FolioSpace.LARGE.toFloat()
internal const val CC_TWO_PANE_HINGE_MARGIN_DP = FolioSpace.MEDIUM.toFloat()
/** The list is at least this share of the window's height, so a panel with few rows still has a list to browse. */
internal const val CC_TWO_PANE_LIST_MIN_HEIGHT_SHARE = .75f
/** The least height of a list row: a 40 dp icon with air around it, never below the touch size (A11Y-1). */
internal const val CC_ADD_ROW_MIN_DP = 56

/**
 * Where the two panes stand in a container, in dp and in layout-direction terms (from the start edge, so a right to left window mirrors it).
 * The panel fills 0 until [panelWidthDp]; the list starts at [paneStartDp] and is [paneWidthDp] wide. [onHinge] is true when a hinge set the divider.
 * [containerDp] is the whole width the panel had before the split, which the panel uses to keep the number of columns it normally has.
 */
internal data class CcTwoPaneSplit(val panelWidthDp: Float, val paneStartDp: Float, val paneWidthDp: Float, val onHinge: Boolean, val containerDp: Float = 0f)

/**
 * The split for a container [containerDp] wide (a window's width less what the caller keeps free), or null when there is no room for two panes.
 * [hingesDp] are the vertical hinges as ranges from the container's start edge; one that does not lie strictly inside it (the container is already
 * one half of a half-open book) is ignored. [classScale] is [Configuration.classScale], so a changed Display size cannot promote a phone (ADP-2).
 *  - With a hinge inside: the panel is everything before the first one and the list the room after it (to the next hinge if there is one), up to
 *    [CC_TWO_PANE_LIST_MAX_DP]; both must reach their least width, or there is no split. Never a fixed share across a crease.
 *  - With none: the list takes [CC_TWO_PANE_LIST_SHARE] of the container between its least and most, and the panel the rest less the gap.
 * The panel needs at least [panelMinDp] (never under [CC_TWO_PANE_PANEL_MIN_DP]); the caller raises it so the panel keeps the columns it has outside the editor.
 */
internal fun ccTwoPaneSplit(containerDp: Float, hingesDp: List<ClosedFloatingPointRange<Float>> = emptyList(), classScale: Float = 1f, panelMinDp: Float = CC_TWO_PANE_PANEL_MIN_DP): CcTwoPaneSplit? {
    @Suppress("NAME_SHADOWING") val panelMinDp = maxOf(panelMinDp, CC_TWO_PANE_PANEL_MIN_DP)
    if (containerDp * classScale < CC_TWO_PANE_MIN_WIDTH_DP) return null
    val inside = hingesDp.filter { it.start > 0f && it.endInclusive < containerDp }.sortedBy { it.start }
    if (inside.isEmpty()) {
        val list = (containerDp * CC_TWO_PANE_LIST_SHARE).coerceIn(CC_TWO_PANE_LIST_MIN_DP, CC_TWO_PANE_LIST_MAX_DP)
        val panel = containerDp - list - CC_TWO_PANE_GAP_DP
        return if (panel >= panelMinDp) CcTwoPaneSplit(panel, panel + CC_TWO_PANE_GAP_DP, list, onHinge = false, containerDp = containerDp) else null
    }
    val crease = inside.first()
    val panel = crease.start - CC_TWO_PANE_HINGE_MARGIN_DP
    val start = crease.endInclusive + CC_TWO_PANE_HINGE_MARGIN_DP
    val room = (inside.getOrNull(1)?.start?.minus(CC_TWO_PANE_HINGE_MARGIN_DP) ?: containerDp) - start
    val list = minOf(room, CC_TWO_PANE_LIST_MAX_DP)
    return if (panel >= panelMinDp && list >= CC_TWO_PANE_LIST_MIN_DP) CcTwoPaneSplit(panel, start, list, onHinge = true, containerDp = containerDp) else null
}

/**
 * The vertical hinges of [hinge] as ranges from the start edge of a container whose left edge stands at [containerLeftPx] in the window and that is
 * [containerWidthPx] wide, in dp. [rtl] counts from the right instead. Horizontal hinges (a tabletop, a flip) are not along the width, so none.
 */
internal fun ccHingeSpansDp(hinge: Hinge?, containerLeftPx: Float, containerWidthPx: Float, rtl: Boolean, density: Float): List<ClosedFloatingPointRange<Float>> {
    if (hinge == null || !hinge.vertical || density <= 0f) return emptyList()
    return hinge.spans.map { span ->
        val from = span.first - containerLeftPx
        val to = span.last - containerLeftPx
        val (a, b) = if (rtl) (containerWidthPx - to) to (containerWidthPx - from) else from to to
        (a / density)..(b / density)
    }
}

/** The window's hinge as the window reports it, flat or bent. [LocalHinge] holds it only while half-open (Control Center moves off a bent hinge), but a flat crease is where the divider goes. */
internal val LocalWindowHinge = staticCompositionLocalOf<Hinge?> { null }

/** What the editor's list shows and what a plus does, handed from the panel (which knows its grid) to the list (which stands beside it). */
internal class CcAddPaneFeed {
    var items by mutableStateOf<List<CcGalleryItem>>(emptyList())
    /** Read when a row is pressed, not while composing, so it never needs to be state. */
    var onAdd: (CcGalleryItem) -> Unit = {}
}

/**
 * Lays Control Center out for the editor. [content] draws the panel and gets the split (null while there is one pane: not editing, a window with
 * no room, the gate shut) and the modifier that sizes the panel, which is what the panel's own modifier was before this box stood in its place: its
 * half when split, else the fill the caller asked of the host. The list ([feed]) is drawn beside it, as tall as the panel. The tree is the same
 * either way, so the panel keeps its state when the editor opens.
 */
@Composable
internal fun CcTwoPaneHost(modifier: Modifier, engaged: Boolean, feed: CcAddPaneFeed, panelMinDp: (containerDp: Float, heightDp: Float?) -> Float = { _, _ -> CC_TWO_PANE_PANEL_MIN_DP }, content: @Composable BoxScope.(CcTwoPaneSplit?, Modifier) -> Unit) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val classScale = LocalConfiguration.current.classScale
    val hinge = LocalWindowHinge.current
    var leftPx by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(modifier.onGloballyPositioned { leftPx = it.positionInWindow().x }) {
        val split = if (engaged && maxWidth != Dp.Infinity) {
            val widthPx = with(density) { maxWidth.toPx() }
            ccTwoPaneSplit(maxWidth.value, ccHingeSpansDp(hinge, leftPx, widthPx, rtl, density.density), classScale,
                panelMinDp(maxWidth.value, if (maxHeight == Dp.Infinity) null else maxHeight.value))
        } else null
        // The panel's own size: its half when split; otherwise whatever the host was told to fill, since this box now stands where the panel's did.
        val panel = when {
            split != null -> Modifier.align(Alignment.TopStart).width(split.panelWidthDp.dp)
            else -> Modifier.then(if (minWidth > 0.dp && minWidth == maxWidth) Modifier.fillMaxWidth() else Modifier)
                .then(if (minHeight > 0.dp && minHeight == maxHeight) Modifier.fillMaxHeight() else Modifier)
        }
        content(split, panel)
        // The list is as tall as the panel beside it, and never shorter than a share of the window: a short panel (few rows) would leave a list too small to browse.
        // A spacer of that height lifts the host's own height, and a box that matches it holds the list at its place.
        if (split != null) {
            androidx.compose.foundation.layout.Spacer(Modifier.height((LocalConfiguration.current.screenHeightDp * CC_TWO_PANE_LIST_MIN_HEIGHT_SHARE).dp))
            Box(Modifier.matchParentSize().padding(start = split.paneStartDp.dp)) { CcAddPane(feed, Modifier.width(split.paneWidthDp.dp).fillMaxHeight()) }
        }
    }
}

/**
 * "Add a Control" as a list docked beside the editor: a search field, then the controls under their sections, each row a name with a teal plus
 * (the same items, search and add as the sheet and the card). The whole row is the button, so the target is the row's height and width, not the
 * plus alone. It is solid, never blurred (DES-14, DES-15), and with Reduce Motion it appears at once instead of fading in.
 */
@Composable
internal fun CcAddPane(feed: CcAddPaneFeed, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val items = feed.items
    val shown = ccGalleryFilter(items, query)
    val title = stringResource(R.string.add_a_control)
    val reduceMotion = LocalReduceMotion.current
    var arrived by remember { mutableStateOf(reduceMotion) }
    LaunchedEffect(Unit) { arrived = true }
    val alpha by animateFloatAsState(if (arrived) 1f else 0f, if (reduceMotion) androidx.compose.animation.core.snap() else tween(180), label = "add pane")
    // Reading order is the layout first, then this list (A11Y-8); the pane is announced by name.
    Column(modifier.graphicsLayer { this.alpha = alpha }.clip(RoundedCornerShape(FolioRadius.GROUPED_CARD.dp)).background(FolioColors.MenuSurface)
        // A tap or a vertical drag on the list's own chrome stays here: the panel's margins close it and pull it up.
        .pointerInput(Unit) { detectTapGestures() }.draggable(rememberDraggableState { }, Orientation.Vertical)
        .semantics { paneTitle = title; isTraversalGroup = true; traversalIndex = 1f }
        .padding(horizontal = FolioSpace.LARGE.dp).padding(top = FolioSpace.MEDIUM.dp)) {
        Text(title, color = Color.White, fontSize = FolioType.TITLE3.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = FolioSpace.SMALL.dp).semantics { heading() })
        GallerySearchField(query, { query = it })
        if (shown.isEmpty()) {
            Text(stringResource(if (items.isEmpty()) R.string.all_controls_added else R.string.no_controls_found), color = FolioGlass.secondary, fontSize = FolioType.FOOTNOTE.sp,
                modifier = Modifier.padding(vertical = FolioSpace.LARGE.dp))
        } else LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = FolioSpace.COMPACT.dp, bottom = FolioSpace.LARGE.dp)) {
            ccGallerySections(shown).forEach { section ->
                item(key = "heading-${section.label}") { GalleryHeading(stringResource(section.label), Modifier.semantics { heading() }) }
                item(key = "section-${section.label}") {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.CARD.dp)).background(Color.White.copy(alpha = .08f))) {
                        section.items.forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider(Modifier.padding(start = CC_ADD_DIVIDER_INSET_DP.dp), color = Color.White.copy(alpha = .12f))
                            CcAddRow(item) { feed.onAdd(it) }
                        }
                    }
                }
            }
        }
    }
}

/** Where a row's divider starts: past the icon, as an iOS grouped list's does. */
private const val CC_ADD_DIVIDER_INSET_DP = 66

@Composable
private fun CcAddRow(item: CcGalleryItem, onAdd: (CcGalleryItem) -> Unit) {
    val add = stringResource(R.string.add_control, item.label)
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth().heightIn(min = CC_ADD_ROW_MIN_DP.dp).clickable { onAdd(item) }.semantics { role = Role.Button; contentDescription = add }
        .padding(horizontal = FolioSpace.COMPACT.dp, vertical = FolioSpace.TINY.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).background(Color.White.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(item.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(item.label, color = Color.White, fontSize = FolioType.BODY.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        // The plus is drawn, and the row is the button. The ring is the accent's light ink so the circle keeps 3:1 against the list (A11Y-9).
        Box(Modifier.size(32.dp).clip(CircleShape).background(accent.fill).border(BorderStroke(1.5.dp, accent.ink), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}
