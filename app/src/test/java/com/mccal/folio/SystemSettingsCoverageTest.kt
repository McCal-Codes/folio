package com.mccal.folio

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The real list of phone-settings pages: the phrases people use land on the page they mean. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemSettingsCoverageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val catalog = systemSettingShortcuts(context)
    private fun firstAction(query: String) = searchSystemSettings(catalog, query, context.getString(R.string.settings)).firstOrNull()?.action

    @Test fun everyPageHasATitleAnAction() {
        for (page in catalog) {
            assertTrue("blank title for ${page.action}", page.title.isNotBlank())
            assertTrue("blank action for ${page.title}", page.action.isNotBlank())
        }
    }

    @Test fun phrasesLandOnThePageTheyMean() {
        val expected = mapOf(
            "hotspot" to Settings.ACTION_WIRELESS_SETTINGS,
            "tethering" to Settings.ACTION_WIRELESS_SETTINGS,
            "vpn" to Settings.ACTION_VPN_SETTINGS,
            "cast" to Settings.ACTION_CAST_SETTINGS,
            "smart view" to Settings.ACTION_CAST_SETTINGS,
            "night light" to Settings.ACTION_NIGHT_DISPLAY_SETTINGS,
            "blue light" to Settings.ACTION_NIGHT_DISPLAY_SETTINGS,
            "battery saver" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
            "power saving" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
            "battery" to Intent.ACTION_POWER_USAGE_SUMMARY,
            "privacy" to Settings.ACTION_PRIVACY_SETTINGS,
            "screen lock" to Settings.ACTION_SECURITY_SETTINGS,
            "accounts" to Settings.ACTION_SYNC_SETTINGS,
            "software update" to Settings.ACTION_SYSTEM_UPDATE_SETTINGS,
            "screen saver" to Settings.ACTION_DREAM_SETTINGS,
            "wallpaper" to Intent.ACTION_SET_WALLPAPER,
            "dark mode" to Settings.ACTION_DISPLAY_SETTINGS,
            "adaptive brightness" to Settings.ACTION_DISPLAY_SETTINGS,
            "display" to Settings.ACTION_DISPLAY_SETTINGS,
            "silent" to Settings.ACTION_SOUND_SETTINGS,
            "ringer" to Settings.ACTION_SOUND_SETTINGS,
            "notifications" to Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS,
            "talkback" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
            "roaming" to Settings.ACTION_NETWORK_OPERATOR_SETTINGS,
            "imei" to Settings.ACTION_DEVICE_INFO_SETTINGS,
            "build number" to Settings.ACTION_DEVICE_INFO_SETTINGS,
        )
        for ((query, action) in expected) assertEquals(query, action, firstAction(query))
    }

    @Test fun sentencesPeopleTypeStillWork() {
        assertEquals(Settings.ACTION_DISPLAY_SETTINGS, firstAction("settings for display"))
        assertEquals(Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS, firstAction("notification settings"))
        assertEquals(Settings.ACTION_WIRELESS_SETTINGS, firstAction("turn on hotspot"))
    }

    @Test fun genericAndNonsenseQueriesStayQuiet() {
        assertEquals(Settings.ACTION_SETTINGS, firstAction("settings"))
        assertNull(firstAction("zzqx"))
    }
}
