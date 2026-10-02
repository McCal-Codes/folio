package com.mccal.folio

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * iOS-style legibility for things drawn straight on the wallpaper (labels, status, page dots, cards' text):
 * dark ink over light wallpapers, white ink over dark ones.
 */
@Immutable
internal data class HomeInk(val dark: Boolean, /** Chosen from the wallpaper ("Automatic") rather than set by hand. */ val automatic: Boolean = false) {
    val primary get() = if (dark) FolioColors.SecondaryBackground else Color.White
    val secondary get() = primary.copy(alpha = if (dark) .65f else .75f)
    val faint get() = primary.copy(alpha = if (dark) .3f else .4f)
    /** Soft shadow for labels: dark under white text, light under dark text. */
    val labelShadow get() = if (dark) Shadow(Color.White.copy(alpha = .45f), Offset(0f, 1f), 3f)
        else Shadow(Color.Black.copy(alpha = .55f), Offset(0f, 1f), 3f)
}

internal val LocalHomeInk = staticCompositionLocalOf { HomeInk(dark = false) }

/** "AUTO", "LIGHT" (white text) or "DARK" (dark text). */
internal fun homeInkFor(setting: String, wallpaperPrefersDarkText: Boolean) = HomeInk(when (setting) {
    "DARK" -> true
    "LIGHT" -> false
    else -> wallpaperPrefersDarkText
}, automatic = setting != "DARK" && setting != "LIGHT")

internal fun WallpaperColors?.prefersDarkText(): Boolean =
    this != null && colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0

/** What Folio knows about the wallpaper behind Home, without reading its pixels. */
@Immutable
internal data class WallpaperTone(val prefersDarkText: Boolean = false, val primary: Int? = null, val secondary: Int? = null)

internal val LocalWallpaperTone = staticCompositionLocalOf { WallpaperTone() }

internal fun WallpaperColors?.tone() = WallpaperTone(prefersDarkText(), this?.primaryColor?.toArgb(), this?.secondaryColor?.toArgb())

/**
 * The wallpaper's tone. Android's wallpaper: the system's own colors and hints (no permission needed, updates
 * when the wallpaper changes). A picture of Folio's, whether your photo or a piece of art: the same, computed
 * from the picture itself. No picture at all: white text, no tint.
 */
@Composable
internal fun rememberWallpaperTone(systemWallpaper: Boolean): WallpaperTone {
    val context = LocalContext.current
    if (systemWallpaper) {
        val manager = remember(context) { WallpaperManager.getInstance(context) }
        var tone by remember { mutableStateOf(runCatching { manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull().tone()) }
        DisposableEffect(manager) {
            val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0) tone = colors.tone()
            }
            runCatching { manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper())) }
            onDispose { runCatching { manager.removeOnColorsChangedListener(listener) } }
        }
        return tone
    }
    val revision = LauncherBackgroundCache.revision.intValue
    val tone by produceState(WallpaperTone(), revision) {
        value = withContext(Dispatchers.Default) {
            val photo = loadLauncherBackground(context)?.takeUnless { it.isRecycled } ?: return@withContext WallpaperTone()
            runCatching { WallpaperColors.fromBitmap(photo).tone() }.getOrDefault(WallpaperTone())
        }
    }
    return tone
}

/** Blends [base] toward the wallpaper's main color, keeping [base]'s alpha (iOS-style tinted material). */
internal fun tintedGlass(base: Color, wallpaper: Int?, amount: Float = .28f): Color {
    if (wallpaper == null) return base
    val w = Color(wallpaper)
    return Color(base.red + (w.red - base.red) * amount, base.green + (w.green - base.green) * amount,
        base.blue + (w.blue - base.blue) * amount, base.alpha)
}

/** A clear, bright version of a wallpaper color for tinted icons (saturation ≥ 45%, brightness ≥ 85%). */
internal fun vividTint(argb: Int): Int {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    hsv[1] = hsv[1].coerceAtLeast(.45f)
    hsv[2] = hsv[2].coerceAtLeast(.85f)
    return android.graphics.Color.HSVToColor(hsv)
}
