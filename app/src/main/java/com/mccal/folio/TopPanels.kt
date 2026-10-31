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
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    ccSize: PanelSize = PanelSize.STANDARD, ccCentered: Boolean = false, ncLayout: NcLayout = NcLayout.FULL_SCREEN, ccLook: CcLook = CcLook.CLEAR,
    ccOptions: CcOptions = CcOptions(),
    focusModes: List<FocusMode> = emptyList(), activeFocus: String? = null, onFocus: (String?) -> Unit = {},
    onSwitchPanel: ((ShadePanel) -> Unit)? = null) {
    val open = panel == ShadePanel.NOTIFICATIONS || panel == ShadePanel.QUICK_SETTINGS
    OverlayBackHandler(open, onClose)
    val pageBack = remember { CcPageBack() }
    var shown by remember { mutableStateOf<ShadePanel?>(null) }
    if (open) shown = panel
    val current = shown ?: return
    if (!open && progress() <= 0.001f) { shown = null; return }
    val lift = with(LocalDensity.current) { 28.dp.toPx() }
    val config = LocalConfiguration.current
    val wide = config.fitsRegularHomeLayout()
    val shape = shadeShape(ncLayout, wide, config.screenWidthDp > config.screenHeightDp, config.screenWidthDp,
        LocalHinge.current?.active == true || LocalShadeOverApps.current, current, showClock)
    // A card or two cards leave Home visible, so the dim is lighter than for a panel that fills the screen.
    val dim = if (shape == ShadeShape.CARD || shape == ShadeShape.BOTH) .6f else 1f
    // The switch between the panels is only drawn where one panel stands alone (two cards side by side already show both) and while its gate is open.
    val switcher: ((ShadePanel) -> Unit)? = onSwitchPanel?.takeIf { shape != ShadeShape.BOTH && FeatureGate.PANEL_SWITCH.isOpen(LocalContext.current) }
    // Where both panels stand alone at the top (any width: a phone, the open screen, a tablet), the switch is drawn once, at the top centre, and stays where it
    // is while the two panels cross-fade under it. A card, a clock beside the list or a window too short for the extra row keeps its switch inside the panel.
    val hingedOrOverApps = LocalHinge.current?.active == true || LocalShadeOverApps.current
    fun shapeOf(panel: ShadePanel) = shadeShape(ncLayout, wide, config.screenWidthDp > config.screenHeightDp, config.screenWidthDp, hingedOrOverApps, panel, showClock)
    val hoistSwitch = switcher != null && config.screenHeightDp >= HOIST_SWITCH_MIN_HEIGHT_DP &&
        shapeOf(ShadePanel.NOTIFICATIONS).let { it == ShadeShape.SINGLE_TOP || it == ShadeShape.SPLIT_CLOCK } && shapeOf(ShadePanel.QUICK_SETTINGS) == ShadeShape.SINGLE_TOP
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
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress() * dim }
        .then(if (LocalShadeBackdropImage.current != null || (LocalShadeOverApps.current && !LocalShadeBackdropBlur.current)) Modifier else Modifier.background(FolioGlass.scrim))
        .then(if (open) Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onClose)
            .ccSwipeUp(pageBack, onClose) else Modifier)
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
    // Half folded, Control Center moves off the hinge like iPhone Duo's controls; Notification Center is a
    // scrolling list, which adapts by scrolling instead.
    val hinge = LocalHinge.current?.takeIf { it.active && current == ShadePanel.QUICK_SETTINGS }
    CompositionLocalProvider(LocalHinge provides hinge, LocalCcLook provides ccLook, LocalCcTone provides tone) {
    FoldAvoidingBox(Modifier.windowInsetsPadding(WindowInsets.folioSafeTop).navigationBarsPadding().padding(start = FolioSpace.LARGE.dp, end = FolioSpace.LARGE.dp, top = FolioSpace.XXL.dp, bottom = FolioSpace.MEDIUM.dp),
        contentAlignment = when {
            shape == ShadeShape.BOTH -> Alignment.TopCenter
            // Notification Center is pulled from the top left and Control Center from the top right: each card sits under its own corner.
            shape == ShadeShape.CARD -> if (current == ShadePanel.NOTIFICATIONS) Alignment.TopStart else Alignment.TopEnd
            split -> Alignment.TopEnd
            current == ShadePanel.NOTIFICATIONS -> if (wide) Alignment.TopCenter else Alignment.TopStart
            wide && ccCentered -> Alignment.TopCenter
            else -> Alignment.TopEnd
        }) {
        // Notification Center uses the width; Control Center is a compact fixed grid (like iPad) so it isn't oversized.
        // Beside the clock the list takes at most 60 percent of the width, so the clock (40 percent) is never drawn under it.
        fun panelModifier(which: ShadePanel) = (if (split) Modifier.fillMaxWidth(.6f) else Modifier).widthIn(max = when {
                split -> 540.dp
                shape == ShadeShape.CARD && which == ShadePanel.NOTIFICATIONS -> 420.dp
                shape == ShadeShape.BOTH -> 440.dp
                which == ShadePanel.QUICK_SETTINGS && shape == ShadeShape.SINGLE_TOP -> Dp.Infinity // Control Center fills the window; its grid sets its own width
                wide -> 460.dp
                else -> 560.dp
            })
            .then(if ((which == ShadePanel.NOTIFICATIONS && shape != ShadeShape.BOTH) || (which == ShadePanel.QUICK_SETTINGS && shape == ShadeShape.SINGLE_TOP)) Modifier.fillMaxWidth() else Modifier)
            .then(if (split) Modifier.fillMaxHeight() else Modifier)
            // Taps on gaps inside the panel must not fall through to the scrim and close it. Control Center's own margins (beside and around the grid) are
            // the exception: a tap there closes it, as a tap outside does; the grid and the page strip take their own taps (see ControlCenter).
            .pointerInput(which) { detectTapGestures(onTap = if (which == ShadePanel.QUICK_SETTINGS) ({ _: androidx.compose.ui.geometry.Offset -> onClose() }) else null) }
            // A swipe up that starts in Control Center's margins is the same swipe as one under it (it used to count as a tap there and close the panel from any page).
            .then(if (which == ShadePanel.QUICK_SETTINGS) Modifier.ccSwipeUp(pageBack, onClose) else Modifier)
            .graphicsLayer {
                val p = progress()
                if (which == ShadePanel.NOTIFICATIONS) {
                    // iOS: Notification Center slides down from the top edge and doesn't zoom; it's fully opaque
                    // early so the list reads as one sheet moving, not a fade.
                    alpha = (OverlayBack.unpreviewed(p) * 1.6f).coerceAtMost(1f)
                    translationY = -lift * 3f * (1f - p)
                } else {
                    // Control Center grows out of the corner it was pulled from; it fades only as it closes, not in a back preview.
                    alpha = OverlayBack.unpreviewed(p)
                    translationY = -lift * (1f - p)
                    scaleX = .92f + .08f * p; scaleY = scaleX
                    transformOrigin = TransformOrigin(1f, 0f)
                }
            }
        if (split) {
            val clockLift = lift
            SplitClock(Modifier.align(Alignment.CenterStart).fillMaxWidth(.4f).graphicsLayer {
                val p = progress(); alpha = OverlayBack.unpreviewed(p); translationY = -clockLift * (1f - p)
            })
        }
        if (shape == ShadeShape.BOTH) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp, Alignment.CenterHorizontally)) {
            // Either pull opens both: Notification Center on the left and Control Center on the right, each with its own close button.
            NotificationCenter(Modifier.weight(1f, fill = false).then(panelModifier(ShadePanel.NOTIFICATIONS)), false, grouped, sections, listMax = cardListMax, onClose = onClose, onSystemQuickSettings = { onSystemPanel(ShadePanel.QUICK_SETTINGS) }) { onSystemPanel(ShadePanel.NOTIFICATIONS) }
            ControlCenter(Modifier.weight(1f, fill = false).then(panelModifier(ShadePanel.QUICK_SETTINGS)), status, ccControls, onCcControls, ccSize, ccOptions, wide, onClose, focusModes, activeFocus, onFocus, panelOpen = open, pageBack = pageBack) { onSystemPanel(ShadePanel.QUICK_SETTINGS) }
        }
        else {
            // Moving between the panels is a plain cross-fade in place: the container always spans the width, and each panel sits where it sits when it
            // is open on its own, so the outgoing panel does not jump to the incoming one's place. With the switch drawn once (hoistSwitch) it does not move.
            fun alignFor(which: ShadePanel): Alignment = when {
                shape == ShadeShape.CARD -> if (which == ShadePanel.NOTIFICATIONS) Alignment.TopStart else Alignment.TopEnd
                split -> Alignment.TopEnd
                which == ShadePanel.NOTIFICATIONS -> if (wide) Alignment.TopCenter else Alignment.TopStart
                wide && ccCentered -> Alignment.TopCenter
                else -> Alignment.TopEnd
            }
            androidx.compose.animation.AnimatedContent(current, Modifier.fillMaxWidth().then(if (split) Modifier.fillMaxHeight() else Modifier), transitionSpec = {
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200))
                    .togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)))
                    .using(androidx.compose.animation.SizeTransform(clip = false) { _, _ -> androidx.compose.animation.core.snap() })
            }, contentAlignment = Alignment.TopStart, label = "panel switch") { which ->
                Box(Modifier.fillMaxWidth().then(if (split) Modifier.fillMaxHeight() else Modifier), contentAlignment = alignFor(which)) {
                    if (which == ShadePanel.NOTIFICATIONS) NotificationCenter(panelModifier(ShadePanel.NOTIFICATIONS), showClock && !split && shape != ShadeShape.CARD, grouped, sections,
                        listMax = if (shape == ShadeShape.CARD) cardListMax else Dp.Infinity, onClose = onClose, onSwitch = switcher.takeUnless { hoistSwitch }, reserveSwitchRow = hoistSwitch,
                        onSystemQuickSettings = { onSystemPanel(ShadePanel.QUICK_SETTINGS) }) { onSystemPanel(ShadePanel.NOTIFICATIONS) }
                    else ControlCenter(panelModifier(ShadePanel.QUICK_SETTINGS), status, ccControls, onCcControls, ccSize, ccOptions, wide, onClose, focusModes, activeFocus, onFocus, panelOpen = open, pageBack = pageBack,
                        onSwitch = switcher.takeUnless { hoistSwitch }, reserveSwitchRow = hoistSwitch) { onSystemPanel(ShadePanel.QUICK_SETTINGS) }
                }
            }
            if (hoistSwitch) Box(Modifier.align(Alignment.TopCenter).graphicsLayer {
                val p = progress(); alpha = (OverlayBack.unpreviewed(p) * 1.6f).coerceAtMost(1f); translationY = -lift * (1f - p)
            }.semantics { isTraversalGroup = true; traversalIndex = -1f }) { PanelSwitch(current, switcher!!) }
        }
    }
    }
}

