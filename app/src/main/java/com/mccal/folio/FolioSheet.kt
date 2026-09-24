package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Fades or slides an overlay into place, and never leaves it invisible.
 *
 * These entrances run on the frame clock, which stops while Folio isn't drawing. A sheet opened just as Folio went
 * behind a system permission screen could stay at its starting value: fully transparent, or slid off screen. The
 * window was still there and still took every tap, so Home sat blurred behind an overlay nobody could see or close,
 * until the phone restarted. The timeout here puts the overlay in place even when no frame ever arrived.
 */
@Composable
internal fun rememberEntrance(stiffness: Float, dampingRatio: Float = 1f, from: Float = 0f, to: Float = 1f):
    androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val reduceMotion = LocalReduceMotion.current
    val entrance = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(if (reduceMotion) to else from) }
    // The animation runs in the composition, which is where the frame clock lives.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        entrance.animateTo(to, androidx.compose.animation.core.spring(dampingRatio = dampingRatio, stiffness = stiffness))
    }
    // The timeout must not depend on frames, so it runs on the main thread alone. Snapping cancels the animation.
    DisposableEffect(Unit) {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
        scope.launch { kotlinx.coroutines.delay(ENTRANCE_TIMEOUT_MS); if (entrance.value != to) entrance.snapTo(to) }
        onDispose { scope.cancel() }
    }
    return entrance
}

private const val ENTRANCE_TIMEOUT_MS = 900L

/**
 * A 0–1 overlay progress that springs to [target] and, like [rememberEntrance], snaps there if frames stall (Folio in
 * the background, a stuck window), so a closed panel can never stay on screen or leave Home blurred.
 */
@Composable
internal fun rememberSettlingProgress(target: Float, spec: androidx.compose.animation.core.AnimationSpec<Float>,
    /** Longer than the slowest Animation Speed takes to settle, so a normal close never jumps. */
    timeoutMs: Long = 1_500L): androidx.compose.runtime.State<Float> {
    val progress = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(target) }
    androidx.compose.runtime.LaunchedEffect(target) { progress.animateTo(target, spec) }
    DisposableEffect(target) {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
        scope.launch { kotlinx.coroutines.delay(timeoutMs); if (progress.value != target) progress.snapTo(target) }
        onDispose { scope.cancel() }
    }
    return progress.asState()
}

/**
 * Folio's sheet: same API as Material's ModalBottomSheet (this package-level function shadows the
 * star-imported one in launcher files), but dark iOS-style glass, a slim handle, and Home blurred behind.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    @Suppress("UNUSED_PARAMETER") containerColor: Color = Color.Unspecified,
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    /** iOS Settings-style full-screen page that slides in, instead of a bottom sheet. */
    fullScreen: Boolean = false,
    /** Widest the sheet gets when shown as a centered form sheet on a regular-size screen. */
    formWidth: androidx.compose.ui.unit.Dp = 560.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    DisposableEffect(Unit) { LauncherSheetsOpen.intValue++; onDispose { LauncherSheetsOpen.intValue-- } }
    if (fullScreen) { FullScreenPage(onDismissRequest, content); return }
    // Regular size (inner screen, either orientation): an iPad-style form sheet centered over Home instead of a stretched bottom sheet.
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    if (configuration.fitsRegularHomeLayout()) {
        FormSheet(onDismissRequest, properties.shouldDismissOnBackPress, formWidth, modifier, content); return
    }
    MaterialTheme(colorScheme = FolioSheetColors, typography = MaterialTheme.typography) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = onDismissRequest, modifier = modifier, sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = FolioColors.SecondaryBackground.copy(alpha = .97f), contentColor = Color.White,
            scrimColor = Color.Black.copy(alpha = .35f),
            dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 5.dp)
                .background(Color.White.copy(alpha = .3f), RoundedCornerShape(3.dp))) },
            properties = properties, content = {
                // The sheet is its own window; hide the status bar there too so Home stays edge to edge.
                val view = androidx.compose.ui.platform.LocalView.current
                androidx.compose.runtime.LaunchedEffect(view) {
                    (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { w ->
                        androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).apply {
                            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                        }
                    }
                }
                content()
            },
        )
    }
}

