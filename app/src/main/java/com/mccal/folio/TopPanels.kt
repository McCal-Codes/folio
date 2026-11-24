@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mccal.folio

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.dismiss
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter


/**
 * Folio's Notification Center (top-left pull) and Control Center (top-right pull). [progress] is the
 * shared overlay animation (0 closed … 1 open); the launcher behind blurs with the same value.
 */
@Composable
internal fun TopPanels(panel: ShadePanel?, progress: () -> Float, status: DeviceStatus, onClose: () -> Unit,
    onSystemPanel: (ShadePanel) -> Unit, showClock: Boolean = true, grouped: Boolean = true, sections: Boolean = false,
    ccControls: List<String> = CcControl.DEFAULTS, onCcControls: (List<String>) -> Unit = {},
    ccLayout: CcSavedLayout? = null, onCcLayout: (CcSavedLayout?) -> Unit = {},
    ccSize: PanelSize = PanelSize.STANDARD, ccCentered: Boolean = false, ccLeft: Boolean = false, ncLayout: NcLayout = NcLayout.FULL_SCREEN, ccLook: CcLook = CcLook.CLEAR,
    ccOptions: CcOptions = CcOptions(),
    focusModes: List<FocusMode> = emptyList(), activeFocus: String? = null, onFocus: (String?) -> Unit = {},
    onSwitchPanel: ((ShadePanel) -> Unit)? = null) {
    val open = panel == ShadePanel.NOTIFICATIONS || panel == ShadePanel.QUICK_SETTINGS
    OverlayBackHandler(open, onClose)
    val pageBack = remember { CcPageBack() }
    // The swipe up that closes a panel pulls it with the finger; a panel opened again starts from rest.
    val pull = remember { PanelPull() }
    LaunchedEffect(open) { if (open) pull.reset() }
    val pullGesture = rememberPanelPull(pull, pageBack, onClose)
    var shown by remember { mutableStateOf<ShadePanel?>(null) }
    if (open) shown = panel
    val current = shown ?: return
    if (!open && progress() <= 0.001f) { shown = null; return }
    val lift = with(LocalDensity.current) { 28.dp.toPx() }
    val config = LocalConfiguration.current
    val wide = config.fitsRegularHomeLayout()
    // The preview pane (0.7.0 gate): Full screen on a window 700 dp wide or more puts the open notification beside the list.
    val previewPane = rememberGateOpen(FeatureGate.NC_PREVIEW_PANE)
    val shape = shadeShape(ncLayout, wide, config.screenWidthDp > config.screenHeightDp, config.screenWidthDp,
        LocalHinge.current?.active == true || LocalShadeOverApps.current, current, showClock, previewPane)
    // A card or two cards leave Home visible, so the dim is lighter than for a panel that fills the screen.
    val dim = if (shape == ShadeShape.CARD || shape == ShadeShape.BOTH) .6f else 1f
    // The switch between the panels is only drawn where one panel stands alone (two cards side by side already show both) and while its gate is open.
    val switcher: ((ShadePanel) -> Unit)? = onSwitchPanel?.takeIf { shape != ShadeShape.BOTH && FeatureGate.PANEL_SWITCH.isOpen(LocalContext.current) }
    // Where both panels stand alone at the top (any width: a phone, the open screen, a tablet), the switch is drawn once, at the top centre, and stays where it
    // is while the two panels cross-fade under it. A card, a clock beside the list or a window too short for the extra row keeps its switch inside the panel.
    val hingedOrOverApps = LocalHinge.current?.active == true || LocalShadeOverApps.current
    fun shapeOf(panel: ShadePanel) = shadeShape(ncLayout, wide, config.screenWidthDp > config.screenHeightDp, config.screenWidthDp, hingedOrOverApps, panel, showClock, previewPane)
    val hoistSwitch = switcher != null && config.screenHeightDp >= HOIST_SWITCH_MIN_HEIGHT_DP &&
        shapeOf(ShadePanel.NOTIFICATIONS).let { it == ShadeShape.SINGLE_TOP || it == ShadeShape.SPLIT_CLOCK || it == ShadeShape.LIST_DETAIL } && shapeOf(ShadePanel.QUICK_SETTINGS) == ShadeShape.SINGLE_TOP
    // A card is as tall as its list, at most two thirds of the window; a panel that fills the window has no ceiling but the room it has.
    val cardListMax = (config.screenHeightDp * .62f).dp

    // Clear glass is as light as the backdrop allows: how bright the backdrop picture is sets the dim and the tiles (the tone eases between values).
    val backdrop = LocalShadeBackdropImage.current
    val targetTone = androidx.compose.runtime.remember(backdrop, ccLook) {
        if (ccLook == CcLook.CLEAR && backdrop != null) {
            ccClearTone(ccBrightness(IntArray(backdrop.width * backdrop.height).also { backdrop.readPixels(it) }), dimScale = dim)
        } else CC_DEFAULT_TONE
    }
    val toneSpec = androidx.compose.animation.core.tween<Float>(220)
    val dimNow by androidx.compose.animation.core.animateFloatAsState(targetTone.dim, toneSpec, label = "panel dim")
    val blackNow by androidx.compose.animation.core.animateFloatAsState(targetTone.tileBlack, toneSpec, label = "tile black")
    val whiteNow by androidx.compose.animation.core.animateFloatAsState(targetTone.tileWhite, toneSpec, label = "tile white")
    val tone = androidx.compose.runtime.remember(dimNow, blackNow, whiteNow) { CcGlassTone(dimNow, blackNow, whiteNow) }

    // Scrim: tap or swipe up anywhere to close.
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress() * dim * PanelPull.fade(pull.offset, size.height) }
        .then(if (LocalShadeBackdropImage.current != null || (LocalShadeOverApps.current && !LocalShadeBackdropBlur.current)) Modifier else Modifier.background(FolioGlass.scrim))
        .then(if (open) Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onClose)
            .then(pullGesture) else Modifier)
        .testTag("top-panel-scrim")) {
        // Where the backdrop is a picture (over an app that cannot blur its window, or Home behind a panel): the picture, blurred once, fades in
        // under a dim. Over an app with no picture yet or none possible, Folio's frosted glass stands in.
        val picture = LocalShadeBackdropImage.current
        val fadeIn by androidx.compose.animation.core.animateFloatAsState(if (picture != null) .94f else 0f, androidx.compose.animation.core.tween(160), label = "backdrop picture")
        if (picture != null) {
            androidx.compose.foundation.Image(picture, null, Modifier.fillMaxSize().graphicsLayer { alpha = fadeIn },
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = tone.dim)))
        } else if (LocalShadeOverApps.current && !LocalShadeBackdropBlur.current) {
            FrostedBackdrop(LocalWallpaperTone.current, Modifier.fillMaxSize().graphicsLayer { alpha = .86f })
        }
    }

    // Unfolded, Notification Center can be iPad-style: big clock on the left, notifications on the right (Full screen), a card under the
    // corner it was pulled from (Card), or beside Control Center (Side by side); see shadeShape.
    val split = shape == ShadeShape.SPLIT_CLOCK
    // The preview pane: the list on the left and the open notification beside it, filling the window's height like the split does.
    val detail = shape == ShadeShape.LIST_DETAIL
    val tall = split || detail
    // Half folded, Control Center moves off the hinge like iPhone Duo's controls; Notification Center is a
    // scrolling list, which adapts by scrolling instead.
    val hinge = LocalHinge.current?.takeIf { it.active && current == ShadePanel.QUICK_SETTINGS }
    CompositionLocalProvider(LocalWindowHinge provides LocalHinge.current, LocalHinge provides hinge, LocalCcLook provides ccLook, LocalCcTone provides tone, LocalPanelPull provides pull) {
    FoldAvoidingBox(Modifier.windowInsetsPadding(WindowInsets.folioSafeTop).navigationBarsPadding().padding(start = FolioSpace.LARGE.dp, end = FolioSpace.LARGE.dp, top = FolioSpace.XXL.dp, bottom = FolioSpace.MEDIUM.dp),
        contentAlignment = when {
            shape == ShadeShape.BOTH -> Alignment.TopCenter
            // Notification Center is pulled from the top left and Control Center from the top right: each card sits under its own corner.
            shape == ShadeShape.CARD -> if (current == ShadePanel.NOTIFICATIONS) Alignment.TopStart else Alignment.TopEnd
            split -> Alignment.TopEnd
            current == ShadePanel.NOTIFICATIONS -> if (wide) Alignment.TopCenter else Alignment.TopStart
            wide && ccCentered -> Alignment.TopCenter
            wide && ccLeft -> Alignment.TopStart
            else -> Alignment.TopEnd
        }) {
        // Notification Center uses the width; Control Center is a compact fixed grid (like iPad) so it isn't oversized.
        // Beside the clock the list takes at most 60 percent of the width, so the clock (40 percent) is never drawn under it.
        // A compact Control Center slides in from the side it sits on and back to it when it closes (the same progress runs both ways); in the
        // middle, or without the compact panel, it grows from its corner as before. Reduce Motion keeps the corner's fade alone.
        val slideReduce = LocalReduceMotion.current
        val slideRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
        val slideSign = (if (ccCentered) 0f else if (ccLeft) -1f else 1f) * (if (slideRtl) -1f else 1f)
        val slideCompact = ccOptions.compact && wide && shape == ShadeShape.SINGLE_TOP && !slideReduce && slideSign != 0f
        fun panelModifier(which: ShadePanel) = (if (split) Modifier.fillMaxWidth(.6f) else Modifier).widthIn(max = when {
                split -> 540.dp
                detail && which == ShadePanel.NOTIFICATIONS -> LIST_DETAIL_MAX_DP.dp
                shape == ShadeShape.CARD && which == ShadePanel.NOTIFICATIONS -> 420.dp
                shape == ShadeShape.BOTH -> 440.dp
                which == ShadePanel.QUICK_SETTINGS && shape == ShadeShape.SINGLE_TOP -> if (ccOptions.compact && wide) CC_COMPACT_WIDTH_DP.dp else Dp.Infinity // Control Center fills the window; its grid sets its own width
                wide -> 460.dp
                else -> 560.dp
            })
            .then(if ((which == ShadePanel.NOTIFICATIONS && shape != ShadeShape.BOTH) || (which == ShadePanel.QUICK_SETTINGS && shape == ShadeShape.SINGLE_TOP && !(ccOptions.compact && wide))) Modifier.fillMaxWidth() else Modifier)
            .then(if (tall) Modifier.fillMaxHeight() else Modifier)
            // Taps on gaps inside the panel must not fall through to the scrim and close it. Control Center's own margins (beside and around the grid) are
            // the exception: a tap there closes it, as a tap outside does; the grid and the page strip take their own taps (see ControlCenter).
            .pointerInput(which) { detectTapGestures(onTap = if (which == ShadePanel.QUICK_SETTINGS) ({ _: androidx.compose.ui.geometry.Offset -> onClose() }) else null) }
            // A swipe up that starts in Control Center's margins is the same swipe as one under it (it used to count as a tap there and close the panel from any page).
            .then(if (open) pullGesture else Modifier)
            .graphicsLayer {
                val p = progress()
                val pulled = pull.offset
                if (which == ShadePanel.NOTIFICATIONS) {
                    // iOS: Notification Center slides down from the top edge and doesn't zoom; it's fully opaque
                    // early so the list reads as one sheet moving, not a fade.
                    alpha = (OverlayBack.unpreviewed(p) * 1.6f).coerceAtMost(1f) * PanelPull.fade(pulled, size.height)
                    translationY = -lift * 3f * (1f - p) + pulled
                } else {
                    // Control Center grows out of the corner it was pulled from; it fades only as it closes, not in a back preview.
                    alpha = OverlayBack.unpreviewed(p) * PanelPull.fade(pulled, size.height)
                    if (slideCompact) {
                        translationX = slideSign * (size.width + 48.dp.toPx()) * (1f - p)
                        translationY = pulled
                    } else {
                        translationY = -lift * (1f - p) + pulled
                        scaleX = .92f + .08f * p; scaleY = scaleX
                        transformOrigin = TransformOrigin(1f, 0f)
                    }
                }
            }
        if (split) {
            val clockLift = lift
            SplitClock(Modifier.align(Alignment.CenterStart).fillMaxWidth(.4f).graphicsLayer {
                val p = progress(); alpha = OverlayBack.unpreviewed(p) * PanelPull.fade(pull.offset, size.height); translationY = -clockLift * (1f - p) + pull.offset
            })
        }
        if (shape == ShadeShape.BOTH) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp, Alignment.CenterHorizontally)) {
            // Either pull opens both: Notification Center on the left and Control Center on the right, each with its own close button.
            NotificationCenter(Modifier.weight(1f, fill = false).then(panelModifier(ShadePanel.NOTIFICATIONS)), false, grouped, sections, listMax = cardListMax, onClose = onClose, onSystemQuickSettings = { onSystemPanel(ShadePanel.QUICK_SETTINGS) }) { onSystemPanel(ShadePanel.NOTIFICATIONS) }
            ControlCenter(Modifier.weight(1f, fill = false).then(panelModifier(ShadePanel.QUICK_SETTINGS)), status, ccControls, onCcControls, ccSize, ccOptions, wide, onClose, focusModes, activeFocus, onFocus, panelOpen = open, pageBack = pageBack, ccLayout = ccLayout, onCcLayout = onCcLayout) { onSystemPanel(ShadePanel.QUICK_SETTINGS) }
        }
        else {
            // Moving between the panels is a plain cross-fade in place: the container always spans the width, and each panel sits where it sits when it
            // is open on its own, so the outgoing panel does not jump to the incoming one's place. With the switch drawn once (hoistSwitch) it does not move.
            fun alignFor(which: ShadePanel): Alignment = when {
                shape == ShadeShape.CARD -> if (which == ShadePanel.NOTIFICATIONS) Alignment.TopStart else Alignment.TopEnd
                split -> Alignment.TopEnd
                which == ShadePanel.NOTIFICATIONS -> if (wide) Alignment.TopCenter else Alignment.TopStart
                wide && ccCentered -> Alignment.TopCenter
                wide && ccLeft -> Alignment.TopStart
                else -> Alignment.TopEnd
            }
            androidx.compose.animation.AnimatedContent(current, Modifier.fillMaxWidth().then(if (tall) Modifier.fillMaxHeight() else Modifier), transitionSpec = {
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200))
                    .togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)))
                    .using(androidx.compose.animation.SizeTransform(clip = false) { _, _ -> androidx.compose.animation.core.snap() })
            }, contentAlignment = Alignment.TopStart, label = "panel switch") { which ->
                Box(Modifier.fillMaxWidth().then(if (tall) Modifier.fillMaxHeight() else Modifier), contentAlignment = alignFor(which)) {
                    if (which == ShadePanel.NOTIFICATIONS) NotificationCenter(panelModifier(ShadePanel.NOTIFICATIONS), showClock && !split && shape != ShadeShape.CARD, grouped, sections,
                        listMax = if (shape == ShadeShape.CARD) cardListMax else Dp.Infinity, onClose = onClose, onSwitch = switcher.takeUnless { hoistSwitch }, reserveSwitchRow = hoistSwitch,
                        detail = detail, onSystemQuickSettings = { onSystemPanel(ShadePanel.QUICK_SETTINGS) }) { onSystemPanel(ShadePanel.NOTIFICATIONS) }
                    else ControlCenter(panelModifier(ShadePanel.QUICK_SETTINGS), status, ccControls, onCcControls, ccSize, ccOptions, wide, onClose, focusModes, activeFocus, onFocus, panelOpen = open, pageBack = pageBack, ccLayout = ccLayout, onCcLayout = onCcLayout,
                        onSwitch = switcher.takeUnless { hoistSwitch }, reserveSwitchRow = hoistSwitch) { onSystemPanel(ShadePanel.QUICK_SETTINGS) }
                }
            }
            if (hoistSwitch) Box(Modifier.align(Alignment.TopCenter).graphicsLayer {
                val p = progress(); alpha = (OverlayBack.unpreviewed(p) * 1.6f).coerceAtMost(1f) * PanelPull.fade(pull.offset, size.height); translationY = -lift * (1f - p) + pull.offset
            }.semantics { isTraversalGroup = true; traversalIndex = -1f }) { PanelSwitch(current, switcher!!) }
        }
    }
    }
}

// ---------------------------------------------------------------------------------------------
// Notification Center

@Composable
private fun NotificationCenter(modifier: Modifier, showClock: Boolean, grouped: Boolean, sections: Boolean = false, listMax: Dp = Dp.Infinity, onClose: () -> Unit, onSwitch: ((ShadePanel) -> Unit)? = null, reserveSwitchRow: Boolean = false,
    detail: Boolean = false, onSystemQuickSettings: () -> Unit = {}, onSystem: () -> Unit) {
    val context = LocalContext.current
    val items by IslandListenerService.notifications.collectAsStateWithLifecycle()
    val hasAccess = remember { IslandListenerService.hasAccess(context) }
    val tick by rememberMinuteTick()
    val now = displayNow(tick)
    val clock = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    var expandedGroup by remember { mutableStateOf<String?>(null) }
    // Axon-style: a row of app icons above the list; tap one to show just that app, tap again for all.
    var filterApp by remember { mutableStateOf<String?>(null) }
    val allGroups = remember(items) { items.groupBy { it.packageName }.values.sortedByDescending { g -> g.maxOf { it.postTime } } }
    LaunchedEffect(allGroups) { if (filterApp != null && allGroups.none { it.first().packageName == filterApp }) filterApp = null }
    var quietOpen by remember { mutableStateOf(false) }
    val sectioned = remember(items, filterApp, sections) { sectionNotifications(items.filter { filterApp == null || it.packageName == filterApp }, sections) }
    // With the preview pane, a tap picks the notification the pane shows; the newest one shown is picked until then, and when the picked one
    // goes (cleared, or filtered out by the app row) the pane moves to the newest one left rather than going blank.
    var pickedKey by remember { mutableStateOf<String?>(null) }
    val picked = if (!detail) null else sectioned.filter { (bucket, _) -> bucket != NotificationBucket.QUIET || quietOpen }
        .flatMap { (_, groups) -> groups.flatten() }.let { shown -> previewPick(shown, pickedKey) }

    // Keyboard for quick reply pushes the list up instead of covering it.
    Column(modifier.windowInsetsPadding(WindowInsets.imeAnimationTarget).testTag("notification-center"), verticalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
        if (onSwitch != null) PanelSwitch(ShadePanel.NOTIFICATIONS, onSwitch, Modifier.align(Alignment.CenterHorizontally))
        // The switch is drawn once above both panels: this row keeps its place so the clock does not move up under it.
        else if (reserveSwitchRow) Spacer(Modifier.height(PANEL_SWITCH_ROW_DP.dp))
        if (showClock && !detail) Column(Modifier.fillMaxWidth().padding(top = FolioSpace.TINY.dp, bottom = FolioSpace.SMALL.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))), color = Color.White.copy(alpha = .9f), fontSize = FolioType.BODY.sp,
                fontWeight = FontWeight.SemiBold)
            Text(now.format(DateTimeFormatter.ofPattern(clock)), color = Color.White, fontSize = 76.sp, fontWeight = FontWeight.Bold,
                lineHeight = 80.sp)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.TINY.dp), verticalAlignment = Alignment.CenterVertically) {
            // Beside a preview pane the window is wide, and the small clock leads the header in place of the 76 sp one above; when the
            // hoisted switch already sits in the top middle, keep the date under the time so it never crowds the switch pill.
            if (showClock && detail) Column(Modifier.weight(1f)) {
                Text(now.format(DateTimeFormatter.ofPattern(clock)), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold, lineHeight = 42.sp)
                Text(now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))), color = Color.White.copy(alpha = .9f), fontSize = FolioType.FOOTNOTE.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            else Text(stringResource(R.string.notification_center), color = Color.White, fontSize = FolioType.TITLE3.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            // Three different things wear a slider ("Tune") icon elsewhere: Folio's Control Center (panel switch, tile).
            // So the two buttons that open the phone's own panels use other icons: the gear is the phone's Quick Settings,
            // the phone glyph is the phone's own notification shade.
            GlassIconButton(Icons.Rounded.Settings, stringResource(R.string.phone_quick_settings)) { onClose(); onSystemQuickSettings() }
            Spacer(Modifier.width(8.dp))
            GlassIconButton(Icons.Rounded.PhoneAndroid, stringResource(R.string.android_notifications)) { onClose(); onSystem() }
            if (items.any { it.clearable }) {
                Spacer(Modifier.width(8.dp))
                // Like iPhone: the × turns into "Clear" and a second tap clears everything.
                var confirmClear by remember { mutableStateOf(false) }
                LaunchedEffect(confirmClear) { if (confirmClear) { kotlinx.coroutines.delay(3_000); confirmClear = false } }
                androidx.compose.animation.AnimatedContent(confirmClear, label = "clear-all") { confirm ->
                    if (confirm) PanelPill(stringResource(R.string.clear)) { confirmClear = false; IslandListenerService.dismissAll() }
                    else GlassIconButton(Icons.Rounded.Close, stringResource(R.string.clear_all)) { confirmClear = true }
                }
            }
        }
        when {
            !hasAccess -> EmptyNote(stringResource(R.string.allow_notification_access_to_see_notific), stringResource(R.string.allow)) {
                onClose(); runCatching { context.startActivity(IslandListenerService.accessSettingsIntent(context)) }
            }
            items.isEmpty() -> Text(stringResource(R.string.no_notifications), color = Color.White.copy(alpha = .6f), fontSize = FolioType.SUBHEAD.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = FolioSpace.XXL.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            else -> {
            val appRow: @Composable () -> Unit = {
            if (allGroups.size > 1 && LocalTintOptions.current.notificationAppRow) androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)) {
                items(allGroups, key = { it.first().packageName }) { group ->
                    val first = group.first()
                    val selected = filterApp == first.packageName
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp))
                        .background(if (selected) Color.White.copy(alpha = .3f) else Color.Transparent)
                        .clickable(onClickLabel = stringResource(R.string.show_notifications, first.appLabel)) { filterApp = if (selected) null else first.packageName }
                        .padding(5.dp)) {
                        first.icon?.let { Image(it.asImageBitmap(), first.appLabel, Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp))
                            .graphicsLayer { alpha = if (filterApp == null || selected) 1f else .45f }) }
                        if (group.size > 1) Box(Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp).heightIn(min = 16.dp).widthIn(min = 16.dp)
                            .background(FolioColors.RedLight, CircleShape).padding(horizontal = FolioSpace.TINY.dp), contentAlignment = Alignment.Center) {
                            Text("${group.size}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, lineHeight = 12.sp)
                        }
                    }
                }
            }
            }
            val list: @Composable ColumnScope.() -> Unit = {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            // The list takes what the column has left after the header and the app row, so it follows the window, not a fixed height.
            // What the list cannot scroll any further up pulls the panel away, so a swipe up on the notifications closes the panel the way one beside them does.
            val pullConnection = rememberPanelPullConnection(LocalPanelPull.current, onClose)
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = listMax).edgeFade(listState)
                .then(if (pullConnection != null) Modifier.nestedScroll(pullConnection) else Modifier), state = listState,
                verticalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                sectioned.forEach { (bucket, bucketGroups) ->
                  if (bucket == NotificationBucket.QUIET && !quietOpen) item("section-quiet") {
                    SectionLabel(stringResource(R.string.nc_section_quiet, bucketGroups.sumOf { it.size }),
                        settle().clickable(onClickLabel = stringResource(R.string.show_quiet_notifications)) { quietOpen = true })
                  } else {
                  // The main pile has no heading: it is what Notification Center always was.
                  if (bucket != null && bucket != NotificationBucket.MAIN) item("section-$bucket") {
                    SectionLabel(stringResource(when (bucket) {
                        NotificationBucket.NOW -> R.string.nc_section_now
                        NotificationBucket.CONVERSATIONS -> R.string.nc_section_conversations
                        else -> R.string.nc_section_quiet_open
                    }), settle()) { if (bucket == NotificationBucket.QUIET) PanelPill(stringResource(R.string.show_less)) { quietOpen = false } }
                  }
                bucketGroups.forEach { group ->
                    val pkg = group.first().packageName
                    val expanded = !grouped || expandedGroup == pkg || group.size == 1
                    if (expanded) {
                        if (group.size > 1) item("$bucket-$pkg-header") {
                            Row(settle().fillMaxWidth().padding(horizontal = FolioSpace.SNUG.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(group.first().appLabel, color = Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                if (sections && group.any { it.clearable }) { ClearAppPill(group.filter { it.clearable }); Spacer(Modifier.width(8.dp)) }
                                PanelPill(stringResource(R.string.show_less)) { expandedGroup = null }
                            }
                        }
                        items(group, key = { it.key }) { item -> NotificationCard(item, Modifier.animateItem(fadeInSpec = FolioMotion.spring(FolioMotion.Settle),
                            placementSpec = FolioMotion.spring(FolioMotion.Settle)), showMore = sections, picked = picked?.key == item.key, besidePane = detail) {
                            // Beside the pane a tap shows the notification there; Open in the pane is what opens the app, as on iPad Mail.
                            if (detail) pickedKey = item.key
                            else { onClose(); IslandListenerService.openNotification(context, item) }
                        } }
                    } else item(group.first().key) {
                        // Same key as the first card when expanded, so the stack slides apart instead of swapping.
                        StackedNotification(group, Modifier.animateItem(fadeInSpec = FolioMotion.spring(FolioMotion.Settle),
                            placementSpec = FolioMotion.spring(FolioMotion.Settle)), showMore = sections,
                            picked = picked != null && group.any { it.key == picked.key }, besidePane = detail) { expandedGroup = pkg }
                    }
                }
                  }
                }
            }
            }
            if (detail) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
                Column(Modifier.weight(LIST_DETAIL_LIST_SHARE).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) { appRow(); list() }
                NotificationPreviewPane(picked, Modifier.weight(1f - LIST_DETAIL_LIST_SHARE).fillMaxHeight().padding(bottom = 12.dp), onClose)
            } else { appRow(); list() }
            }
        }
    }
}

