package com.mccal.folio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * One glass language for every Folio overlay (Control Center, Notification Center, Spotlight,
 * pickers): a darker, more solid frost than the Home rail, so busy blurred icons behind never
 * compete with the content, plus a hairline light edge like iOS materials.
 */
internal object FolioGlass {
    /** Dim over the blurred Home behind an overlay. */
    val scrim = Color.Black.copy(alpha = .42f)
    /** Large tiles (Control Center modules). */
    val module = Color(0xFF1C1C1E).copy(alpha = .76f)
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
    primary = Color(0xFF0A84FF), onPrimary = Color.White,
    primaryContainer = Color(0xFF0A84FF), onPrimaryContainer = Color.White,
    secondary = Color(0xFF64D2FF), onSecondary = Color.Black,
    secondaryContainer = Color(0xFF3A3A3C), onSecondaryContainer = Color.White,
    surface = Color(0xFF1C1C1E), onSurface = Color.White, onSurfaceVariant = Color(0xFFA1A1A6),
    surfaceContainerLowest = Color(0xFF141416), surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF242428), surfaceContainerHigh = Color(0xFF2C2C2E), surfaceContainerHighest = Color(0xFF3A3A3C),
    outline = Color(0xFF545458), outlineVariant = Color(0xFF38383A), error = Color(0xFFFF453A),
)

/** Set while any launcher sheet is open, so Home blurs behind it like the other overlays. */
internal val LauncherSheetsOpen = androidx.compose.runtime.mutableIntStateOf(0)

/** Full-screen pages (Setup, Settings pages): nothing behind them is visible, so Home skips its blur while one is up. */
internal val LauncherPagesOpen = androidx.compose.runtime.mutableIntStateOf(0)

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
