package com.mccal.folio

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Control Center's Now Playing controls are real touch targets (A11Y-1), at every size the panel offers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class NowPlayingTargetsTest {
    @get:Rule val compose = createComposeRule()

    private val cellDp = mutableFloatStateOf(66f)

    private fun tileAt(cell: Float) {
        if (!shown) compose.setContent {
            val side = (cellDp.floatValue * (2 + CC_GAP_RATIO)).dp
            MediaTile(null, Modifier.size(side, side), cellDp.floatValue.dp, onOpen = {}, slim = false)
        }
        shown = true
        cellDp.floatValue = cell
        compose.waitForIdle()
    }

    private var shown = false

    @Test fun `previous, play and next are at least 48 dp tall at every panel size`() {
        for (size in PanelSize.entries) {
            tileAt(size.cell.value)
            for (tag in listOf("cc-previous", "cc-play", "cc-next")) {
                val bounds = compose.onNodeWithTag(tag).getBoundsInRoot()
                assertTrue("$tag at ${size.name}: ${bounds.height}", bounds.height.value >= FolioTouch.MIN - .01f)
            }
        }
    }

    @Test fun `the three controls share the tile's width and reach 48 dp from the standard size up`() {
        for (size in listOf(PanelSize.STANDARD, PanelSize.LARGE, PanelSize.EXTRA_LARGE)) {
            tileAt(size.cell.value)
            for (tag in listOf("cc-previous", "cc-play", "cc-next")) {
                val bounds = compose.onNodeWithTag(tag).getBoundsInRoot()
                assertTrue("$tag at ${size.name}: ${bounds.width}", bounds.width.value >= FolioTouch.MIN - .5f)
            }
        }
    }
}
