package com.mccal.folio

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Delete App and Remove App (Lab scene `delete-apps`): who gets which choice, and what each one does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class AppRemovalTest {
    @get:Rule val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val spotify = AppEntry("com.spotify.music/.Main", "Spotify", Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    @Test fun `an ordinary app can be deleted, or only taken off Home`() {
        assertEquals(RemoveOptions(RemoveReason.NONE, listOf(RemoveChoice.DELETE, RemoveChoice.REMOVE_FROM_HOME)),
            removeOptions(cameWithPhone = false, isWork = false, onHome = true))
        assertEquals(listOf(RemoveChoice.DELETE), removeOptions(cameWithPhone = false, isWork = false, onHome = false).choices)
    }

    @Test fun `an app that came with the phone is never offered a Delete that fails`() {
        val options = removeOptions(cameWithPhone = true, isWork = false, onHome = true)
        assertEquals(RemoveReason.CAME_WITH_PHONE, options.reason)
        assertEquals(listOf(RemoveChoice.HIDE, RemoveChoice.REMOVE_FROM_HOME, RemoveChoice.APP_INFO), options.choices)
        assertFalse(canDelete(cameWithPhone = true, isWork = false))
    }

    @Test fun `a work app is deleted from App Info for now`() {
        val options = removeOptions(cameWithPhone = false, isWork = true, onHome = true)
        assertEquals(RemoveReason.WORK_APP, options.reason)
        assertFalse(RemoveChoice.DELETE in options.choices)
        assertFalse(canDelete(cameWithPhone = false, isWork = true))
        assertTrue(canDelete(cameWithPhone = false, isWork = false))
    }

    @Test fun `a Screenshot Mode stand-in is removed like the phone's own app, so the menu can't give the swap away`() {
        val context = RuntimeEnvironment.getApplication()
        val camera = AppEntry("com.android.camera/.Main", "Camera", Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        val swapped = spotify.copy(label = "Camera", iconFrom = camera)
        assertTrue(cameWithPhone(context, swapped))
        assertFalse(canDelete(cameWithPhone(context, swapped), swapped.isWork))
        assertEquals(RemoveReason.CAME_WITH_PHONE, removeOptions(cameWithPhone(context, swapped), swapped.isWork, onHome = true).reason)
    }

    @Test fun `one check says what shipped with the phone, and each caller picks its own answer for unknown`() {
        val context = RuntimeEnvironment.getApplication()
        // A package Android won't describe: unknown, so no Delete for it, and it is never a stand-in.
        assertEquals(null, shippedWithPhone(context.packageManager, "com.example.not.installed"))
        assertTrue(cameWithPhone(context, "com.example.not.installed"))
        assertTrue(systemPackages(context, setOf("com.example.not.installed")).isEmpty())
    }

    @Test fun `Cancel on Android's confirmation is not a failure, and a package that is gone was deleted`() {
        val firstUser = android.app.Activity.RESULT_FIRST_USER
        // Cancel: Android answers RESULT_FIRST_USER, with no status (the newer screen) or DELETE_FAILED_ABORTED (-5),
        // and the app is still there.
        assertEquals(DeleteOutcome.CANCELED, deleteOutcome(firstUser, null, stillInstalled = true))
        assertEquals(DeleteOutcome.CANCELED, deleteOutcome(firstUser, -5, stillInstalled = true))
        assertEquals(DeleteOutcome.CANCELED, deleteOutcome(android.app.Activity.RESULT_CANCELED, null, stillInstalled = true))
        // Deleted: the package is gone, whatever the code.
        assertEquals(DeleteOutcome.DELETED, deleteOutcome(android.app.Activity.RESULT_OK, 1, stillInstalled = false))
        assertEquals(DeleteOutcome.DELETED, deleteOutcome(firstUser, null, stillInstalled = false))
        // A real failure carries its status code, and the app is still there.
        assertEquals(DeleteOutcome.FAILED, deleteOutcome(firstUser, -1, stillInstalled = true))
        assertEquals(DeleteOutcome.FAILED, deleteOutcome(firstUser, -2, stillInstalled = true))

        // The status code is read from the screen's answer when it sends one.
        assertEquals(-5, installResultOf(Intent().putExtra("android.intent.extra.INSTALL_RESULT", -5)))
        assertEquals(null, installResultOf(Intent()))
        assertEquals(null, installResultOf(null))
    }

    @Test fun `delete asks Android's own uninstall confirmation for that one package, and wants the result back`() {
        val intent = uninstallIntent("com.spotify.music")
        @Suppress("DEPRECATION") assertEquals(Intent.ACTION_UNINSTALL_PACKAGE, intent.action)
        assertEquals("package:com.spotify.music", intent.dataString)
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false))
    }

    @Test fun `an unknown package counts as one that came with the phone, so it gets no Delete`() {
        assertTrue(cameWithPhone(context, "com.example.not.installed"))
    }

    @Test fun `the alert offers each choice once, and Cancel`() {
        val picked = mutableListOf<RemoveChoice>()
        var dismissed = false
        compose.setContent {
            RemoveAppAlert(spotify, removeOptions(cameWithPhone = false, isWork = false, onHome = true), { picked += it }, { dismissed = true })
        }
        compose.onNodeWithText(text(R.string.remove_app_title, "Spotify")).assertExists()
        compose.onNodeWithText(text(R.string.remove_app_message)).assertExists()
        compose.onNodeWithTag("remove-app-delete").performClick()
        compose.onNodeWithTag("remove-app-remove_from_home").performClick()
        compose.onNodeWithTag("remove-app-cancel").performClick()
        assertEquals(listOf(RemoveChoice.DELETE, RemoveChoice.REMOVE_FROM_HOME), picked)
        assertTrue(dismissed)
    }

    @Test fun `the alert for an app that came with the phone says why, and offers Hide and App Info`() {
        compose.setContent {
            RemoveAppAlert(spotify, removeOptions(cameWithPhone = true, isWork = false, onHome = false), {}, {})
        }
        compose.onNodeWithText(text(R.string.remove_app_came_with_phone, "Spotify")).assertExists()
        compose.onNodeWithTag("remove-app-hide").assertExists()
        compose.onNodeWithTag("remove-app-app_info").assertExists()
        compose.onNodeWithTag("remove-app-delete").assertDoesNotExist()
        compose.onNodeWithTag("remove-app-remove_from_home").assertDoesNotExist()
    }

    private fun menu(onHome: Boolean, app: AppEntry = spotify, onRemoveApp: (() -> Unit)? = null, onDeleteApp: (() -> Unit)? = null,
        onAddOrRemove: () -> Unit = {}) {
        compose.setContent {
            AppContextMenu(app, onHome = onHome, hidden = false, onDismiss = {}, onMove = {}, onAddOrRemove = onAddOrRemove,
                onCreateFolder = {}, onWidgets = null, onToggleHidden = {}, onInfo = {}, onRename = {},
                onRemoveApp = onRemoveApp, onDeleteApp = onDeleteApp)
        }
    }

    @Test fun `on Home the menu says Remove App and opens the choice instead of removing at once`() {
        var opened = 0; var removed = 0
        menu(onHome = true, onRemoveApp = { opened++ }, onAddOrRemove = { removed++ })
        compose.onNodeWithText(text(R.string.remove_app)).performClick()
        assertEquals(1, opened); assertEquals(0, removed)
        compose.onNodeWithTag("menu-delete-app").assertDoesNotExist()
    }

    @Test fun `in the App Library the menu says Delete App, and only for an app Folio can delete`() {
        var deleted = 0
        menu(onHome = false, onDeleteApp = { deleted++ })
        compose.onNodeWithText(text(R.string.add_to_home)).assertExists()
        compose.onNodeWithTag("menu-delete-app").performClick()
        assertEquals(1, deleted)
    }

    @Test fun `without Delete App the App Library menu is as before`() {
        menu(onHome = false)
        compose.onNodeWithTag("menu-delete-app").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.add_to_home)).assertExists()
    }

    @Test fun `a shortcut is still deleted as a shortcut`() {
        val shortcut = AppEntry("com.android.chrome/${SHORTCUT_CLASS_PREFIX}abc", "Docs", Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        var removed = 0; var opened = 0
        menu(onHome = true, app = shortcut, onRemoveApp = { opened++ }, onAddOrRemove = { removed++ })
        compose.onNodeWithText(text(R.string.delete_shortcut)).performClick()
        assertEquals(1, removed); assertEquals(0, opened)
    }
}
