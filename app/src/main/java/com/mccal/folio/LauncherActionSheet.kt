package com.mccal.folio

import androidx.compose.ui.res.stringResource
import androidx.activity.OnBackPressedCallback
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Handles Back on the ComponentDialog which owns a Material modal sheet. A regular Compose
 * BackHandler sees the activity owner inherited by the sheet composition, while platform Back is
 * dispatched to the dialog first.
 */
@Composable
internal fun ModalDialogBackHandler(onBack: () -> Unit) {
    val localView = androidx.compose.ui.platform.LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBack by rememberUpdatedState(onBack)
    val dispatcherOwner = remember(localView) {
        val dialogWindow = (localView.parent as? DialogWindowProvider)?.window
        dialogWindow?.decorView?.findViewTreeOnBackPressedDispatcherOwner()
    }
    DisposableEffect(dispatcherOwner, lifecycleOwner) {
        val callback = object : OnBackPressedCallback(dispatcherOwner != null) {
            // Predictive back: full-screen sheets follow the swipe (SheetBackProgress) before it commits or cancels.
            override fun handleOnBackProgressed(backEvent: androidx.activity.BackEventCompat) { SheetBackProgress.floatValue = backEvent.progress }
            override fun handleOnBackCancelled() { SheetBackProgress.floatValue = 0f }
            override fun handleOnBackPressed() { SheetBackProgress.floatValue = 0f; currentOnBack() }
        }
        dispatcherOwner?.onBackPressedDispatcher?.addCallback(lifecycleOwner, callback)
        onDispose { callback.remove() }
    }
}

/**
 * iOS 18's Home "Edit" menu: tapping Edit while icons wiggle opens a short menu right under the button, the same
 * on the cover, unfolded and turned sideways (it's a popover, not a sheet, so it never covers the page you're editing).
 */
@Composable
internal fun HomeEditMenu(anchor: androidx.compose.ui.unit.IntRect?, onDismiss: () -> Unit, onWidgets: () -> Unit, onWallpaper: () -> Unit,
    onCustomize: () -> Unit, onAddPage: (() -> Unit)?, onRemovePage: (() -> Unit)?) {
    val density = LocalDensity.current
    val margin = with(density) { 12.dp.roundToPx() }
    val reduceMotion = LocalReduceMotion.current
    val appear = remember { androidx.compose.animation.core.Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = .8f, stiffness = 700f)) }
    var alignEnd by remember { mutableStateOf(false) }
    val position = remember(anchor, margin) {
        object : androidx.compose.ui.window.PopupPositionProvider {
            override fun calculatePosition(anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: androidx.compose.ui.unit.IntSize,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection, popupContentSize: androidx.compose.ui.unit.IntSize): IntOffset {
                val a = anchor ?: androidx.compose.ui.unit.IntRect(windowSize.width / 2, margin * 4, windowSize.width / 2, margin * 4)
                // Line the menu up with the Edit button's leading edge, or its trailing edge when that keeps it on screen.
                val fromStart = a.left
                val fromEnd = a.right - popupContentSize.width
                val x = (if (fromStart + popupContentSize.width + margin <= windowSize.width) fromStart else fromEnd)
                    .coerceIn(margin, maxOf(margin, windowSize.width - popupContentSize.width - margin))
                alignEnd = x != fromStart
                // Below the button, or above it when the button is near the bottom (the unfolded Edit bar).
                val below = a.bottom + margin / 2
                val y = if (below + popupContentSize.height + margin <= windowSize.height) below
                    else (a.top - margin / 2 - popupContentSize.height).coerceAtLeast(margin)
                return IntOffset(x, y)
            }
        }
    }
    androidx.compose.ui.window.Popup(popupPositionProvider = position, onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true)) {
        Column(Modifier.width(250.dp)
            .graphicsLayer {
                val g = appear.value; alpha = g.coerceIn(0f, 1f); scaleX = .7f + .3f * g; scaleY = scaleX
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (alignEnd) 1f else 0f, 0f)
            }
            .shadow(18.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color(0xFF2A2A2E).copy(alpha = .97f))
            .border(FolioGlass.edge, RoundedCornerShape(18.dp)).testTag("home-edit-menu")) {
            fun act(action: () -> Unit) = { onDismiss(); action() }
            Box(Modifier.testTag("empty-space-widgets")) { MenuRow(stringResource(R.string.add_widget_2), Icons.Rounded.Widgets, onClick = act(onWidgets)) }
            MenuDivider()
            Box(Modifier.testTag("empty-space-wallpaper")) { MenuRow(stringResource(R.string.wallpaper_appearance_2), Icons.Rounded.Wallpaper, onClick = act(onWallpaper)) }
            // A thicker gap between groups, like iOS menus.
            Box(Modifier.fillMaxWidth().height(8.dp).background(Color.Black.copy(alpha = .25f)))
            onAddPage?.let { Box(Modifier.testTag("empty-space-add-page")) { MenuRow(stringResource(R.string.add_page), Icons.Rounded.AddToPhotos, onClick = act(it)) }; MenuDivider() }
            onRemovePage?.let { Box(Modifier.testTag("empty-space-remove-page")) { MenuRow(stringResource(R.string.remove_this_empty_page), Icons.Rounded.DeleteOutline, destructive = true, onClick = act(it)) }; MenuDivider() }
            Box(Modifier.testTag("empty-space-customize")) { MenuRow(stringResource(R.string.folio_settings), Icons.Rounded.Tune, onClick = act(onCustomize)) }
        }
    }
}
