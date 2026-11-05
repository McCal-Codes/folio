package com.mccal.folio

import android.app.Application
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SpotlightSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val application get() = ApplicationProvider.getApplicationContext<Application>()
    private val hiddenOthers = setOf("SUGGESTIONS", "CONTACTS", "CALCULATOR", "WEB")

    @Test fun keyboardSearchOpensTheMatchingSetting() {
        var closed = false
        compose.setContent {
            SpotlightOverlay(true, { 1f }, LauncherState(spotlightHidden = hiddenOthers),
                { closed = true }, { fail("No app should launch") }, {})
        }
        compose.onNodeWithTag("spotlight-field").performTextInput("notification settings")
        compose.onNodeWithText(application.getString(R.string.notifications_title)).assertIsDisplayed()
        compose.onNodeWithTag("spotlight-field").performImeAction()
        compose.runOnIdle {
            assertTrue(closed)
            assertEquals(Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS, Shadows.shadowOf(application).nextStartedActivity.action)
        }
    }

    @Test fun disablingSettingsHidesPhoneResults() {
        compose.setContent {
            SpotlightOverlay(true, { 1f }, LauncherState(spotlightHidden = hiddenOthers + "SETTINGS"), {}, {}, {})
        }
        compose.onNodeWithTag("spotlight-field").performTextInput("display settings")
        compose.onNodeWithText(application.getString(R.string.display_brightness)).assertDoesNotExist()
    }

    @Test fun appResultRetainsKeyboardSearchPriority() {
        val app = AppEntry("com.example.display/.Main", "Display", Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        var launched: AppEntry? = null
        compose.setContent {
            SpotlightOverlay(true, { 1f }, LauncherState(apps = listOf(app), spotlightHidden = hiddenOthers), {}, { launched = it }, {})
        }
        compose.onNodeWithTag("spotlight-field").performTextInput("display")
        compose.onNodeWithTag("spotlight-field").performImeAction()
        compose.runOnIdle { assertEquals(app, launched) }
    }

    @Test fun unfamiliarSettingsQueryOpensNativeSettingsSearchWithNoSubmittedQuery() {
        compose.setContent {
            SpotlightOverlay(true, { 1f }, LauncherState(spotlightHidden = hiddenOthers), {}, {}, {})
        }
        compose.onNodeWithTag("spotlight-field").performTextInput("unfamiliar phone settings")
        compose.onNodeWithText(application.getString(R.string.search_phone_settings)).assertIsDisplayed()
        compose.onNodeWithTag("spotlight-field").performImeAction()
        compose.runOnIdle {
            val intent = Shadows.shadowOf(application).nextStartedActivity
            assertEquals(Settings.ACTION_APP_SEARCH_SETTINGS, intent.action)
            assertNull(intent.data)
            assertNull(intent.extras)
        }
    }
}