/**
 * The notification the preview pane shows, out of [shown] (in the order the list draws them): the one [pickedKey] names while it is still
 * there, otherwise the first, and null when there is none. Pure, so the fallback when the picked one is cleared can be tested.
 */
internal fun previewPick(shown: List<NotificationItem>, pickedKey: String?): NotificationItem? =
    shown.firstOrNull { it.key == pickedKey } ?: shown.firstOrNull()

/**
 * The open notification beside the list (the preview pane): its app, title, whole text and its actions, which the cards beside it leave
 * out. Moving to another notification cross-fades the pane's content (160 ms; with Reduce Motion a plain 120 ms fade; nothing slides).
 */
@Composable
private fun NotificationPreviewPane(item: NotificationItem?, modifier: Modifier, onClose: () -> Unit) {
    val context = LocalContext.current
    val reduce = LocalReduceMotion.current
    val overApps = LocalShadeOverApps.current
    val replyOverApp = LocalShadeReply.current
    val shape = RoundedCornerShape((FolioRadius.NOTIFICATION + 6).dp)
    Box(modifier.clip(shape).background(NotifGlass).border(FolioGlass.edge, shape).testTag("notification-preview")) {
        androidx.compose.animation.AnimatedContent(item, Modifier.fillMaxSize(), contentKey = { it?.key }, transitionSpec = {
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(if (reduce) 120 else 160))
                .togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(if (reduce) 120 else 150)))
                .using(androidx.compose.animation.SizeTransform(clip = false) { _, _ -> androidx.compose.animation.core.snap() })
        }, label = "notification preview") { shown ->
            if (shown == null) return@AnimatedContent
            Column(Modifier.fillMaxSize().padding(FolioSpace.LARGE.dp), verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    shown.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(28.dp).clip(RoundedCornerShape(7.dp))); Spacer(Modifier.width(FolioSpace.SMALL.dp)) }
                    Text(shown.appLabel, color = FolioGlass.secondary, fontSize = FolioType.FOOTNOTE.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(relativeTime(shown.postTime), color = FolioGlass.secondary, fontSize = FolioType.FOOTNOTE.sp)
                }
                Text(shown.title ?: shown.appLabel, color = Color.White, fontSize = FolioType.TITLE3.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() })
                // The whole text, line breaks kept: the card beside it shows at most four lines.
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    shown.text?.let { Text(it.trim(), color = Color.White.copy(alpha = .9f), fontSize = FolioType.BODY.sp, lineHeight = 22.sp) }
                }
                var replying by remember(shown.key) { mutableStateOf(false) }
                if (replying) QuickReplyField(shown.title, onSend = { IslandListenerService.reply(context, shown.key, it) }, onDone = { replying = false })
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                    MessageActionPill(stringResource(R.string.open)) { onClose(); IslandListenerService.openNotification(context, shown) }
                    // Typing needs a focusable window, so over another app Reply starts a small box to type in, as the card's does.
                    if (shown.canReply) MessageActionPill(stringResource(R.string.reply)) { if (overApps) (replyOverApp?.invoke(shown) ?: run { onClose(); IslandListenerService.openNotification(context, shown) }) else replying = true }
                    if (shown.canMarkRead) MessageActionPill(stringResource(R.string.mark_as_read)) { IslandListenerService.markRead(shown.key) }
                    if (shown.clearable) MessageActionPill(stringResource(R.string.clear)) { IslandListenerService.dismiss(shown.key) }
                }
            }
        }
    }
}

/**
 * An item that slides to its new place and fades in or out, the way a card does, so a section opening or closing is not a jump.
 * With Reduce Motion it only fades: it takes its new place at once (DYN-11).
 */
@Composable
private fun androidx.compose.foundation.lazy.LazyItemScope.settle(): Modifier {
    val reduce = LocalReduceMotion.current
    return Modifier.animateItem(fadeInSpec = FolioMotion.spring(FolioMotion.Settle),
        placementSpec = if (reduce) androidx.compose.animation.core.snap<androidx.compose.ui.unit.IntOffset>() else FolioMotion.spring<androidx.compose.ui.unit.IntOffset>(FolioMotion.Settle))
}

/** Clears one app's clearable notifications; like Clear all, the first tap asks and a second one within three seconds does it. */
@Composable
private fun ClearAppPill(clearable: List<NotificationItem>) {
    var confirm by remember(clearable.size) { mutableStateOf(false) }
    LaunchedEffect(confirm) { if (confirm) { kotlinx.coroutines.delay(3_000); confirm = false } }
    // The pill changes label in place, the way Clear all's does, instead of swapping with a jump; with Reduce Motion it only fades.
    val reduce = LocalReduceMotion.current
    androidx.compose.animation.AnimatedContent(confirm, label = "clear-app", transitionSpec = {
        androidx.compose.animation.ContentTransform(
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(if (reduce) FolioMotion.FADE_MS else 220, delayMillis = if (reduce) 0 else 90)) +
                (if (reduce) androidx.compose.animation.EnterTransition.None else androidx.compose.animation.scaleIn(FolioMotion.spring(FolioMotion.Quick), initialScale = .92f)),
            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90)))
    }) { asking ->
        if (asking) PanelPill(stringResource(R.string.clear_n_notifications, clearable.size)) { confirm = false; clearable.forEach { IslandListenerService.dismiss(it.key) } }
        else PanelPill(stringResource(R.string.clear)) { confirm = true }
    }
}

/** A section's name above its cards; with [trailing] a control at the end (Quiet's Show Less). A heading for TalkBack. */
@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = FolioSpace.SNUG.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Color.White.copy(alpha = .75f), fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke()
    }
}

@Composable
private fun StackedNotification(group: List<NotificationItem>, modifier: Modifier, showMore: Boolean = false, picked: Boolean = false, besidePane: Boolean = false, onExpand: () -> Unit) {
    Box(modifier.fillMaxWidth().padding(bottom = FolioSpace.MEDIUM.dp)) {
        // Two receding cards behind, like an iPhone notification stack.
        Box(Modifier.matchParentSize().padding(horizontal = FolioSpace.XXL.dp).offset(y = 14.dp).clip(RoundedCornerShape(FolioRadius.NOTIFICATION.dp)).background(FolioColors.SurfaceCard.copy(alpha = .45f)))
        Box(Modifier.matchParentSize().padding(horizontal = FolioSpace.MEDIUM.dp).offset(y = 7.dp).clip(RoundedCornerShape(FolioRadius.NOTIFICATION.dp)).background(FolioColors.SurfaceCard.copy(alpha = .66f)))
        key(group.first().key) { NotificationCard(group.first(), Modifier, extraCount = group.size - 1, showMore = showMore, picked = picked, besidePane = besidePane, onOpen = onExpand) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationCard(item: NotificationItem, modifier: Modifier, extraCount: Int = 0, showMore: Boolean = false, picked: Boolean = false, besidePane: Boolean = false, onOpen: () -> Unit) {
    // iOS: swipe left to reveal Options and Clear; a long swipe clears; tapping the card closes the buttons.
    val scope = rememberCoroutineScope()
    val swipe = remember(item.key) { androidx.compose.animation.core.Animatable(0f) }
    val density = LocalDensity.current
    // Over another app there is no Options menu (it is a dialog, which needs an activity), so only Clear is revealed.
    val overApps = LocalShadeOverApps.current
    val reveal = with(density) { (if (item.clearable && !overApps) 176.dp else 92.dp).toPx() }
    var options by remember(item.key) { mutableStateOf(false) }
    fun settle(to: Float) = scope.launch { swipe.animateTo(to, MotionSpeed.spring(.85f, Spring.StiffnessMediumLow)) }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(FolioRadius.NOTIFICATION.dp))) {
        val widthPx = constraints.maxWidth.toFloat()
        if (swipe.value < -1f) Row(Modifier.matchParentSize().padding(start = FolioSpace.MEDIUM.dp).graphicsLayer {
                // Buttons grow in as the card slides, like iOS, instead of popping in at full size.
                val t = (-swipe.value / reveal).coerceIn(0f, 1f)
                alpha = t; scaleX = .7f + .3f * t; scaleY = scaleX; transformOrigin = TransformOrigin(1f, .5f)
            }, horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically) {
            if (!overApps) SwipeAction(stringResource(R.string.options)) { settle(0f); options = true }
            if (item.clearable) SwipeAction(stringResource(R.string.clear)) { scope.launch { swipe.animateTo(-widthPx); IslandListenerService.dismiss(item.key) } }
        }
        Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(swipe.value.roundToInt(), 0) }
            .pointerInput(item.key, widthPx) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            item.clearable && swipe.value < -widthPx * .55f -> scope.launch { swipe.animateTo(-widthPx); IslandListenerService.dismiss(item.key) }
                            swipe.value < -reveal / 2 -> settle(-reveal)
                            else -> settle(0f)
                        }
                    },
                    onDragCancel = { settle(0f) },
                ) { change, amount -> change.consume(); scope.launch { swipe.snapTo((swipe.value + amount).coerceIn(-widthPx, 0f)) } }
            }) {
        val context = LocalContext.current
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        var cardBounds by remember { mutableStateOf(android.graphics.Rect()) }
        Box(Modifier.onGloballyPositioned { cardBounds = it.boundsInWindow().let { b -> android.graphics.Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt()) } }) {
        if (options) NotificationOptions(item, cardBounds) { options = false }
        val cardAccent = if (LocalTintOptions.current.notifications) rememberAccent(item.icon) else null
        val press = remember { MutableInteractionSource() }
        val pressed by press.collectIsPressedAsState()
        val pressScale by androidx.compose.animation.core.animateFloatAsState(if (pressed) .97f else 1f,
            FolioMotion.spring(FolioMotion.Control), label = "notification press")
        Row(Modifier.fillMaxWidth().graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clip(RoundedCornerShape(FolioRadius.NOTIFICATION.dp)).background(mixColor(NotifGlass, cardAccent, .32f))
            // The one the preview pane shows is lifted a step, as a selected row in iPad Mail is; TalkBack hears it as selected.
            .then(if (picked) Modifier.background(Color.White.copy(alpha = .12f)) else Modifier)
            .border(if (picked) androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = .45f)) else FolioGlass.edge, RoundedCornerShape(FolioRadius.NOTIFICATION.dp))
            .then(if (besidePane) Modifier.semantics { selected = picked } else Modifier)
            .combinedClickable(interactionSource = press, indication = null, onClick = { if (swipe.value < -1f) settle(0f) else onOpen() }, onLongClick = {
                if (!overApps) { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); options = true }
            }, onLongClickLabel = stringResource(R.string.notification_options))
            .padding(horizontal = FolioSpace.COMFY.dp, vertical = FolioSpace.MEDIUM.dp), verticalAlignment = Alignment.CenterVertically) {
            item.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(38.dp).clip(RoundedCornerShape(9.dp))) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title ?: item.appLabel, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = FolioType.SUBHEAD.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(relativeTime(item.postTime), color = FolioGlass.secondary, fontSize = FolioType.FOOTNOTE.sp)
                }
                item.text?.let { Text(it.lines().filter(String::isNotBlank).joinToString(" "), color = Color.White.copy(alpha = .88f),
                    fontSize = FolioType.COMPACT.sp, maxLines = if (extraCount > 0 || besidePane) 2 else 4, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp) }
                if (extraCount > 0) Text(pluralStringResource(R.plurals.more_from_app, extraCount, extraCount, item.appLabel),
                    color = FolioGlass.secondary, fontSize = FolioType.FOOTNOTE.sp, modifier = Modifier.padding(top = FolioSpace.HAIR.dp))
                // Beside the preview pane, Reply and Mark as Read are in the pane, so the row stays a preview.
                if (extraCount == 0 && !besidePane && (item.canReply || item.canMarkRead)) {
                    var replying by remember(item.key) { mutableStateOf(false) }
                    if (replying) QuickReplyField(item.title, Modifier.padding(top = FolioSpace.SMALL.dp),
                        onSend = { IslandListenerService.reply(context, item.key, it) }, onDone = { replying = false })
                    else Row(Modifier.padding(top = FolioSpace.SMALL.dp), horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                        // Typing needs a focusable window, so over another app Reply starts a small box to type in; without one, the conversation opens.
                        val replyOverApp = LocalShadeReply.current
                        if (item.canReply) MessageActionPill(stringResource(R.string.reply)) { if (overApps) (replyOverApp?.invoke(item) ?: onOpen()) else replying = true }
                        if (item.canMarkRead) MessageActionPill(stringResource(R.string.mark_as_read)) { IslandListenerService.markRead(item.key) }
                    }
                }
            }
            // The same menu as a long press or a swipe, always on screen (Settings › Notification sections).
            // Not over other apps: the menu is a dialog, which needs an activity's window (Options are on Home only there).
            if (showMore && !overApps) Box(Modifier.size(48.dp).clickable(onClickLabel = stringResource(R.string.notification_options),
                role = androidx.compose.ui.semantics.Role.Button) { options = true }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MoreHoriz, stringResource(R.string.notification_options), tint = FolioGlass.secondary)
            }
        }
        }
    }
}
}

@Composable
private fun SwipeAction(label: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxHeight().width(80.dp).clip(RoundedCornerShape(FolioRadius.NOTIFICATION.dp)).background(NotifGlass)
        .border(FolioGlass.edge, RoundedCornerShape(FolioRadius.NOTIFICATION.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = FolioType.COMPACT.sp, fontWeight = FontWeight.Medium)
    }
}

/** True while the panels are drawn over another app from the gestures service rather than on Home: no dialogs, no typing, no fold information. */
internal val LocalShadeOverApps = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Whether the window behind the panels is really blurred (the phone allows it and Settings › Blur is on); over an app without it the dim is stronger. */
internal val LocalShadeBackdropBlur = androidx.compose.runtime.staticCompositionLocalOf { true }

/** The shrunk, blurred picture of the app behind the panels, when one could be taken; null draws Folio's frosted glass instead. */
internal val LocalShadeBackdropImage = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.ui.graphics.ImageBitmap?> { null }

/** Over another app, what Reply does: starts a box to type in (the overlay cannot take the keyboard). Null on Home, where the card has its own field. */
internal val LocalShadeReply = androidx.compose.runtime.staticCompositionLocalOf<((NotificationItem) -> Unit)?> { null }

/** How Notification Center sits on a wide screen: filling it with the clock beside the list, a card under the corner pulled from, or beside Control Center. */
enum class NcLayout(@androidx.annotation.StringRes val label: Int) {
    FULL_SCREEN(R.string.nc_layout_full), CARD(R.string.nc_layout_card), SIDE_BY_SIDE(R.string.nc_layout_both)
}

/** Control Center size choice (the largest a small control can be). */
enum class PanelSize(@androidx.annotation.StringRes val label: Int, val cell: Dp) {
    SMALL(R.string.small, 50.dp), COMPACT(R.string.compact, 58.dp), STANDARD(R.string.standard, 66.dp), LARGE(R.string.large, 76.dp), EXTRA_LARGE(R.string.extra_large, 88.dp)
}

/**
 * How Control Center's tiles are drawn (McCal 25 Oct 2026, from the Lab's options A to C): Clear glass is see-through with a bright edge
 * (3.5:1 for white text on the Lab's backdrop, so best on a dark wallpaper), Frosted gives them dark smoky tiles with no plate behind (about 9:1), Solid is the
 * opaque look it had before (10.6:1).
 */
enum class CcLook(@androidx.annotation.StringRes val label: Int) { CLEAR(R.string.cc_look_clear), FROSTED(R.string.cc_look_frosted), SOLID(R.string.cc_look_solid) }

internal val LocalCcLook = androidx.compose.runtime.compositionLocalOf { CcLook.CLEAR }

/** What Clear glass uses over the backdrop at hand (its dim and its tiles), worked out from how bright the backdrop is; see [ccClearTone]. */
private val LocalCcTone = androidx.compose.runtime.staticCompositionLocalOf { CC_DEFAULT_TONE }

/** The page a swipe up goes back to from page [current] (in the order of the pages), or null on the first, where the same swipe closes the panel. */
internal fun ccBackTarget(current: Int): Int? = if (current > 0) current - 1 else null

/** What a swipe up in the empty space under the controls does: end editing, go back a page, or (on the first page, not editing) close the panel. */
internal sealed interface CcSwipeUp {
    object StopEditing : CcSwipeUp
    data class GoBack(val page: Int) : CcSwipeUp
    object Close : CcSwipeUp
}

internal fun ccSwipeUp(editing: Boolean, current: Int): CcSwipeUp =
    if (editing) CcSwipeUp.StopEditing else ccBackTarget(current)?.let { CcSwipeUp.GoBack(it) } ?: CcSwipeUp.Close

/**
 * Lets the scrim behind the panels ask Control Center to go back a page before it closes the panel: a swipe up that starts in the
 * empty space under the controls lands on the scrim, not on the pager, and used to close the panel from the Media and Connectivity pages.
 */
internal class CcPageBack {
    var back: (() -> Boolean)? = null
    /** True if Control Center took the swipe by going back a page. */
    fun goBack(): Boolean = back?.invoke() == true
}

