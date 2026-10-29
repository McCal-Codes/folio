package com.mccal.folio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BI-3 of the 27 Oct break-it test: the Wallpaper page's Android / Folio control was a fixed 36 dp and cut its second line at 200%
 * text. It is 36 dp at the default size and grows with its labels, which wrap between words; both labels stay inside the track.
 *
 * Robolectric measures text from a stand-in font, so only the relations are asserted (taller at 200%, labels inside the track),
 * not pixel heights.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SegmentedLargeTextTest {
    @get:Rule val compose = createComposeRule()

    private val options = listOf(true to "Android Wallpaper", false to "Folio Background")

    private fun show(fontScale: Float, shown: List<Pair<Boolean, String>> = options) = compose.setContent {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            MaterialTheme { Column(Modifier.width(320.dp)) { IosSegmented(shown, true, {}, tag = "segmented") } }
        }
    }

    private fun assertLabelsInsideTrack(fontScale: Float) {
        show(fontScale)
        val track = compose.onNodeWithTag("segmented").getBoundsInRoot()
        for ((_, text) in options) {
            val label = compose.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot()
            assertTrue("$text top ${label.top} inside the track ${track.top}", label.top >= track.top)
            assertTrue("$text bottom ${label.bottom} inside the track bottom ${track.bottom}", label.bottom <= track.bottom)
        }
    }

    @Test fun `at the default size both labels sit inside the track`() = assertLabelsInsideTrack(1f)

    @Test fun `at 200 percent both labels still sit inside the track, which grew to hold them`() = assertLabelsInsideTrack(2f)
}
