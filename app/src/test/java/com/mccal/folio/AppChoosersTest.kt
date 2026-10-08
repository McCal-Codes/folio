package com.mccal.folio

import android.graphics.Bitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The dock's chooser and Create Folder's list of apps. Both used to offer every app, so an app hidden behind Settings'
 * unlock could be picked from either one; and the dock's search knew only the name on the icon.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class AppChoosersTest {
    @get:Rule val compose = createComposeRule()

    private val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
    private val icon = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private fun app(id: String, label: String, systemLabel: String = label) =
        AppEntry("com.example.$id/.Main", label, icon, systemLabel = systemLabel)

    private val mail = app("mail", "Mail")
    private val diary = app("diary", "Diary")

    @Test fun `a hidden app is offered to neither the dock nor a new folder`() {
        val state = LauncherState(apps = listOf(mail, diary), hiddenApps = setOf(diary.id))
        assertEquals(listOf(mail), pickerApps(state))
        // Every list is built from it: the dock's chooser, the apps Create Folder offers to go with the first, and Add Apps in a folder.
        val screen = java.io.File(root, "app/src/main/java/com/mccal/folio/LauncherScreen.kt").readText()
        assertFalse("the dock's chooser lists every app", "AppPicker(state.apps" in screen)
        assertEquals("every chooser should leave hidden apps out", 3, Regex("""pickerApps\(state\)""").findAll(screen).count())
    }

    @Test fun `the dock's chooser finds an app by the name Android gives it`() {
        compose.setContent { AppPicker(listOf(app("mail", "Post", systemLabel = "Gmail"), diary), 0, {}, {}, {}) }
        compose.onNodeWithTag("search-field").performTextInput("gmail")
        compose.onAllNodesWithTag("picker-app-${app("mail", "Post").id}").assertCountEquals(1)
        compose.onAllNodesWithTag("picker-app-${diary.id}").assertCountEquals(0)
    }

    @Test fun `the dock's chooser finds a Chinese name by its pinyin`() {
        val weixin = app("weixin", "微信")
        compose.setContent { AppPicker(listOf(weixin, mail), 0, {}, {}, {}) }
        compose.onNodeWithTag("search-field").performTextInput("wx")
        compose.onAllNodesWithTag("picker-app-${weixin.id}").assertCountEquals(1)
        compose.onAllNodesWithTag("picker-app-${mail.id}").assertCountEquals(0)
    }
}
