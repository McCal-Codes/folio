package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.remember
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One thing the gallery can add to Control Center: a control, or a module (connectivity, Now Playing, a slider, Focus) that was taken off. */
internal data class CcGalleryItem(val id: String, val label: String, val icon: ImageVector, val module: Boolean)

/** The sections the gallery lists controls under (as iOS's Add a Control), in the order shown. */
internal enum class CcGalleryGroup(@androidx.annotation.StringRes val label: Int, val ids: Set<String>) {
    CONNECTIVITY(R.string.cc_group_connectivity, setOf("HOTSPOT", "CAST", "VPN", "LOCATION")),
    DISPLAY(R.string.cc_group_display, setOf("DISPLAY", "ROTATION", "DND", "BATTERY_SAVER", "SOUND_MODE", "SCREEN_TIMEOUT", "NIGHT_LIGHT")),
    TOOLS(R.string.cc_group_tools, setOf("FLASHLIGHT", "CAMERA", "TIMER", "ALARM", "CALCULATOR", "SCREENSHOT", "RECORDER", "NOTES")),
    SECURITY(R.string.cc_group_security, setOf("WALLET", "LOCK")),
    ACCESSIBILITY(R.string.cc_group_accessibility, setOf("ACCESSIBILITY")),
    SYSTEM(R.string.cc_group_system, setOf("SETTINGS", "SYSTEM"));
}

/** The section a control belongs to; null for one that is in none (it is listed last, under "Controls"). */
internal fun ccGalleryGroup(id: String): CcGalleryGroup? = CcGalleryGroup.entries.firstOrNull { id in it.ids }

/** The items whose name has [query] in it, ignoring case and the spaces around it; every item for a blank query. */
internal fun ccGalleryFilter(items: List<CcGalleryItem>, query: String): List<CcGalleryItem> {
    val q = query.trim()
    return if (q.isEmpty()) items else items.filter { it.label.contains(q, ignoreCase = true) }
}

/** One section of the gallery: a heading ([label], a string resource) and the [items] under it. */
internal data class CcGallerySection(@androidx.annotation.StringRes val label: Int, val items: List<CcGalleryItem>)

/**
 * [shown] split into the sections the gallery lists, in order: the modules, then each group of controls, then the controls that are in none.
 * A section with nothing in it is left out. Shared by the sheet, the card and the editor's side list so they never disagree.
 */
internal fun ccGallerySections(shown: List<CcGalleryItem>): List<CcGallerySection> {
    val controls = shown.filter { !it.module }
    return buildList {
        shown.filter { it.module }.takeIf { it.isNotEmpty() }?.let { add(CcGallerySection(R.string.cc_gallery_modules, it)) }
        CcGalleryGroup.entries.forEach { group -> controls.filter { ccGalleryGroup(it.id) == group }.takeIf { it.isNotEmpty() }?.let { add(CcGallerySection(group.label, it)) } }
        controls.filter { ccGalleryGroup(it.id) == null }.takeIf { it.isNotEmpty() }?.let { add(CcGallerySection(R.string.cc_gallery_controls, it)) }
    }
}

/**
 * The sheet Control Center's edit mode opens to add a control (as iOS's Add a Control): a search field, then the modules that are off and
 * the controls that are not on the page, each a round button with its name under it. A tap adds that item and the sheet stays open to add
 * more; it closes itself once there is nothing left to add. Nothing here decides where an item goes: [onAdd] does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CcControlGallery(items: List<CcGalleryItem>, panel: androidx.compose.ui.geometry.Rect? = null, fit: Float = 1f, minFit: Float = CC_GALLERY_MIN_FIT, onFit: (Float) -> Unit = {}, onAdd: (CcGalleryItem) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = ccGalleryFilter(items, query)
    // On a wide window (the unfolded Fold, a tablet) it floats as a card, as on an iPad: beside the panel when there is room, else low under it. It can be dragged.
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= CC_GALLERY_CARD_MIN_WIDTH_DP
    if (wide) androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        FloatingGalleryCard(items, shown, query, { query = it }, panel, fit, minFit, onFit, onAdd, onDismiss)
    } else ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = FolioSpace.LARGE.dp).padding(bottom = FolioSpace.LARGE.dp)) {
            GalleryBody(items, shown, query, { query = it }, onAdd)
        }
    }
}

/** Where the card stands (top left, in px) and the most height it may take. */
internal data class CcCardPlacement(val x: Float, val y: Float, val maxHeight: Float, val low: Boolean = false)

/**
 * The scale that fits Control Center (from [panelTop] down, [panelHeight] tall at full size, in px) above a low card whose top is [cardTop], keeping [margin] between them,
 * so the card never covers a control. 1 when it already fits; never below [minFit], which keeps a tile at least the minimum touch size (the card is draggable past that).
 */
