package com.mccal.folio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where a pop-up menu goes, at a cover-screen window and an unfolded one (#117).
 *
 * Until 0.6.7 the menu was a `Popup` whose content filled the whole window and centered itself in it, so a menu
 * opened from a row near the bottom of the screen was drawn in the middle of the screen, and its window, as big as
 * the screen and transparent everywhere the menu wasn't, took every tap meant to close it. Both show up here as the
 * menu sitting far from the top-left corner of its own window: a window bigger than what it draws.
 *
 * This checks placement inside the menu's own window, which is what Folio decides. Where that window lands on the
 * screen is Android's anchoring, and is checked on the phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MenuPlacementTest {
    @get:Rule val compose = createComposeRule()

    private fun openTheStyleMenu() {
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                // Down at the bottom end of the window, where #117 was reported from (a Style row at y 1726 of 1972).
                Column(Modifier.align(Alignment.BottomEnd).width(320.dp)) {
                    IosMenuRow("Style", listOf("auto" to "Automatic", "light" to "Light", "dark" to "Dark"),
                        "auto", {}, tag = "style")
                }
            }
        }
        compose.onNodeWithTag("style").performClick()
        compose.waitForIdle()
    }

    private fun theMenuFillsItsOwnWindow() {
        openTheStyleMenu()
        compose.onNodeWithText("Dark").assertIsDisplayed()
        val menu = compose.onNodeWithTag("style-menu").fetchSemanticsNode()
        val at = menu.positionInRoot
        // Room for the surface's own padding, but not for a screen's worth of empty, tap-eating window around it.
        assertTrue("the menu is drawn at $at inside its own window, which is therefore bigger than the menu",
            at.x <= 8f && at.y <= 24f)
        assertTrue("the menu is ${menu.size.width} wide", menu.size.width in 200..280)
    }

    @Test @Config(qualifiers = "w475dp-h751dp") fun `the menu fills its own window on a cover screen`() =
        theMenuFillsItsOwnWindow()

    @Test @Config(qualifiers = "w932dp-h704dp") fun `the menu fills its own window unfolded`() =
        theMenuFillsItsOwnWindow()
}
