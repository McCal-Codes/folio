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
import kotlinx.coroutines.launch
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

/**
 * What Folio knows about the wallpaper behind Home, without reading its pixels. [fromAndroid] is true when Android's own
 * wallpaper is behind Home (the user's setting, not whether a Folio picture happens to be saved): the colors are then
 * the ones Android shares for it, and all null when it shares none.
 *
 * [palette] is the phone's theme colors (Android's system palette), kept only with Android's wallpaper and only for the
 * colors a person picks as the wallpaper's (the Big Clock's, and tinted icons' "Wallpaper color" through [choiceColor]),
 * as their second source when Android shares no wallpaper colors (One UI on the Fold8 shares none). It stays out of
 * [primary]: the status rail and the glass read [primary] as the wallpaper's own color, which the palette is not.
 */
@Immutable
internal data class WallpaperTone(val prefersDarkText: Boolean = false, val primary: Int? = null, val secondary: Int? = null,
    val tertiary: Int? = null, val fromAndroid: Boolean = false, val palette: SystemPalette? = null)

internal val LocalWallpaperTone = staticCompositionLocalOf { WallpaperTone() }

internal fun WallpaperColors?.tone(fromAndroid: Boolean = false, palette: SystemPalette? = null) = WallpaperTone(prefersDarkText(),
    this?.primaryColor?.toArgb(), this?.secondaryColor?.toArgb(), this?.tertiaryColor?.toArgb(), fromAndroid, palette)

/**
 * The middle shade (500, tone 50) of the three accent ranges of Android's system palette (API 31+). Android, or One UI
 * when its Color palette is on, makes these from the wallpaper or from a color the user picked; an app can't always tell which.
 * The middle shade keeps each range's hue without the lightening or darkening of the light and dark theme roles.
 */
@Immutable
internal data class SystemPalette(val accent1: Int, val accent2: Int, val accent3: Int) {
    val colors get() = listOf(accent1, accent2, accent3)
}

internal fun systemPalette(context: android.content.Context): SystemPalette? = runCatching {
    SystemPalette(context.getColor(android.R.color.system_accent1_500), context.getColor(android.R.color.system_accent2_500),
        context.getColor(android.R.color.system_accent3_500))
}.getOrNull()

/** Where the Big Clock's colors come from with Android's wallpaper, in the order they are tried (NONE for a Folio picture). */
internal enum class WallpaperColorSource { ANDROID_COLORS, SYSTEM_PALETTE, NONE }

internal fun WallpaperTone.colorSource(): WallpaperColorSource = when {
    !fromAndroid -> WallpaperColorSource.NONE
    primary != null -> WallpaperColorSource.ANDROID_COLORS
    palette != null -> WallpaperColorSource.SYSTEM_PALETTE
    else -> WallpaperColorSource.NONE
}

/**
 * The color a "Wallpaper color" choice stands for, from the same sources in the same order as the Big Clock
 * ([colorSource]): the wallpaper's own main color, else with Android's wallpaper the phone's theme color (the palette's
 * first accent), else null. Unlike [WallpaperTone.primary], which the status rail and the glass read as the wallpaper's
 * own color, this can be the palette's, so it is only for things a person picks as "the wallpaper's color".
 */
internal fun WallpaperTone.choiceColor(): Int? = primary ?: palette?.accent1?.takeIf { fromAndroid }

/** What tinted icons draw with "Wallpaper color" picked, and what its dot in Home customization shows: one source for both. */
internal fun wallpaperIconTint(tone: WallpaperTone): Int? = tone.choiceColor()?.let(::vividTint)

/**
 * Android's colors for the home screen's wallpaper, or a tone with no colors when it shares none (or the call fails).
 * The system palette rides along for the clock's second source; it is read only when the wallpaper colors are missing.
 */
internal fun WallpaperManager.androidWallpaperTone(context: android.content.Context): WallpaperTone {
    val colors = runCatching { getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull()
    return colors.tone(fromAndroid = true, palette = if (colors == null) systemPalette(context) else null)
}

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
        // Read once where it is composed, so the first frame already has the colors; later reads go off the main thread.
        var tone by remember { mutableStateOf(manager.androidWallpaperTone(context)) }
        val scope = rememberCoroutineScope()
        val refresh = { scope.launch { tone = withContext(Dispatchers.IO) { manager.androidWallpaperTone(context) } }; Unit }
        DisposableEffect(manager) {
            // Any change reads the home screen's colors again rather than trusting the ones handed over: One UI reports
            // `which` with its own display bits added (5 is home on the main screen), and the colors in a callback can
            // be the lock screen's or the other screen's.
            val listener = WallpaperManager.OnColorsChangedListener { _, _ -> refresh() }
            runCatching { manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper())) }
            onDispose { runCatching { manager.removeOnColorsChangedListener(listener) } }
        }
        // The listener only hears changes. Android can have no colors yet right after boot or while a live wallpaper
        // starts, so Home asks again each time it comes back rather than keeping a first empty answer for good.
        androidx.lifecycle.compose.LifecycleResumeEffect(manager) { refresh(); onPauseOrDispose { } }
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