internal fun ccGalleryFit(panelTop: Float, panelHeight: Float, cardTop: Float, margin: Float, minFit: Float = CC_GALLERY_MIN_FIT): Float =
    if (panelHeight <= 0f) 1f else ((cardTop - margin - panelTop) / panelHeight).coerceIn(minFit.coerceAtMost(1f), 1f)

/** The least Control Center shrinks to when nothing says better. */
internal const val CC_GALLERY_MIN_FIT = .6f

/**
 * Where the gallery card first stands, from the window, the Control Center [panel] (left, top, right, bottom) and the card's width and measured height ([cardH], 0 before it is measured),
 * all in px with [margin] kept from every edge. Beside the panel when one side has room for the card, as on the iPad (the right side first, then the left), top aligned with the panel
 * and as tall as the window allows; when neither does, low on the screen under the controls and centered on the panel, capped so it covers the empty slots, not the controls. Never off screen.
 */
internal fun ccGalleryCardPlacement(winW: Float, winH: Float, panel: FloatArray, cardW: Float, cardH: Float, margin: Float): CcCardPlacement {
    val pl = panel[0]; val pt = panel[1]; val pr = panel[2]
    val tall = (winH - 2 * margin).coerceAtLeast(0f)
    fun beside(x: Float): CcCardPlacement { val y = pt.coerceIn(margin, maxOf(margin, winH - margin - minOf(cardH, tall))); return CcCardPlacement(x, y, minOf(tall, winH - y - margin)) }
    return when {
        winW - pr >= cardW + 2 * margin -> beside(pr + margin)
        pl >= cardW + 2 * margin -> beside(pl - margin - cardW)
        else -> {
            val cap = winH * CC_GALLERY_LOW_CARD_HEIGHT
            val x = ((pl + pr) / 2f - cardW / 2f).coerceIn(margin, maxOf(margin, winW - cardW - margin))
            CcCardPlacement(x, (winH - margin - minOf(cardH, cap)).coerceAtLeast(margin), cap, low = true)
        }
    }
}

/** The share of the window's height the card takes when it stands low under the controls. */
internal const val CC_GALLERY_LOW_CARD_HEIGHT = .45f
/** How far one step of the move actions shifts the card, in dp. */
internal const val CC_GALLERY_MOVE_STEP_DP = 48

@Composable
private fun FloatingGalleryCard(items: List<CcGalleryItem>, shown: List<CcGalleryItem>, query: String, onQuery: (String) -> Unit,
    panel: androidx.compose.ui.geometry.Rect?, fit: Float, minFit: Float, onFit: (Float) -> Unit, onAdd: (CcGalleryItem) -> Unit, onDismiss: () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val winW = with(density) { config.screenWidthDp.dp.toPx() }
    val winH = with(density) { config.screenHeightDp.dp.toPx() }
    val margin = with(density) { FolioSpace.LARGE.dp.toPx() }
    val cardW = with(density) { CC_GALLERY_CARD_WIDTH_DP.dp.toPx() }
    val step = with(density) { CC_GALLERY_MOVE_STEP_DP.dp.toPx() }
    var cardH by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // What the person has dragged it by, kept through a rotation.
    var shiftX by rememberSaveable { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var shiftY by rememberSaveable { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val bounds = panel?.let { floatArrayOf(it.left, it.top, it.right, it.bottom) } ?: floatArrayOf(winW / 2f, margin, winW / 2f, winH)
    val place = ccGalleryCardPlacement(winW, winH, bounds, cardW, cardH.toFloat(), margin)
    val x = (place.x + shiftX).coerceIn(margin / 2f, maxOf(margin / 2f, winW - cardW - margin / 2f))
    val y = (place.y + shiftY).coerceIn(margin / 2f, maxOf(margin / 2f, winH - cardH - margin / 2f))
    fun nudge(dx: Float, dy: Float) { shiftX += dx; shiftY += dy }
    // Standing low, the card would cover the controls: Control Center shrinks to fit above it, and comes back to full size when the card moves off or closes.
    // [bounds] is where the panel stands now, at the scale [fit] it has, so its full height is that divided by the scale.
    val shrink = if (place.low && cardH > 0) ccGalleryFit(bounds[1], (bounds[3] - bounds[1]) / fit, y, margin, minFit) else 1f
    androidx.compose.runtime.LaunchedEffect(shrink) { onFit(shrink) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { onFit(1f) } }
    val move = stringResource(R.string.cc_gallery_move)
    val left = stringResource(R.string.cc_gallery_move_left)
    val right = stringResource(R.string.cc_gallery_move_right)
    val up = stringResource(R.string.cc_gallery_move_up)
    val down = stringResource(R.string.cc_gallery_move_down)
    Box(Modifier.fillMaxSize().then(dismissScrim(onDismiss))) {
        Column(Modifier.offset { androidx.compose.ui.unit.IntOffset(x.roundToInt(), y.roundToInt()) }.width(CC_GALLERY_CARD_WIDTH_DP.dp)
            .heightIn(max = with(density) { place.maxHeight.toDp() }).onSizeChanged { cardH = it.height }
            .clip(RoundedCornerShape(FolioRadius.PANEL.dp)).background(FolioColors.MenuSurface).clickable(interactionSource = null, indication = null) {}
            .padding(horizontal = FolioSpace.LARGE.dp).padding(bottom = FolioSpace.LARGE.dp)) {
            // The grip: drag it anywhere; for TalkBack and a keyboard the same moves are actions.
            Box(Modifier.fillMaxWidth().height(FolioTouch.MIN.dp).pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); shiftX += drag.x; shiftY += drag.y } }
                .semantics {
                    contentDescription = move
                    customActions = listOf(
                        CustomAccessibilityAction(left) { nudge(-step, 0f); true }, CustomAccessibilityAction(right) { nudge(step, 0f); true },
                        CustomAccessibilityAction(up) { nudge(0f, -step); true }, CustomAccessibilityAction(down) { nudge(0f, step); true })
                }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 40.dp, height = 5.dp).clip(CircleShape).background(Color.White.copy(alpha = .35f)))
            }
            GalleryBody(items, shown, query, onQuery, onAdd)
        }
    }
}

