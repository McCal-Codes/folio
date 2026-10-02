package com.mccal.folio.market

/**
 * How Featured looks. McCal's call (2026-09-17): the carousel is the default, and the introduction offers both.
 *
 * The words for each one live with the screens that draw them, so they can be translated; this is the choice, not
 * its label.
 */
enum class FeaturedStyle(val id: String) {
    CAROUSEL("carousel"),
    CALM("calm");

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: CAROUSEL
    }
}

/**
 * The Market's own settings, kept with the rest of its data rather than in the launcher's state: they only matter
 * inside the store, and they shouldn't travel in a Home layout backup.
 */
class MarketPrefs(private val store: KeyValueStore) {
    var featuredStyle: FeaturedStyle
        get() = FeaturedStyle.from(store.get(FEATURED_STYLE))
        set(value) { store.set(FEATURED_STYLE, value.id) }

    /**
     * Whether Folio checks the sources you added in the background. Off by default: Folio is local-first, so with this
     * off it only goes online when you open the store and refresh.
     */
    var backgroundRefresh: Boolean
        get() = store.get(BACKGROUND_REFRESH) == "1"
        set(value) { store.set(BACKGROUND_REFRESH, if (value) "1" else null) }

    /** Whether a background refresh waits for an unmetered network. On by default, so it never spends mobile data. */
    var refreshOnWifiOnly: Boolean
        get() = store.get(WIFI_ONLY) != "0"
        set(value) { store.set(WIFI_ONLY, if (value) null else "0") }

    /**
     * Whether Folio installs an app itself, rather than sending you to Play, F-Droid or Obtainium.
     *
     * Off by default, and only ever for a source whose key ships inside Folio - see `MarketApkInstall` for why
     * that line is where it is. Android shows its own install screen for a first install; an update to an app
     * Folio installed itself, signed by the same key, may go in without one (`MarketAppUpdate`).
     */
    var installApps: Boolean
        get() = store.get(INSTALL_APPS) == "1"
        set(value) { store.set(INSTALL_APPS, if (value) "1" else null) }

    /** The introduction is shown once, after updating to 0.7.0, and again if the user asks for it in Settings. */
    var introductionSeen: Boolean
        get() = store.get(INTRO_SEEN) == "1"
        set(value) { store.set(INTRO_SEEN, if (value) "1" else null) }

    private companion object {
        const val FEATURED_STYLE = "market:featured-style"
        const val INTRO_SEEN = "market:intro-seen"
        const val BACKGROUND_REFRESH = "market:background-refresh"
        const val WIFI_ONLY = "market:wifi-only"
        const val INSTALL_APPS = "market:install-apps"
    }
}
