package com.mccal.folio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 27 Oct sweep F07 and F08: nothing in Spotlight or the App Library is cut off at the edge when its text is longer than the room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class LibraryLabelsTest {
    @get:Rule val compose = createComposeRule()

    private fun layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    /**
     * Robolectric has no real font, so a title never wraps here whatever the room; what is checked is what the Text is told: up to two
     * lines, centered, ellipsis only after the second. The wrapped title itself is on the emulator screenshots (docs/mockups-out/visual-fixes).
     */
    @Test fun `a category title is allowed two lines and is centered`() {
        val title = "Productivity & Finance"
        compose.setContent { Box(Modifier.width(150.dp)) { CategoryCard(title, emptyList(), Modifier, onLaunch = {}, onOpen = {}) } }
        val input = layoutOf(title).layoutInput
        assertEquals(2, input.maxLines)
        assertEquals(androidx.compose.ui.text.style.TextAlign.Center, input.style.textAlign)
        assertEquals(androidx.compose.ui.text.style.TextOverflow.Ellipsis, input.overflow)
    }

    @Test fun `the web search chips wrap onto another line instead of running off the edge`() {
        var room by mutableStateOf(900.dp)
        val chips = WebSearchTarget.chips("GOOGLE")
        compose.setContent { Box(Modifier.width(room)) { WebSearchChips(chips) {} } }
        compose.waitForIdle()
        fun bounds() = chips.map { compose.onNodeWithTag("web-chip-${it.name}").getUnclippedBoundsInRoot() }
        val wide = bounds()
        assertEquals("one line when the room is wide", 1, wide.map { it.top }.distinct().size)
        // Room for only the first two chips and a little: the others wrap, and none passes the right edge.
        val need = wide[1].right
        compose.runOnIdle { room = need + 8.dp }
        compose.waitForIdle()
        val tight = bounds()
        assertTrue("the chips take more than one line in a narrow window", tight.map { it.top }.distinct().size > 1)
        tight.forEach { assertTrue("a chip ends at ${it.right}, the room is $room", it.right <= room + 0.5.dp) }
    }
}