/** One page of Control Center: Controls pages are numbered from 0 when the controls need more than one. */
internal data class CcPageSpec(val page: CcPage, val index: Int)

/** The pages in order, as the pager and the strip both read them: [controlsPages] Controls pages, then [extra] (see [ccExtraPages]). */
internal fun ccPageSpecs(controlsPages: Int, extra: List<CcPage>): List<CcPageSpec> = List(controlsPages) { CcPageSpec(CcPage.CONTROLS, it) } + extra.map { CcPageSpec(it, 0) }

private val CcLook.tile: Color
    @Composable get() = when (this) {
        CcLook.CLEAR -> {
            // The tile's black and white fills as one color: black first, then white over it (see CcGlassTone).
            val t = LocalCcTone.current
            val alpha = 1f - (1f - t.tileBlack) * (1f - t.tileWhite)
            val gray = if (alpha > 0f) t.tileWhite / alpha else 1f
            Color(gray, gray, gray, alpha)
        }
        CcLook.FROSTED -> FolioGlass.frostedTile
        CcLook.SOLID -> FolioGlass.module
    }
/** A round toggle at rest: Solid keeps the lighter disc it always had on its dark modules. */
private val CcLook.toggleOff: Color @Composable get() = if (this == CcLook.SOLID) Color.White.copy(alpha = .16f) else tile
private val CcLook.edge: androidx.compose.foundation.BorderStroke get() = when (this) {
    CcLook.CLEAR -> androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color.White.copy(alpha = .42f), Color.White.copy(alpha = .12f))))
    CcLook.FROSTED -> androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .14f))
    CcLook.SOLID -> FolioGlass.edge
}

/** The width of "00:00" in bold, in em, with air (measured about 2.72 at 112 sp): the time is sized by the room it has. */
private const val SPLIT_CLOCK_EM_WIDTH = 2.95f

/** iPad-style clock and date for the split (unfolded) Notification Center. */
@Composable
private fun SplitClock(modifier: Modifier) {
    val context = LocalContext.current
    val tick by rememberMinuteTick()
    val now = displayNow(tick)
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    // The time is 112 sp where there is room and shrinks with the width it is given (a phone on its side), so it never runs into the list.
    BoxWithConstraints(modifier) {
        val room = (maxWidth - FolioSpace.XXL.dp).value
        val timeSize = minOf(112f, room / SPLIT_CLOCK_EM_WIDTH).coerceAtLeast(48f)
        Column(Modifier.padding(start = FolioSpace.XXL.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))), color = Color.White.copy(alpha = .9f), fontSize = minOf(22f, timeSize / 4f + 8f).sp, fontWeight = FontWeight.SemiBold)
            Text(now.format(DateTimeFormatter.ofPattern(pattern)), color = Color.White, fontSize = timeSize.sp, fontWeight = FontWeight.Bold, lineHeight = (timeSize * 1.04f).sp)
        }
    }
}

/**
 * iPhone-style options for a long-pressed notification: the card lifts where it is over a blurred, dimmed
 * background and a compact menu appears under it (above it when there's no room).
 */
