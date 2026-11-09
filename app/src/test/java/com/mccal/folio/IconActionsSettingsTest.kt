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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class IconActionsSettingsTest {
    @get:Rule val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private fun app(pkg: String, label: String) = AppEntry("$pkg/.Main", label, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
    private val camera = app("com.example.camera", "Camera")
    private val notes = app("com.example.notes", "Notes")
    private var actions by mutableStateOf(emptyMap<String, IconActions>())

    private fun show(start: Map<String, IconActions>) {
        actions = start
        compose.setContent {
            Column { IconActionsSettingsPage(listOf(camera, notes), actions, { id, a -> actions = editIconActions(actions, id, a) }, { actions = it }) }
        }
    }

    @Test fun `with no actions it says how to add one`() {
        show(emptyMap())
        compose.onNodeWithTag("icon-actions-none").assertExists()
        compose.onNodeWithTag("icon-actions-reset-all").assertDoesNotExist()
    }

    @Test fun `each icon with actions is listed with what its gestures run, and opens the editor`() {
        show(mapOf(camera.id to IconActions(up = ActionRef("TORCH"))))
        compose.onNodeWithText(context.getString(R.string.icon_action_talkback, context.getString(R.string.icon_action_swipe_up),
            context.getString(R.string.flashlight))).assertExists()
        compose.onNodeWithTag("icon-actions-app-${notes.id}").assertDoesNotExist()
        compose.onNodeWithTag("icon-actions-app-${camera.id}").performClick()
        compose.onNodeWithTag("icon-actions-editor").assertExists()
        compose.onNodeWithTag("icon-actions-back").performClick()
        compose.onNodeWithTag("icon-actions-app-${camera.id}").assertExists()
    }

    @Test fun `Reset All clears every icon, and Undo Reset puts them back`() {
        val before = mapOf(camera.id to IconActions(up = ActionRef("TORCH")), notes.id to IconActions(double = ActionRef("SPOTLIGHT")))
        show(before)
        compose.onNodeWithTag("icon-actions-reset-all").performClick()
        assertEquals(emptyMap<String, IconActions>(), actions)
        compose.onNodeWithTag("icon-actions-undo-reset").performClick()
        assertEquals(before, actions)
        compose.onNodeWithTag("icon-actions-undo-reset").assertDoesNotExist()
    }

    @Test fun `the editor's Undo Changes puts the icon back as it was when it opened`() {
        val before = IconActions(up = ActionRef("TORCH"))
        show(mapOf(camera.id to before))
        compose.onNodeWithTag("icon-actions-app-${camera.id}").performClick()
        compose.onNodeWithTag("icon-actions-undo").assertDoesNotExist()
        compose.onNodeWithTag("icon-actions-down").performClick()
        compose.onNodeWithTag("icon-actions-row-SPOTLIGHT").performClick()
        assertEquals(IconActions(up = ActionRef("TORCH"), down = ActionRef("SPOTLIGHT")), actions[camera.id])
        compose.onNodeWithTag("icon-actions-undo").performClick()
        assertEquals(before, actions[camera.id])
    }
}
