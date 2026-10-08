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
     * Whether Folio checks the sources you added in the background. Off until someone turns it on, by the switch or by
     * answering the first-use question ([updatesQuestionSeen]): nothing goes online by itself. It only ever asks sources
     * you added, at most once a day, and waits for Wi-Fi ([refreshOnWifiOnly]).
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

    /**
     * Whether packages you installed from a source update in the background: the daily refresh downloads and checks a
     * newer version, and it goes in when Folio is running (or at its next start). Off until someone turns it on, and
     * the first-use question never turns it on; off means nothing is downloaded for you and an update waits for you to
     * tap it.
     */
    var autoUpdatePackages: Boolean
        get() = store.get(AUTO_UPDATE_PACKAGES) == "1"
        set(value) { store.set(AUTO_UPDATE_PACKAGES, if (value) "1" else null) }

    /** Packages the person turned automatic updates off for, one id per line. The global switch ([autoUpdatePackages]) is checked first. */
    private var autoUpdateOff: Set<String>
        get() = store.get(AUTO_UPDATE_OFF)?.split('\n')?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        set(value) { store.set(AUTO_UPDATE_OFF, value.takeIf { it.isNotEmpty() }?.joinToString("\n")) }

    /** Whether [id] updates itself: the global switch is on and this package has not been turned off. */
    fun autoUpdateFor(id: String): Boolean = autoUpdatePackages && id !in autoUpdateOff

    /** Whether this package has been turned off on its own page, whatever the global switch says. */
    fun autoUpdateTurnedOff(id: String): Boolean = id in autoUpdateOff

    fun setAutoUpdateFor(id: String, on: Boolean) { autoUpdateOff = if (on) autoUpdateOff - id else autoUpdateOff + id }

    /**
     * Whether Folio tells you when a refresh finds newer versions of what you installed. Off by default, and only
     * meaningful while [backgroundRefresh] is on: nothing is found in the background otherwise.
     */
    var notifyUpdates: Boolean
        get() = store.get(NOTIFY_UPDATES) == "1"
        set(value) { store.set(NOTIFY_UPDATES, if (value) "1" else null) }

    /**
     * Whether the one first-use question ("Keep your packages up to date?") has been answered, either way. Once it has,
     * Folio never asks again; the switches in Market settings are how to change it.
     */
    var updatesQuestionSeen: Boolean
        get() = store.get(UPDATES_QUESTION_SEEN) == "1"
        set(value) { store.set(UPDATES_QUESTION_SEEN, if (value) "1" else null) }

    /** Versions the person undid after an automatic update ("id@version"), which are never installed by themselves again. */
    var skippedUpdates: Set<String>
        get() = store.get(SKIPPED_UPDATES)?.split('\n')?.filter { it.isNotEmpty() }?.toSet().orEmpty()
        set(value) { store.set(SKIPPED_UPDATES, value.takeIf { it.isNotEmpty() }?.joinToString("\n")) }

    /** What the last update notice listed, so the same updates are never announced twice. */
    var lastNotifiedUpdates: String?
        get() = store.get(LAST_NOTIFIED)
        set(value) { store.set(LAST_NOTIFIED, value) }

    /** The introduction is shown once, after updating to 0.7.0, and again if the user asks for it in Settings. */
    var introductionSeen: Boolean
        get() = store.get(INTRO_SEEN) == "1"
        set(value) { store.set(INTRO_SEEN, if (value) "1" else null) }

    private companion object {
        const val FEATURED_STYLE = "market:featured-style"
        const val INTRO_SEEN = "market:intro-seen"
        const val BACKGROUND_REFRESH = "market:background-refresh"
        const val NOTIFY_UPDATES = "market:notify-updates"
        const val SKIPPED_UPDATES = "market:skipped-updates"
        const val AUTO_UPDATE_PACKAGES = "market:auto-update-packages"
        const val AUTO_UPDATE_OFF = "market:auto-update-off"
        const val UPDATES_QUESTION_SEEN = "market:updates-question-seen"
        const val LAST_NOTIFIED = "market:last-notified-updates"
        const val WIFI_ONLY = "market:wifi-only"
        const val INSTALL_APPS = "market:install-apps"
    }
}
