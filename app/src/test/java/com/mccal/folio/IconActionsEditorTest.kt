package com.mccal.folio

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class IconActionsEditorTest {
    @get:Rule val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private fun app(pkg: String, label: String) = AppEntry("$pkg/.Main", label, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
    private val camera = app("com.example.camera", "Camera")
    private val notes = app("com.example.notes", "Notes")
    private var saved by mutableStateOf(IconActions())

    private fun show(start: IconActions = IconActions(), accessibility: Boolean = false) {
        saved = start
        val env = ActionEnv(accessibilityConnected = { accessibility }, policyAccess = { true }, sdk = 34, safeMode = { false })
        compose.setContent { Column { IconActionsEditor(camera, saved, listOf(camera, notes), { saved = it }, onBack = {}, env = { env }) } }
    }
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    @Test fun `picking Flashlight from Suggested saves it for Swipe up and offers Try it`() {
        show()
        compose.onNodeWithTag("icon-actions-up").performClick()
        compose.onNodeWithTag("icon-actions-row-TORCH").performClick()
        assertEquals(ActionRef("TORCH"), saved.up)
        compose.onNodeWithTag("icon-actions-try").assertExists()
    }

    @Test fun `Nothing clears a gesture`() {
        show(IconActions(down = ActionRef("SPOTLIGHT")))
        compose.onNodeWithTag("icon-actions-down").performClick()
        compose.onNodeWithTag("icon-actions-nothing").performClick()
        assertNull(saved.down)
    }

    @Test fun `with the service off, an accessibility action explains how to turn it on instead of being saved`() {
        show()
        compose.onNodeWithTag("icon-actions-up").performClick()
        compose.onNodeWithTag("icon-actions-category-accessibility").performClick()
        compose.onNodeWithText(text(R.string.icon_actions_access_banner)).assertExists()
        compose.onNodeWithTag("icon-actions-row-a11y.back").performClick()
        compose.onNodeWithText(text(R.string.icon_actions_access_title)).assertExists()
        assertNull(saved.up)
    }

    @Test fun `opening another app asks which app and names it`() {
        show()
        compose.onNodeWithTag("icon-actions-double").performClick()
        compose.onNodeWithTag("icon-actions-category-apps").performClick()
        compose.onNodeWithTag("icon-actions-row-app.open").performClick()
        compose.onNodeWithTag("icon-actions-app-${notes.id}").performClick()
        assertEquals("app.open", saved.double?.id)
        assertEquals(notes.component.flattenToString(), saved.double?.args?.get("component"))
        compose.onNodeWithText(text(R.string.icon_actions_open_app, "Notes")).assertExists()
        compose.onNodeWithText(text(R.string.icon_actions_double_waits, "Camera")).assertExists()
    }

    @Test fun `Lock Screen on Double tap gets a caution, and can move to Swipe up`() {
        show(accessibility = true)
        compose.onNodeWithTag("icon-actions-double").performClick()
        compose.onNodeWithTag("icon-actions-category-accessibility").performClick()
        compose.onNodeWithTag("icon-actions-row-LOCK").performClick()
        assertEquals(ActionRef("LOCK"), saved.double)
        compose.onNodeWithText(text(R.string.icon_actions_caution)).assertExists()
        compose.onNodeWithText(text(R.string.icon_actions_use_swipe_up)).performClick()
        assertEquals(ActionRef("LOCK"), saved.up)
        assertNull(saved.double)
    }

    @Test fun `Try it on Lock Screen counts down, and Cancel stops it`() {
        show(accessibility = true)
        compose.onNodeWithTag("icon-actions-up").performClick()
        compose.onNodeWithTag("icon-actions-category-accessibility").performClick()
        compose.onNodeWithTag("icon-actions-row-LOCK").performClick()
        compose.onNodeWithTag("icon-actions-try").performClick()
        compose.onNodeWithText(text(R.string.icon_actions_try_countdown, 3)).assertExists()
        compose.onNodeWithTag("icon-actions-try-cancel").performClick()
        compose.onNodeWithTag("icon-actions-try").assertExists()
        compose.onNodeWithText(text(R.string.icon_actions_try_note)).assertExists()
    }

    @Test fun `an action for a newer Android is dimmed and says which release`() {
        show(accessibility = true)
        compose.onNodeWithTag("icon-actions-up").performClick()
        compose.onNodeWithTag("icon-actions-category-accessibility").performClick()
        compose.onNodeWithText(text(R.string.icon_actions_needs_android, 16)).assertExists()
        compose.onNodeWithTag("icon-actions-row-a11y.mediaplaypause").performClick()
        assertNull("a dimmed row can't be picked", saved.up)
    }

    @Test fun `the panels category says why Wi-Fi only opens a panel`() {
        show()
        compose.onNodeWithTag("icon-actions-up").performClick()
        compose.onNodeWithTag("icon-actions-category-panels").performClick()
        compose.onNodeWithText(text(R.string.icon_actions_panels_note)).assertExists()
        compose.onNodeWithTag("icon-actions-row-panel.wifi").performClick()
        assertEquals(ActionRef("panel.wifi"), saved.up)
    }

    @Test fun `the Android release a too-new action needs is named by release, not API level`() {
        assertEquals(16, androidRelease(36))
        assertEquals(13, androidRelease(33))
        assertEquals(12, androidRelease(32))
    }
}
