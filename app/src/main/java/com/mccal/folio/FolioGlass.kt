package com.mccal.folio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer

/**
 * One glass language for every Folio overlay (Control Center, Notification Center, Spotlight,
 * pickers): a darker, more solid frost than the Home rail, so busy blurred icons behind never
 * compete with the content, plus a hairline light edge like iOS materials.
 */
internal object FolioGlass {
    /** Dim over the blurred Home behind an overlay. */
    val scrim = Color.Black.copy(alpha = .42f)
    /** Large tiles (Control Center modules). */
    val module = FolioColors.SecondaryBackground.copy(alpha = .76f)
    /** Cards and rows (notifications, Spotlight sections, search field). */
    val card = Color(0xFF242428).copy(alpha = .82f)
    /** Controls sitting on a card (inactive toggles, pills, chips). */
    val raised = Color.White.copy(alpha = .14f)
    /** Hairline edge that separates glass from glass. */
    val edge = BorderStroke(1.dp, Color.White.copy(alpha = .08f))
    /** Secondary text on glass. */
    val secondary = Color.White.copy(alpha = .62f)
}

/** Dark, iOS-like colors for Folio's sheets (settings, app options, setup). */
internal val FolioSheetColors = androidx.compose.material3.darkColorScheme(
    primary = FolioColors.Blue, onPrimary = Color.White,
    primaryContainer = FolioColors.Blue, onPrimaryContainer = Color.White,
    secondary = FolioColors.Cyan, onSecondary = Color.Black,
    secondaryContainer = Color(0xFF3A3A3C), onSecondaryContainer = Color.White,
    surface = FolioColors.SecondaryBackground, onSurface = Color.White, onSurfaceVariant = Color(0xFFA1A1A6),
    surfaceContainerLowest = Color(0xFF141416), surfaceContainerLow = FolioColors.SecondaryBackground,
    surfaceContainer = Color(0xFF242428), surfaceContainerHigh = Color(0xFF2C2C2E), surfaceContainerHighest = Color(0xFF3A3A3C),
    outline = Color(0xFF545458), outlineVariant = Color(0xFF38383A), error = FolioColors.Red,
)

/** Set while any launcher sheet is open, so Home blurs behind it like the other overlays. */
internal val LauncherSheetsOpen = androidx.compose.runtime.mutableIntStateOf(0)

/** Full-screen pages (Setup, Settings pages): nothing behind them is visible, so Home skips its blur while one is up. */
internal val LauncherPagesOpen = androidx.compose.runtime.mutableIntStateOf(0)

/** How far a back swipe has gone over the full-screen Settings page (0–1), for predictive back. */
internal val SheetBackProgress = androidx.compose.runtime.mutableFloatStateOf(0f)

/** A Home layout slider being dragged in Settings, with its bounds in the Settings window (see [SettingsPeek]). */
internal data class PeekSlider(val label: String, val valueLabel: String, val fraction: Float, val bounds: androidx.compose.ui.geometry.Rect)

/** Set while a Home layout slider is dragged: Settings fades so the real Home shows the change, like iOS. */
internal val SettingsPeek = androidx.compose.runtime.mutableStateOf<PeekSlider?>(null)

/**
 * Top-safe insets that respect the real camera cutout. Folio hides the status bar on Home, which
 * makes `statusBarsPadding()` zero, so content slid under the camera; the display cutout is still
 * reported while bars are hidden, so union both.
 */
internal val WindowInsets.Companion.folioSafeTop: WindowInsets
    @androidx.compose.runtime.Composable get() = WindowInsets.statusBars.union(WindowInsets.displayCutout).union(rememberHiddenCameraInsets())

/** Per-folder tint colors, provided from saved settings. */
internal val LocalFolderColors = androidx.compose.runtime.compositionLocalOf { emptyMap<String, Long>() }

/** Velvet/ColorFlow tint options, provided from settings. */
@androidx.compose.runtime.Immutable
internal data class TintOptions(val notifications: Boolean = false, val media: Boolean = true, val notificationAppRow: Boolean = true)
internal val LocalTintOptions = androidx.compose.runtime.staticCompositionLocalOf { TintOptions() }

/** How strong Home's glass is: widget frost and the outline around widgets and Side Bar capsules (Settings › Glass). */
internal data class GlassLook(val widget: Float = .26f, val outline: Float = .16f) {
    val outlineColor get() = androidx.compose.ui.graphics.Color.White.copy(alpha = outline)
}
internal val LocalGlassLook = androidx.compose.runtime.staticCompositionLocalOf { GlassLook() }

/** Settings › Home Screen & Dock › Folders. */
enum class FolderBackground(@androidx.annotation.StringRes val label: Int) { GLASS(R.string.glass), SOLID(R.string.solid), CLEAR(R.string.clear) }
internal data class FolderLook(val columns: Int = 0, val background: FolderBackground = FolderBackground.GLASS)
internal val LocalFolderLook = androidx.compose.runtime.staticCompositionLocalOf { FolderLook() }

/** App name size on Home (Settings › Icons & Side Bar). */
enum class LabelSize(@androidx.annotation.StringRes val label: Int, val sp: Float, val lineSp: Float) { SMALL(R.string.small, 10f, 13f), STANDARD(R.string.standard, 11f, 14f), LARGE(R.string.large, 13f, 16f) }
internal val LocalLabelSize = androidx.compose.runtime.staticCompositionLocalOf { LabelSize.STANDARD }

