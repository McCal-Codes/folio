package com.mccal.folio

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Flipbook's page lists its effects: the built-ins, then the ones Market packages added, the way jailbreak Cylinder
 * lists its scripts (mocked in the lab as `flipbook-host`). Before 0.6.8 this was a menu in Settings › Gestures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class FlipbookEffectsTest {
    @get:Rule val compose = createComposeRule()

    private val tilt = PackagedPageEffect.of("com.mccal.folio.effect.tilt", "Tilt", 18f, "center", .08f, 3.5f)

    // The effects are two cards, laid out by the tweak page's column. Without one the test root stacks them on top
    // of each other, and a tap on Cube lands on whatever the second card has at that height (found 30 Sep 2026).
    private fun show(state: LauncherState, onEffect: (PageEffect) -> Unit = {}, onPackaged: (String) -> Unit = {}) =
        compose.setContent { androidx.compose.foundation.layout.Column { FlipbookEffects(state, onEffect, onPackaged) } }

    @Test fun `the built-ins, then what packages added, with the one in use selected`() {
        val state = LauncherState(pageEffect = PageEffect.CAROUSEL, packagedEffects = listOf(tilt))
        show(state)
        compose.onNodeWithTag("page-effect-cube").assertIsNotSelected()
        compose.onNodeWithTag("page-effect-carousel").assertIsSelected()
        compose.onNodeWithText("From Packages", ignoreCase = true).assertExists()
        compose.onNodeWithTag("page-effect-package-com.mccal.folio.effect.tilt").assertIsNotSelected()
    }

    @Test fun `a packaged effect in use is the one selected, and choosing reports which`() {
        val state = LauncherState(pageEffect = PageEffect.CAROUSEL, packagedEffects = listOf(tilt), packagedEffectId = tilt.id)
        var picked: PageEffect? = null
        var pickedPackage: String? = null
        show(state, { picked = it }, { pickedPackage = it })
        compose.onNodeWithTag("page-effect-package-com.mccal.folio.effect.tilt").assertIsSelected()
        compose.onNodeWithTag("page-effect-carousel").assertIsNotSelected()
        compose.onNodeWithTag("page-effect-cube").performClick()
        assertEquals(PageEffect.CUBE, picked)
        compose.onNodeWithTag("page-effect-package-com.mccal.folio.effect.tilt").performClick()
        assertEquals(tilt.id, pickedPackage)
    }

    @Test fun `with Flipbook off nothing is selected, and without packages there's no packages group`() {
        show(LauncherState(pageEffect = PageEffect.NONE))
        compose.onNodeWithTag("page-effect-cube").assertIsNotSelected()
        compose.onNodeWithTag("page-effect-carousel").assertIsNotSelected()
        compose.onNodeWithText("From Packages", ignoreCase = true).assertDoesNotExist()
    }
}