@Composable
private fun NotificationOptions(item: NotificationItem, bounds: android.graphics.Rect, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, FolioMotion.spring(FolioMotion.Appear)) }
    fun act(action: () -> Unit) { onDismiss(); action() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = androidx.compose.ui.platform.LocalView.current
        LaunchedEffect(view) {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { w ->
                w.setDimAmount(0f)
                // Same full-screen window as the launcher, so the lifted card lines up exactly with the original.
                androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).apply {
                    systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                }
                w.attributes = w.attributes.apply { layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS }
                w.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND); w.attributes = w.attributes.apply { blurBehindRadius = 40 }
            }
        }
        var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionOnScreen() }
            .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }.background(Color.Black.copy(alpha = .35f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)) {
            val screenH = with(density) { maxHeight.toPx() }
            val top = bounds.top - origin.y
            val left = bounds.left - origin.x
            // The card stays exactly where the notification is; the menu goes under it, or above when it wouldn't fit.
            val gap = with(density) { 10.dp.toPx() }
            var cardH by remember { mutableIntStateOf(bounds.height()) }
            var menuH by remember { mutableIntStateOf(with(density) { 230.dp.roundToPx() }) }
            val below = top + cardH + gap + menuH + with(density) { 16.dp.toPx() } < screenH
            Box(Modifier.fillMaxSize()) {
                val card: @Composable () -> Unit = {
                    Row(Modifier.offset { androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()) }
                        .width(with(density) { bounds.width().toDp() }).heightIn(min = with(density) { bounds.height().toDp() })
                        .onSizeChanged { cardH = it.height }
                        .graphicsLayer { val s = 1f + .03f * appear.value; scaleX = s; scaleY = s }
                        .clip(RoundedCornerShape(FolioRadius.NOTIFICATION.dp)).background(FolioColors.SheetSurface).padding(horizontal = FolioSpace.COMFY.dp, vertical = FolioSpace.MEDIUM.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        item.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(38.dp).clip(RoundedCornerShape(9.dp))) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title ?: item.appLabel, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = FolioType.SUBHEAD.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            item.text?.let { Text(it, color = Color.White.copy(alpha = .85f), fontSize = FolioType.COMPACT.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
                val menu: @Composable () -> Unit = {
                    Column(Modifier.offset { androidx.compose.ui.unit.IntOffset(left.toInt(),
                            (if (below) top + cardH + gap else top - gap - menuH).toInt().coerceAtLeast(0)) }
                        .width(minOf(280.dp, with(density) { bounds.width().toDp() })).onSizeChanged { menuH = it.height }
                        .graphicsLayer { val s = .8f + .2f * appear.value; scaleX = s; scaleY = s
                            transformOrigin = TransformOrigin(0f, if (below) 0f else 1f) }
                        .clip(RoundedCornerShape(FolioRadius.GROUP.dp)).background(FolioColors.PopoverSurface.copy(alpha = .97f)).border(FolioGlass.edge, RoundedCornerShape(FolioRadius.GROUP.dp))
                        .clickable(remember { MutableInteractionSource() }, null) {}) {
                        MenuRow(stringResource(R.string.open), Icons.Rounded.OpenInNew) { act { IslandListenerService.openNotification(context, item) } }
                        if (item.clearable) {
                            MenuDivider()
                            MenuRow(stringResource(R.string.snooze_for_1_hour), Icons.Rounded.Snooze) { act { IslandListenerService.snooze(item.key, 60 * 60_000L) } }
                            MenuDivider()
                            MenuRow(stringResource(R.string.snooze_until_tomorrow), Icons.Rounded.Bedtime) { act {
                                val morning = java.time.LocalDate.now().plusDays(1).atTime(8, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                IslandListenerService.snooze(item.key, (morning - System.currentTimeMillis()).coerceAtLeast(60_000L))
                            } }
                        }
                        MenuDivider()
                        MenuRow(stringResource(R.string.notification_settings), Icons.Rounded.Tune) { act {
                            val intent = item.channelId?.let { MessageChannel(item.packageName, item.appLabel, it, null, 0).settingsIntent() }
                                ?: Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, item.packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        } }
                        if (item.clearable) { MenuDivider(); MenuRow(stringResource(R.string.clear), Icons.Rounded.Close, destructive = true) { act { IslandListenerService.dismiss(item.key) } } }
                    }
                }
                card(); menu()
            }
        }
    }
}

@Composable
private fun relativeTime(time: Long): String {
    val minutes = relativeMinutes(time, System.currentTimeMillis())
    return when {
        minutes < 1 -> stringResource(R.string.time_now)
        minutes < 60 -> pluralStringResource(R.plurals.minutes_ago, minutes.toInt(), minutes)
        minutes < 24 * 60 -> (minutes / 60).let { pluralStringResource(R.plurals.hours_ago, it.toInt(), it) }
        else -> (minutes / (24 * 60)).let { pluralStringResource(R.plurals.days_ago, it.toInt(), it) }
    }
}

/**
 * How long ago a notification was posted, in whole minutes, never negative: a phone whose clock has just been
 * corrected can hand back a notification posted in the future, and "-3m ago" is not a thing to show anybody.
 */
internal fun relativeMinutes(time: Long, now: Long): Long = ((now - time) / 60_000).coerceAtLeast(0L)

// ---------------------------------------------------------------------------------------------
// Control Center: a strict 4-column grid; every module is a whole number of cells.

/** Small (1×1) controls the user can add, remove and order. */
internal enum class CcControl(@androidx.annotation.StringRes val label: Int) {
    ROTATION(R.string.rotation_lock), DND(R.string.do_not_disturb), FLASHLIGHT(R.string.flashlight), CAMERA(R.string.camera), TIMER(R.string.timer),
    ALARM(R.string.alarm), CALCULATOR(R.string.calculator), SCREENSHOT(R.string.screenshot), LOCK(R.string.lock_screen), WALLET(R.string.wallet),
    NOTES(R.string.notes), HOTSPOT(R.string.hotspot), BATTERY_SAVER(R.string.battery_saver), SETTINGS(R.string.settings), SYSTEM(R.string.android_quick_settings),
    // Apple's other tiles that open a system page or app without any special permission.
    CAST(R.string.screen_mirroring), DISPLAY(R.string.display), ACCESSIBILITY(R.string.accessibility), RECORDER(R.string.recorder), LOCATION(R.string.location), VPN(R.string.vpn),
    // Tiles that show a value and step through it on a tap (see [CcValue]), or switch something Android keeps behind a permission (see [DeviceControls]).
    SOUND_MODE(R.string.sound_mode), SCREEN_TIMEOUT(R.string.screen_timeout), NIGHT_LIGHT(R.string.night_light);

    companion object {
        val DEFAULTS = listOf(ROTATION, DND, FLASHLIGHT, CAMERA, TIMER, CALCULATOR, SCREENSHOT, SYSTEM).map { it.name }
        val WALLETS = listOf("com.google.android.apps.walletnfcrel", "com.samsung.android.spay")
        val NOTE_APPS = listOf("com.samsung.android.app.notes", "com.google.android.keep")
    }
}

private fun CcControl.icon(): ImageVector = when (this) {
    CcControl.ROTATION -> Icons.Rounded.ScreenLockRotation
    CcControl.DND -> Icons.Rounded.DarkMode
    CcControl.FLASHLIGHT -> Icons.Rounded.FlashlightOn
    CcControl.CAMERA -> Icons.Rounded.PhotoCamera
    CcControl.TIMER -> Icons.Rounded.Timer
    CcControl.ALARM -> Icons.Rounded.Alarm
    CcControl.CALCULATOR -> Icons.Rounded.Calculate
    CcControl.SCREENSHOT -> Icons.Rounded.Screenshot
    CcControl.LOCK -> Icons.Rounded.Lock
    CcControl.WALLET -> Icons.Rounded.Wallet
    CcControl.NOTES -> Icons.Rounded.EditNote
    CcControl.HOTSPOT -> Icons.Rounded.WifiTethering
    CcControl.BATTERY_SAVER -> Icons.Rounded.BatterySaver
    CcControl.SETTINGS -> Icons.Rounded.Settings
    CcControl.SYSTEM -> Icons.Rounded.Tune
    CcControl.CAST -> Icons.Rounded.Cast
    CcControl.DISPLAY -> Icons.Rounded.BrightnessMedium
    CcControl.ACCESSIBILITY -> Icons.Rounded.Accessibility
    CcControl.RECORDER -> Icons.Rounded.Mic
    CcControl.LOCATION -> Icons.Rounded.LocationOn
    CcControl.VPN -> Icons.Rounded.VpnKey
    CcControl.SOUND_MODE -> Icons.Rounded.NotificationsActive
    CcControl.SCREEN_TIMEOUT -> Icons.Rounded.Timelapse
    CcControl.NIGHT_LIGHT -> Icons.Rounded.WbTwilight
}

/**
 * What a control shows beyond its icon and name: a [caption] for the value it holds (Ring, 30s) and an [icon] when the picture follows
 * that value. Controls that only do one thing have none ([CcValue.NONE]). Drawn under the icon on a one-cell tile and beside the name on a wide one.
 */
internal data class CcValue(val icon: ImageVector? = null, val caption: String? = null) {
    companion object { val NONE = CcValue() }
}

@Composable
private fun ControlCenter(modifier: Modifier, status: DeviceStatus, controlNames: List<String>, onControls: (List<String>) -> Unit,
    size: PanelSize, ccOptions: CcOptions, wide: Boolean, onClose: () -> Unit,
    focusModes: List<FocusMode>, activeFocus: String?, onFocus: (String?) -> Unit, panelOpen: Boolean = true, pageBack: CcPageBack? = null, onSwitch: ((ShadePanel) -> Unit)? = null, reserveSwitchRow: Boolean = false, ccLayout: CcSavedLayout? = null, onCcLayout: (CcSavedLayout?) -> Unit = {}, onSystem: () -> Unit) {
    val context = LocalContext.current
    val controls = remember { DeviceControls(context) }
    DisposableEffect(controls) { controls.start(); onDispose { controls.stop() } }
    val media = (IslandListenerService.activity.collectAsStateWithLifecycle().value as? IslandActivity.Media)
    val open = { intent: Intent -> onClose(); runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; Unit }
    // Starts the first intent a phone has an app for, and says so when none opens: a tap that silently does nothing reads as broken (#554).
    // Starting and catching is the check, because `resolveActivity` can't see another app's activities without a <queries> entry.
    val openFirst = { label: Int, intents: List<Intent> ->
        onClose()
        val opened = intents.any { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
        if (!opened) IslandEvents.notice(context, context.getString(R.string.folio_couldn_t_open_1_s_on_this_phone, context.getString(label)))
    }
    fun launchFirst(packages: List<String>) = packages.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
    fun available(control: CcControl) = when (control) {
        CcControl.WALLET -> launchFirst(CcControl.WALLETS) != null
        CcControl.NOTES -> launchFirst(CcControl.NOTE_APPS) != null
        CcControl.FLASHLIGHT -> controls.hasTorch
        else -> true
    }
    val chosen = remember(controlNames) { controlNames.mapNotNull { n -> CcControl.entries.firstOrNull { it.name == n } } }.filter(::available)
    val edit = remember { HomeEditMode() }
    // Moving controls (FeatureGate.CC_REORDER): while it is shut, edit mode only adds and removes, as before.
    val reorder = if (FeatureGate.CC_REORDER.isOpen(context)) remember { CcReorderState() } else null
    // What this edit session changed, so Undo can take it back a step at a time (a drag, a resize, a remove, an add, a reset). It ends with the session.
    var history by remember { mutableStateOf(CcEditHistory()) }
    val editView = androidx.compose.ui.platform.LocalView.current
    val record = { history = history.push(CcEditState(controlNames, ccLayout)) }
    val changeControls: (List<String>) -> Unit = { next -> record(); onControls(next) }
    val changeLayout: (List<CcSlot>, Int, Boolean) -> Unit = { next, columns, focus -> record(); onCcLayout(CcSavedLayout(columns, next, focus)) }
    val undoEdit = {
        history.pop()?.let { (before, rest) ->
            history = rest
            if (before.controls != controlNames) onControls(before.controls)
            if (before.layout != ccLayout) onCcLayout(before.layout)
            editView.announceForAccessibility(context.getString(R.string.cc_undone))
        }
        Unit
    }
    LaunchedEffect(edit.active) { history = CcEditHistory() }
    val moveControl: (String, String) -> Unit = { moving, target -> changeControls(ccMoveControl(controlNames, moving, target)) }
    SafeBackHandler(edit.active) { edit.stop() }
    // Pages that would repeat the first are left out, Now Playing is a slim row while nothing plays and the battery ring stands apart from the dots (FeatureGate.CC_PAGES_DEDUPE).
    val dedupe = remember { FeatureGate.CC_PAGES_DEDUPE.isOpen(context) }
    // The deeper settings (FeatureGate.CC_SETTINGS_V2): until the gate opens the options are the defaults, so nothing below changes.
    val v2 = remember { FeatureGate.CC_SETTINGS_V2.isOpen(context) }
    val options = if (v2) ccOptions else CcOptions()
    // "Edit controls" in Settings opens the panel in edit mode, on the first page.
    val editRequested = remember { v2 && CcEditRequest.take() }
    val closeScope = rememberCoroutineScope()

    fun isOn(control: CcControl) = when (control) {
        CcControl.ROTATION -> controls.rotationLocked
        CcControl.DND -> controls.dndOn
        CcControl.FLASHLIGHT -> controls.torchOn
        CcControl.BATTERY_SAVER -> controls.powerSaveOn
        CcControl.NIGHT_LIGHT -> controls.nightLightOn
        CcControl.SOUND_MODE -> controls.ringer != AudioManager.RINGER_MODE_NORMAL
        else -> false
    }
    // The value a tile holds, read while composing so it follows the phone (the ringer and the timeout are re-read when they change).
    fun valueOf(control: CcControl): CcValue = when (control) {
        CcControl.SOUND_MODE -> when (controls.ringer) {
            AudioManager.RINGER_MODE_SILENT -> CcValue(Icons.Rounded.NotificationsOff, context.getString(R.string.ringer_silent))
            AudioManager.RINGER_MODE_VIBRATE -> CcValue(Icons.Rounded.Vibration, context.getString(R.string.ringer_vibrate))
            else -> CcValue(Icons.Rounded.NotificationsActive, context.getString(R.string.ringer_ring))
        }
        CcControl.SCREEN_TIMEOUT -> CcValue(null, ccTimeoutLabel(controls.screenTimeoutMs).let { (n, minutes) ->
            if (n == 0) "" else if (n < 0) context.getString(R.string.timeout_never) else context.getString(if (minutes) R.string.timeout_minutes else R.string.timeout_seconds, n) })
        else -> CcValue.NONE
    }
    fun accent(control: CcControl) = when (control) {
        CcControl.ROTATION -> AccentRed
        CcControl.DND -> AccentPurple
        CcControl.BATTERY_SAVER -> AccentOrange
        CcControl.SOUND_MODE -> AccentRed
        CcControl.NIGHT_LIGHT -> AccentOrange
        else -> Color.White
    }
    fun runNow(control: CcControl) {
        when (control) {
            CcControl.ROTATION -> if (!controls.toggleRotationLock()) open(controls.writeSettingsIntent())
            CcControl.DND -> if (!controls.toggleDnd()) open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            CcControl.FLASHLIGHT -> controls.toggleTorch()
            CcControl.CAMERA -> open(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            CcControl.TIMER -> openFirst(R.string.timer, listOf(Intent(AlarmClock.ACTION_SHOW_TIMERS), Intent(AlarmClock.ACTION_SHOW_ALARMS)))
            CcControl.ALARM -> openFirst(R.string.alarm, listOf(Intent(AlarmClock.ACTION_SHOW_ALARMS)))
            CcControl.CALCULATOR -> {
                val calc = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
                val target = if (calc.resolveActivity(context.packageManager) != null) calc else {
                    listOf("com.google.android.calculator", "com.sec.android.app.popupcalculator", "com.android.calculator2")
                        .firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) } ?: calc
                }
                open(target)
            }
            // Close first so the panel isn't in the screenshot.
            // (A main-thread post, not a composition scope: the panel leaves composition as it closes.)
            CcControl.SCREENSHOT -> { onClose(); android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!SystemShadeAccessibilityService.global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT))
                    IslandEvents.notice(context, context.getString(R.string.needs_accessibility_service)) }, 450) }
            CcControl.LOCK -> { onClose(); if (!SystemShadeAccessibilityService.global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) onSystem() }
            CcControl.WALLET -> launchFirst(CcControl.WALLETS)?.let(open)
            CcControl.NOTES -> launchFirst(CcControl.NOTE_APPS)?.let(open)
            CcControl.HOTSPOT -> open(Intent("android.settings.TETHER_SETTINGS").takeIf { it.resolveActivity(context.packageManager) != null }
                ?: Intent(Settings.ACTION_WIRELESS_SETTINGS))
            CcControl.BATTERY_SAVER -> open(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
            CcControl.SETTINGS -> open(Intent(Settings.ACTION_SETTINGS))
            CcControl.SYSTEM -> { onClose(); onSystem() }
            CcControl.CAST -> openFirst(R.string.screen_mirroring, listOf(Intent(Settings.ACTION_CAST_SETTINGS)))
            CcControl.DISPLAY -> open(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            CcControl.ACCESSIBILITY -> open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            CcControl.RECORDER -> openFirst(R.string.recorder, listOf(Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)))
            CcControl.LOCATION -> open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            CcControl.VPN -> open(Intent(Settings.ACTION_VPN_SETTINGS))
            CcControl.SOUND_MODE -> if (!controls.cycleRinger()) open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            CcControl.SCREEN_TIMEOUT -> if (!controls.cycleScreenTimeout()) open(controls.writeSettingsIntent())
            // Switches Night Light itself when Folio has been given the secure-settings permission (one adb grant, as the System Bridge does); otherwise opens its page.
            CcControl.NIGHT_LIGHT -> if (!controls.toggleNightLight()) openFirst(R.string.night_light, listOf(Intent("android.settings.NIGHT_DISPLAY_SETTINGS"), Intent(Settings.ACTION_DISPLAY_SETTINGS)))
        }
    }

    fun run(control: CcControl) {
        runNow(control)
        // After the tile's own press and haptic, not instead of them.
        if (ccClosesAfterTap(control, options)) closeScope.launch { delay(CC_CLOSE_AFTER_MS); onClose() }
    }

    var panelBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // The gallery card, when it has to stand low, asks the panel to shrink out of its way; it glides there and comes back (a snap with reduced motion).
    var galleryFit by remember { mutableFloatStateOf(1f) }
    val fitNow by androidx.compose.animation.core.animateFloatAsState(galleryFit, if (LocalReduceMotion.current) androidx.compose.animation.core.snap() else FolioMotion.spring<Float>(FolioMotion.Glide), label = "gallery fit") // english-only
    // The editor on a wide window (FeatureGate.CC_TWO_PANE): the panel on the left and "Add a Control" as a list beside it (CcTwoPane.kt) instead of the floating card.
    val twoPaneGate = remember { FeatureGate.CC_TWO_PANE.isOpen(context) }
    val addFeed = remember { CcAddPaneFeed() }
    // The panel keeps the columns it has outside the editor, so the pane must be wide enough for them at the touch size; if not, the editor stays one pane.
    val keepColumnsMin = { containerDp: Float, heightDp: Float? ->
        val cell = (size.cell + if (wide) 8.dp else 0.dp).value
        val columns = controlCenterGrid(containerDp, heightDp, chosen.size, focusModes.isNotEmpty(), cell, FolioTouch.MIN.toFloat(), (FolioSpace.LARGE * 2).toFloat(), options.rail).columns
        controlCenterMinPanelDp(columns, cell, FolioTouch.MIN.toFloat(), (FolioSpace.LARGE * 2).toFloat())
    }
    CcTwoPaneHost(modifier, edit.active && twoPaneGate, addFeed, keepColumnsMin) { twoPane, panelSize ->
    BoxWithConstraints(panelSize.testTag("control-center").semantics { isTraversalGroup = true; traversalIndex = 0f }
        .graphicsLayer { scaleX = fitNow; scaleY = fitNow; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 0f) }.onGloballyPositioned { panelBounds = it.boundsInWindow() }) {
        val sizeCell = size.cell + if (wide) 8.dp else 0.dp
        val tall = if (maxHeight == Dp.Infinity) null else maxHeight.value
        // Fills the window: 4, 6 or 8 columns by the width, cells near the size chosen; the page strip at the edge on a wide panel, under the grid on a narrow one.
        val gridFor = { minColumns: Int -> controlCenterGrid(maxWidth.value, tall, chosen.size, focusModes.isNotEmpty(), sizeCell.value, FolioTouch.MIN.toFloat(), (FolioSpace.LARGE * 2).toFloat(), options.rail, minColumns) }
        // In the editor's two panes the panel keeps the columns it has outside the editor, so the layout being edited is the one that is saved, as long as the tiles stay at the touch size.
        val keptColumns = twoPane?.let { controlCenterGrid(it.containerDp, tall, chosen.size, focusModes.isNotEmpty(), sizeCell.value, FolioTouch.MIN.toFloat(), (FolioSpace.LARGE * 2).toFloat(), options.rail).columns }
        val grid = keptColumns?.let(gridFor)?.takeIf { it.cellDp >= FolioTouch.MIN } ?: gridFor(4)
        val gap = grid.gapDp.dp
        val cols = grid.columns
        val cell = grid.cellDp.dp
        fun span(n: Int): Dp = cell * n + gap * (n - 1)
        val gridWidth = span(cols)
        val focusOn = focusModes.isNotEmpty()
        // What the header and the strip take; the rest is for the pages. Controls that do not fit go on another page, never into a scroll (PNL-1).
        // The switch, drawn once above both panels, has a row of its own above this header (reserveSwitchRow), which the pages give up room for.
        val chrome = FolioTouch.MIN.dp + gap + CC_PANEL_PAD_DP.dp * 2 + (if (grid.stripAtEdge || grid.stripInHeader) 0.dp else grid.stripDp.dp) +
            (if (reserveSwitchRow) PANEL_SWITCH_ROW_DP.dp + gap else 0.dp)
        val pagesAvailable = if (maxHeight == Dp.Infinity) null else (maxHeight - chrome).coerceAtLeast(cell)
        // With the Media page switched off the sliders move onto a Controls page instead of vanishing (they are never behind a preference).
        val plan = remember(cols, cell, gap, pagesAvailable, chosen.size, focusOn, options.mediaPage) { ccPlanControls(cols, cell.value, gap.value, pagesAvailable?.value, chosen.size, focusOn, options.mediaPage) }
        // A page is shown when the first cannot hold what it holds (the automatic hiding) and its switch is on.
        val neededPages = remember(plan, pagesAvailable, cell, gap, focusOn, dedupe) { ccExtraPages(plan, pagesAvailable?.value, cell.value, gap.value, focusOn, dedupe) }
        // Pages the first one has to give up (it ran out of room) and whose switch is on; the editor's grid needs those to be none.
        val neededShown = remember(neededPages, options.mediaPage, options.connectivityPage) { ccShownExtraPages(neededPages, options) }
        // Beside the grid the rail always lists the Media and Connectivity pages, as the Duo does.
        val extraPages = remember(neededPages, options.mediaPage, options.connectivityPage, grid.stripAtEdge) { ccRailExtraPages(neededPages, options, grid.stripAtEdge) }
        // The Control Center editor (FeatureGate.CC_EDITOR): when the modules fit, the Controls are places on a grid that can be rearranged, and a
        // window too short for all of it splits the grid into pages by rows (PNL-1: never a scroll); a held control dwelling at a page's edge is carried
        // to the next page. A window that cannot hold the modules at all (they moved to their own pages) keeps the ordered list.
        val editorOn = remember { FeatureGate.CC_EDITOR.isOpen(context) }
        val slotMode = editorOn && neededShown.isEmpty() && plan.focusOnFirst && plan.slidersOnFirst
        // The editor's places, and how many rows a page holds: the window's rows less the "Add a Control" row, kept free on every page so the pages
        // break in the same places whether or not the editor is open. The editor adds one empty row to drop into, which opens a page when the last is full.
        val slots = remember(ccLayout, cols, chosen, focusOn) { ccLayoutFor(ccLayout, cols, chosen.map { it.name }, focusOn) }
        val addRowHeight = if (slotMode) FolioTouch.MIN.dp + gap else 0.dp
        val slotPerPage = if (pagesAvailable == null) 40 else maxOf(3, minOf(ccRowsForWindow(LocalConfiguration.current.screenHeightDp.toFloat()), ccRowsThatFit((pagesAvailable - addRowHeight).value, cell.value, gap.value)))
        // Pages come only from overflow: the empty row never opens a page of its own, so a full last page has no spare row (as iOS, which has no empty page).
        val slotUsed = if (slotMode) maxOf(ccRowsUsed(slots), ccRowsUsed(ccDefaultLayout(cols, chosen.map { it.name }, focusOn))).coerceAtLeast(1) else 0
        val slotSpare = slotMode && edit.active && slotUsed - ccPageStarts(slots, slotPerPage, slotUsed).last() < slotPerPage
        val slotRows = slotUsed + (if (slotSpare) 1 else 0)
        val slotStarts = remember(slots, slotPerPage, slotRows, slotMode) { if (slotMode) ccPageStarts(slots, slotPerPage, slotRows) else listOf(0) }
        fun slotEnd(index: Int) = slotStarts.getOrNull(index + 1) ?: maxOf(slotRows, slotStarts[index] + 1)
        val pages = remember(plan, extraPages, slotMode, slotStarts.size) { ccPageSpecs(if (slotMode) slotStarts.size else plan.perPage.size, extraPages) }
        // The pager runs forward but its pages are in reverse (Controls at the last index): the first page is anchored at the bottom, the next ones come down from above,
        // and a swipe up past the first page has nowhere to go, so it closes the panel. (reverseLayout stopped the drag from following the finger.)
        // It opens on the first page, or on the page it was last left on (Open on), and always on the first when Settings asked for edit mode.
        val startPosition = remember { if (editRequested) 0 else ccStartPage(options.openOn, CcLastPage.page, pages) }
        val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = pages.lastIndex - startPosition) { pages.size }
        val firstIndex = pages.lastIndex
        val current = firstIndex - pager.currentPage // the page shown, in the order of [pages]
        // A different set of pages (another window, a fold, a font size) starts again on the first. With fewer pages shown the same count can hold other pages, so the pages themselves are the key.
        // (Not the first time: the pager was built on the page it should open on.)
        // (In the editor the number of Controls pages follows the layout, so that count is not part of the key: see the effect below.)
        val pagesSettled = remember { booleanArrayOf(false) }
        LaunchedEffect(if (slotMode) pages.filter { it.page != CcPage.CONTROLS } else if (dedupe || v2) pages else pages.size, slotMode) {
            if (!pagesSettled[0]) { pagesSettled[0] = true; return@LaunchedEffect }
            if (pager.currentPage != pages.lastIndex) pager.scrollToPage(pages.lastIndex)
        }
        // A move, an Undo or an add can open or close a Controls page in the editor. The pager counts its pages from the other end, so the page in view
        // is put back (never past the last Controls page), and the editing is not ended by the page number being read on the way.
        val reduceMotionNow = LocalReduceMotion.current
        val pageCountSeen = remember { intArrayOf(pages.size) }
        val logicalSeen = remember { intArrayOf(0) }
        SideEffect { if (pageCountSeen[0] == pages.size) logicalSeen[0] = firstIndex - pager.currentPage }
        LaunchedEffect(pages.size) {
            if (pageCountSeen[0] == pages.size) return@LaunchedEffect
            pageCountSeen[0] = pages.size
            if (!slotMode) return@LaunchedEffect
            val lastControls = pages.count { it.page == CcPage.CONTROLS } - 1
            val want = if (edit.active) minOf(logicalSeen[0], lastControls) else 0
            val index = pages.lastIndex - want
            // The same page kept in view is only the pager's count re-anchoring, which must not be seen; a page that is gone (an Undo, a move out of it) slides back to the one before.
            if (pager.currentPage != index) { if (want == logicalSeen[0] || reduceMotionNow) pager.scrollToPage(index) else pager.animateScrollToPage(index, animationSpec = FolioMotion.spring(FolioMotion.Sheet)) }
        }
        // The page in view is remembered for Open on: Last page used.
        LaunchedEffect(current, pages) { if (v2) CcLastPage.page = pages.getOrNull(current)?.page }
        LaunchedEffect(Unit) { if (editRequested) edit.start() }
        val pageScope = rememberCoroutineScope()
        val reduceMotion = LocalReduceMotion.current
        // Editing carries across the Controls pages (the gallery to add from is on the last); it ends on Media and Connectivity.
        LaunchedEffect(current, pages.getOrNull(current)?.page) {
            // A pager index past the new page count (a page just went away) is not a page the person went to.
            val shown = pages.getOrNull(current) ?: return@LaunchedEffect
            if (shown.page != CcPage.CONTROLS && pageCountSeen[0] == pages.size) edit.stop()
        }
        // Swipe away: an upward drag that the first page cannot use (there is no page before it) pulls the whole panel up with the finger. Past
        // CC_SWIPE_AWAY_DP, or with a quick flick up, it closes; otherwise it springs back on Quick. Not while editing, where a drag is for moving things.
        val closeLabel = stringResource(R.string.close)
        val pull = remember { PanelPull() }
        // Closing leaves the pull where it was while the panel fades out. Pulled open again in that time, this same panel is reused, so it starts from rest.
        LaunchedEffect(panelOpen) { if (ccSwipeAwayOnOpenChange(panelOpen, pull.offset) == 0f) pull.reset() }
        val swipeDensity = androidx.compose.ui.platform.LocalDensity.current
        // The connection below is made once, so it reads these through State: a new density (a fold, a display-size change) or a new onClose is seen at the next gesture.
        val swipeAwayPx by rememberUpdatedState(with(swipeDensity) { CC_SWIPE_AWAY_DP.dp.toPx() })
        val flickPx by rememberUpdatedState(with(swipeDensity) { CC_SWIPE_AWAY_FLICK_DP.dp.toPx() })
        val closePanel by rememberUpdatedState(onClose)
        val tick by rememberUpdatedState(androidx.compose.ui.platform.LocalHapticFeedback.current)
        val swipeConnection = remember(pager, edit, pull) {
            CcSwipeAwayConnection(pull, pageScope, onFirstPage = { pager.currentPage == pager.pageCount - 1 }, editing = { edit.active },
                thresholdPx = { swipeAwayPx }, flickPx = { flickPx }, close = { closePanel() },
                tick = { tick.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) })
        }
        fun goTo(page: Int) { val index = firstIndex - page; pageScope.launch { if (reduceMotion) pager.scrollToPage(index) else pager.animateScrollToPage(index, animationSpec = FolioMotion.spring(FolioMotion.Sheet)) } }
        // A swipe up in the empty space under the controls reaches the scrim, which asks here first: from Media or Connectivity it goes back a page, and only the first page lets it close the panel.
        // Read when the swipe lands, not when it was last drawn: a swipe made while the page is still changing counts against the page it is changing to, and one made while editing ends the editing first.
        DisposableEffect(pageBack, pager, edit, firstIndex) {
            pageBack?.back = {
                when (val action = ccSwipeUp(edit.active, firstIndex - pager.targetPage)) {
                    CcSwipeUp.StopEditing -> { edit.stop(); true }
                    is CcSwipeUp.GoBack -> { goTo(action.page); true }
                    CcSwipeUp.Close -> false
                }
            }
            onDispose { pageBack?.back = null }
        }
        // The pages share one height (the Controls page's rows), so the panel does not change size as you move between them.
        // While editing, the last Controls page also holds the gallery of controls to add back, so it counts for the page height.
        val galleryRows = if (edit.active) ccEditGalleryRows(0, cols) else 0
        // What the gallery can add: modules taken off in the editor and controls that are not on the page. The + in the header and Add a Control both open it.
        var galleryOpen by remember { mutableStateOf(false) }
        val galleryItems = run {
            val moduleLabel: @Composable (String) -> String = { id -> when (id) {
                CC_CONNECTIVITY -> stringResource(R.string.cc_page_connectivity); CC_MEDIA -> stringResource(R.string.now_playing)
                CC_BRIGHTNESS -> stringResource(R.string.brightness); CC_VOLUME -> stringResource(R.string.volume); else -> stringResource(R.string.focus) } }
            (if (slotMode) ccMissingModules(slots, focusOn) else emptyList()).map { id -> CcGalleryItem(id, moduleLabel(id), when (id) { CC_CONNECTIVITY -> Icons.Rounded.Wifi; CC_MEDIA -> Icons.Rounded.MusicNote
                CC_BRIGHTNESS -> Icons.Rounded.LightMode; CC_VOLUME -> Icons.AutoMirrored.Rounded.VolumeUp; else -> Icons.Rounded.DarkMode }, true) } +
                CcControl.entries.filter { it !in chosen && available(it) }.map { c -> CcGalleryItem(c.name, stringResource(c.label), c.icon(), false) }
        }
        val addFromGallery: (CcGalleryItem) -> Unit = { item ->
            if (item.module) changeLayout(ccAdd(slots, item.id, cols, slotRows + slotPerPage) ?: slots, cols, focusOn) else changeControls(controlNames + item.id)
            if (galleryItems.size <= 1) galleryOpen = false
        }
        // With two panes the list beside the panel is the gallery: it is handed what to show and what a plus does, and the card stays shut.
        if (twoPane != null) SideEffect { addFeed.items = galleryItems; addFeed.onAdd = addFromGallery }
        if (galleryOpen && edit.active && twoPane == null) CcControlGallery(galleryItems, panel = panelBounds, fit = fitNow, minFit = FolioTouch.MIN / cell.value, onFit = { galleryFit = it }, onDismiss = { galleryOpen = false }, onAdd = addFromGallery)
        // The editor's gallery is a sheet, not rows under the grid, so the places page counts only its own rows (with the gallery's two counted too, a
        // control in the sixth row made the page "too tall", it became a scroll container and cut off the resize handles in the last column).
        fun controlsRows(index: Int) = if (slotMode) slotEnd(index.coerceIn(0, slotStarts.lastIndex)) - slotStarts[index.coerceIn(0, slotStarts.lastIndex)]
            else ccPlanRows(plan, index, cols, focusOn) + if (index == plan.perPage.lastIndex) galleryRows else 0
        val neededRows = maxOf(if (CcPage.MEDIA in extraPages) ccMediaRows(plan, focusOn) else 0, (if (slotMode) slotStarts.indices.toList() else plan.perPage.indices.toList()).maxOf { controlsRows(it) })
        // The editor's "Add a Control" row stands under its grid on the last page, so the pages are as tall as a grid plus that row while editing.
        val addRowShown = if (slotMode && edit.active) addRowHeight else 0.dp
        val wantedHeight = cell * neededRows + gap * (neededRows - 1) + addRowShown
        val pageHeight = if (pagesAvailable == null) wantedHeight else minOf(wantedHeight, pagesAvailable).coerceAtLeast(cell)

        // The sliders: beside the small controls with four columns, at the end of the top row with six or eight.
        var hold by remember { mutableStateOf<CcHold?>(null) }
        val brightnessSlider: @Composable (Dp) -> Unit = { height ->
            TallSlider(if (controls.brightness < .35f) Icons.Rounded.BrightnessLow else Icons.Rounded.LightMode, stringResource(R.string.brightness), controls.brightness, cell, height,
                onStart = { if (!android.provider.Settings.System.canWrite(context)) open(controls.writeSettingsIntent()) }, ticks = options.sliderTicks,
                onHold = controls::holdBrightness,
                // Dragging the slider turns Auto off; this says so and turns it back on (v2). It follows the phone's own mode, so it also
                // changes when automatic brightness is switched somewhere else.
                // Not while editing: tiles do nothing then, and the minus badge sits where the chip is.
                badge = if (v2 && controls.autoBrightnessKnown && !edit.active) { alpha ->
                    AutoBrightnessChip(controls.brightnessAuto, cell, alpha) {
                        if (!android.provider.Settings.System.canWrite(context)) open(controls.writeSettingsIntent()) else controls.setBrightnessAuto(!controls.brightnessAuto)
                    }
                } else null, onLongPress = if (edit.active) null else { { hold = CcHold.BRIGHTNESS } }) { value ->
                controls.changeBrightness(value) }
        }
        val volumeSlider: @Composable (Dp) -> Unit = { height ->
            TallSlider(when {
                controls.volume <= .01f -> Icons.AutoMirrored.Rounded.VolumeOff
                controls.volume < .5f -> Icons.AutoMirrored.Rounded.VolumeDown
                else -> Icons.AutoMirrored.Rounded.VolumeUp
            }, stringResource(R.string.volume), controls.volume, cell, height, ticks = options.sliderTicks, onHold = controls::holdVolume,
                onLongPress = if (edit.active) null else { { hold = CcHold.VOLUME } }) { controls.changeVolume(it) }
        }
        val sliders: @Composable (Dp) -> Unit = { height -> brightnessSlider(height); volumeSlider(height) }

        // The cards a press and hold on a slider opens (CcHoldCard): the same control wider, with what a tap leaves out.
        when (hold) {
            CcHold.BRIGHTNESS -> {
                val dark = (androidx.compose.ui.platform.LocalConfiguration.current.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                CcHoldTall(if (controls.brightness < .35f) Icons.Rounded.BrightnessLow else Icons.Rounded.LightMode, stringResource(R.string.brightness), controls.brightness, onDismiss = { hold = null },
                    onStart = { if (!android.provider.Settings.System.canWrite(context)) open(controls.writeSettingsIntent()) }, onHold = controls::holdBrightness,
                    toggles = {
                        if (controls.autoBrightnessKnown) CcHoldToggle(Icons.Rounded.BrightnessAuto, stringResource(R.string.cc_auto_brightness), stringResource(if (controls.brightnessAuto) R.string.state_on else R.string.state_off), controls.brightnessAuto) {
                            if (!android.provider.Settings.System.canWrite(context)) open(controls.writeSettingsIntent()) else controls.setBrightnessAuto(!controls.brightnessAuto) }
                        // Android keeps the system dark theme behind Settings, so this shows its state and opens Display settings.
                        CcHoldToggle(Icons.Rounded.DarkMode, stringResource(R.string.cc_dark_theme), stringResource(if (dark) R.string.state_on else R.string.state_off), dark) { hold = null; open(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
                    }) { controls.changeBrightness(it) }
            }
            CcHold.VOLUME -> CcHoldCard(stringResource(R.string.volume), onDismiss = { hold = null }) {
                listOf(AudioManager.STREAM_MUSIC to R.string.cc_hold_media, AudioManager.STREAM_RING to R.string.cc_hold_ring, AudioManager.STREAM_ALARM to R.string.cc_hold_alarm).forEach { (stream, name) ->
                    var level by remember(stream) { mutableStateOf(controls.streamFraction(stream)) }
                    CcHoldSlider(if (level <= .01f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, stringResource(name), level) { v ->
                        level = v
                        if (stream == AudioManager.STREAM_MUSIC) controls.changeVolume(v) else controls.setStreamVolume(stream, v)
                    }
                }
                CcHoldAction(stringResource(R.string.cc_hold_open_volume)) { hold = null; controls.showVolumePanel() }
            }
            null -> {}
        }

        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        val connectivity = listOf(
            ConnectivityItem(Icons.Rounded.AirplanemodeActive, stringResource(R.string.airplane_mode_2), status.airplane, AccentOrange) { open(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) },
            ConnectivityItem(Icons.Rounded.SignalCellularAlt, stringResource(R.string.cellular_data), !status.airplane && (status.cellularLevel ?: 0) > 0, AccentGreen, status.carrierName) { open(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) },
            ConnectivityItem(Icons.Rounded.Wifi, stringResource(R.string.wi_fi), status.wifiConnected && !status.airplane, AccentBlue, status.wifiName) { open(Intent(Settings.Panel.ACTION_WIFI)) },
            ConnectivityItem(Icons.Rounded.Bluetooth, stringResource(R.string.bluetooth), controls.bluetoothOn, AccentBlue) {
                val panel = Intent("android.settings.panel.action.BLUETOOTH")
                val target = if (panel.resolveActivity(context.packageManager) != null) panel else Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                open(target)
            },
        )
        val playing: () -> Unit = { media?.let { onClose(); IslandListenerService.open(context, it) } }

        // Focus, like iOS: the Focus that's on (or "Focus"); the icon toggles it, the rest opens the list. It stands under the modules on the first page, or on the Media page when the first has no row for it.
        val focusModuleAt: @Composable (Dp, Dp) -> Unit = { focusWidth, focusHeight ->
            var focusOpen by remember { mutableStateOf(false) }
            val current = focusModes.firstOrNull { it.id == activeFocus }
            if (focusOpen) Module(Modifier.width(focusWidth)) {
                Column(Modifier.fillMaxWidth().padding(vertical = FolioSpace.SMALL.dp)) {
                    focusModes.forEach { mode ->
                        val on = mode.id == activeFocus
                        Row(Modifier.fillMaxWidth().clickable { onFocus(if (on) null else mode.id); focusOpen = false }
                            .padding(horizontal = FolioSpace.COMFY.dp, vertical = FolioSpace.SMALL.dp).testTag("cc-focus-${mode.id}"), verticalAlignment = Alignment.CenterVertically) {
                            RoundToggle(mode.icon(), mode.name, on, Color(mode.color), 40.dp) { onFocus(if (on) null else mode.id); focusOpen = false }
                            Spacer(Modifier.width(12.dp))
                            Text(mode.name, color = Color.White, fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (on) Text(stringResource(R.string.on), color = FolioGlass.secondary, fontSize = FolioType.FOOTNOTE.sp)
                        }
                    }
                }
            } else Module(Modifier.width(focusWidth).height(focusHeight).clickable(onClickLabel = stringResource(R.string.choose_a_focus)) { focusOpen = true }.testTag("cc-focus")) {
                Row(Modifier.fillMaxSize().padding(horizontal = cell * .14f), verticalAlignment = Alignment.CenterVertically) {
                    val mode = current ?: focusModes.first()
                    RoundToggle(mode.icon(), mode.name, current != null, Color(mode.color), cell * .7f) {
                        onFocus(if (current != null) null else mode.id)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(current?.name ?: stringResource(R.string.focus), color = Color.White, fontSize = ccTileSp(FolioType.SUBHEAD.toFloat()), fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (current != null) Text(stringResource(R.string.on), color = FolioGlass.secondary, fontSize = ccTileSp(FolioType.FOOTNOTE.toFloat()))
                    }
                }
            }
        }

        val focusModule: @Composable () -> Unit = { focusModuleAt(span(cols), cell) }

        // What a place in the editor is called, for the pages' announcements as well as for its tiles.
        val slotLabel: (String) -> String = { id -> when (id) {
            CC_CONNECTIVITY -> context.getString(R.string.cc_page_connectivity); CC_MEDIA -> context.getString(R.string.now_playing)
            CC_BRIGHTNESS -> context.getString(R.string.brightness); CC_VOLUME -> context.getString(R.string.volume); CC_FOCUS -> context.getString(R.string.focus)
            else -> CcControl.entries.firstOrNull { it.name == id }?.let { context.getString(it.label) } ?: id } }
        // Carries [id] to the page [by] pages away (1 the next, -1 the one before) and turns to it. False when that page has no room for it.
        val carryTo: (String, Int, Int) -> Boolean = carry@{ id, from, by ->
            val to = from + by
            if (to !in slotStarts.indices) return@carry false
            val next = ccCarry(slots, id, cols, slotStarts[to], slotEnd(to), fromBottom = by < 0)
            if (next == null) { editView.announceForAccessibility(context.getString(R.string.cc_page_full, to + 1, slotLabel(id))); return@carry false }
            changeLayout(next, cols, focusOn)
            goTo(to)
            editView.announceForAccessibility(context.getString(R.string.cc_moved_to_page, slotLabel(id), to + 1))
            true
        }

        // One page's content. Pages scroll on their own when a window is too short for them.
        val pageContent: @Composable (CcPageSpec) -> Unit = { spec ->
            // The grid is centered in a header that may be wider than it (a short, narrow window puts the page dots in the header).
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            // A page scrolls only where its rows do not fit the window. A scroll container that cannot scroll still took the downward drag
            // (its edge stretch ate it), so a swipe down never reached the pager; with none, or with the stretch off, the pager gets it.
            val rowsFit = ccRowsThatFit(pageHeight.value, cell.value, gap.value)
            val rowsNeeded = when (spec.page) { CcPage.CONTROLS -> controlsRows(spec.index); CcPage.MEDIA -> ccMediaRows(plan, focusOn); CcPage.CONNECTIVITY -> 4 }
            @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
            CompositionLocalProvider(androidx.compose.foundation.LocalOverscrollConfiguration provides null) {
            // The editor's pages never scroll (PNL-1): what does not fit is on another page.
            val scrolls = !(slotMode && spec.page == CcPage.CONTROLS) && rowsNeeded > rowsFit
            val pageScroll = rememberScrollState()
            // The page's own box, so a control held at its edge can be carried to the next page (see CcSlotGrid).
            var pageBox by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
            Column(Modifier.width(gridWidth).onGloballyPositioned { pageBox = it }
                .then(if (scrolls) Modifier.fadingVerticalScroll(pageScroll) else Modifier), verticalArrangement = Arrangement.spacedBy(gap)) {
                when (spec.page) {
                    CcPage.CONTROLS -> {
                        val first = spec.index == 0
                        val before = plan.perPage.take(spec.index).sum()
                        val allPageControls = chosen.subList(before, before + (plan.perPage.getOrNull(spec.index) ?: 0))
                        val focusBesideCount = if (spec.index == 0 && focusOn && plan.focusOnFirst) minOf(2, allPageControls.size) else 0
                        val pageControls = allPageControls.drop(focusBesideCount)
                        if (slotMode) {
                            // The Control Center editor: every module and control at its own place, the page's rows of them (see CcSlotGrid).
                            var slotConnectivityOpen by remember { mutableStateOf(false) }
                            val pageAt = spec.index.coerceIn(0, slotStarts.lastIndex)
                            val pageStart = slotStarts[pageAt]
                            val pageEnd = slotEnd(pageAt)
                            val pageSlots = remember(slots, pageStart, pageEnd) { ccPageSlots(slots, pageStart, pageEnd) }
                            val labelOfSlot: @Composable (String) -> String = { id -> slotLabel(id) }
                            CcSlotGrid(pageSlots, cols, pageEnd - pageStart, cell, gap, edit.active, raised = if (slotConnectivityOpen) CC_CONNECTIVITY else null,
                                removable = { true }, labelOf = labelOfSlot,
                                onChange = { next -> changeLayout(ccMergePage(slots, pageStart, pageEnd, next), cols, focusOn) },
                                onRemove = { id -> if (CcControl.entries.any { it.name == id }) changeControls(controlNames - id) else changeLayout(ccRemove(slots, id), cols, focusOn) },
                                onEnterEdit = { edit.start() }, viewport = { pageBox },
                                carryNext = if (pageAt < slotStarts.lastIndex) { id -> carryTo(id, pageAt, 1) } else null,
                                carryBack = if (pageAt > 0) { id -> carryTo(id, pageAt, -1) } else null) { slot ->
                                when (slot.id) {
                                    CC_CONNECTIVITY ->
                                        // The open list is wider and taller than the 2 by 2 place it came from, so it is measured without that place's limits
                                        // (it wrapped "Cellular Data" onto three lines and ran over Focus and Rotation) and slid left to stay inside the grid.
                                        // It also stands over Not Playing, Focus and the sliders, so it is drawn on a solid surface, not the see-through tile:
                                        // the iPhone blurs what is behind it, and without a blur the neighbors' names ran through its own ("Airplane Mode" over "Not Playing").
                                        if (slotConnectivityOpen && !edit.active) Module(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                                            .offset(x = -((cell + gap) * maxOf(0, slot.col + minOf(cols, 4) - cols)))
                                            .width(span(minOf(cols, 4))).wrapContentHeight(unbounded = true)
                                            .combinedClickable(onClick = { slotConnectivityOpen = false }, onLongClick = { slotConnectivityOpen = false }), color = FolioColors.MenuSurface) {
                                            ConnectivityList(connectivity, Modifier.fillMaxWidth().padding(vertical = FolioSpace.SMALL.dp))
                                        } else Module(Modifier.fillMaxSize().combinedClickable(onClick = {}, enabled = !edit.active, onLongClick = {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); slotConnectivityOpen = true
                                        }, onLongClickLabel = stringResource(R.string.show_connectivity_details))) {
                                            Column(Modifier.fillMaxSize().padding(cell * .16f), verticalArrangement = Arrangement.SpaceBetween) {
                                                connectivity.chunked(2).forEach { pair ->
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        pair.forEach { RoundToggle(it.icon, it.label, it.on, it.accent, cell * .7f) { if (!edit.active) it.onClick() } }
                                                    }
                                                }
                                            }
                                        }
                                    CC_MEDIA -> MediaModule(media, Modifier.fillMaxSize(), cell, onOpen = { if (!edit.active) playing() }, slimWhenIdle = dedupe && !edit.active, slot = span(slot.h))
                                    CC_BRIGHTNESS -> brightnessSlider(span(slot.h))
                                    CC_VOLUME -> volumeSlider(span(slot.h))
                                    CC_FOCUS -> focusModuleAt(span(slot.w), span(slot.h))
                                    else -> CcControl.entries.firstOrNull { it.name == slot.id }?.let { c ->
                                        val v = valueOf(c)
                                        if (slot.w == 1) SquareToggle(v.icon ?: c.icon(), stringResource(c.label), isOn(c), accent(c), cell, v.caption) { if (!edit.active) run(c) }
                                        else Module(Modifier.fillMaxSize().clickable(enabled = !edit.active) { run(c) }) {
                                            Row(Modifier.fillMaxSize().padding(horizontal = cell * .09f), verticalAlignment = Alignment.CenterVertically) {
                                                // A little smaller than a one-cell toggle, so a two-cell tile has the room for its name (Rotation, Camera) and, for a
                                                // control that holds a value, for the name on up to two lines over the value.
                                                RoundToggle(v.icon ?: c.icon(), stringResource(c.label), isOn(c), accent(c), cell * .58f) { if (!edit.active) run(c) }
                                                Spacer(Modifier.width(4.dp))
                                                Column(Modifier.weight(1f)) {
                                                    val hasValue = !v.caption.isNullOrEmpty()
                                                    Text(stringResource(c.label), color = Color.White, fontSize = ccTileSp(if (hasValue) FolioType.FOOTNOTE.toFloat() else if (stringResource(c.label).length > 7) 12f else FolioType.SUBHEAD.toFloat()), fontWeight = FontWeight.SemiBold,
                                                        maxLines = if (hasValue) 2 else 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, lineHeight = ccTileSp(if (hasValue) 15f else 20f))
                                                    if (!v.caption.isNullOrEmpty()) Text(v.caption, color = Color.White.copy(alpha = .7f), fontSize = ccTileSp(FolioType.FOOTNOTE.toFloat()), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                        // Row 1–2: connectivity (2×2, long-press to expand like iOS) and now playing; with six or eight columns the sliders end this row.
                        var connectivityOpen by remember { mutableStateOf(false) }
                        val topBlock: @Composable () -> Unit = {
                            if (connectivityOpen) Module(Modifier.width(span(if (cols >= 6) cols - 2 else cols)).combinedClickable(onClick = { connectivityOpen = false }, onLongClick = { connectivityOpen = false })) {
                                ConnectivityList(connectivity, Modifier.fillMaxWidth().padding(vertical = FolioSpace.SMALL.dp))
                            }
                            else Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                Module(Modifier.size(span(2)).combinedClickable(onClick = {}, onLongClick = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); connectivityOpen = true
                                }, onLongClickLabel = stringResource(R.string.show_connectivity_details))) {
                                    Column(Modifier.fillMaxSize().padding(cell * .16f), verticalArrangement = Arrangement.SpaceBetween) {
                                        connectivity.chunked(2).forEach { pair ->
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                pair.forEach { RoundToggle(it.icon, it.label, it.on, it.accent, cell * .7f, it.onClick) }
                                            }
                                        }
                                    }
                                }
                                MediaModule(media, Modifier.size(span(if (cols >= 6) cols - 4 else 2), span(2)), cell, onOpen = playing, slimWhenIdle = dedupe, slot = span(2))
                            }
                        }
                        if (first) { if (cols >= 6) Row(horizontalArrangement = Arrangement.spacedBy(gap)) { topBlock(); sliders(span(2)) } else topBlock() }
                        // Focus: see focusModule.
                        // A small bar, as in the editor: two cells wide, with the first two controls beside it.
                        if (focusBesideCount > 0) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            focusModuleAt(span(2), cell)
                            allPageControls.take(focusBesideCount).forEach { c -> EditableControl(c, isOn(c), accent(c), cell, edit, onRemove = { changeControls(controlNames - c.name) }, reorder = reorder, onMove = moveControl, onNudge = { by -> changeControls(ccMoveControlBy(controlNames, c.name, by)) }, value = valueOf(c),
                                settleIndex = if (first) allPageControls.indexOf(c) else -1, panelOpen = panelOpen) { run(c) } }
                        } else if (first && focusOn && plan.focusOnFirst) focusModuleAt(span(2), cell)
                        // Row 3–4 with four columns: the first four small controls (2×2) and brightness + volume sliders
                        // (With the Media page off and no room on the first page, the same block leads the next Controls page; with no control to put beside them the sliders stand in the middle.)
                        if (cols == 4 && spec.index == plan.sliderPage) {
                            val beside = pageControls.take(4)
                            Row(if (beside.isEmpty()) Modifier.width(gridWidth) else Modifier,
                                horizontalArrangement = if (beside.isEmpty()) Arrangement.Center else Arrangement.spacedBy(gap)) {
                                if (beside.isNotEmpty()) Column(Modifier.width(span(2)), verticalArrangement = Arrangement.spacedBy(gap)) {
                                    beside.chunked(2).forEach { pair ->
                                        Row(Modifier.zIndex(if (reorder?.moving in pair.map { it.name }) 1f else 0f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                            pair.forEach { c -> EditableControl(c, isOn(c), accent(c), cell, edit, onRemove = { changeControls(controlNames - c.name) }, reorder = reorder, onMove = moveControl, onNudge = { by -> changeControls(ccMoveControlBy(controlNames, c.name, by)) }, value = valueOf(c),
                                                settleIndex = if (first) beside.indexOf(c) else -1, panelOpen = panelOpen) { run(c) } }
                                        }
                                    }
                                }
                                sliders(span(2))
                            }
                        }
                        // The rest, one row of the grid at a time
                        (if (cols >= 6 || spec.index != plan.sliderPage) pageControls else pageControls.drop(4)).chunked(cols).forEach { row ->
                            // The row holding the control being dragged is drawn above the rows after it.
                            Row(Modifier.zIndex(if (reorder?.moving in row.map { it.name }) 1f else 0f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                row.forEach { c -> EditableControl(c, isOn(c), accent(c), cell, edit, onRemove = { changeControls(controlNames - c.name) }, reorder = reorder, onMove = moveControl, onNudge = { by -> changeControls(ccMoveControlBy(controlNames, c.name, by)) }, value = valueOf(c),
                                    settleIndex = if (first) pageControls.indexOf(c) else -1, panelOpen = panelOpen) { run(c) } }
                            }
                        }
                        }
                        // Edit mode: "Add a Control" under the grid opens the gallery (the + in the header does the same).
                        if (edit.active && twoPane == null && spec.index == (if (slotMode) slotStarts.lastIndex else plan.perPage.lastIndex)) {
                            Row(Modifier.width(gridWidth).clip(RoundedCornerShape(FolioRadius.CARD.dp)).then(if (galleryItems.isEmpty()) Modifier else Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) { galleryOpen = true })
                                .heightIn(min = FolioTouch.MIN.dp).padding(FolioSpace.SMALL.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                if (galleryItems.isNotEmpty()) Icon(Icons.Rounded.AddCircle, null, tint = Color.White, modifier = Modifier.padding(end = FolioSpace.SNUG.dp).size((FolioSpace.XL.dp.value * ccIconScale(LocalDensity.current.fontScale)).dp))
                                Text(if (galleryItems.isEmpty()) stringResource(R.string.all_controls_added) else stringResource(R.string.add_a_control),
                                    color = if (galleryItems.isEmpty()) FolioGlass.secondary else Color.White, fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    // Media and sound: Now Playing large, with brightness and volume tall beside it.
                    CcPage.MEDIA -> {
                        // The sliders stand here only if the first page has none (otherwise this page would repeat them); the larger Now Playing takes their width.
                        val slidersHere = ccMediaPageHasSliders(plan, dedupe)
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            MediaModule(media, Modifier.size(span(if (slidersHere) cols - 2 else cols), span(3)), cell, onOpen = playing, slimWhenIdle = dedupe, slot = span(3))
                            if (slidersHere) sliders(span(3))
                        }
                        // The sliders stand on the first page, so the volume has its own bar here, like the Music page of an iPhone.
                        if (!slidersHere) MediaVolumeBar(controls.volume, span(cols), cell, options.sliderTicks, !edit.active) { controls.changeVolume(it) }
                        if (focusOn && !plan.focusOnFirst) focusModule()
                    }
                    // Connectivity: each connection as a full row, with its state.
                    CcPage.CONNECTIVITY -> Module(Modifier.width(span(cols))) { ConnectivityList(connectivity, Modifier.fillMaxWidth().padding(vertical = FolioSpace.SMALL.dp)) }
                }
            }
            }
            }
        }

        // The edge or bottom strip that moves between pages (tap), as on the iPad; a swipe across the panel does the same.
        val strip: @Composable () -> Unit = {
            val label: @Composable (CcPage) -> String = { page -> stringResource(when (page) { CcPage.CONTROLS -> R.string.cc_page_controls; CcPage.MEDIA -> R.string.cc_page_media; CcPage.CONNECTIVITY -> R.string.cc_page_connectivity }) }
            val controlsPages = pages.count { it.page == CcPage.CONTROLS }
            val items: @Composable () -> Unit = {
                pages.forEachIndexed { index, spec ->
                    val page = spec.page
                    val selected = current == index
                    val name = label(page)
                    val description = stringResource(R.string.cc_page_of, name, index + 1, pages.size)
                    // Minimal, as Apple draws them: a bare glyph, and only the page in view gets a soft round fill.
                    val fill by androidx.compose.animation.core.animateFloatAsState(if (selected) .28f else 0f,
                        if (reduceMotion) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(180), label = "cc page fill")
                    Box(Modifier.size(FolioTouch.MIN.dp).clip(CircleShape).background(Color.White.copy(alpha = fill))
                        .clickable(role = androidx.compose.ui.semantics.Role.Tab) { goTo(index) }
                        .semantics { contentDescription = description; this.selected = selected },
                        contentAlignment = Alignment.Center) {
                        // With more than one Controls page each says which it is, so the marks are never the same.
                        if (page == CcPage.CONTROLS && controlsPages > 1) Text("${spec.index + 1}", color = if (selected) Color.White else Color.White.copy(alpha = .65f),
                            fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.Bold)
                        else Icon(when (page) { CcPage.CONTROLS -> Icons.Rounded.Favorite; CcPage.MEDIA -> Icons.Rounded.MusicNote; CcPage.CONNECTIVITY -> Icons.Rounded.SettingsInputAntenna },
                            null, tint = if (selected) Color.White else Color.White.copy(alpha = .65f), modifier = Modifier.size((22 * ccIconScale(LocalDensity.current.fontScale)).dp))
                    }
                }
            }
            // A swipe up or down along the strip changes page too, as on the Duo: down goes to the next page and up goes back, the way the pages are laid out.
            val stripPage by rememberUpdatedState(current)
            val stripPages by rememberUpdatedState(pages.size)
            val swipePages = Modifier.pointerInput(Unit) {
                var travel = 0f
                val threshold = 24.dp.toPx()
                detectVerticalDragGestures(onDragStart = { travel = 0f }, onDragCancel = { travel = 0f },
                    onDragEnd = {
                        val target = ccStripSwipeTarget(stripPage, stripPages, travel, threshold)
                        if (target != stripPage) goTo(target)
                        travel = 0f
                    }) { change, by -> change.consume(); travel += by }
            }
            if (grid.stripInHeader) Row(verticalAlignment = Alignment.CenterVertically) {
                // Small dots, each a 32 by 48 dp target side by side (no gap between targets, so a tap near the edge cannot hit the wrong page): a short window has no row to spare for round buttons.
                pages.forEachIndexed { index, spec ->
                    val selected = current == index
                    val description = stringResource(R.string.cc_page_of, label(spec.page), index + 1, pages.size)
                    Box(Modifier.size(32.dp, FolioTouch.MIN.dp).clickable(role = androidx.compose.ui.semantics.Role.Tab) { goTo(index) }
                        .semantics { contentDescription = description; this.selected = selected }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(if (selected) 10.dp else 8.dp).clip(CircleShape).background(if (selected) Color.White else Color.White.copy(alpha = .4f)))
                    }
                }
            }
            else if (grid.stripAtEdge) {
                val roomy = (tall ?: 0f) >= 560f
                val top: @Composable () -> Unit = {
                    // As beside the grid on the iPhone and the Duo: the battery first, then the pages. Only where the column has the height for it.
                    // A gap on top of the strip's own keeps the ring from reading as the first page dot.
                    if ((tall ?: Float.MAX_VALUE) >= 360f) Box(if (dedupe) Modifier.padding(bottom = CC_BATTERY_APART_DP.dp) else Modifier) { BatteryRing(status) }
                    // Under the ring, the connection marks the Duo's rail shows (Wi-Fi, then cellular), when the column is tall enough for them as well.
                    if ((tall ?: Float.MAX_VALUE) >= 520f) RailReadouts(status)
                }
                if (roomy) {
                    // Tall enough: the status stays at the top and the page icons stand in the middle of the grid's height, as Apple places them.
                    val railHeight = pageHeight + FolioTouch.MIN.dp + gap + (if (reserveSwitchRow) PANEL_SWITCH_ROW_DP.dp + gap else 0.dp)
                    Box(Modifier.height(railHeight).then(swipePages)) {
                        Column(Modifier.align(Alignment.TopCenter), verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp), horizontalAlignment = Alignment.CenterHorizontally) { top() }
                        Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(FolioSpace.TINY.dp), horizontalAlignment = Alignment.CenterHorizontally) { items() }
                    }
                } else Column(swipePages, verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    top()
                    items()
                }
            }
            else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FolioSpace.COMFY.dp, Alignment.CenterHorizontally)) { items() }
        }

        ProvideJiggle(edit) {
        Box(Modifier.fillMaxWidth().padding(vertical = CC_PANEL_PAD_DP.dp).graphicsLayer { translationY = pull.offset; alpha = PanelPull.fade(pull.offset, this.size.height) }
            .semantics { dismiss(closeLabel) { onClose(); true } }, contentAlignment = Alignment.TopCenter) {
            Row(Modifier.padding(horizontal = FolioSpace.LARGE.dp), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
                // The grid takes the taps that land between its controls, so only the margins around it close the panel.
                Column(Modifier.width(maxOf(gridWidth, if (grid.stripInHeader) ccHeaderMinWidth(pages.size).dp else 0.dp)).pointerInput(Unit) { detectTapGestures() }, verticalArrangement = Arrangement.spacedBy(gap)) {
                    if (reserveSwitchRow) Spacer(Modifier.height(PANEL_SWITCH_ROW_DP.dp))
                    // iOS 18 header: edit on the left, power on the right.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (edit.active) {
                            PanelPill(stringResource(R.string.done)) { edit.stop() }
                            if (galleryItems.isNotEmpty() && twoPane == null) { Spacer(Modifier.width(gap)); GlassIconButton(Icons.Rounded.Add, stringResource(R.string.add_a_control)) { galleryOpen = true } }
                            if (history.canUndo) { Spacer(Modifier.width(gap)); GlassIconButton(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.undo)) { undoEdit() } }
                        }
                        else if (pages.getOrNull(current)?.page == CcPage.CONTROLS) GlassIconButton(Icons.Rounded.Add, stringResource(R.string.edit_controls)) { edit.start() }
                        Spacer(Modifier.weight(1f))
                        // The switch to Notification Center stands in the middle of the header while there is room beside the edit and power buttons.
                        if (onSwitch != null && !grid.stripInHeader && gridWidth >= CC_SWITCH_BUTTON_MIN_GRID_DP.dp) { PanelSwitch(ShadePanel.QUICK_SETTINGS, onSwitch, compact = gridWidth < CC_SWITCH_PILL_MIN_GRID_DP.dp); Spacer(Modifier.weight(1f)) }
                        if (grid.stripInHeader) { strip(); Spacer(Modifier.weight(1f)) }
                        // A button for the way out, for anyone who does not know the swipe (a short, narrow window has no room for it: the swipe and a tap outside close there).
                        else GlassIconButton(Icons.Rounded.KeyboardArrowUp, closeLabel) { onClose() }
                        GlassIconButton(Icons.Rounded.PowerSettingsNew, stringResource(R.string.power_menu)) {
                            onClose(); if (!SystemShadeAccessibilityService.global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)) onSystem()
                        }
                    }
                    // Pages are stacked and the next one comes down with a swipe down (reverseLayout: the first page is anchored, so a swipe up past it closes the panel), as the pages beside the grid read on the Duo. The settle is Folio's Sheet spring
                    // (a snap with Reduce Motion), and each page fades and shrinks a little as it leaves, read while drawing from where the finger has taken it.
                    androidx.compose.foundation.pager.VerticalPager(pager, Modifier.fillMaxWidth().height(pageHeight)
                        // The pages travel with the finger and leave through the bottom of the screen, as Apple's do, instead of being cut off at the
                        // controls' own edge: the pager is laid out as tall as the room under the header, while the panel still reports the controls' height.
                        .layout { measurable, constraints ->
                            val shown = constraints.maxHeight
                            val tall = maxOf(shown, pagesAvailable?.roundToPx() ?: shown)
                            val placeable = measurable.measure(constraints.copy(minHeight = tall, maxHeight = tall))
                            layout(placeable.width, shown) { placeable.place(0, 0) }
                        }.nestedScroll(swipeConnection), pageSpacing = gap, horizontalAlignment = Alignment.Start, userScrollEnabled = true,
                        flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(pager,
                            // A page is the whole panel tall, so the usual halfway rule asked for a drag across half the screen: a fifth of it is enough to turn the page, and a short swipe springs back.
                            snapPositionalThreshold = CC_PAGE_TURN,
                            snapAnimationSpec = if (reduceMotion) androidx.compose.animation.core.snap() else FolioMotion.spring(FolioMotion.Sheet))) { index ->
                        // The pager is laid out taller than the controls (see above), so a tap under them lands on it, and the grid's own
                        // handler swallows taps: a tap below the controls closes the panel here, as it did before the pages ran to the screen's edge.
                        val shownPx = with(LocalDensity.current) { pageHeight.toPx() }
                        Box(Modifier.fillMaxSize().graphicsLayer {
                            // A page at rest is exactly 1 (a hair of float residue after a layout change made it .9999 and drew the page through an
                            // offscreen layer, which cut off everything drawn past its edge: the resize handles of the last column in edit mode).
                            val away = kotlin.math.abs((pager.currentPage - index) + pager.currentPageOffsetFraction).coerceIn(0f, 1f).let { if (it < .005f) 0f else it }
                            // The page slides with the finger (the pager does that) and thins out as it leaves, so the one coming in reads as the one in front.
                            alpha = ccPageAlpha(away)
                            if (!reduceMotion) { val scale = 1f - CC_PAGE_SHRINK * away; scaleX = scale; scaleY = scale }
                        }.pointerInput(shownPx) { detectTapGestures { at -> if (at.y > shownPx) closePanel() } },
                            contentAlignment = Alignment.TopStart) { pageContent(pages[firstIndex - index]) }
                    }
                    if (!grid.stripAtEdge && !grid.stripInHeader && pages.size > 1) strip()
                }
                if (grid.stripAtEdge) Box(Modifier.pointerInput(Unit) { detectTapGestures() }) { strip() }
            }
        }
        }
    }
    }
}