/**
 * Animation Speed (Settings › Gestures & Actions): scales the stiffness of Folio's springs, so panels, folders, menus
 * and page snaps all move faster or slower together. Android's Remove animations still turns motion off.
 */
enum class MotionSpeed(@androidx.annotation.StringRes val label: Int, val factor: Float) {
    RELAXED(R.string.relaxed, .55f), STANDARD(R.string.standard, 1f), SNAPPY(R.string.snappy, 1.8f);
    companion object {
        @Volatile var current: MotionSpeed = STANDARD
        fun <T> spring(dampingRatio: Float, stiffness: Float) =
            androidx.compose.animation.core.spring<T>(dampingRatio = dampingRatio, stiffness = stiffness * current.factor)
    }
}

/**
 * Soft edges on scrolling content, like iOS: rows fade out toward an edge where there's more to scroll. The fade
 * follows an eased curve rather than a straight ramp, and grows in over the first [size] of scrolling instead of
 * popping in, so a list at rest (or barely nudged) keeps crisp first and last rows. [top] and [bottom] return how much
 * of each fade to show, from 0 to 1, given the fade depth in pixels.
 */
internal fun androidx.compose.ui.Modifier.edgeFade(top: (Float) -> Float, bottom: (Float) -> Float,
    size: androidx.compose.ui.unit.Dp = 28.dp): androidx.compose.ui.Modifier =
    // An offscreen layer only while an edge is actually fading, so a list at rest draws normally.
    graphicsLayer {
        val px = size.toPx()
        compositingStrategy = if (top(px) > 0f || bottom(px) > 0f) androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            else androidx.compose.ui.graphics.CompositingStrategy.Auto
    }.drawWithContent {
        drawContent()
        val px = size.toPx().coerceAtMost(this.size.height / 3f)
        val t = top(px).coerceIn(0f, 1f)
        val b = bottom(px).coerceIn(0f, 1f)
        if (t > 0f) drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(*fadeStops(t, towardEnd = false), startY = 0f, endY = px),
            size = androidx.compose.ui.geometry.Size(this.size.width, px), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
        if (b > 0f) drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(*fadeStops(b, towardEnd = true),
            startY = this.size.height - px, endY = this.size.height),
            topLeft = androidx.compose.ui.geometry.Offset(0f, this.size.height - px),
            size = androidx.compose.ui.geometry.Size(this.size.width, px), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
    }

/** Mask stops for one fade: fully kept away from the edge, easing (smoothstep) down to 1 − [amount] at the edge. */
private fun fadeStops(amount: Float, towardEnd: Boolean): Array<Pair<Float, Color>> = Array(FADE_STEPS + 1) { i ->
    val s = i / FADE_STEPS.toFloat()
    val fromEdge = if (towardEnd) 1f - s else s
    val eased = fromEdge * fromEdge * (3f - 2f * fromEdge)
    s to Color.Black.copy(alpha = (1f - amount) + amount * eased)
}

private const val FADE_STEPS = 6

internal fun androidx.compose.ui.Modifier.edgeFade(state: androidx.compose.foundation.ScrollState) =
    edgeFade({ px -> state.value / px }, { px -> if (state.maxValue == Int.MAX_VALUE) 0f else (state.maxValue - state.value) / px })

internal fun androidx.compose.ui.Modifier.edgeFade(state: androidx.compose.foundation.lazy.LazyListState) =
    edgeFade({ px -> if (state.firstVisibleItemIndex > 0) 1f else state.firstVisibleItemScrollOffset / px }, { px ->
        val info = state.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull() ?: return@edgeFade 0f
        if (last.index < info.totalItemsCount - 1) 1f
        else (last.offset + last.size + info.afterContentPadding - info.viewportEndOffset) / px
    })

internal fun androidx.compose.ui.Modifier.edgeFade(state: androidx.compose.foundation.lazy.grid.LazyGridState) =
    edgeFade({ px -> if (state.firstVisibleItemIndex > 0) 1f else state.firstVisibleItemScrollOffset / px }, { px ->
        val info = state.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull() ?: return@edgeFade 0f
        if (last.index < info.totalItemsCount - 1) 1f
        else (last.offset.y + last.size.height + info.afterContentPadding - info.viewportEndOffset) / px
    })

/** verticalScroll with soft edges (see edgeFade). */
internal fun androidx.compose.ui.Modifier.fadingVerticalScroll() = composed {
    val state = androidx.compose.foundation.rememberScrollState()
    edgeFade(state).verticalScroll(state)
}

/** Reduce Transparency is in effect (the setting, or Android's high contrast): glass controls show as nearly solid. */
internal val LocalSolidGlass = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Android 14+ high contrast (Settings › Accessibility › Contrast). Earlier versions don't expose it, so it's off. */
@androidx.compose.runtime.Composable
internal fun rememberSystemHighContrast(): Boolean {
    if (android.os.Build.VERSION.SDK_INT < 34) return false
    val context = androidx.compose.ui.platform.LocalContext.current
    val ui = androidx.compose.runtime.remember { context.getSystemService(android.app.UiModeManager::class.java) }
    val contrast = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(ui?.contrast ?: 0f) }
    androidx.compose.runtime.DisposableEffect(ui) {
        val listener = android.app.UiModeManager.ContrastChangeListener { contrast.floatValue = it }
        ui?.addContrastChangeListener(context.mainExecutor, listener)
        onDispose { ui?.removeContrastChangeListener(listener) }
    }
    // Android's levels are 0 (standard), 0.5 (medium) and 1 (high).
    return contrast.floatValue >= .5f
}
