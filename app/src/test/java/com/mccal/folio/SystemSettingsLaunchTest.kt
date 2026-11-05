package com.mccal.folio

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemSettingsLaunchTest {
    private class RecordingContext(private val refuse: (String) -> Unit = {}) :
        ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        val attempts = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) {
            attempts += intent
            refuse(checkNotNull(intent.action))
        }
    }

    @Test fun supportedPageOpensDirectlyInItsOwnTask() {
        val context = RecordingContext()
        assertTrue(openSystemSetting(context, Settings.ACTION_DISPLAY_SETTINGS))
        assertEquals(listOf(Settings.ACTION_DISPLAY_SETTINGS), context.attempts.map { it.action })
        assertTrue(context.attempts.single().flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun missingPageFallsBackToSettingsHome() {
        val context = RecordingContext { if (it != Settings.ACTION_SETTINGS) throw ActivityNotFoundException() }
        assertTrue(openSystemSetting(context, Settings.ACTION_DISPLAY_SETTINGS))
        assertEquals(listOf(Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SETTINGS), context.attempts.map { it.action })
    }

    @Test fun vendorRestrictedPageFallsBack() {
        val context = RecordingContext { if (it != Settings.ACTION_SETTINGS) throw SecurityException() }
        assertTrue(openSystemSetting(context, Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS))
        assertEquals(Settings.ACTION_SETTINGS, context.attempts.last().action)
    }

    @Test fun unavailableSettingsAreReportedWithoutThrowing() {
        val context = RecordingContext { throw ActivityNotFoundException() }
        assertFalse(openSystemSetting(context, Settings.ACTION_DISPLAY_SETTINGS))
        assertEquals(2, context.attempts.size)
    }

    @Test fun settingsHomeIsAttemptedOnlyOnce() {
        val context = RecordingContext { throw SecurityException() }
        assertFalse(openSystemSetting(context, Settings.ACTION_SETTINGS))
        assertEquals(1, context.attempts.size)
    }

    @Test fun actualCatalogIncludesTheRequestedSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entries = systemSettingShortcuts(context)
        val settingsLabel = context.getString(R.string.settings)
        assertEquals(Settings.ACTION_DISPLAY_SETTINGS, searchSystemSettings(entries, "settings for display", settingsLabel).first().action)
        assertEquals(Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS, searchSystemSettings(entries, "notification settings", settingsLabel).first().action)
    }
}
