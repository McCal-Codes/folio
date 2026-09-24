package com.mccal.folio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The background has to be wider than the window to have anywhere to slide to, and the window has to stay the size it
 * was: a background that reported its own wider size would push Home's layout out with it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class BackgroundDriftLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun sizes(pages: Int): Pair<IntSize, IntSize> {
        var reported = IntSize.Zero
        var drawn = IntSize.Zero
        compose.setContent {
            Box(Modifier.requiredSize(300.dp, 600.dp)) {
                Box(Modifier.fillMaxSize().onSizeChanged { reported = it }.driftRoom(pages)
                    .onSizeChanged { drawn = it })
            }
        }
        compose.waitForIdle()
        return reported to drawn
    }

    @Test fun `three pages draw a wider background than the window they sit behind`() {
        val (reported, drawn) = sizes(3)
        assertEquals("the window keeps its width", reported.width + BackgroundDrift.spareWidth(reported.width, 3), drawn.width)
        assertEquals("nothing is added above or below", reported.height, drawn.height)
        // Six tenths of a page: the whole travel three or more pages get.
        val spare = drawn.width - reported.width
        assertTrue("$spare px spare behind a ${reported.width} px window",
            kotlin.math.abs(spare - reported.width * BackgroundDrift.MOST) <= 2f)
    }

    @Test fun `one page leaves the background exactly the size of the window`() {
        val (reported, drawn) = sizes(1)
        assertEquals(reported, drawn)
    }
}