// ---------------------------------------------------------------------------------------------
// Notification Center

@Composable
private fun NotificationCenter(modifier: Modifier, showClock: Boolean, grouped: Boolean, sections: Boolean = false, listMax: Dp = Dp.Infinity, onClose: () -> Unit, onSwitch: ((ShadePanel) -> Unit)? = null, reserveSwitchRow: Boolean = false, onSystemQuickSettings: () -> Unit = {}, onSystem: () -> Unit) {
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

    // Keyboard for quick reply pushes the list up instead of covering it.
    Column(modifier.windowInsetsPadding(WindowInsets.imeAnimationTarget).testTag("notification-center"), verticalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
        if (onSwitch != null) PanelSwitch(ShadePanel.NOTIFICATIONS, onSwitch, Modifier.align(Alignment.CenterHorizontally))
        // The switch is drawn once above both panels: this row keeps its place so the clock does not move up under it.
        else if (reserveSwitchRow) Spacer(Modifier.height(PANEL_SWITCH_ROW_DP.dp))
        if (showClock) Column(Modifier.fillMaxWidth().padding(top = FolioSpace.TINY.dp, bottom = FolioSpace.SMALL.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(now.format(DateTimeFormatter.ofPattern(stringResource(R.string.eeee_mmmm_d))), color = Color.White.copy(alpha = .9f), fontSize = FolioType.BODY.sp,
                fontWeight = FontWeight.SemiBold)
            Text(now.format(DateTimeFormatter.ofPattern(clock)), color = Color.White, fontSize = 76.sp, fontWeight = FontWeight.Bold,
                lineHeight = 80.sp)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.TINY.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.notification_center), color = Color.White, fontSize = FolioType.TITLE3.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            // The list takes what the column has left after the header and the app row, so it follows the window, not a fixed height.
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = listMax).edgeFade(listState), state = listState,
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
                            placementSpec = FolioMotion.spring(FolioMotion.Settle)), showMore = sections) {
                            onClose(); IslandListenerService.openNotification(context, item)
                        } }
                    } else item(group.first().key) {
                        // Same key as the first card when expanded, so the stack slides apart instead of swapping.
                        StackedNotification(group, Modifier.animateItem(fadeInSpec = FolioMotion.spring(FolioMotion.Settle),
                            placementSpec = FolioMotion.spring(FolioMotion.Settle)), showMore = sections) { expandedGroup = pkg }
                    }
                }
                  }
                }
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
private fun StackedNotification(group: List<NotificationItem>, modifier: Modifier, showMore: Boolean = false, onExpand: () -> Unit) {
    Box(modifier.fillMaxWidth().padding(bottom = FolioSpace.MEDIUM.dp)) {
        // Two receding cards behind, like an iPhone notification stack.
        Box(Modifier.matchParentSize().padding(horizontal = FolioSpace.XXL.dp).offset(y = 14.dp).clip(RoundedCornerShape(FolioRadius.NOTIFICATION.dp)).background(FolioColors.SurfaceCard.copy(alpha = .45f)))
        Box(Modifier.matchParentSize().padding(horizontal = FolioSpace.MEDIUM.dp).offset(y = 7.dp).clip(RoundedCornerShape(FolioRadius.NOTIFICATION.dp)).background(FolioColors.SurfaceCard.copy(alpha = .66f)))
        key(group.first().key) { NotificationCard(group.first(), Modifier, extraCount = group.size - 1, showMore = showMore, onOpen = onExpand) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationCard(item: NotificationItem, modifier: Modifier, extraCount: Int = 0, showMore: Boolean = false, onOpen: () -> Unit) {
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
            .border(FolioGlass.edge, RoundedCornerShape(FolioRadius.NOTIFICATION.dp))
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
                    fontSize = FolioType.COMPACT.sp, maxLines = if (extraCount > 0) 2 else 4, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp) }
                if (extraCount > 0) Text(pluralStringResource(R.plurals.more_from_app, extraCount, extraCount, item.appLabel),
                    color = FolioGlass.secondary, fontSize = FolioType.FOOTNOTE.sp, modifier = Modifier.padding(top = FolioSpace.HAIR.dp))
                if (extraCount == 0 && (item.canReply || item.canMarkRead)) {
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
 * The swipe up on a panel's scrim, or on Control Center's margins: end editing, go back a page, or close. One swipe does one of them, once,
 * so a long drag does not walk back through every page and then close.
 */
private fun Modifier.ccSwipeUp(pageBack: CcPageBack, onClose: () -> Unit): Modifier = pointerInput(pageBack) {
    var total = 0f
    var done = false
    detectVerticalDragGestures(onDragStart = { total = 0f; done = false }) { _, drag ->
        total += drag
        if (!done && total < -48.dp.toPx()) { done = true; if (!pageBack.goBack()) onClose() }
    }
}

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
    NOTES(R.string.notes), HOTSPOT(R.string.hotspot), BATTERY_SAVER(R.string.battery_saver), SETTINGS(R.string.settings), SYSTEM(R.string.android_quick_settings);

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
}

@Composable
private fun ControlCenter(modifier: Modifier, status: DeviceStatus, controlNames: List<String>, onControls: (List<String>) -> Unit,
    size: PanelSize, ccOptions: CcOptions, wide: Boolean, onClose: () -> Unit,
    focusModes: List<FocusMode>, activeFocus: String?, onFocus: (String?) -> Unit, panelOpen: Boolean = true, pageBack: CcPageBack? = null, onSwitch: ((ShadePanel) -> Unit)? = null, reserveSwitchRow: Boolean = false, onSystem: () -> Unit) {
    val context = LocalContext.current
    val controls = remember { DeviceControls(context) }
    DisposableEffect(controls) { controls.start(); onDispose { controls.stop() } }
    val media = (IslandListenerService.activity.collectAsStateWithLifecycle().value as? IslandActivity.Media)
    val open = { intent: Intent -> onClose(); runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; Unit }
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
    val moveControl: (String, String) -> Unit = { moving, target -> onControls(ccMoveControl(controlNames, moving, target)) }
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
        else -> false
    }
    fun accent(control: CcControl) = when (control) {
        CcControl.ROTATION -> AccentRed
        CcControl.DND -> AccentPurple
        else -> Color.White
    }
    fun runNow(control: CcControl) {
        when (control) {
            CcControl.ROTATION -> if (!controls.toggleRotationLock()) open(controls.writeSettingsIntent())
            CcControl.DND -> if (!controls.toggleDnd()) open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            CcControl.FLASHLIGHT -> controls.toggleTorch()
            CcControl.CAMERA -> open(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            CcControl.TIMER -> open(Intent(AlarmClock.ACTION_SHOW_TIMERS))
            CcControl.ALARM -> open(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            CcControl.CALCULATOR -> open(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))
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
        }
    }

    fun run(control: CcControl) {
        runNow(control)
        // After the tile's own press and haptic, not instead of them.
        if (ccClosesAfterTap(control, options)) closeScope.launch { delay(CC_CLOSE_AFTER_MS); onClose() }
    }

    BoxWithConstraints(modifier.testTag("control-center")) {
        val sizeCell = size.cell + if (wide) 8.dp else 0.dp
        val tall = if (maxHeight == Dp.Infinity) null else maxHeight.value
        // Fills the window: 4, 6 or 8 columns by the width, cells near the size chosen; the page strip at the edge on a wide panel, under the grid on a narrow one.
        val grid = controlCenterGrid(maxWidth.value, tall, chosen.size, focusModes.isNotEmpty(), sizeCell.value, FolioTouch.MIN.toFloat(), (FolioSpace.LARGE * 2).toFloat())
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
        val extraPages = remember(neededPages, options.mediaPage, options.connectivityPage) { ccShownExtraPages(neededPages, options) }
        val pages = remember(plan, extraPages) { ccPageSpecs(plan.perPage.size, extraPages) }
        // The pager runs forward but its pages are in reverse (Controls at the last index): the first page is anchored at the bottom, the next ones come down from above,
        // and a swipe up past the first page has nowhere to go, so it closes the panel. (reverseLayout stopped the drag from following the finger.)
        // It opens on the first page, or on the page it was last left on (Open on), and always on the first when Settings asked for edit mode.
        val startPosition = remember { if (editRequested) 0 else ccStartPage(options.openOn, CcLastPage.page, pages) }
        val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = pages.lastIndex - startPosition) { pages.size }
        val firstIndex = pages.lastIndex
        val current = firstIndex - pager.currentPage // the page shown, in the order of [pages]
        // A different set of pages (another window, a fold, a font size) starts again on the first. With fewer pages shown the same count can hold other pages, so the pages themselves are the key.
        // (Not the first time: the pager was built on the page it should open on.)
        val pagesSettled = remember { booleanArrayOf(false) }
        LaunchedEffect(if (dedupe || v2) pages else pages.size) {
            if (!pagesSettled[0]) { pagesSettled[0] = true; return@LaunchedEffect }
            if (pager.currentPage != pages.lastIndex) pager.scrollToPage(pages.lastIndex)
        }
        // The page in view is remembered for Open on: Last page used.
        LaunchedEffect(current, pages) { if (v2) CcLastPage.page = pages.getOrNull(current)?.page }
        LaunchedEffect(Unit) { if (editRequested) edit.start() }
        val pageScope = rememberCoroutineScope()
        val reduceMotion = LocalReduceMotion.current
        // Editing carries across the Controls pages (the gallery to add from is on the last); it ends on Media and Connectivity.
        LaunchedEffect(current, pages.getOrNull(current)?.page) { if (pages.getOrNull(current)?.page != CcPage.CONTROLS) edit.stop() }
        // Swipe away: an upward drag that the first page cannot use (there is no page before it) pulls the whole panel up with the finger. Past
        // CC_SWIPE_AWAY_DP, or with a quick flick up, it closes; otherwise it springs back on Quick. Not while editing, where a drag is for moving things.
        val closeLabel = stringResource(R.string.close)
        var swipeAway by remember { mutableFloatStateOf(0f) }
        // Closing leaves the pull where it was while the panel fades out. Pulled open again in that time, this same panel is reused, so it starts from rest.
        LaunchedEffect(panelOpen) { swipeAway = ccSwipeAwayOnOpenChange(panelOpen, swipeAway) }
        val swipeDensity = androidx.compose.ui.platform.LocalDensity.current
        // The connection below is made once, so it reads these through State: a new density (a fold, a display-size change) or a new onClose is seen at the next gesture.
        val swipeAwayPx by rememberUpdatedState(with(swipeDensity) { CC_SWIPE_AWAY_DP.dp.toPx() })
        val flickPx by rememberUpdatedState(with(swipeDensity) { CC_SWIPE_AWAY_FLICK_DP.dp.toPx() })
        val closePanel by rememberUpdatedState(onClose)
        val tick by rememberUpdatedState(androidx.compose.ui.platform.LocalHapticFeedback.current)
        val swipeConnection = remember(pager, edit) {
            // A drag counts only if it started on the first page, so paging back from Media and going on past the first page does not close it by accident.
            var startedOnFirst = false
            var gestureActive = false
            // One light tick as the pull passes the point where letting go closes the panel (and again if it goes back), so the finger knows.
            var pastClose = false
            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                    // Only a finger starts a gesture: the pager's own settling after a release also scrolls (as a side effect) and must not pick the next gesture's page.
                    if (source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) return androidx.compose.ui.geometry.Offset.Zero
                    if (!gestureActive) { gestureActive = true; startedOnFirst = pager.currentPage == pager.pageCount - 1 }
                    if (swipeAway < 0f && available.y > 0f) { val used = minOf(available.y, -swipeAway); swipeAway += used; return androidx.compose.ui.geometry.Offset(0f, used) }
                    return androidx.compose.ui.geometry.Offset.Zero
                }
                override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                    if (!edit.active && startedOnFirst && pager.currentPage == pager.pageCount - 1 && available.y < 0f && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
                        swipeAway += available.y
                        val past = swipeAway < -swipeAwayPx
                        if (past != pastClose) { pastClose = past; tick.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) }
                        return androidx.compose.ui.geometry.Offset(0f, available.y)
                    }
                    return androidx.compose.ui.geometry.Offset.Zero
                }
                override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                    gestureActive = false; pastClose = false
                    if (swipeAway >= 0f) return androidx.compose.ui.unit.Velocity.Zero
                    if (ccSwipeAwayCloses(swipeAway, available.y, swipeAwayPx, flickPx)) closePanel()
                    else androidx.compose.animation.core.animate(swipeAway, 0f, animationSpec = FolioMotion.spring(FolioMotion.Quick)) { v, _ -> swipeAway = v }
                    return available
                }
            }
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
        val galleryRows = if (edit.active) ccEditGalleryRows(CcControl.entries.count { it !in chosen && available(it) }, cols) else 0
        fun controlsRows(index: Int) = ccPlanRows(plan, index, cols, focusOn) + if (index == plan.perPage.lastIndex) galleryRows else 0
        val neededRows = maxOf(if (CcPage.MEDIA in extraPages) ccMediaRows(plan, focusOn) else 0, plan.perPage.indices.maxOf { controlsRows(it) })
        val wantedHeight = cell * neededRows + gap * (neededRows - 1)
        val pageHeight = if (pagesAvailable == null) wantedHeight else minOf(wantedHeight, pagesAvailable).coerceAtLeast(cell)

        // The sliders: beside the small controls with four columns, at the end of the top row with six or eight.
        val sliders: @Composable (Dp) -> Unit = { height ->
            TallSlider(if (controls.brightness < .35f) Icons.Rounded.BrightnessLow else Icons.Rounded.LightMode, stringResource(R.string.brightness), controls.brightness, cell, height,
                onStart = { if (!android.provider.Settings.System.canWrite(context)) open(controls.writeSettingsIntent()) }, ticks = options.sliderTicks,
                onHold = controls::holdBrightness,
                // Dragging the slider turns Auto off; this says so and turns it back on (v2). It follows the phone's own mode, so it also
                // changes when automatic brightness is switched somewhere else.
                badge = if (v2 && controls.autoBrightnessKnown) { alpha ->
                    AutoBrightnessChip(controls.brightnessAuto, cell, alpha) {
                        if (!android.provider.Settings.System.canWrite(context)) open(controls.writeSettingsIntent()) else controls.setBrightnessAuto(!controls.brightnessAuto)
                    }
                } else null) { value ->
                controls.changeBrightness(value) }
            TallSlider(when {
                controls.volume <= .01f -> Icons.AutoMirrored.Rounded.VolumeOff
                controls.volume < .5f -> Icons.AutoMirrored.Rounded.VolumeDown
                else -> Icons.AutoMirrored.Rounded.VolumeUp
            }, stringResource(R.string.volume), controls.volume, cell, height, ticks = options.sliderTicks, onHold = controls::holdVolume) { controls.changeVolume(it) }
        }
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        val connectivity = listOf(
            ConnectivityItem(Icons.Rounded.AirplanemodeActive, stringResource(R.string.airplane_mode_2), status.airplane, AccentOrange) { open(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) },
            ConnectivityItem(Icons.Rounded.SignalCellularAlt, stringResource(R.string.cellular_data), !status.airplane && (status.cellularLevel ?: 0) > 0, AccentGreen) { open(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) },
            ConnectivityItem(Icons.Rounded.Wifi, stringResource(R.string.wi_fi), status.wifiConnected, AccentBlue) { open(Intent(Settings.Panel.ACTION_WIFI)) },
            ConnectivityItem(Icons.Rounded.Bluetooth, stringResource(R.string.bluetooth), controls.bluetoothOn, AccentBlue) { open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
        )
        val playing: () -> Unit = { media?.let { onClose(); IslandListenerService.open(context, it) } }

        // Focus, like iOS: the Focus that's on (or "Focus"); the icon toggles it, the rest opens the list. It stands under the modules on the first page, or on the Media page when the first has no row for it.
        val focusModule: @Composable () -> Unit = {
            var focusOpen by remember { mutableStateOf(false) }
            val current = focusModes.firstOrNull { it.id == activeFocus }
            if (focusOpen) Module(Modifier.width(span(cols))) {
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
            } else Module(Modifier.width(span(cols)).height(cell).clickable(onClickLabel = stringResource(R.string.choose_a_focus)) { focusOpen = true }.testTag("cc-focus")) {
                Row(Modifier.fillMaxSize().padding(horizontal = cell * .14f), verticalAlignment = Alignment.CenterVertically) {
                    val mode = current ?: focusModes.first()
                    RoundToggle(mode.icon(), mode.name, current != null, Color(mode.color), cell * .7f) {
                        onFocus(if (current != null) null else mode.id)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(current?.name ?: stringResource(R.string.focus), color = Color.White, fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        if (current != null) Text(stringResource(R.string.on), color = FolioGlass.secondary, fontSize = FolioType.FOOTNOTE.sp)
                    }
                }
            }
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
            Column(Modifier.width(gridWidth).then(if (rowsNeeded > rowsFit) Modifier.fadingVerticalScroll() else Modifier), verticalArrangement = Arrangement.spacedBy(gap)) {
                when (spec.page) {
                    CcPage.CONTROLS -> {
                        val first = spec.index == 0
                        val before = plan.perPage.take(spec.index).sum()
                        val pageControls = chosen.subList(before, before + plan.perPage[spec.index])
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
                        if (first && focusOn && plan.focusOnFirst) focusModule()
                        // Row 3–4 with four columns: the first four small controls (2×2) and brightness + volume sliders
                        // (With the Media page off and no room on the first page, the same block leads the next Controls page; with no control to put beside them the sliders stand in the middle.)
                        if (cols == 4 && spec.index == plan.sliderPage) {
                            val beside = pageControls.take(4)
                            Row(if (beside.isEmpty()) Modifier.width(gridWidth) else Modifier,
                                horizontalArrangement = if (beside.isEmpty()) Arrangement.Center else Arrangement.spacedBy(gap)) {
                                if (beside.isNotEmpty()) Column(Modifier.width(span(2)), verticalArrangement = Arrangement.spacedBy(gap)) {
                                    beside.chunked(2).forEach { pair ->
                                        Row(Modifier.zIndex(if (reorder?.moving in pair.map { it.name }) 1f else 0f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                            pair.forEach { c -> EditableControl(c, isOn(c), accent(c), cell, edit, onRemove = { onControls(controlNames - c.name) }, reorder = reorder, onMove = moveControl, onNudge = { by -> onControls(ccMoveControlBy(controlNames, c.name, by)) }) { run(c) } }
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
                                row.forEach { c -> EditableControl(c, isOn(c), accent(c), cell, edit, onRemove = { onControls(controlNames - c.name) }, reorder = reorder, onMove = moveControl, onNudge = { by -> onControls(ccMoveControlBy(controlNames, c.name, by)) }) { run(c) } }
                            }
                        }
                        // Edit mode: gallery of controls that aren't in Control Center yet
                        if (edit.active && spec.index == plan.perPage.lastIndex) {
                            val unused = CcControl.entries.filter { it !in chosen && available(it) }
                            Text(if (unused.isEmpty()) stringResource(R.string.all_controls_added) else stringResource(R.string.add_a_control), color = FolioGlass.secondary, fontSize = FolioType.FOOTNOTE.sp,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = FolioSpace.TINY.dp, start = FolioSpace.TINY.dp))
                            unused.chunked(cols).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                    row.forEach { c ->
                                        Column(Modifier.width(cell), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Box {
                                                SquareToggle(c.icon(), stringResource(R.string.add_control, stringResource(c.label)), false, Color.White, cell) { onControls(controlNames + c.name) }
                                                Box(Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(20.dp).clip(CircleShape).background(AccentGreen),
                                                    contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                                            }
                                            Text(stringResource(c.label), color = Color.White, fontSize = 10.sp, maxLines = 2, lineHeight = 12.sp,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = FolioSpace.TINY.dp))
                                        }
                                    }
                                }
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
            val controlsPages = plan.perPage.size
            val items: @Composable () -> Unit = {
                pages.forEachIndexed { index, spec ->
                    val page = spec.page
                    val selected = current == index
                    val name = label(page)
                    val description = stringResource(R.string.cc_page_of, name, index + 1, pages.size)
                    Box(Modifier.size(FolioTouch.MIN.dp).clip(CircleShape).background(if (selected) Color.White else NotifGlass)
                        .clickable(role = androidx.compose.ui.semantics.Role.Tab) { goTo(index) }
                        .semantics { contentDescription = description; this.selected = selected },
                        contentAlignment = Alignment.Center) {
                        // With more than one Controls page each says which it is, so the marks are never the same.
                        if (page == CcPage.CONTROLS && controlsPages > 1) Text("${spec.index + 1}", color = if (selected) FolioColors.MenuSurface else Color.White,
                            fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.Bold)
                        else Icon(when (page) { CcPage.CONTROLS -> Icons.Rounded.Apps; CcPage.MEDIA -> Icons.Rounded.MusicNote; CcPage.CONNECTIVITY -> Icons.Rounded.Wifi },
                            null, tint = if (selected) FolioColors.MenuSurface else Color.White, modifier = Modifier.size((22 * ccIconScale(LocalDensity.current.fontScale)).dp))
                    }
                }
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
            else if (grid.stripAtEdge) Column(verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // As beside the grid on the iPhone and the Duo: the battery first, then the pages. Only where the column has the height for it.
                // A gap on top of the strip's own keeps the ring from reading as the first page dot.
                if ((tall ?: Float.MAX_VALUE) >= 360f) Box(if (dedupe) Modifier.padding(bottom = CC_BATTERY_APART_DP.dp) else Modifier) { BatteryRing(status) }
                items()
            }
            else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FolioSpace.COMFY.dp, Alignment.CenterHorizontally)) { items() }
        }

        ProvideJiggle(edit) {
        Box(Modifier.fillMaxWidth().padding(vertical = CC_PANEL_PAD_DP.dp).graphicsLayer { translationY = swipeAway; alpha = (1f + swipeAway / (this.size.height * .9f)).coerceIn(.15f, 1f) }
            .semantics { dismiss(closeLabel) { onClose(); true } }, contentAlignment = Alignment.TopCenter) {
            Row(Modifier.padding(horizontal = FolioSpace.LARGE.dp), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
                // The grid takes the taps that land between its controls, so only the margins around it close the panel.
                Column(Modifier.width(maxOf(gridWidth, if (grid.stripInHeader) ccHeaderMinWidth(pages.size).dp else 0.dp)).pointerInput(Unit) { detectTapGestures() }, verticalArrangement = Arrangement.spacedBy(gap)) {
                    if (reserveSwitchRow) Spacer(Modifier.height(PANEL_SWITCH_ROW_DP.dp))
                    // iOS 18 header: edit on the left, power on the right.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (edit.active) PanelPill(stringResource(R.string.done)) { edit.stop() }
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
                    androidx.compose.foundation.pager.VerticalPager(pager, Modifier.fillMaxWidth().height(pageHeight).nestedScroll(swipeConnection), pageSpacing = gap, horizontalAlignment = Alignment.Start, userScrollEnabled = true,
                        flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(pager,
                            // A page is the whole panel tall, so the usual halfway rule asked for a drag across half the screen: a fifth of it is enough to turn the page, and a short swipe springs back.
                            snapPositionalThreshold = CC_PAGE_TURN,
                            snapAnimationSpec = if (reduceMotion) androidx.compose.animation.core.snap() else FolioMotion.spring(FolioMotion.Sheet))) { index ->
                        Box(Modifier.fillMaxSize().graphicsLayer {
                            val away = kotlin.math.abs((pager.currentPage - index) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                            alpha = 1f - CC_PAGE_FADE * away
                            if (!reduceMotion) { val scale = 1f - CC_PAGE_SHRINK * away; scaleX = scale; scaleY = scale }
                        }, contentAlignment = Alignment.TopStart) { pageContent(pages[firstIndex - index]) }
                    }
                    if (!grid.stripAtEdge && !grid.stripInHeader) strip()
                }
                if (grid.stripAtEdge) Box(Modifier.pointerInput(Unit) { detectTapGestures() }) { strip() }
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

/** The four connections as rows, each with its switch and its state: the long-press view on the Controls page and the whole Connectivity page. */
@Composable
private fun ConnectivityList(items: List<ConnectivityItem>, modifier: Modifier) {
    Column(modifier) {
        items.forEach { item ->
            Row(Modifier.fillMaxWidth().clickable(onClick = item.onClick).padding(horizontal = FolioSpace.COMFY.dp, vertical = FolioSpace.SMALL.dp),
                verticalAlignment = Alignment.CenterVertically) {
                RoundToggle(item.icon, item.label, item.on, item.accent, 44.dp, item.onClick)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.label, color = Color.White, fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(if (item.on) R.string.state_on else R.string.state_off), color = FolioGlass.secondary, fontSize = FolioType.FOOTNOTE.sp)
                }
            }
        }
    }
}

/** A 1×1 control that wiggles with a "–" while editing. */
@Composable
private fun EditableControl(control: CcControl, on: Boolean, accent: Color, cell: Dp, edit: HomeEditMode, onRemove: () -> Unit,
    reorder: CcReorderState? = null, onMove: (moving: String, target: String) -> Unit = { _, _ -> }, onNudge: (by: Int) -> Unit = {}, onRun: () -> Unit) {
    Box(Modifier.jiggle(control.name, .6f).then(
        if (reorder != null) Modifier.ccReorderable(control.name, reorder, edit.active, stringResource(R.string.move_control_earlier, stringResource(control.label)),
            stringResource(R.string.move_control_later, stringResource(control.label)), onMove) { onNudge(it) } else Modifier)) {
        SquareToggle(control.icon(), stringResource(control.label), on, accent, cell) { if (!edit.active) onRun() }
        if (edit.active) JiggleRemoveButton(stringResource(R.string.remove_control, stringResource(control.label)), onRemove = onRemove)
    }
}

private data class ConnectivityItem(val icon: ImageVector, val label: String, val on: Boolean, val accent: Color, val onClick: () -> Unit)

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
            Text(media?.title ?: stringResource(R.string.not_playing), color = if (media != null) Color.White else FolioGlass.secondary,
                fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    Column(Modifier.padding(top = 3.dp)) {
        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = .22f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f)).background(accent ?: Color.White))
        }
        Row(Modifier.fillMaxWidth().padding(top = 1.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMediaTime(position), color = FolioGlass.secondary, fontSize = FolioType.GROUP_LABEL.sp)
            Text("-" + formatMediaTime(duration - position), color = FolioGlass.secondary, fontSize = FolioType.GROUP_LABEL.sp)
        }
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

/** How far a page fades and shrinks as the finger takes it away (0 to 1 of the way out): enough to read as depth, little enough that a page half-way is still readable. */
private const val CC_PAGE_FADE = .55f

/** How far up the panel has to be pulled to close, and how fast a flick (px per second) closes it from less. */
private const val CC_SWIPE_AWAY_DP = 96f
/** A flick up faster than this closes Control Center, in dp per second (about what 1400 px per second was on the Fold's 2.6 density), so a dense screen needs no lighter a flick. */
private const val CC_SWIPE_AWAY_FLICK_DP = 530f
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
private fun SquareToggle(icon: ImageVector, label: String, on: Boolean, accent: Color, size: Dp, onClick: () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val state = stringResource(if (on) R.string.state_on else R.string.state_off)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(Modifier.size(size).ccPress(pressed).clip(RoundedCornerShape(size * .3f)).background(if (on) accent else LocalCcLook.current.tile)
        .clickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, role = androidx.compose.ui.semantics.Role.Button) { haptic.toggle(!on); onClick() }
        .semantics { contentDescription = label; stateDescription = state }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (on && accent == Color.White) Color.Black else Color.White, modifier = Modifier.size(size * .4f))
    }
}

@Composable
private fun TallSlider(icon: ImageVector, label: String, value: Float, width: Dp, height: Dp,
    onStart: () -> Unit = {}, onHold: (Boolean) -> Unit = {}, ticks: Boolean = true, badge: (@Composable BoxScope.(Float) -> Unit)? = null, onValue: (Float) -> Unit) {
    // A change from outside (a volume key, automatic brightness) moves the fill with the same no-overshoot spring as a drag,
    // and not at all under Reduce Motion. It never ticks: the haptics below run only from the finger.
    val shown by androidx.compose.animation.core.animateFloatAsState(value.coerceIn(0f, 1f),
        if (LocalReduceMotion.current) androidx.compose.animation.core.snap() else FolioMotion.spring(FolioMotion.Snap), label = label)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
    val readout by androidx.compose.animation.core.animateFloatAsState(if (dragging) 1f else 0f, FolioMotion.spring(FolioMotion.Quick), label = "slider readout")
    Box(Modifier.size(width, height).clip(RoundedCornerShape(width * .36f)).background(LocalCcLook.current.tile)
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
            .padding(horizontal = FolioSpace.COMPACT.dp, vertical = FolioSpace.TINY.dp)) {
            Text(stringResource(R.string.cc_auto), color = if (on) FolioColors.MenuSurface else Color.White, fontSize = font, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

/** The narrowest grid that has room for the two-icon pill between the edit, close and power buttons (three 48 dp targets, the pill, and a little air). */
private const val CC_SWITCH_PILL_MIN_GRID_DP = 280

/** The height of the row the switch stands in at the top of a panel (the 48 dp touch target of its buttons), kept free where the switch is drawn once above both panels. */
private const val PANEL_SWITCH_ROW_DP = 48

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

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { if (cameraId == torchId) torchOn = enabled }
    }

    private val levels = SystemLevels(context) { reading ->
        volumeLevel.fromOutside(reading.volume)
        brightnessLevel.fromOutside(reading.brightness)
        applyMode(reading)
    }

    fun start() {
        runCatching { camera.registerTorchCallback(torchCallback, null) }
        levels.read().let { volumeLevel.fromOutside(it.volume); brightnessLevel.fromOutside(it.brightness); applyMode(it) }
        levels.start()
        rotationLocked = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 0 }.getOrDefault(false)
        dndOn = notifications.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
        bluetoothOn = runCatching { context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.isEnabled == true }.getOrDefault(false)
    }

    /** Unregisters everything start() registered: the torch callback and the volume, brightness and screen observers. */
    fun stop() { levels.stop(); runCatching { camera.unregisterTorchCallback(torchCallback) } }

    /** A finger went down on, or came up from, a slider. On release the latest outside change lands, then the real value is read. */
    fun holdVolume(down: Boolean) { if (down) volumeLevel.hold() else { volumeLevel.release(); levels.requestRefresh() } }
    fun holdBrightness(down: Boolean) { if (down) brightnessLevel.hold() else { brightnessLevel.release(); levels.requestRefresh() } }

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
            if (plan.scrubber) Column(Modifier.fillMaxWidth(.84f).padding(top = 4.dp)) {
                Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = .22f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f)).background(Color.White))
                }
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatMediaTime(position), color = Color.White.copy(alpha = .72f), fontSize = FolioType.GROUP_LABEL.sp)
                    Text("-" + formatMediaTime(duration - position), color = Color.White.copy(alpha = .72f), fontSize = FolioType.GROUP_LABEL.sp)
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
private const val CC_PAGE_TURN = .2f

/** How much Control Center's header and strip icons grow with the font size: never smaller than drawn, never past a third larger, so they stay inside their 48 dp targets. */
internal fun ccIconScale(fontScale: Float): Float = fontScale.coerceIn(1f, 4f / 3f)