@Composable
private fun FullScreenPage(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
            dismissOnBackPress = false)) {
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.LaunchedEffect(view) {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { w ->
                w.setDimAmount(0f)
                w.setWindowAnimations(0)
                androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).apply {
                    systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                }
            }
        }
        val slide = rememberEntrance(stiffness = 500f, from = 1f, to = 0f)
        // Once the page covers Home, drawing and blurring Home is work nobody sees, and it shows up as stutter
        // while you're moving between Folio and Android's permission screens. It comes back the moment it's needed.
        val covering = slide.value == 0f
        DisposableEffect(covering) {
            if (covering) LauncherPagesOpen.intValue++
            onDispose { if (covering) LauncherPagesOpen.intValue-- }
        }
        // Dragging a Home layout slider: the page fades to a trace so the real Home behind shows the change, and only
        // the slider stays readable (a capsule drawn where it is).
        val peek = SettingsPeek.value
        val fade = androidx.compose.animation.core.animateFloatAsState(if (peek != null) .14f else 1f,
            if (LocalReduceMotion.current) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(250), label = "settings peek")
        DisposableEffect(Unit) { onDispose { SettingsPeek.value = null } }
        MaterialTheme(colorScheme = FolioSheetColors, typography = MaterialTheme.typography) {
            Box(Modifier.fillMaxSize()) {
                // The page background stays as a faint trace; its text and controls fade out completely, so nothing
                // half-readable competes with Home (only the dragged slider's capsule shows).
                // Predictive back: the page eases right and shrinks a little with the swipe, like Android's own screens.
                val back = androidx.compose.animation.core.animateFloatAsState(SheetBackProgress.floatValue,
                    androidx.compose.animation.core.spring(stiffness = 1400f), label = "sheet back").value
                androidx.compose.material3.Surface(Modifier.fillMaxSize().graphicsLayer {
                    translationX = size.width * slide.value + size.width * .08f * back
                    scaleX = 1f - .1f * back; scaleY = scaleX
                    shape = androidx.compose.foundation.shape.RoundedCornerShape((28 * back).dp); clip = back > 0f
                }, color = Color.Black.copy(alpha = fade.value), contentColor = Color.White) {
                    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()
                        .graphicsLayer { alpha = ((fade.value - .14f) / .86f).coerceIn(0f, 1f) }
                        .windowInsetsPadding(WindowInsets.folioSafeTop).navigationBarsPadding()
                        .windowInsetsPadding(WindowInsets.ime), content = content)
                }
                peek?.let { PeekCapsule(it) }
            }
        }
    }
}

/** The dragged slider over the faded Settings page: its name, live value and track, where the slider is. */
@Composable
private fun PeekCapsule(peek: PeekSlider) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val height = with(density) { peek.bounds.height.toDp() }
    // Along the bottom of the Home area, clear of the Side Bar and dock, so the whole page stays in view.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding()
        .padding(horizontal = 96.dp).padding(bottom = 64.dp), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
    androidx.compose.foundation.layout.Column(Modifier
        .size(minOf(maxWidth, 440.dp), height).clip(RoundedCornerShape(FolioRadius.GROUP.dp)).background(FolioColors.SecondaryBackground.copy(alpha = .94f))
        .padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
        androidx.compose.foundation.layout.Row {
            androidx.compose.material3.Text(peek.label, Modifier.weight(1f), color = Color.White, fontSize = FolioType.BODY.sp, maxLines = 1)
            androidx.compose.material3.Text(peek.valueLabel, color = Color.White.copy(alpha = .6f), fontSize = FolioType.BODY.sp, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = .22f))) {
                Box(Modifier.fillMaxWidth(peek.fraction).height(4.dp).background(LocalAccent.current.fill))
            }
            // The thumb, where the finger is.
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                Box(Modifier.padding(start = (maxWidth - 28.dp) * peek.fraction.coerceIn(0f, 1f)).size(28.dp)
                    .background(Color.White, androidx.compose.foundation.shape.CircleShape))
            }
        }
    }
    }
}

