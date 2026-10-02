package com.mccal.folio

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * At a start Home's own apps are shown ahead of the full list: loading every app's icon in turn kept Home's icons away
 * for most of a second (J1 in docs/standards/performance.md). The part must never pass for the whole.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class HomeAppsFirstTest {
    @get:Rule val compose = createComposeRule()

    private val icon = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private fun app(id: String) = AppEntry("com.example.$id/.Main", id.replaceFirstChar(Char::uppercase), icon)
    private val folder = FolderEntry(newFolderId(), "Work", listOf("in-folder-1", "in-folder-2"))

    @Test fun `Home's apps are its pages, the dock, and what its folders and icon stacks hold`() {
        val state = LauncherState(
            homeSlots = listOf("page-1", null, folder.id) + List(HOME_CELLS - 3) { null } + listOf("page-2"),
            leadingSlots = listOf("leading"), dock = listOf("dock", null), folders = listOf(folder),
            iconStacks = mapOf("stack-top" to listOf("stack-under")),
            apps = listOf(app("library-only")))
        assertEquals(setOf("page-1", "page-2", "leading", "dock", "in-folder-1", "in-folder-2", "stack-top", "stack-under"),
            state.homeAppIds())
    }

    @Test fun `Home's apps are shown while the rest load, with their custom names`() {
        val maps = app("maps")
        val start = LauncherState(appNames = mapOf(maps.id to "Directions"))
        val shown = start.withHomeApps(listOf(maps))
        assertEquals(listOf("Directions"), shown.apps.map { it.label })
        assertTrue(shown.loading)
        assertTrue(shown.homeAppsLoaded)
        assertTrue(shown.homeReady)
    }

    @Test fun `a list already in is never replaced by Home's part of it`() {
        val full = LauncherState(apps = listOf(app("maps"), app("notes")))
        assertSame(full, full.withHomeApps(listOf(app("maps"))))
        val loaded = LauncherState(loading = false)
        assertSame(loaded, loaded.withHomeApps(listOf(app("maps"))))
    }

    @Test fun `Home is ready once its apps are in, or when loading ends even if it failed`() {
        val start = LauncherState()
        assertFalse(start.homeReady)
        // A Home with no apps has nothing to show early; it's ready when loading ends.
        assertSame(start, start.withHomeApps(emptyList()))
        assertTrue(LauncherState(homeAppsLoaded = true).homeReady)
        assertTrue(LauncherState(loading = false, error = "Apps could not be loaded").homeReady)
    }

    @Test fun `the App Library waits for every app rather than show Home's as all of them`() {
        val partial = LauncherState(libraryCategories = false).withHomeApps(listOf(app("maps")))
        var state by mutableStateOf(partial)
        compose.setContent { AppLibrary(state, "", {}, {}, { _, _ -> }, {}) }
        compose.onNodeWithText("Loading apps…").assertIsDisplayed()
        compose.onNodeWithText("Maps").assertDoesNotExist()
        state = partial.copy(loading = false)
        compose.onNodeWithText("Maps").assertIsDisplayed()
    }
}
