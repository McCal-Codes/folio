package com.mccal.folio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Control Center's two-pane editor (CcTwoPaneHost and CcAddPane), on a stand-in panel: the panes' places on a window with and without a crease, the
 * list's rows, search and plus, and the one-pane layout when the host is not engaged. The panel's own editing is covered by CcSlotGridDragTest.
 */
class CcTwoPaneHostTest {
    // The window the test lands on can be a 375 dp cover or a 750 dp inner screen. A smaller density makes the 900 dp
    // hosts below fit either one, so no result depends on which display the runner picks.
    private val TEST_DENSITY = 0.8f

    @get:Rule val compose = createComposeRule()

    private fun item(id: String, label: String, module: Boolean = false) = CcGalleryItem(id, label, Icons.Rounded.Add, module)
    private val added = mutableListOf<String>()

    private fun host(width: Int, engaged: Boolean, hinge: Hinge? = null, items: List<CcGalleryItem> = listOf(item("TIMER", "Timer"), item("ALARM", "Alarm"), item(CC_MEDIA, "Now Playing", module = true))) {
        val feed = CcAddPaneFeed().also { it.items = items; it.onAdd = { added += it.id } }
        compose.setContent {
            CompositionLocalProvider(LocalWindowHinge provides hinge, LocalReduceMotion provides true, LocalDensity provides Density(TEST_DENSITY, 1f)) {
                CcTwoPaneHost(Modifier.testTag("host").width(width.dp).height(500.dp), engaged, feed) { split, size ->
                    Box(size.testTag("panel").height(300.dp)) { Box(Modifier.testTag("split-${split?.panelWidthDp?.toInt()}").height(1.dp).width(1.dp)) }
                }
            }
        }
    }

    @Test fun notEngagedDrawsOnlyThePanelAtItsOwnSize() {
        host(900, engaged = false)
        compose.onNodeWithTag("panel").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add Timer").assertDoesNotExist()
        assertEquals("the panel fills the host", compose.onNodeWithTag("host").getBoundsInRoot().width.value, compose.onNodeWithTag("panel").getBoundsInRoot().width.value, 1f)
    }

    @Test fun aWindowTooNarrowForTwoPanesStaysOnePane() {
        host(600, engaged = true)
        compose.onNodeWithContentDescription("Add Timer").assertDoesNotExist()
    }

    @Test fun engagedWithNoHingeStandsThePanelAndTheListSideBySide() {
        host(900, engaged = true)
        val panel = compose.onNodeWithTag("panel").getBoundsInRoot()
        val timer = compose.onNodeWithContentDescription("Add Timer").getBoundsInRoot()
        assertTrue("the list is to the right of the panel", timer.left >= panel.right)
        assertTrue("the panel is narrower than the whole window", panel.width.value < 900f)
    }

    @Test fun theListHasASectionAndAPlusButtonOnEveryRow() {
        host(900, engaged = true)
        compose.onNodeWithText("Add a Control").assertIsDisplayed()
        compose.onNodeWithText("Modules").assertIsDisplayed()
        compose.onNodeWithText("Tools").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Add Timer").assertCountEquals(1)
        compose.onNodeWithContentDescription("Add Alarm").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add Now Playing").assertIsDisplayed()
    }

    @Test fun everyRowIsAtLeastAsTallAsTheTouchTarget() {
        host(900, engaged = true)
        listOf("Add Timer", "Add Alarm", "Add Now Playing").forEach {
            assertTrue("$it is at least 48 dp tall", compose.onNodeWithContentDescription(it).getBoundsInRoot().height.value >= FolioTouch.MIN)
        }
    }

    @Test fun pressingARowAddsThatItem() {
        host(900, engaged = true)
        compose.onNodeWithContentDescription("Add Alarm").performClick()
        assertEquals(listOf("ALARM"), added)
    }

    @Test fun searchNarrowsTheList() {
        host(900, engaged = true)
        compose.onNodeWithContentDescription("Search Controls").performTextInput("tim")
        compose.onNodeWithContentDescription("Add Timer").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add Alarm").assertDoesNotExist()
    }

    @Test fun anEmptyListSaysSo() {
        host(900, engaged = true, items = emptyList())
        compose.onNodeWithText("All controls added").assertIsDisplayed()
    }

    @Test fun theListIsAnnouncedAsAPaneAndReadAfterThePanel() {
        host(900, engaged = true)
        val pane = compose.onNode(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
        pane.assertIsDisplayed()
        assertEquals(1f, pane.fetchSemanticsNode().config[SemanticsProperties.TraversalIndex], 0f)
    }

    @Test fun aCreaseInTheMiddleIsTheDividerAndNeitherPaneTouchesIt() {
        val density = TEST_DENSITY
        val crease = Hinge(active = false, vertical = true, startPx = (450 * density).toInt(), endPx = (450 * density).toInt())
        host(900, engaged = true, hinge = crease)
        val panel = compose.onNodeWithTag("panel").getBoundsInRoot()
        val timer = compose.onNodeWithContentDescription("Add Timer").getBoundsInRoot()
        assertTrue("the panel ends before the crease", panel.right.value < 450f)
        assertTrue("the list starts after it", timer.left.value > 450f)
    }
}