/** The window width from which the gallery floats as a card, and the card's width. */
internal const val CC_GALLERY_CARD_MIN_WIDTH_DP = 600
internal const val CC_GALLERY_CARD_WIDTH_DP = 360

@Composable
private fun GalleryBody(items: List<CcGalleryItem>, shown: List<CcGalleryItem>, query: String, onQuery: (String) -> Unit, onAdd: (CcGalleryItem) -> Unit) {
    Column {
        run {
            GallerySearchField(query, onQuery)
            if (shown.isEmpty()) {
                Text(stringResource(if (items.isEmpty()) R.string.all_controls_added else R.string.no_controls_found), color = FolioGlass.secondary, fontSize = FolioType.FOOTNOTE.sp,
                    modifier = Modifier.padding(vertical = FolioSpace.LARGE.dp))
            } else LazyVerticalGrid(GridCells.Adaptive(88.dp), Modifier.weight(1f, fill = false).heightIn(max = 520.dp).padding(top = FolioSpace.COMPACT.dp),
                horizontalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp), verticalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp)) {
                ccGallerySections(shown).forEach { section ->
                    item(span = { GridItemSpan(maxLineSpan) }) { GalleryHeading(stringResource(section.label)) }
                    items(section.items, key = { it.id }) { GalleryTile(it, onAdd) }
                }
            }
        }
    }
}

/** The search field the gallery and the editor's side list share: a rounded field with a magnifier, hinting "Search Controls". */
@Composable
internal fun GallerySearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val searchLabel = stringResource(R.string.search_controls)
    Row(modifier.fillMaxWidth().heightIn(min = FolioTouch.MIN.dp).clip(RoundedCornerShape(FolioRadius.CARD.dp)).background(Color.White.copy(alpha = .1f)).padding(horizontal = FolioSpace.COMPACT.dp, vertical = FolioSpace.SNUG.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FolioSpace.SNUG.dp)) {
        Icon(Icons.Rounded.Search, null, tint = FolioGlass.secondary, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(stringResource(R.string.search_controls), color = FolioGlass.secondary, fontSize = FolioType.BODY.sp)
            BasicTextField(query, onQuery, singleLine = true, cursorBrush = SolidColor(Color.White),
                textStyle = TextStyle(color = Color.White, fontSize = FolioType.BODY.sp), keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = searchLabel })
        }
    }
}

@Composable
internal fun GalleryHeading(text: String, modifier: Modifier = Modifier) =
    Text(text, color = FolioGlass.secondary, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold, modifier = modifier.padding(top = FolioSpace.TINY.dp))

@Composable
private fun GalleryTile(item: CcGalleryItem, onAdd: (CcGalleryItem) -> Unit) {
    val add = stringResource(R.string.add_control, item.label)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.CARD.dp)).clickable { onAdd(item) }.semantics { role = Role.Button; contentDescription = add }.padding(vertical = FolioSpace.TINY.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        // The badge sits on the circle's edge, outside its clip, so the plus is whole.
        Box(Modifier.size(64.dp)) {
            Box(Modifier.matchParentSize().clip(CircleShape).background(Color.White.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(item.icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Box(Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(FolioColors.Green), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Text(item.label, color = Color.White, fontSize = FolioType.GROUP_LABEL.sp, maxLines = 2, lineHeight = FolioType.GROUP_LABEL.sp * 1.2f, textAlign = TextAlign.Center, modifier = Modifier.padding(top = FolioSpace.TINY.dp))
    }
}
