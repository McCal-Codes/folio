package com.mccal.folio

import android.provider.Settings
import org.junit.Assert.*
import org.junit.Test

class SystemSettingsSearchTest {
    private val display = SystemSettingShortcut("Display & Brightness", Settings.ACTION_DISPLAY_SETTINGS, listOf("screen", "dark", "font", "text size", "screen timeout"))
    private val notifications = SystemSettingShortcut("Notifications", Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS, listOf("notification", "alerts"))
    private val wifi = SystemSettingShortcut("Wi-Fi", Settings.ACTION_WIFI_SETTINGS, listOf("wifi", "wireless", "network"))
    private val general = SystemSettingShortcut("Settings", Settings.ACTION_SETTINGS, listOf("preferences"))
    private val catalog = listOf(wifi, display, notifications, general)
    private fun search(query: String) = searchSystemSettings(catalog, query, "Settings")

    @Test fun displaySettingsMatchOrdinaryPhrases() {
        for (query in listOf("display settings", "settings for display", "show me display settings", "change screen brightness", "font size", "screen timeout")) {
            assertEquals(query, display, search(query).firstOrNull())
        }
    }

    @Test fun notificationQueriesAllowSingularAndPlural() {
        for (query in listOf("notifications", "notification settings", "phone notification settings", "open notifications", "alerts")) {
            assertEquals(query, notifications, search(query).single())
        }
    }

    @Test fun punctuationCaseAccentsAndWhitespaceDoNotHideMatches() {
        assertEquals(wifi, search("  WÍ--FÍ   SETTINGS ").single())
        assertEquals(display, search("DISPLAY - SETTINGS").single())
    }

    @Test fun allMeaningfulWordsMustMatchTheSameSetting() {
        assertTrue(search("display bluetooth").isEmpty())
        assertTrue(search("notification bananas").isEmpty())
    }

    @Test fun blankSymbolsAndFillerDoNotShowArbitrarySettings() {
        for (query in listOf("", " ", "-", "&", "a", "show me", "turn off")) assertTrue(query, search(query).isEmpty())
    }

    @Test fun genericSettingsQueryOnlyShowsSettingsHome() {
        for (query in listOf("settings", "phone settings", "system settings", "open settings")) assertEquals(query, listOf(general), search(query))
        val olderNotifications = notifications.copy(action = Settings.ACTION_SETTINGS)
        assertEquals(listOf(general), searchSystemSettings(listOf(olderNotifications, general), "settings", "Settings"))
    }

    @Test fun localizedTitlesAndJoinedSettingsSuffixesMatch() {
        val chinese = SystemSettingShortcut("通知", "notifications", listOf("notification"))
        val korean = SystemSettingShortcut("디스플레이 및 밝기", "display", listOf("screen"))
        assertEquals(listOf(chinese), searchSystemSettings(listOf(chinese), "通知设置", "设置"))
        assertEquals(listOf(korean), searchSystemSettings(listOf(korean), "디스플레이 설정", "설정"))
        val french = SystemSettingShortcut("Écran", "display", emptyList())
        assertEquals(listOf(french), searchSystemSettings(listOf(french), "ecran", "Paramètres"))
    }

    @Test fun titleMatchesRankBeforeBroadAliases() {
        val alias = SystemSettingShortcut("Other", "other", listOf("display"))
        assertEquals(listOf(display, alias), searchSystemSettings(listOf(alias, display), "display", "Settings"))
    }

    @Test fun resultsAreBoundedAndTieOrderIsStable() {
        val entries = (1..8).map { SystemSettingShortcut("Screen " + it, "screen." + it, emptyList()) }
        assertEquals(entries.take(4), searchSystemSettings(entries, "screen", "Settings"))
        assertTrue(searchSystemSettings(entries, "screen", "Settings", limit = 0).isEmpty())
    }

    @Test fun unfamiliarSettingsQueriesCanContinueInAndroidSettingsSearch() {
        val nativeSearch = SystemSettingShortcut("Search phone settings", Settings.ACTION_APP_SEARCH_SETTINGS, emptyList())
        val entries = catalog + nativeSearch
        assertEquals(listOf(nativeSearch), searchSystemSettings(entries, "unfamiliar phone settings", "Settings"))
        assertTrue(searchSystemSettings(entries, "unfamiliar query", "Settings").isEmpty())
        assertEquals(listOf(notifications), searchSystemSettings(entries, "notification settings", "Settings"))
    }
}