/** The battery as a ring with its percent, at the top of the page strip; green while charging. Says the same in words for TalkBack. */
@Composable
private fun BatteryRing(status: DeviceStatus) {
    val level = status.battery
    val words = when {
        level == null -> stringResource(R.string.battery_unavailable)
        status.charging -> stringResource(R.string.battery_1_d_percent_charging, level)
        else -> stringResource(R.string.battery_1_d_percent, level)
    }
    val ring = if (status.charging) FolioColors.Green else Color.White
    Box(Modifier.size(FolioTouch.MIN.dp).semantics { contentDescription = words }, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(38.dp)) {
            val stroke = size.width * .1f
            val inset = stroke / 2f
            val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val corner = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(Color.White.copy(alpha = .25f), 0f, 360f, false, corner, arc, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            if (level != null) drawArc(ring, -90f, 360f * level.coerceIn(0, 100) / 100f, false, corner, arc,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        // The number lives inside a fixed 38 dp ring, so it follows the font size only up to 1.3 times (at 2.0 the "100" was wider than the ring, and three digits still touched it at 1.3, so they are a little smaller); the words for TalkBack carry the full value.
        val ringFont = with(androidx.compose.ui.platform.LocalDensity.current) { (12f * minOf(fontScale, 1.3f) * (if ((level ?: 0) >= 100) .82f else 1f)).dp.toSp() }
        if (level != null) Text(level.toString(), color = Color.White, fontSize = ringFont, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * The two connection marks on the rail beside the grid, as the Duo draws Wi-Fi and signal under the battery: a mark dims when it has no
 * signal, and TalkBack reads each as its name and state. They are read-only; the Connectivity page changes them.
 */
@Composable
private fun RailReadouts(status: DeviceStatus) {
    val wifiOn = status.wifiConnected && !status.airplane
    val cellOn = !status.airplane && (status.cellularLevel ?: 0) > 0
    val wifiWords = stringResource(R.string.cc_rail_mark, stringResource(R.string.wi_fi), stringResource(if (wifiOn) R.string.state_on else R.string.state_off))
    val cellWords = if (status.cellularLevel == null) stringResource(R.string.cellular_signal_unavailable) else stringResource(R.string.cc_rail_mark, stringResource(R.string.cellular_data), stringResource(if (cellOn) R.string.state_on else R.string.state_off))
    // Words under the marks, as on the Duo: the network's name when Android gives it (else "Wi-Fi"), and SOS when the phone has no signal at all.
    val noService = !status.airplane && status.cellularLevel == 0
    // A name too long for the rail slides sideways to show all of it (still ellipsized when the person asked for less motion).
    val reduceMotion = LocalReduceMotion.current
    val slide = if (reduceMotion) Modifier else Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 600, velocity = 24.dp)
    val slideEnd = if (reduceMotion) TextOverflow.Ellipsis else TextOverflow.Clip
    val wordSize = FolioType.GROUP_LABEL.sp
    Column(Modifier.widthIn(max = 56.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FolioSpace.TINY.dp)) {
        Icon(Icons.Rounded.Wifi, null, tint = Color.White.copy(alpha = if (wifiOn) 1f else .35f), modifier = Modifier.size(22.dp).semantics { contentDescription = wifiWords })
        if (wifiOn) Text(status.wifiName ?: stringResource(R.string.wi_fi), color = Color.White, fontSize = wordSize, maxLines = 1, overflow = slideEnd,
            modifier = slide.clearAndSetSemantics {})
        Icon(Icons.Rounded.SignalCellularAlt, null, tint = Color.White.copy(alpha = if (cellOn) 1f else .35f), modifier = Modifier.size(22.dp).semantics { contentDescription = cellWords })
        // No service: SOS. Otherwise the network the phone is on.
        if (noService) Text(stringResource(R.string.status_sos), color = Color.White, fontSize = wordSize, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {})
        else if (cellOn && status.carrierName != null) Text(status.carrierName!!, color = Color.White, fontSize = wordSize, maxLines = 1, overflow = slideEnd,
            modifier = slide.clearAndSetSemantics {})
    }
}

/** The four connections as rows, each with its switch and its state: the long-press view on the Controls page and the whole Connectivity page. */
@Composable
private fun ConnectivityList(items: List<ConnectivityItem>, modifier: Modifier) {
    // The open connectivity block, as the iPhone Duo draws it: two columns of glass tiles, each with its icon above its name and state. Where a column is
    // wide enough (150 dp) the first row is two slim pills (icon beside the text), the way the Duo shows Airplane Mode and Personal Hotspot; on a narrow
    // window, such as a flip's cover, every control is a tile, because a pill's text would have 30 dp. Two rows of tiles are about 210 dp tall, so the
    // block fits a short window that the old four-row list ran off the bottom of (the last row's state was cut).
    BoxWithConstraints(modifier) {
        val pills = (maxWidth - FolioSpace.SMALL.dp * 3) / 2 >= CC_CONNECTIVITY_PILL_MIN_WIDTH.dp
        Column(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.SMALL.dp), verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
            items.chunked(2).forEachIndexed { row, pair ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                    pair.forEach { item ->
                        if (pills && row == 0) ConnectivityPill(item, Modifier.weight(1f)) else ConnectivityTile(item, Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** A column is wide enough for a pill (icon beside two lines of text) from this many dp. */
private const val CC_CONNECTIVITY_PILL_MIN_WIDTH = 150

/** The name of a control's network or carrier, sliding sideways when it is too long for its place (ellipsized when motion is reduced), else On or Off. */
@Composable
private fun ConnectivityState(item: ConnectivityItem) {
    val reduce = LocalReduceMotion.current
    val name = item.detail?.takeIf { item.on }
    Text(name ?: stringResource(if (item.on) R.string.state_on else R.string.state_off), color = FolioGlass.secondary, fontSize = FolioType.FOOTNOTE.sp,
        maxLines = 1, overflow = if (reduce) TextOverflow.Ellipsis else TextOverflow.Clip,
        modifier = if (name == null || reduce) Modifier else Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 600, velocity = 24.dp))
}

/** A big glass tile: the round toggle at the top left, the name and state at the bottom left. */
@Composable
private fun ConnectivityTile(item: ConnectivityItem, modifier: Modifier) {
    Column(modifier.fillMaxHeight().heightIn(min = 92.dp).clip(RoundedCornerShape(FolioRadius.GROUP.dp)).background(Color.White.copy(alpha = .10f))
        .clickable(onClick = item.onClick).padding(FolioSpace.COMPACT.dp), verticalArrangement = Arrangement.SpaceBetween) {
        RoundToggle(item.icon, item.label, item.on, item.accent, 36.dp, item.onClick)
        // "Airplane Mode" and "Cellular Data" take two lines in a narrow tile, as they do on the Duo; "Bluetooth" is one word and fits.
        Column(Modifier.padding(top = FolioSpace.SNUG.dp)) {
            Text(item.label, color = Color.White, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ConnectivityState(item)
        }
    }
}

/** A slim glass pill: the round toggle, then the name and state beside it. */
@Composable
private fun ConnectivityPill(item: ConnectivityItem, modifier: Modifier) {
    Row(modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(FolioRadius.RAIL.dp)).background(Color.White.copy(alpha = .10f))
        .clickable(onClick = item.onClick).padding(FolioSpace.SNUG.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundToggle(item.icon, item.label, item.on, item.accent, 44.dp, item.onClick)
        Spacer(Modifier.width(FolioSpace.COMPACT.dp))
        Column(Modifier.weight(1f)) {
            Text(item.label, color = Color.White, fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ConnectivityState(item)
        }
    }
}

/** How long after the control before it a control starts to settle in, and how many controls count (the rest start with the last). */
private const val CC_SETTLE_STAGGER_MS = 6
private const val CC_SETTLE_COUNT = 8

/**
 * How a control looks while it arrives: [alpha] fades in, [scale] grows from [SCALE_FROM] to 1, and [blurRadiusDp] clears from [BLUR_DP] to nothing, all on
 * the same 0 to 1 progress, so a control comes in small, ghosted and soft and sharpens as it lands. Pure, so the ends can be tested.
 * Measured on a screen recording of an iPhone's Control Center (23 Nov 2026): tiles arrive together a few frames after the swipe, slightly small and
 * blurred, and are settled by about a quarter of a second.
 */
internal object CcSettle {
    const val SCALE_FROM = .92f
    const val BLUR_DP = 6f
    fun alpha(progress: Float): Float = progress.coerceIn(0f, 1f)
    fun scale(progress: Float): Float = SCALE_FROM + (1f - SCALE_FROM) * progress.coerceIn(0f, 1f)
    fun blurRadiusDp(progress: Float): Float = BLUR_DP * (1f - progress.coerceIn(0f, 1f))
}

/**
 * The arrival of Control Center, one shot and never a loop: once the panel is open each control fades in, grows from 92 percent and clears from a 6 dp
 * blur, [CC_SETTLE_STAGGER_MS] after the one before it (so the grid reads as arriving together), on a quick spring that is settled in about a quarter of
 * a second. The panel's own fade and lift already say "opening"; this says the controls landed. Nothing moves in edit mode (the controls are being
 * arranged), under Reduce Motion, or when [index] is negative. It starts from nothing on each open, so a closing panel is not cut short. The blur layer
 * exists only while the control is still arriving, so a settled control carries no extra render pass.
 */
@Composable
private fun Modifier.ccSettleIn(index: Int, open: Boolean, edit: Boolean): Modifier {
    if (index < 0 || edit || LocalReduceMotion.current) return this
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(open) {
        if (!open) return@LaunchedEffect
        progress.snapTo(0f)
        delay(index.coerceAtMost(CC_SETTLE_COUNT - 1) * CC_SETTLE_STAGGER_MS.toLong())
        progress.animateTo(1f, FolioMotion.spring(FolioMotion.Quick))
    }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    return graphicsLayer {
        val p = progress.value
        alpha = CcSettle.alpha(p)
        val scale = CcSettle.scale(p)
        scaleX = scale; scaleY = scale
        val radius = CcSettle.blurRadiusDp(p) * density
        renderEffect = if (radius >= .5f) androidx.compose.ui.graphics.BlurEffect(radius, radius, androidx.compose.ui.graphics.TileMode.Decal) else null
    }
}

/** A 1×1 control that wiggles with a "–" while editing. */
@Composable
private fun EditableControl(control: CcControl, on: Boolean, accent: Color, cell: Dp, edit: HomeEditMode, onRemove: () -> Unit,
    reorder: CcReorderState? = null, onMove: (moving: String, target: String) -> Unit = { _, _ -> }, onNudge: (by: Int) -> Unit = {},
    settleIndex: Int = -1, panelOpen: Boolean = true, value: CcValue = CcValue.NONE, onRun: () -> Unit) {
    Box(Modifier.ccSettleIn(settleIndex, panelOpen, edit.active).jiggle(control.name, .6f).then(
        if (reorder != null) Modifier.ccReorderable(control.name, reorder, edit.active, stringResource(R.string.move_control_earlier, stringResource(control.label)),
            stringResource(R.string.move_control_later, stringResource(control.label)), onMove) { onNudge(it) } else Modifier)) {
        SquareToggle(value.icon ?: control.icon(), stringResource(control.label), on, accent, cell, value.caption) { if (!edit.active) onRun() }
        if (edit.active) JiggleRemoveButton(stringResource(R.string.remove_control, stringResource(control.label)), insetX = (-7).dp, onRemove = onRemove)
    }
}

/** [detail] is the network or carrier name shown instead of "On" while the connection is on (null when Android does not give one). */
private data class ConnectivityItem(val icon: ImageVector, val label: String, val on: Boolean, val accent: Color, val detail: String? = null, val onClick: () -> Unit)

/** Elapsed and total position for a playing track, ticking once a second while playing. */
@Composable
private fun rememberMediaPosition(media: IslandActivity.Media): Pair<Long, Long> {
    val tick by rememberSecondTick()
    return remember(tick, media) {
        val controller = runCatching { media.controller }.getOrNull()
        val state = controller?.playbackState
        val duration = controller?.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val elapsed = state?.let {
            if (it.state == android.media.session.PlaybackState.STATE_PLAYING)
                it.position + ((android.os.SystemClock.elapsedRealtime() - it.lastPositionUpdateTime) * it.playbackSpeed).toLong()
            else it.position
        } ?: 0L
        elapsed.coerceIn(0L, duration.coerceAtLeast(0L)) to duration
    }
}

internal fun formatMediaTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/**
 * Now Playing: album art, title and transport controls in its [modifier]-sized place on the page. With [slimWhenIdle], nothing playing shrinks it to one
 * cell high (the title alone, no dead buttons) at the top of that place; the place keeps its size, so nothing else on the page moves when playback starts
 * or stops. The height follows a FolioMotion spring and snaps under Reduce Motion. [slot] is the place's full height.
 */
@Composable
private fun MediaModule(media: IslandActivity.Media?, modifier: Modifier, cell: Dp, onOpen: () -> Unit, slimWhenIdle: Boolean = false, slot: Dp = cell) {
    if (!slimWhenIdle) return MediaTile(media, modifier, cell, onOpen, slim = false)
    val reduce = LocalReduceMotion.current
    val height by androidx.compose.animation.core.animateDpAsState(if (media == null) cell else slot, if (reduce) androidx.compose.animation.core.snap() else FolioMotion.spring(FolioMotion.Sheet), label = "media tile height")
    val shown = height.coerceIn(minOf(cell, slot), maxOf(cell, slot))
    // The full card takes over once the tile has grown past a cell and a half, and the slim row again when it shrinks back below that.
    Box(modifier) { MediaTile(media, Modifier.fillMaxWidth().height(shown), cell, onOpen, slim = shown < cell * 1.5f) }
}

/** Now Playing (2×2): album art, title and transport controls; or, [slim], one row with the title alone. */
@Composable
internal fun MediaTile(media: IslandActivity.Media?, modifier: Modifier, cell: Dp, onOpen: () -> Unit, slim: Boolean) {
    val artAccent = if (LocalTintOptions.current.media) rememberAccent(media?.art) else null
    val tint by androidx.compose.animation.animateColorAsState(mixColor(LocalCcLook.current.tile, artAccent, .45f), label = "music tint")
    val context = LocalContext.current
    val appLabel = media?.packageName?.let { pkg ->
        remember(pkg) { runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull() }
    }
    val nowPlayingLabel = stringResource(R.string.now_playing)
    val playingLabel = stringResource(R.string.playing)
    val pausedLabel = stringResource(R.string.paused)
    val playLabel = stringResource(R.string.play)
    val pauseLabel = stringResource(R.string.pause)
    val notPlayingLabel = stringResource(R.string.not_playing)
    val description = if (media != null) "$nowPlayingLabel, ${media.title}" +
        (media.subtitle?.let { ", $it" } ?: "") + (appLabel?.let { ", $it" } ?: "") + ", " + (if (media.playing) playingLabel else pausedLabel)
    else notPlayingLabel
    Module(modifier.clickable(enabled = media != null, onClick = onOpen)
        .semantics(mergeDescendants = true) {
            contentDescription = description
        }, color = tint) {
        if (slim) Row(Modifier.fillMaxSize().padding(horizontal = cell * .16f), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MusicNote, null, tint = FolioGlass.secondary, modifier = Modifier.size(cell * .34f))
            Spacer(Modifier.width(FolioSpace.SMALL.dp))
            // Two lines, so "Not Playing" stays whole at large text instead of shortening to "Not Playi…".
            Text(media?.title ?: stringResource(R.string.not_playing), color = if (media != null) Color.White else FolioGlass.secondary,
                fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        } else Column(Modifier.fillMaxSize().padding(top = cell * .16f), verticalArrangement = Arrangement.SpaceBetween) {
            // The sides are padded row by row: the transport row below runs the full width, so each of its three controls is a third of the tile.
            Row(Modifier.padding(horizontal = cell * .16f), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(cell * .58f).clip(RoundedCornerShape(cell * .13f)).background(FolioGlass.raised), contentAlignment = Alignment.Center) {
                    val image = media?.art ?: media?.icon
                    if (image != null) Image(image.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    else Icon(Icons.Rounded.MusicNote, null, tint = FolioGlass.secondary, modifier = Modifier.size(cell * .26f))
                }
                Spacer(Modifier.width(cell * .1f))
                Column(Modifier.weight(1f)) {
                    Text(media?.title ?: stringResource(R.string.not_playing), color = if (media != null) Color.White else FolioGlass.secondary,
                        fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    media?.subtitle?.let { Text(it, color = FolioGlass.secondary, fontSize = FolioType.GROUP_LABEL.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            if (media != null) Box(Modifier.padding(horizontal = cell * .16f)) { MediaScrubber(media, artAccent?.let { Color(vividTint(it.toArgb())) }) } else Spacer(Modifier.height(1.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val t = media?.controller?.transportControls
                val tint = Color.White.copy(alpha = if (t != null) 1f else .35f)
                // Each control draws as small as before and takes a third of the tile's width and at least 48 dp of height (A11Y-1): the glyph is not the only part that answers a tap.
                Box(Modifier.testTag("cc-previous").weight(1f).heightIn(min = FolioTouch.MIN.dp).clip(CircleShape).clickable(t != null) { t?.skipToPrevious() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.FastRewind, stringResource(R.string.previous), tint = tint, modifier = Modifier.size(cell * .34f))
                }
                Box(Modifier.testTag("cc-play").weight(1f).heightIn(min = FolioTouch.MIN.dp).clip(CircleShape).clickable(t != null) { if (media?.playing == true) t?.pause() else t?.play() },
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(cell * .46f).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        Icon(if (media?.playing == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            stringResource(if (media?.playing == true) R.string.pause else R.string.play),
                            tint = if (t != null) Color.Black else Color.Black.copy(alpha = .35f), modifier = Modifier.size(cell * .26f))
                    }
                }
                Box(Modifier.testTag("cc-next").weight(1f).heightIn(min = FolioTouch.MIN.dp).clip(CircleShape).clickable(t != null) { t?.skipToNext() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.FastForward, stringResource(R.string.next), tint = tint, modifier = Modifier.size(cell * .34f))
                }
            }
        }
    }
}

/** The scrub line plus elapsed and remaining times, so the control center shows real progress, not just a bare line. */
@Composable
private fun MediaScrubber(media: IslandActivity.Media, accent: Color?) {
    val (position, duration) = rememberMediaPosition(media)
    if (duration <= 0L) return
    var scrub by remember { mutableStateOf<Float?>(null) }
    val shown = scrub?.let { (it * duration).toLong() } ?: position
    // The line and the times under it are one place to tap or drag, so the touch area is more than the 3 dp the line is drawn with.
    Column(Modifier.padding(top = 3.dp).mediaSeekable(media, duration) { scrub = it }) {
        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = .22f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((shown.toFloat() / duration).coerceIn(0f, 1f)).background(accent ?: Color.White))
        }
        Row(Modifier.fillMaxWidth().padding(top = 1.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMediaTime(shown), color = FolioGlass.secondary, fontSize = FolioType.GROUP_LABEL.sp)
            Text("-" + formatMediaTime(duration - shown), color = FolioGlass.secondary, fontSize = FolioType.GROUP_LABEL.sp)
        }
    }
}

/** Whether the player lets a track be moved to a place ([PlaybackState.ACTION_SEEK_TO]); some, like a live stream, do not. */
internal fun mediaCanSeek(actions: Long?): Boolean = ((actions ?: 0L) and android.media.session.PlaybackState.ACTION_SEEK_TO) != 0L

/** The track position, in milliseconds, a finger at [x] of [width] points to on a line [duration] long; always inside the track. */
internal fun mediaSeekTarget(x: Float, width: Float, duration: Long): Long =
    if (width <= 0f || duration <= 0L) 0L else (duration * (x / width).coerceIn(0f, 1f)).toLong()

/**
 * Lets a finger tap or drag along a progress line to move the track there, when the player allows it. [onScrub] gets the finger's place (0 to 1)
 * while it is down, so the line and times follow it, and null when it lifts, which is when the player is asked to seek. Right to left layouts
 * mirror the line, so the finger's place is mirrored back.
 */
@Composable
private fun Modifier.mediaSeekable(media: IslandActivity.Media, duration: Long, onScrub: (Float?) -> Unit): Modifier {
    if (!mediaCanSeek(runCatching { media.controller.playbackState?.actions }.getOrNull())) return this
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val latestScrub = androidx.compose.runtime.rememberUpdatedState(onScrub)
    fun seek(x: Float, width: Int) {
        val at = if (rtl) width - x else x
        runCatching { media.controller.transportControls.seekTo(mediaSeekTarget(at, width.toFloat(), duration)) }
    }
    return this
        .pointerInput(media, duration, rtl) { detectTapGestures { seek(it.x, size.width) } }
        .pointerInput(media, duration, rtl) {
            var last = 0f
            detectHorizontalDragGestures(
                onDragStart = { last = if (rtl) size.width - it.x else it.x; latestScrub.value((last / size.width).coerceIn(0f, 1f)) },
                onHorizontalDrag = { change, by -> change.consume(); last = (last + (if (rtl) -by else by)).coerceIn(0f, size.width.toFloat()); latestScrub.value(last / size.width) },
                onDragEnd = { latestScrub.value(null); seek(if (rtl) size.width - last else last, size.width) },
                onDragCancel = { latestScrub.value(null) },
            )
        }
}

@Composable
private fun Module(modifier: Modifier, color: Color? = null, content: @Composable BoxScope.() -> Unit) {
    val look = LocalCcLook.current
    Box(modifier.clip(RoundedCornerShape(26.dp)).background(color ?: look.tile)
        .border(look.edge, RoundedCornerShape(26.dp)), content = content)
}

/**
 * A tile dips a little under the finger and springs back (McCal 25 Oct 2026, finishing touches): a spring from FolioMotion that follows the finger and
 * can be interrupted, scale and alpha read while drawing, and under Reduce Motion no scale at all, only the dim (DYN-6, DYN-11).
 */
@Composable
private fun Modifier.ccPress(pressed: Boolean): Modifier {
    val reduce = LocalReduceMotion.current
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed && !reduce) CC_PRESS_SCALE else 1f, FolioMotion.spring(FolioMotion.Quick), label = "tile press")
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (pressed) .88f else 1f, FolioMotion.spring(FolioMotion.Quick), label = "tile press alpha")
    return graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
}

private const val CC_PRESS_SCALE = .94f

/**
 * The page a swipe along the page strip lands on: a drag of [travel] pixels (down is positive) that is at least [threshold] moves one page, down to
 * the next and up back, and never past either end. Shorter drags stay where they are.
 */
internal fun ccStripSwipeTarget(current: Int, count: Int, travel: Float, threshold: Float): Int = when {
    travel >= threshold -> (current + 1).coerceAtMost(count - 1)
    travel <= -threshold -> (current - 1).coerceAtLeast(0)
    else -> current
}

/** The leaving page thins out as it goes (a quarter of its weight left a whole page away), so a page half-way is still readable. */
internal fun ccPageAlpha(away: Float): Float = 1f - .75f * away.coerceIn(0f, 1f)

/** How far up the panel has to be pulled to close, and how fast a flick (px per second) closes it from less. */
internal const val CC_SWIPE_AWAY_DP = 96f
/** A flick up faster than this closes Control Center, in dp per second (about what 1400 px per second was on the Fold's 2.6 density), so a dense screen needs no lighter a flick. */
internal const val CC_SWIPE_AWAY_FLICK_DP = 530f
private const val CC_PAGE_SHRINK = .05f

@Composable
internal fun RoundToggle(icon: ImageVector, label: String, on: Boolean, accent: Color, size: Dp, onClick: () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val stateText = stringResource(if (on) R.string.state_on else R.string.state_off)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(Modifier.size(size).ccPress(pressed).clip(CircleShape).background(if (on) accent else LocalCcLook.current.toggleOff)
        .clickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current) { haptic.toggle(!on); onClick() }
        .semantics { contentDescription = label; stateDescription = stateText; role = androidx.compose.ui.semantics.Role.Switch },
        contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * .46f))
    }
}

@Composable
private fun SquareToggle(icon: ImageVector, label: String, on: Boolean, accent: Color, size: Dp, caption: String? = null, onClick: () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    // A tile that holds a value says it too, so TalkBack reads "Sound mode, Vibrate" and not just on or off.
    val state = caption?.takeIf { it.isNotEmpty() } ?: stringResource(if (on) R.string.state_on else R.string.state_off)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(Modifier.size(size).ccPress(pressed).clip(RoundedCornerShape(size * .3f)).background(if (on) accent else LocalCcLook.current.tile)
        .clickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, role = androidx.compose.ui.semantics.Role.Button) { haptic.toggle(!on); onClick() }
        .semantics { contentDescription = label; stateDescription = state }, contentAlignment = Alignment.Center) {
        val ink = if (on && accent == Color.White) Color.Black else Color.White
        if (caption.isNullOrEmpty()) Icon(icon, null, tint = ink, modifier = Modifier.size(size * .4f))
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = ink, modifier = Modifier.size(size * .34f))
            // Short on purpose (Ring, 30s, Silent): at the largest text size it still fits a one-cell tile on one line.
            Text(caption, color = ink, fontSize = ccTileSp(FolioType.CAPTION.toFloat()), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = size * .06f))
        }
    }
}

/** The volume as a wide bar under Now Playing: a speaker icon, a fill that follows a drag or tap along it, and the percent. */
@Composable
private fun MediaVolumeBar(value: Float, width: Dp, cell: Dp, ticks: Boolean, enabled: Boolean, onValue: (Float) -> Unit) {
    val shown by androidx.compose.animation.core.animateFloatAsState(value.coerceIn(0f, 1f),
        if (LocalReduceMotion.current) androidx.compose.animation.core.snap() else FolioMotion.spring(FolioMotion.Snap), label = "media volume")
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val label = stringResource(R.string.volume)
    val icon = when {
        shown <= .01f -> Icons.AutoMirrored.Rounded.VolumeOff
        shown < .5f -> Icons.AutoMirrored.Rounded.VolumeDown
        else -> Icons.AutoMirrored.Rounded.VolumeUp
    }
    val height = maxOf(cell * .5f, FolioTouch.MIN.dp)
    Box(Modifier.size(width, height).clip(RoundedCornerShape(height / 2)).background(LocalCcLook.current.tile)
        .then(if (!enabled) Modifier else Modifier
            .pointerInput(Unit) { detectTapGestures { at -> onValue((at.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                var last = -1f
                detectHorizontalDragGestures(onDragStart = { last = -1f }) { change, _ ->
                    change.consume()
                    val v = (change.position.x / size.width).coerceIn(0f, 1f)
                    if (ticks && (v == 0f || v == 1f) && v != last) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentFrequentTick)
                    last = v
                    onValue(v)
                }
            })
        .semantics {
            contentDescription = label
            progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(value, 0f..1f)
            if (enabled) setProgress { target -> onValue(target.coerceIn(0f, 1f)); true }
        }) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(shown).background(Color.White))
        Icon(icon, null, tint = if (shown > .1f) FolioColors.MenuSurface else Color.White, modifier = Modifier.align(Alignment.CenterStart).padding(start = height * .28f).size(height * .44f))
        Text((shown * 100).toInt().toString() + "%", color = if (shown > .9f) FolioColors.MenuSurface else Color.White, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = height * .3f).clearAndSetSemantics {})
    }
}

@Composable
private fun TallSlider(icon: ImageVector, label: String, value: Float, width: Dp, height: Dp,
    onStart: () -> Unit = {}, onHold: (Boolean) -> Unit = {}, ticks: Boolean = true, badge: (@Composable BoxScope.(Float) -> Unit)? = null, onLongPress: (() -> Unit)? = null, onValue: (Float) -> Unit) {
    // A change from outside (a volume key, automatic brightness) moves the fill with the same no-overshoot spring as a drag,
    // and not at all under Reduce Motion. It never ticks: the haptics below run only from the finger.
    val shown by androidx.compose.animation.core.animateFloatAsState(value.coerceIn(0f, 1f),
        if (LocalReduceMotion.current) androidx.compose.animation.core.snap() else FolioMotion.spring(FolioMotion.Snap), label = label)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
    val readout by androidx.compose.animation.core.animateFloatAsState(if (dragging) 1f else 0f, FolioMotion.spring(FolioMotion.Quick), label = "slider readout")
    val holdLabel = stringResource(R.string.cc_show_options)
    Box(Modifier.size(width, height).clip(RoundedCornerShape(width * .36f)).background(LocalCcLook.current.tile)
        // A press and hold that does not move opens the control's card; a drag that starts first cancels it.
        .then(if (onLongPress == null) Modifier else Modifier.pointerInput(Unit) {
            detectTapGestures(onLongPress = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onLongPress() })
        })
        .pointerInput(Unit) {
            var last = -1f
            detectVerticalDragGestures(onDragStart = { onStart(); onHold(true); last = -1f; dragging = true },
                onDragEnd = { dragging = false; onHold(false) }, onDragCancel = { dragging = false; onHold(false) }) { change, _ ->
                change.consume()
                val v = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                // Like iOS: a tick when the slider hits its top or bottom.
                if (ticks && (v == 0f || v == 1f) && v != last) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentFrequentTick)
                last = v
                onValue(v)
            }
        }
        .semantics {
            contentDescription = label
            progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(value, 0f..1f)
            setProgress { target -> onStart(); onValue(target.coerceIn(0f, 1f)); true } // TalkBack can adjust it
            if (onLongPress != null) onLongClick(holdLabel) { onLongPress(); true }
        }) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(shown).background(Color.White))
        Icon(icon, null, tint = if (shown > .18f) FolioColors.MenuSurface else Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = width * .22f).size(width * .36f))
        // The number while a finger is on it, over the part above the fill: it fades in and out and never moves the slider.
        // Dark once the white fill reaches it, like the icon below, so it stays readable at 100%.
        val readoutOnFill = shown > 1f - (width * .22f + FolioType.FOOTNOTE.dp / 2) / height
        Text((shown * 100).toInt().toString(), color = if (readoutOnFill) FolioColors.MenuSurface else Color.White, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = width * .22f).graphicsLayer { alpha = readout })
        // A chip at the top (Auto brightness) steps aside for the number while a finger is on the slider.
        badge?.invoke(this, 1f - readout)
    }
}

