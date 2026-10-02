package com.mccal.folio

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Back in Settings goes up a page wherever Settings is open, and closes the sheet only from the top page.
 *
 * Found in the beta.4 smoke test (1 Oct 2026): opened from the Folio icon, Settings is the Market's Settings tab, and
 * that sheet's Back only knew how to close, so Back from General went all the way to Home. Built like the real one:
 * a dialog whose own Back closes it, with Settings inside it answering first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsBackTest {
    @get:Rule val compose = createComposeRule()

    private var page by mutableStateOf(CustomizationPage.OVERVIEW)
    private var settingsShown by mutableStateOf(true)
    private var closed = false
    private lateinit var dispatcher: OnBackPressedDispatcher

    private fun sheet(start: CustomizationPage) {
        page = start
        compose.setContent {
            Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false)) {
                val view = LocalView.current
                dispatcher = (view.parent as DialogWindowProvider).window.decorView
                    .findViewTreeOnBackPressedDispatcherOwner()!!.onBackPressedDispatcher
                ModalDialogBackHandler { closed = true }
                // The Market draws Settings only while its Settings tab is open.
                if (settingsShown) SettingsPageBack(page) { page = page.parent }
            }
        }
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test fun `Back goes up one page at a time, then closes the sheet`() {
        sheet(CustomizationPage.BACKUP)
        back()
        assertEquals(CustomizationPage.GENERAL, page)
        assertFalse(closed)
        back()
        assertEquals(CustomizationPage.OVERVIEW, page)
        assertFalse(closed)
        back()
        assertTrue(closed)
    }

    @Test fun `on the top page Back closes the sheet`() {
        sheet(CustomizationPage.OVERVIEW)
        back()
        assertTrue(closed)
        assertEquals(CustomizationPage.OVERVIEW, page)
    }

    @Test fun `Settings still answers first after another Market tab and back`() {
        sheet(CustomizationPage.GENERAL)
        settingsShown = false
        compose.waitForIdle()
        back()
        assertTrue("on another tab Back closes the sheet", closed)
        closed = false
        settingsShown = true
        compose.waitForIdle()
        back()
        assertEquals(CustomizationPage.OVERVIEW, page)
        assertFalse(closed)
    }
}
