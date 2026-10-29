package com.mccal.folio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 27 Oct sweep, F04 to F06: Home and Settings at the system's largest text size. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class LargeTextTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `glanceable text follows the font scale to 130 percent and no further`() {
        for (scale in listOf(1f, 1.15f, 1.3f)) assertEquals(1f, glanceFontFactor(scale), 0f)
        for (scale in listOf(1.5f, 2f, 3f)) assertEquals("effective scale at $scale", GLANCE_MAX_FONT_SCALE, glanceFontFactor(scale) * scale, 0.001f)
    }

    @Test fun `the Settings large title has a line height that keeps its lines apart`() {
        assertTrue(FolioType.LARGE_TITLE_LINE > FolioType.LARGE_TITLE)
        assertTrue("under a 1.4 ratio so two lines do not drift apart", FolioType.LARGE_TITLE_LINE <= FolioType.LARGE_TITLE * 1.4f)
    }

    /** The Preview bar in a 100 dp column (far narrower than any phone, so the Row must put the button first whatever the font measures) at the given text size. */
    private fun previewBarAt(fontScale: Float) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                Box(Modifier.width(100.dp).testTag("column")) { PreviewBar(onUseAsHome = {}, onExit = {}) }
            }
        }
        compose.onNodeWithTag("preview-exit").assertIsDisplayed()
        compose.onNodeWithTag("preview-use-as-home").assertIsDisplayed()
        val column = compose.onNodeWithTag("column").getUnclippedBoundsInRoot()
        val exit = compose.onNodeWithTag("preview-exit").getUnclippedBoundsInRoot()
        assertTrue("font $fontScale: the close button ends at ${exit.right}, the column at ${column.right}", exit.right <= column.right + 0.5.dp)
        assertTrue("font $fontScale: the close button keeps its 48 dp touch target, it is ${exit.width} wide", exit.width >= 47.5.dp && exit.height >= 47.5.dp)
    }

    @Test fun `the Preview bar keeps its close button at the normal text size`() = previewBarAt(1f)
    @Test fun `the Preview bar keeps its close button at 130 percent text`() = previewBarAt(1.3f)
    @Test fun `the Preview bar keeps its close button at 200 percent text`() = previewBarAt(2f)
}