/**
 * Auto brightness as a chip on the brightness slider: green while the phone sets the brightness, dark while the person does.
 * A switch to TalkBack, named "Auto brightness"; its target is the slider's width by 48 dp, the drawn pill smaller.
 */
@Composable
private fun BoxScope.AutoBrightnessChip(on: Boolean, width: Dp, alpha: Float, onClick: () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val name = stringResource(R.string.cc_auto_brightness)
    val stateText = stringResource(if (on) R.string.state_on else R.string.state_off)
    // The word follows the font size only up to 1.3 times: it lives on a slider as wide as one cell (the full size is in the words for TalkBack).
    val font = with(LocalDensity.current) { (FolioType.FOOTNOTE * minOf(fontScale, 1.3f)).dp.toSp() }
    Box(Modifier.align(Alignment.TopCenter).size(width, FolioTouch.MIN.dp).graphicsLayer { this.alpha = alpha }
        .clickable(role = androidx.compose.ui.semantics.Role.Switch) { haptic.toggle(!on); onClick() }
        .semantics(mergeDescendants = true) { contentDescription = name; stateDescription = stateText },
        contentAlignment = Alignment.Center) {
        Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) AccentGreen else FolioColors.MenuSurface.copy(alpha = .72f))
            // The pill stays inside a slider that is one small cell wide (six and eight columns, a narrow window): "Auto" was cut off at its edge.
            .padding(horizontal = (width * .1f).coerceAtMost(FolioSpace.COMPACT.dp), vertical = FolioSpace.TINY.dp)) {
            Text(stringResource(R.string.cc_auto), color = if (on) FolioColors.MenuSurface else Color.White, fontSize = font, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

/** The narrowest grid that has room for the two-icon pill between the edit, close and power buttons (three 48 dp targets, the pill, and a little air). */
private const val CC_SWITCH_PILL_MIN_GRID_DP = 280

/** The height of the row the switch stands in at the top of a panel (the 48 dp touch target of its buttons), kept free where the switch is drawn once above both panels. */
private const val PANEL_SWITCH_ROW_DP = 48

/** The widest the preview pane's Notification Center grows: past it, on a big tablet or a monitor, the list and the pane stop stretching. */
private const val LIST_DETAIL_MAX_DP = 1100

/** The list's share of the width beside the preview pane; the pane takes the rest (the Lab's wide-panels-live split). */
private const val LIST_DETAIL_LIST_SHARE = .42f

/** A window shorter than this keeps the switch inside each panel: the extra row would cost Control Center a page. */
private const val HOIST_SWITCH_MIN_HEIGHT_DP = 480

/** Narrower than that and the header holds only its three buttons and one more: the switch becomes a single round button. */
private const val CC_SWITCH_BUTTON_MIN_GRID_DP = 200

/**
 * Moves between Notification Center and Control Center (FeatureGate.PANEL_SWITCH): two icons in a glass pill, the panel that is open filled. Each is a 48 dp
 * target with its own name for TalkBack; the fill eases in unless Reduce Motion is on, and the icons follow the font size like the other header buttons.
 */
@Composable
internal fun PanelSwitch(current: ShadePanel, onSwitch: (ShadePanel) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    // Where the header has no room for the pill, one round button opens the other panel (its icon says which).
    if (compact) {
        val other = if (current == ShadePanel.NOTIFICATIONS) ShadePanel.QUICK_SETTINGS else ShadePanel.NOTIFICATIONS
        Box(modifier.testTag("panel-switch")) {
            GlassIconButton(if (other == ShadePanel.NOTIFICATIONS) Icons.Rounded.Notifications else Icons.Rounded.Tune,
                stringResource(if (other == ShadePanel.NOTIFICATIONS) R.string.open_notification_center else R.string.open_control_center)) { onSwitch(other) }
        }
        return
    }
    val grow = ccIconScale(LocalDensity.current.fontScale)
    val reduce = LocalReduceMotion.current
    val segments = listOf(
        Triple(ShadePanel.NOTIFICATIONS, Icons.Rounded.Notifications, stringResource(R.string.notification_center)),
        Triple(ShadePanel.QUICK_SETTINGS, Icons.Rounded.Tune, stringResource(R.string.control_center)),
    )
    Row(modifier.clip(CircleShape).background(NotifGlass).padding(horizontal = FolioSpace.TINY.dp).selectableGroup().testTag("panel-switch"), verticalAlignment = Alignment.CenterVertically) {
        segments.forEach { (panel, icon, label) ->
            val on = panel == current
            val fill by androidx.compose.animation.core.animateFloatAsState(if (on) .3f else 0f, if (reduce) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(220), label = "panel switch fill")
            Box(Modifier.minimumInteractiveComponentSize().selectable(selected = on, role = Role.Tab, onClick = { if (!on) onSwitch(panel) }).semantics { contentDescription = label },
                contentAlignment = Alignment.Center) {
                Box(Modifier.size((34 * grow).dp).clip(CircleShape).background(Color.White.copy(alpha = fill)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size((18 * grow).dp))
                }
            }
        }
    }
}

@Composable
private fun GlassIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    // The drawn circle and its icon follow the font size the person chose (up to a third larger), inside a target that never drops below 48 dp.
    val grow = ccIconScale(LocalDensity.current.fontScale)
    Box(Modifier.minimumInteractiveComponentSize().clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center) { Box(Modifier.size((34 * grow).dp).clip(CircleShape).background(NotifGlass), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size((18 * grow).dp))
    } }
}

