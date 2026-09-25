package com.mccal.folio

import org.json.JSONObject

/**
 * A Folio theme (after SnowBoard): the whole look in one file, icons, badges, glass, text on Home and the status rail.
 * Only appearance: never the layout, apps, widgets or permissions, so importing a theme can't change anything else.
 */
data class FolioTheme(
    val name: String,
    val iconStyle: IconStyle = IconStyle.DEFAULT,
    val iconTint: Long = FolioColors.Value.Warning,
    val iconTintFromWallpaper: Boolean = false,
    val iconShape: IconShape = IconShape.DEFAULT,
    val iconPack: String? = null,
    val badgeStyle: BadgeStyle = BadgeStyle.DOT,
    val badgeColor: BadgeColor = BadgeColor.RED,
    val liveIcons: Boolean = true,
    val homeInk: String = "AUTO",
    val tintedGlass: Boolean = true,
    val dimWallpaperDark: Boolean = true,
    val statusStyle: StatusStyle = StatusStyle(),
    val badgeLook: BadgeLook = BadgeLook.IOS,
    val badgeSize: BadgeSize = BadgeSize.STANDARD,
) {
    fun toJson(): JSONObject = JSONObject().put("folioTheme", 1).put("name", name)
        .put("iconStyle", iconStyle.name).put("iconTint", iconTint).put("iconTintFromWallpaper", iconTintFromWallpaper)
        .put("iconShape", iconShape.name).put("iconPack", iconPack ?: JSONObject.NULL).put("badgeStyle", badgeStyle.name)
        .put("badgeColor", badgeColor.name).put("liveIcons", liveIcons).put("homeInk", homeInk).put("tintedGlass", tintedGlass)
        .put("dimWallpaperDark", dimWallpaperDark).put("statusStyle", statusStyle.toJson())
        .put("badgeLook", badgeLook.name).put("badgeSize", badgeSize.name)

    companion object {
        fun of(state: LauncherState, name: String) = FolioTheme(name, state.iconStyle, state.iconTint, state.iconTintFromWallpaper,
            state.iconShape, state.iconPack, state.badgeStyle, state.badgeColor, state.liveIcons, state.homeInk, state.tintedGlass,
            state.dimWallpaperDark, state.statusStyle, state.badgeLook, state.badgeSize)

        /** Reads a theme file; null if it isn't one. Unknown values fall back to Folio's defaults. */
        fun fromJson(raw: String): FolioTheme? = runCatching {
            val j = JSONObject(raw)
            require(j.optInt("folioTheme", 0) == 1)
            fun <T : Enum<T>> enum(values: Array<T>, key: String, default: T) = values.firstOrNull { it.name == j.optString(key) } ?: default
            FolioTheme(
                name = j.optString("name").trim().take(40).ifBlank { "Imported Theme" },
                iconStyle = enum(IconStyle.entries.toTypedArray(), "iconStyle", IconStyle.DEFAULT),
                iconTint = j.optLong("iconTint", FolioColors.Value.Warning) or 0xFF000000,
                iconTintFromWallpaper = j.optBoolean("iconTintFromWallpaper", false),
                iconShape = enum(IconShape.entries.toTypedArray(), "iconShape", IconShape.DEFAULT),
                iconPack = j.optString("iconPack").takeIf { it.isNotBlank() && it != "null" },
                badgeStyle = enum(BadgeStyle.entries.toTypedArray(), "badgeStyle", BadgeStyle.DOT),
                badgeColor = enum(BadgeColor.entries.toTypedArray(), "badgeColor", BadgeColor.RED),
                liveIcons = j.optBoolean("liveIcons", true),
                homeInk = j.optString("homeInk", "AUTO").takeIf { it in setOf("AUTO", "LIGHT", "DARK") } ?: "AUTO",
                tintedGlass = j.optBoolean("tintedGlass", true),
                dimWallpaperDark = j.optBoolean("dimWallpaperDark", true),
                statusStyle = StatusStyle.fromJson(j.optJSONObject("statusStyle")),
                badgeLook = enum(BadgeLook.entries.toTypedArray(), "badgeLook", BadgeLook.IOS),
                badgeSize = enum(BadgeSize.entries.toTypedArray(), "badgeSize", BadgeSize.STANDARD),
            )
        }.getOrNull()

        /** Built-in looks, each a take on an iOS appearance. */
        val PRESETS = listOf(
            FolioTheme("Classic"),
            FolioTheme("Dark", iconStyle = IconStyle.DARK, badgeColor = BadgeColor.SOFT, statusStyle = StatusStyle(railGlass = .38f)),
            FolioTheme("Tinted", iconStyle = IconStyle.TINTED, iconTintFromWallpaper = true, badgeColor = BadgeColor.APP),
            FolioTheme("Clear", iconShape = IconShape.SQUIRCLE, badgeStyle = BadgeStyle.COUNT, tintedGlass = false,
                statusStyle = StatusStyle(showDate = false, glyph = StatusGlyph.MINIMAL, railGlass = .1f)),
        )
    }
}

internal fun LauncherState.withTheme(theme: FolioTheme, installedPacks: Set<String>) = copy(
    iconStyle = theme.iconStyle, iconTint = theme.iconTint, iconTintFromWallpaper = theme.iconTintFromWallpaper,
    iconShape = theme.iconShape, iconPack = theme.iconPack?.takeIf { it in installedPacks },
    badgeStyle = theme.badgeStyle, badgeColor = theme.badgeColor, liveIcons = theme.liveIcons, homeInk = theme.homeInk,
    tintedGlass = theme.tintedGlass, dimWallpaperDark = theme.dimWallpaperDark, statusStyle = theme.statusStyle,
    badgeLook = theme.badgeLook, badgeSize = theme.badgeSize,
)

/** Whether the current look matches [theme] (ignoring its name). */
internal fun LauncherState.looksLike(theme: FolioTheme) = FolioTheme.of(this, theme.name) == theme
