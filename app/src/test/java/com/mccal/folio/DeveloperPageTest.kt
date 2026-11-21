package com.mccal.folio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings › Advanced › Developer: a warning first, no passphrase, then the switches until the page is left. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class DeveloperPageTest {
    @get:Rule val compose = createComposeRule()

    private fun page(shown: () -> Unit = {}) = compose.setContent {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { DeveloperPage(onShowBuild = shown, onClose = {}) }
    }

    @Test fun `the page opens on a warning, with the switches hidden and no passphrase asked`() {
        page()
        compose.onNodeWithTag("developer-warning").assertIsDisplayed()
        compose.onNodeWithTag("developer-enable").assertIsDisplayed()
        compose.onNodeWithTag("display-aid-show_touches").assertDoesNotExist()
        compose.onNodeWithTag("developer-pass").assertDoesNotExist()
    }

    @Test fun `agreeing to the warning shows the display aids and the way back`() {
        page()
        compose.onNodeWithTag("developer-enable").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("display-aid-show_touches").assertIsDisplayed()
        compose.onNodeWithTag("display-aid-pointer_location").assertIsDisplayed()
        compose.onNodeWithTag("developer-hide").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("developer-warning").assertDoesNotExist()
    }

    @Test fun `hiding the settings puts the warning back`() {
        page()
        compose.onNodeWithTag("developer-enable").performClick()
        compose.onNodeWithTag("developer-hide").performScrollTo().performClick()
        compose.waitForIdle()
        // The page stays scrolled to where the Hide row was, so scroll back up to the warning.
        compose.onNodeWithTag("developer-warning").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("display-aid-show_touches").assertDoesNotExist()
    }

    @Test fun `show the build page calls back once the warning is agreed to`() {
        var shown = 0
        page { shown++ }
        compose.onNodeWithText("Show the build page again").assertDoesNotExist()
        compose.onNodeWithTag("developer-enable").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Show the build page again").performClick()
        assertEquals(1, shown)
    }
}
