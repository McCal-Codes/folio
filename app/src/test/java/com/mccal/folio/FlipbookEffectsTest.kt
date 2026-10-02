package com.mccal.folio

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
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

/** Flipbook's page lists its effects: the built-ins, then the ones Market packages added. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class FlipbookEffectsTest {
    @get:Rule val compose = createComposeRule()

    private val tilt = PackagedPageEffect.of("com.mccal.folio.effect.tilt", "Tilt", 18f, "center", .08f, 3.5f)
    private val tiltTag = "page-effect-package-${tilt.id}"

    // Two cards, laid out by the tweak page's column. Without one the test root stacks them, and a tap on Cube lands
    // on whatever the second card has at that height.
    private fun show(state: LauncherState, onEffect: (PageEffect) -> Unit = {}, onPackaged: (String) -> Unit = {}) =
        compose.setContent { Column { FlipbookEffects(state, onEffect, onPackaged) } }

    @Test fun `the built-in in use is selected, and choosing a package's effect reports which`() {
        val picked = mutableListOf<Any>()
        show(LauncherState(pageEffect = PageEffect.CAROUSEL, packagedEffects = listOf(tilt)), { picked += it }, { picked += it })
        compose.onNodeWithTag("page-effect-cube").assertIsNotSelected()
        compose.onNodeWithTag("page-effect-carousel").assertIsSelected()
        compose.onNodeWithText("From Packages", ignoreCase = true).assertExists()
        compose.onNodeWithTag(tiltTag).assertIsNotSelected()
        compose.onNodeWithTag(tiltTag).performClick()
        // Tapping the effect already in use changes nothing, so nothing is saved again.
        compose.onNodeWithTag("page-effect-carousel").performClick()
        assertEquals(listOf<Any>(tilt.id), picked)
    }

    @Test fun `a packaged effect in use is the one selected, and choosing a built-in reports it`() {
        val picked = mutableListOf<Any>()
        show(LauncherState(pageEffect = PageEffect.CAROUSEL, packagedEffects = listOf(tilt), packagedEffectId = tilt.id),
            { picked += it }, { picked += it })
        compose.onNodeWithTag(tiltTag).assertIsSelected()
        compose.onNodeWithTag("page-effect-carousel").assertIsNotSelected()
        compose.onNodeWithTag("page-effect-cube").performClick()
        compose.onNodeWithTag(tiltTag).performClick()
        assertEquals(listOf<Any>(PageEffect.CUBE), picked)
    }

    // Cube's pair beside it, then Carousel's: Inside Cube is Cube from inside, Stack steps back like Carousel without turning.
    @Test fun `Flipbook offers its four effects in pairs, and choosing Stack reports it`() {
        assertEquals(listOf(PageEffect.CUBE, PageEffect.INSIDE_CUBE, PageEffect.CAROUSEL, PageEffect.STACK), PageEffect.CHOICES)
        val picked = mutableListOf<Any>()
        show(LauncherState(pageEffect = PageEffect.CUBE), { picked += it })
        compose.onNodeWithTag("page-effect-inside_cube").assertIsNotSelected()
        compose.onNodeWithText("Inside Cube").assertExists()
        compose.onNodeWithTag("page-effect-stack").performClick()
        assertEquals(listOf<Any>(PageEffect.STACK), picked)
    }

    // Both tweaks it re-creates ideas from are named on its page, and the Tweak Library row names them without authors.
    @Test fun `Flipbook credits Barrel and Cylinder, and its Tweak Library row names both`() {
        val flipbook = TweakFeatures.first { it.id == "pageEffects" }
        assertEquals("Barrel by Aaron Ash, Cylinder by Reed Weichler", flipbook.inspiredBy)
        assertEquals("Barrel, Cylinder", flipbook.inspiredNames)
        assertEquals("one tweak, several authors", "Velvet", TweakFeatures.first { it.inspiredBy.startsWith("Velvet") }.inspiredNames)
    }

    @Test fun `with Flipbook off nothing is selected, and without packages there's no packages group`() {
        show(LauncherState(pageEffect = PageEffect.NONE))
        compose.onNodeWithTag("page-effect-cube").assertIsNotSelected()
        compose.onNodeWithTag("page-effect-carousel").assertIsNotSelected()
        compose.onNodeWithText("From Packages", ignoreCase = true).assertDoesNotExist()
    }
}
