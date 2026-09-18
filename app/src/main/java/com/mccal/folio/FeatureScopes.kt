package com.mccal.folio

import androidx.compose.material.icons.rounded.*

/** Where a tweak can be overridden (Choicy-style scopes). Focus overrides come later. */
enum class FolioScreen(@androidx.annotation.StringRes val label: Int) { COVER(R.string.cover_screen), INNER(R.string.inner_screen) }

/** DEFAULT inherits the tweak's main switch; ON/OFF force it on that screen. */
enum class ScopeValue(@androidx.annotation.StringRes val label: Int) { DEFAULT(R.string.default_choice), ON(R.string.on), OFF(R.string.off) }

internal object FeatureScopes {
    fun value(scopes: Map<String, Map<String, String>>, id: String, screen: FolioScreen): ScopeValue =
        scopes[id]?.get(screen.name)?.let { name -> ScopeValue.entries.firstOrNull { it.name == name } } ?: ScopeValue.DEFAULT

    /** Whether a feature is on for [screen]: an override wins, otherwise the main switch. */
    fun on(scopes: Map<String, Map<String, String>>, id: String, global: Boolean, screen: FolioScreen): Boolean =
        when (value(scopes, id, screen)) { ScopeValue.ON -> true; ScopeValue.OFF -> false; ScopeValue.DEFAULT -> global }

    fun set(scopes: Map<String, Map<String, String>>, id: String, screen: FolioScreen, value: ScopeValue): Map<String, Map<String, String>> {
        val current = scopes[id].orEmpty()
        val next = if (value == ScopeValue.DEFAULT) current - screen.name else current + (screen.name to value.name)
        return if (next.isEmpty()) scopes - id else scopes + (id to next)
    }
}

internal fun screenFor(wide: Boolean) = if (wide) FolioScreen.INNER else FolioScreen.COVER

/** A tweak-inspired feature: its page in Settings › Tweaks, with a main switch and per-screen overrides. */
internal data class TweakFeature(
    val id: String, val name: String, val inspiredBy: String, val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Long,
    val get: (LauncherState) -> Boolean, val set: (LauncherModel, Boolean) -> Unit, val default: Boolean,
)

internal val TweakFeatures = listOf(
    TweakFeature("appPanels", "App Panels", "Velox by Phillip Tennen",
        "Swipe up on an app icon for a small panel with its shortcuts, latest notifications and music controls.",
        androidx.compose.material.icons.Icons.Rounded.Widgets, 0xFF0A84FF, { it.appPanels }, { m, v -> m.setAppPanels(v) }, true),
    TweakFeature("dockMagnify", "Dock Magnification", "Harbor by Evan Swick",
        "Dock icons swell under your finger as you slide along the dock.",
        androidx.compose.material.icons.Icons.Rounded.Add, 0xFF5E5CE6, { it.dockMagnify }, { m, v -> m.setDockMagnify(v) }, false),
    TweakFeature("notificationAppRow", "Notification App Row", "Axon by Nepeta",
        "A row of app icons above Notification Center. Tap one to show only that app.",
        androidx.compose.material.icons.Icons.Rounded.Notifications, 0xFFFF3B30, { it.notificationAppRow }, { m, v -> m.setNotificationAppRow(v) }, true),
    TweakFeature("tintNotifications", "Tinted Notifications", "Velvet by NoisyFlake & HiMyNameisUbik",
        "Notification cards take on a soft version of their app’s color.",
        androidx.compose.material.icons.Icons.Rounded.Star, 0xFFFF9F0A, { it.tintNotifications }, { m, v -> m.setTintNotifications(v) }, false),
    TweakFeature("tintMedia", "Album Art Colors", "ColorFlow by David Goldman",
        "The music card and the island’s sound bars take on the album art’s color.",
        androidx.compose.material.icons.Icons.Rounded.MusicNote, 0xFFFF375F, { it.tintMedia }, { m, v -> m.setTintMedia(v) }, true),
)