@Composable
private fun FormSheet(onDismissRequest: () -> Unit, dismissOnBack: Boolean, width: androidx.compose.ui.unit.Dp, modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
            dismissOnBackPress = dismissOnBack)) {
        FolioDialogWindow(dim = 0f)
        val appear = rememberEntrance(stiffness = 520f)
        MaterialTheme(colorScheme = FolioSheetColors, typography = MaterialTheme.typography) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = appear.value }.background(Color.Black.copy(alpha = .35f))
                .clickable(androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null, onClick = onDismissRequest))
            FoldAvoidingBox(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    val maxSheetHeight = minOf(maxHeight, 760.dp)
                    androidx.compose.material3.Surface(modifier.widthIn(max = width).fillMaxWidth().heightIn(max = maxSheetHeight)
                        .graphicsLayer {
                            // Slides up into place, like a sheet presented on iPad.
                            alpha = appear.value
                            translationY = (1f - appear.value) * size.height * .12f
                        },
                        shape = RoundedCornerShape(FolioRadius.CARD.dp), color = FolioColors.SecondaryBackground, contentColor = Color.White) {
                        androidx.compose.foundation.layout.Column(Modifier.padding(top = 14.dp), content = content)
                    }
                }
            }
        }
    }
}

/**
 * iOS alert with Material's AlertDialog API (this package-level function shadows the star-imported one):
 * a 270dp rounded card, centered title and message, and full-width text buttons split by hairlines.
 */
@Composable
internal fun AlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit, modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null, title: (@Composable () -> Unit)? = null, text: (@Composable () -> Unit)? = null) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        FolioDialogWindow(dim = .3f)
        val appear = rememberEntrance(stiffness = 900f, dampingRatio = .85f)
        val base = MaterialTheme.typography
        // The accent's ink, not its fill: the alert is always dark, and a fill-weight colour on its grey fails
        // contrast (iOS blue measures 3.82:1 there).
        val blue = LocalAccent.current.ink
        fun buttons(weight: androidx.compose.ui.text.font.FontWeight) = base.copy(labelLarge = androidx.compose.ui.text.TextStyle(fontSize = FolioType.BODY.sp, fontWeight = weight))
        FoldAvoidingBox(Modifier.windowInsetsPadding(WindowInsets.safeDrawing), role = FoldRole.INFO) {
            MaterialTheme(colorScheme = FolioSheetColors.copy(primary = blue), typography = base) {
                androidx.compose.foundation.layout.Column(modifier.width(270.dp)
                    .graphicsLayer { alpha = appear.value; scaleX = 1.12f - .12f * appear.value; scaleY = scaleX }
                    .clip(RoundedCornerShape(FolioRadius.CARD.dp)).background(Color(0xFF2C2C2E).copy(alpha = .98f))) {
                    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 19.dp, bottom = 16.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                        title?.let { androidx.compose.material3.ProvideTextStyle(androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = FolioType.BODY.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center), it) }
                        text?.let { androidx.compose.material3.ProvideTextStyle(androidx.compose.ui.text.TextStyle(color = Color.White.copy(alpha = .85f), fontSize = FolioType.FOOTNOTE.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)) { Box(Modifier.heightIn(max = 420.dp)) { it() } } }
                    }
                    androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = .16f), thickness = .5.dp)
                    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                        dismissButton?.let { dismiss ->
                            Box(Modifier.weight(1f).heightIn(min = 44.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                MaterialTheme(colorScheme = FolioSheetColors.copy(primary = blue), typography = buttons(androidx.compose.ui.text.font.FontWeight.Normal), content = dismiss)
                            }
                            androidx.compose.material3.VerticalDivider(color = Color.White.copy(alpha = .16f), thickness = .5.dp)
                        }
                        Box(Modifier.weight(1f).heightIn(min = 44.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            MaterialTheme(colorScheme = FolioSheetColors.copy(primary = blue), typography = buttons(androidx.compose.ui.text.font.FontWeight.SemiBold), content = confirmButton)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Folio's overlay windows (form sheets, alerts, folders): no system window animation, Android's status bar
 * stays hidden like on Home (so nothing shifts), and Home dims and blurs behind, like iOS materials.
 */
@Composable
internal fun FolioDialogWindow(dim: Float, blurRadiusDp: Int = 0) {
    val view = androidx.compose.ui.platform.LocalView.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.LaunchedEffect(view) {
        (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { w ->
            w.setDimAmount(dim)
            w.setWindowAnimations(0)
            if (blurRadiusDp > 0) {
                w.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                w.attributes = w.attributes.apply { blurBehindRadius = with(density) { blurRadiusDp.dp.roundToPx() } }
            }
            androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).apply {
                systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            }
        }
    }
}
