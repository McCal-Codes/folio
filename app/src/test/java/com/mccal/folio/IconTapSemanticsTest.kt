package com.mccal.folio

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What Icon Actions relies on when an icon gets a double tap: a tile with no double tap keeps an instant tap, and a tile
 * with one waits out the double-tap timeout before it counts a single tap. Pinned here because the whole "only icons that
 * set a double tap pay for it" promise rests on it, and a Compose update could change it.
 */
@OptIn(ExperimentalFoundationApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IconTapSemanticsTest {
    @get:Rule val compose = createComposeRule()

    private var taps = 0
    private var doubles = 0

    private fun tile(withDouble: Boolean) = compose.setContent {
        Box(Modifier.size(64.dp).testTag("tile").combinedClickable(
            onClick = { taps++ },
            onDoubleClick = if (withDouble) ({ doubles++ }) else null))
    }

    @Test fun `an icon with no double tap opens on the first tap, with no wait`() {
        tile(withDouble = false)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("tile").performClick()
        assertEquals("a plain tap must be instant", 1, taps)
    }

    @Test fun `an icon with a double tap waits before it counts a single tap`() {
        tile(withDouble = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("tile").performClick()
        assertEquals("the tap should wait for a second one", 0, taps)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(1, taps)
        assertEquals(0, doubles)
    }

    @Test fun `a double tap runs the double tap and not the single tap`() {
        tile(withDouble = true)
        compose.onNodeWithTag("tile").performTouchInput { doubleClick() }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(1, doubles)
        assertEquals(0, taps)
    }
}
