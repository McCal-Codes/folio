package com.mccal.folio

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick

/**
 * A tap on a widget card now opens the picker's second step instead of placing at once. From there these tests carry on into the older
 * placement mode: "Place on Home screen" when the picker has a Home page to draw, the step's own Add when it falls back to that mode.
 */
fun ComposeTestRule.continueToPlacement() {
    waitForIdle()
    val place = onAllNodesWithTag("widget-detail-place", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    onNodeWithTag(if (place) "widget-detail-place" else "widget-detail-add", useUnmergedTree = true).performClick()
}