@Composable
private fun PanelPill(label: String, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = FolioTouch.MIN.dp).clip(RoundedCornerShape(50)).background(NotifGlass).clickable(onClick = onClick)
        .padding(horizontal = FolioSpace.COMFY.dp, vertical = FolioSpace.MEDIUM.dp), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyNote(text: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.NOTIFICATION.dp)).background(NotifGlass).padding(FolioSpace.XL.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp)) {
        Text(text, color = Color.White.copy(alpha = .85f), fontSize = FolioType.COMPACT.sp)
        if (action != null) PanelPill(action, onAction)
    }
}

private val NotifGlass = FolioGlass.card
private val AccentBlue = FolioColors.Blue
private val AccentGreen = FolioColors.Green
private val AccentOrange = FolioColors.Orange
private val AccentPurple = FolioColors.Indigo
private val AccentRed = FolioColors.Red

private class DeviceControls(private val context: Context) {
    private val camera = context.getSystemService(CameraManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val torchId = runCatching {
        camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()

    val hasTorch get() = torchId != null
    var torchOn by mutableStateOf(false); private set
    // Both sliders follow the phone while the panel is up (SystemLevels.kt): the side keys, the system volume panel, a mute,
    // automatic brightness. The volume slider is the music stream. A held slider ignores outside changes until release.
    private val volumeLevel = LiveLevel(0f)
    private val brightnessLevel = LiveLevel(.5f)
    val volume get() = volumeLevel.value
    val brightness get() = brightnessLevel.value
    /** Whether the phone sets the brightness by itself, and whether it says (the setting is missing on some). Follows the system, like the sliders. */
    var brightnessAuto by mutableStateOf(false); private set
    var autoBrightnessKnown by mutableStateOf(false); private set
    private var manualModeSet = false
    private fun applyMode(reading: SystemReading) {
        brightnessAuto = reading.brightnessMode == BrightnessMode.AUTO
        autoBrightnessKnown = reading.brightnessMode != BrightnessMode.UNKNOWN
        // Automatic brightness, or another app, took the mode: the next drag must switch it back to manual.
        manualModeSet = reading.brightnessManual
    }
    var rotationLocked by mutableStateOf(false); private set
    var dndOn by mutableStateOf(false); private set
    var bluetoothOn by mutableStateOf(false); private set
    var powerSaveOn by mutableStateOf(false); private set
    /** The ringer (AudioManager.RINGER_MODE_*), the screen timeout in ms (0 when unknown) and Night Light, re-read whenever the volume observers fire. */
    var ringer by mutableStateOf(AudioManager.RINGER_MODE_NORMAL); private set
    var screenTimeoutMs by mutableStateOf(0); private set
    var nightLightOn by mutableStateOf(false); private set

    private fun readValues() {
        ringer = runCatching { audio.ringerMode }.getOrDefault(AudioManager.RINGER_MODE_NORMAL)
        screenTimeoutMs = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT) }.getOrDefault(0)
        nightLightOn = runCatching { Settings.Secure.getInt(context.contentResolver, NIGHT_LIGHT_KEY, 0) == 1 }.getOrDefault(false)
    }

    /** Steps Ring, Vibrate, (Silent where Do Not Disturb access lets Folio set it). False when nothing could be set, so the tile can open the access page. */
    fun cycleRinger(): Boolean {
        val next = ccNextRinger(ringer, notifications.isNotificationPolicyAccessGranted)
        val set = runCatching { audio.ringerMode = next }.isSuccess
        if (set) ringer = runCatching { audio.ringerMode }.getOrDefault(next)
        return set
    }

    /** Steps the screen timeout through [CC_TIMEOUTS]. False when "Modify system settings" has not been granted. */
    fun cycleScreenTimeout(): Boolean {
        if (!Settings.System.canWrite(context)) return false
        val next = ccNextTimeout(screenTimeoutMs)
        val set = runCatching { Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, next) }.isSuccess
        if (set) screenTimeoutMs = next
        return set
    }

    /** Switches Night Light when Folio holds WRITE_SECURE_SETTINGS; false when it does not (the tile then opens Night Light's own page). */
    fun toggleNightLight(): Boolean {
        if (context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return false
        val set = runCatching { Settings.Secure.putInt(context.contentResolver, NIGHT_LIGHT_KEY, if (nightLightOn) 0 else 1) }.isSuccess
        if (set) nightLightOn = !nightLightOn
        return set
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) {
                val was = torchOn
                torchOn = enabled
                if (enabled != was) IslandEvents.post(IslandEvent.Torch(enabled))
            }
        }
    }

    private val levels = SystemLevels(context) { reading ->
        volumeLevel.fromOutside(reading.volume)
        brightnessLevel.fromOutside(reading.brightness)
        applyMode(reading)
        readValues()
    }

    fun start() {
        runCatching { camera.registerTorchCallback(torchCallback, null) }
        levels.read().let { volumeLevel.fromOutside(it.volume); brightnessLevel.fromOutside(it.brightness); applyMode(it) }
        levels.start()
        rotationLocked = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 0 }.getOrDefault(false)
        dndOn = notifications.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
        bluetoothOn = runCatching { context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.isEnabled == true }.getOrDefault(false)
        powerSaveOn = runCatching { context.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true }.getOrDefault(false)
        readValues()
    }

    /** Unregisters everything start() registered: the torch callback and the volume, brightness and screen observers. */
    fun stop() { levels.stop(); runCatching { camera.unregisterTorchCallback(torchCallback) } }

    /** A finger went down on, or came up from, a slider. On release the latest outside change lands, then the real value is read. */
    fun holdVolume(down: Boolean) { if (down) volumeLevel.hold() else { volumeLevel.release(); levels.requestRefresh() } }
    fun holdBrightness(down: Boolean) { if (down) brightnessLevel.hold() else { brightnessLevel.release(); levels.requestRefresh() } }

    /** A stream's volume as 0 to 1 (Ring and Alarm as well as Media, for the volume card). */
    fun streamFraction(stream: Int): Float {
        val max = audio.getStreamMaxVolume(stream); val min = audio.getStreamMinVolume(stream)
        return if (max <= min) 0f else ((audio.getStreamVolume(stream) - min).toFloat() / (max - min)).coerceIn(0f, 1f)
    }

    /** Sets a stream other than Media; Android may refuse Ring while Do Not Disturb is on, which is left as it was. */
    fun setStreamVolume(stream: Int, value: Float) {
        val max = audio.getStreamMaxVolume(stream); val min = audio.getStreamMinVolume(stream)
        runCatching { audio.setStreamVolume(stream, volumeStep(value, min, max), 0) }
    }

    /** Shows Android's own volume panel. */
    fun showVolumePanel() { runCatching { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI) } }

    fun toggleTorch() { torchId?.let { id -> runCatching { camera.setTorchMode(id, !torchOn) } } }

    fun changeVolume(value: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val min = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, volumeStep(value, min, max), 0); volumeLevel.fromUser(value) }
    }

    /** Returns false when "Modify system settings" hasn't been granted yet. */
    fun changeBrightness(value: Float): Boolean {
        if (!Settings.System.canWrite(context)) return false
        val level = (value * 255).toInt().coerceIn(1, 255)
        if (level == (brightness * 255).toInt()) return true // skip identical writes while dragging
        brightnessLevel.fromUser(value)
        runCatching {
            // Dragging the slider means the person is choosing the level, so Auto goes off (the Auto chip shows it and turns it back on).
            if (!manualModeSet) {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                manualModeSet = true
                brightnessAuto = false
            }
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, level)
        }
        return true
    }

    /** Turns Auto brightness on or off from the chip. Returns false when "Modify system settings" hasn't been granted yet. */
    fun setBrightnessAuto(on: Boolean): Boolean {
        if (!Settings.System.canWrite(context)) return false
        runCatching {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            brightnessAuto = on
            manualModeSet = !on
        }
        levels.requestRefresh()
        return true
    }

    fun toggleRotationLock(): Boolean {
        if (!Settings.System.canWrite(context)) return false
        runCatching {
            Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (rotationLocked) 1 else 0)
            rotationLocked = !rotationLocked
        }
        return true
    }

    /** Returns false when Do Not Disturb access hasn't been granted yet. */
    fun toggleDnd(): Boolean {
        if (!notifications.isNotificationPolicyAccessGranted) return false
        notifications.setInterruptionFilter(if (dndOn) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        dndOn = !dndOn
        return true
    }

    fun writeSettingsIntent() = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
}


private fun androidx.compose.ui.hapticfeedback.HapticFeedback.toggle(on: Boolean) = performHapticFeedback(
    if (on) androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOn else androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOff)

internal enum class MusicShape { STRIP, CARD, POSTER }

/** Which of the three shapes the Music widget draws at this size (dp). Pure, so the thresholds are tested. */
internal fun musicShape(widthDp: Float, heightDp: Float): MusicShape = when {
    heightDp < 110f -> MusicShape.STRIP
    widthDp >= 160f && heightDp >= 180f -> MusicShape.POSTER
    else -> MusicShape.CARD
}

/**
 * The playing track as a placeable Home widget: a compact Strip at short spans, a big Poster at tall or square
 * ones. One widget that morphs with its size, like the Big Clock, rather than two things to pick between.
 * LW audit 2026-10-02 Style B and C. Hides itself (shows "Nothing Playing") when there is no media session.
 */
@Composable
internal fun MusicWidgetCard(onEdit: () -> Unit) {
    val media = IslandListenerService.media.collectAsStateWithLifecycle().value
    val artAccent = if (LocalTintOptions.current.media) rememberAccent(media?.art) else null
    // Lifted the same way MediaModule's scrubber is: raw album-art colors are often too dark to read as a thin
    // line or a bar of wave bars against the Poster's blurred backdrop.
    val accent = artAccent?.let { Color(vividTint(it.toArgb())) } ?: Color.White
    val notPlayingLabel = stringResource(R.string.not_playing)
    val playingLabel = stringResource(R.string.playing)
    val pausedLabel = stringResource(R.string.paused)
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(FolioRadius.PANEL.dp))
        // Tappable either way: empty, it opens widget options the same as every other built-in widget does when
        // there's nothing to show. It used to do nothing at all when nothing was playing.
        .background(FolioGlass.module).clickable(onClick = onEdit)
        .semantics(mergeDescendants = true) {
            contentDescription = media?.let { "${it.title}${it.subtitle?.let { s -> ", $s" } ?: ""}, ${if (it.playing) playingLabel else pausedLabel}" } ?: notPlayingLabel
        }) {
        // Strip below ~110dp; Poster once there's room for big centered art and a wave; Card in between, the same
        // shape as the Control Center's own now-playing tile. Nothing playing uses the same three shapes, not one small
        // icon floating in whatever space a resized widget happens to have.
        val shape = musicShape(maxWidth.value, maxHeight.value)
        if (media == null) when (shape) {
            MusicShape.STRIP -> EmptyMusicStrip(notPlayingLabel)
            MusicShape.POSTER -> EmptyMusicPoster(notPlayingLabel)
            MusicShape.CARD -> EmptyMusicCard(notPlayingLabel)
        } else when (shape) {
            MusicShape.STRIP -> MusicStrip(media, accent)
            MusicShape.POSTER -> MusicPoster(media, accent)
            MusicShape.CARD -> MusicCard(media, accent)
        }
    }
}

/** Sp to dp at the person's font size and display size, for the Music widget's text rows (see [musicCardPlan]). */
@Composable
private fun rememberSpToDp(): (Float) -> Float {
    val density = LocalDensity.current
    return remember(density) { { sp: Float -> with(density) { sp.sp.toDp().value } } }
}

@Composable
private fun MusicCard(media: IslandActivity.Media, accent: Color) {
    val t = media.controller.transportControls
    val (_, duration) = rememberMediaPosition(media)
    val spToDp = rememberSpToDp()
    BoxWithConstraints(Modifier.fillMaxSize().padding(14.dp)) {
        // Same fixed-budget approach as the Poster: the art gets whatever height is left over after everything
        // else, never a weight negotiation that starved it to zero the first time this was tried.
        // The text rows are worked out from their type tokens at the font size in use, so a large font takes its room from the art, then the scrubber and the artist, and never from the title or the controls.
        val plan = musicCardPlan(maxHeight.value, minOf(maxWidth * .3f, 64.dp).value, FolioType.SUBHEAD, FolioType.FOOTNOTE, media.subtitle != null, duration > 0L,
            fixedDp = 8f, controlsDp = FolioTouch.MIN.toFloat(), spToDp = spToDp)
        val art = plan.artDp.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(art).clip(RoundedCornerShape(art * .2f)).background(FolioGlass.raised), contentAlignment = Alignment.Center) {
                    val image = media.art ?: media.icon
                    if (image != null) Image(image.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    else Icon(Icons.Rounded.MusicNote, null, tint = FolioGlass.secondary, modifier = Modifier.size(art * .4f))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(media.title, color = Color.White, fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    media.subtitle?.takeIf { plan.subtitle }?.let { Text(it, color = FolioGlass.secondary, fontSize = FolioType.FOOTNOTE.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            if (plan.scrubber) MediaScrubber(media, accent)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                // Each control is a 48 dp touch box (A11Y-1) around its glyph, so the small glyphs are not the only tappable part.
                Box(Modifier.size(FolioTouch.MIN.dp).clip(CircleShape).clickable { t.skipToPrevious() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.FastRewind, stringResource(R.string.previous), tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Box(Modifier.size(FolioTouch.MIN.dp).clip(CircleShape).clickable { if (media.playing) t.pause() else t.play() }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        Icon(if (media.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (media.playing) R.string.pause else R.string.play),
                            tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                }
                Box(Modifier.size(FolioTouch.MIN.dp).clip(CircleShape).clickable { t.skipToNext() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.FastForward, stringResource(R.string.next), tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun MusicStrip(media: IslandActivity.Media, accent: Color) {
    val t = media.controller.transportControls
    val (position, duration) = rememberMediaPosition(media)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // A narrow Strip (a cover screen, a split window) keeps its title: the next button goes first, then the art (see musicStripPlan).
        val plan = musicStripPlan(maxWidth.value)
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (plan.art) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).background(FolioGlass.raised), contentAlignment = Alignment.Center) {
                    val image = media.art ?: media.icon
                    if (image != null) Image(image.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    else Icon(Icons.Rounded.MusicNote, null, tint = FolioGlass.secondary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(media.title, color = Color.White, fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                media.subtitle?.let { Text(it, color = FolioGlass.secondary, fontSize = FolioType.GROUP_LABEL.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Box(Modifier.size(FolioTouch.MIN.dp).clip(CircleShape).clickable { if (media.playing) t.pause() else t.play() }, contentAlignment = Alignment.Center) {
                Icon(if (media.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (media.playing) R.string.pause else R.string.play),
                    tint = Color.White, modifier = Modifier.size(28.dp))
            }
            if (plan.next) Box(Modifier.size(FolioTouch.MIN.dp).clip(CircleShape).clickable { t.skipToNext() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.FastForward, stringResource(R.string.next), tint = Color.White.copy(alpha = .85f), modifier = Modifier.size(22.dp))
            }
        }
        if (duration > 0L) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f)).background(accent))
        }
    }
}

@Composable
private fun MusicPoster(media: IslandActivity.Media, accent: Color) {
    val t = media.controller.transportControls
    val (position, duration) = rememberMediaPosition(media)
    val reduceMotion = LocalReduceMotion.current
    val image = media.art ?: media.icon
    // Built once: the graphics layer block runs on every draw, and a blur effect is not free to make.
    val backdropBlur = remember { android.graphics.RenderEffect.createBlurEffect(60f, 60f, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect() }
    val spToDp = rememberSpToDp()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (image != null) Image(image.asImageBitmap(), null, Modifier.fillMaxSize()
            .graphicsLayer { renderEffect = backdropBlur; alpha = .55f }
            .background(Color.Black), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .3f)))
        // Everything but the art has a fixed height, so the art gets whatever is left over (never a weight
        // negotiation, which can starve it to zero when the rest of the card already fills the box). The wave
        // only shows once there is enough left over for a worthwhile art size with it still on screen.
        val plan = musicCardPlan(maxHeight.value, Float.MAX_VALUE, FolioType.BODY, FolioType.FOOTNOTE, media.subtitle != null, duration > 0L,
            fixedDp = 8f + 8f + 28f, controlsDp = 52f, spToDp = spToDp)
        val art = plan.artDp.dp
        val showWave = art - 26.dp >= 56.dp
        val artSize = (if (showWave) art - 26.dp else art).coerceIn(32.dp, maxOf(32.dp, minOf(maxWidth * .6f, 120.dp)))
        Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(artSize).clip(RoundedCornerShape(artSize * .12f)).background(FolioGlass.raised).shadow(8.dp, RoundedCornerShape(artSize * .12f)), contentAlignment = Alignment.Center) {
                if (image != null) Image(image.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                else Icon(Icons.Rounded.MusicNote, null, tint = FolioGlass.secondary, modifier = Modifier.size(artSize * .36f))
            }
            Spacer(Modifier.height(8.dp))
            Text(media.title, color = Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            media.subtitle?.takeIf { plan.subtitle }?.let { Text(it, color = Color.White.copy(alpha = .72f), fontSize = FolioType.FOOTNOTE.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            if (showWave) MusicWave(media.playing && !reduceMotion, accent)
            var scrub by remember { mutableStateOf<Float?>(null) }
            val shown = scrub?.let { (it * duration).toLong() } ?: position
            if (plan.scrubber) Column(Modifier.fillMaxWidth(.84f).padding(top = 4.dp).mediaSeekable(media, duration) { scrub = it }) {
                Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = .22f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((shown.toFloat() / duration).coerceIn(0f, 1f)).background(Color.White))
                }
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatMediaTime(shown), color = Color.White.copy(alpha = .72f), fontSize = FolioType.GROUP_LABEL.sp)
                    Text("-" + formatMediaTime(duration - shown), color = Color.White.copy(alpha = .72f), fontSize = FolioType.GROUP_LABEL.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            // The Poster has room to spare, so previous and next get a real 48 dp touch box (A11Y-1) around their
            // smaller glyph, instead of the glyph itself being the only tappable area.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(FolioTouch.MIN.dp).clip(CircleShape).clickable { t.skipToPrevious() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.FastRewind, stringResource(R.string.previous), tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White).clickable { if (media.playing) t.pause() else t.play() }, contentAlignment = Alignment.Center) {
                    Icon(if (media.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (media.playing) R.string.pause else R.string.play),
                        tint = Color.Black, modifier = Modifier.size(26.dp))
                }
                Box(Modifier.size(FolioTouch.MIN.dp).clip(CircleShape).clickable { t.skipToNext() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.FastForward, stringResource(R.string.next), tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

/** Nothing playing, at Strip size: the same icon box and row [MusicStrip] uses, just without a title or controls. */
@Composable
private fun EmptyMusicStrip(label: String) {
    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).background(FolioGlass.raised), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, null, tint = FolioGlass.secondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(label, color = FolioGlass.secondary, fontSize = FolioType.SUBHEAD.sp)
    }
}

/** Nothing playing, at Card size: the same art box and title row [MusicCard] uses, just without a subtitle, scrubber or transport. */
@Composable
private fun EmptyMusicCard(label: String) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(14.dp)) {
        val art = (maxHeight * .5f).coerceIn(32.dp, maxOf(32.dp, minOf(maxWidth * .3f, 64.dp)))
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(art).clip(RoundedCornerShape(art * .2f)).background(FolioGlass.raised), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, tint = FolioGlass.secondary, modifier = Modifier.size(art * .4f))
            }
            Spacer(Modifier.width(10.dp))
            Text(label, color = FolioGlass.secondary, fontSize = FolioType.SUBHEAD.sp)
        }
    }
}

/** Nothing playing, at Poster size: the same centered art square [MusicPoster] uses, just without a wave, scrubber or transport. */
@Composable
private fun EmptyMusicPoster(label: String) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
        val artSize = (minOf(maxWidth, maxHeight) * .32f).coerceIn(40.dp, 96.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(artSize).clip(RoundedCornerShape(artSize * .12f)).background(FolioGlass.raised), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, tint = FolioGlass.secondary, modifier = Modifier.size(artSize * .4f))
            }
            Spacer(Modifier.height(10.dp))
            Text(label, color = FolioGlass.secondary, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * A decorative wave: it moves with play and pause, not the audio, since reading the mic would need RECORD_AUDIO.
 * Freezes on Reduce Motion or while paused, per LW's always-on and motion rules.
 */
@Composable
private fun MusicWave(animate: Boolean, accent: Color) {
    val bars = remember { List(24) { i -> (.3 + .7 * kotlin.math.abs(kotlin.math.sin(i * .7)) * kotlin.math.abs(kotlin.math.cos(i * .23))).toFloat() } }
    // Nothing runs while it is still (paused, or Reduce Motion): no transition is created, so no frames are spent on it.
    val phase = if (animate) rememberInfiniteTransition(label = "music wave")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "phase").value else 0f
    Row(Modifier.fillMaxWidth(.7f).height(22.dp).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEachIndexed { i, base ->
            val wobble = if (animate) (kotlin.math.sin((phase * 2 * Math.PI).toFloat() + i).let { (it + 1f) / 2f }) else 0f
            val h = (base * .6f + wobble * .4f).coerceIn(.12f, 1f)
            Box(Modifier.weight(1f).fillMaxHeight(h).clip(CircleShape).background(accent))
        }
    }
}

/** How far a drag has to take a Control Center page (as a fraction of its height) before letting go turns it. */
private const val CC_PAGE_TURN = .12f

/** How much Control Center's header and strip icons grow with the font size: never smaller than drawn, never past a third larger, so they stay inside their 48 dp targets. */
internal fun ccIconScale(fontScale: Float): Float = fontScale.coerceIn(1f, 4f / 3f)

/** Text on a control tile, [base] sp at normal size and growing with the system text size only up to 1.3x: the tile does not grow with it, so past that the text would not fit. */
@Composable
private fun ccTileSp(base: Float): androidx.compose.ui.unit.TextUnit =
    with(LocalDensity.current) { (base * minOf(fontScale, 1.3f)).dp.toSp() }
