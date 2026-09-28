package com.mccal.folio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The search field stays a field once something is typed (#166).
 *
 * In 0.6.7 the field's height became "at least 40 dp" so large text could grow it, but the clear button, which
 * only appears with text, still filled the height it was offered. Inside the App Library's column that was all the
 * space left, so the field swallowed the panel and the results list got none: searching showed nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h900dp")
class SearchFieldHeightTest {
    @get:Rule val compose = createComposeRule()

    @Test fun typingDoesNotGrowTheField() {
        compose.setContent {
            Column(Modifier.width(360.dp).height(800.dp)) {
                IosSearchField("spot", {}, "App Library", Modifier.testTag("field"))
                Box(Modifier.weight(1f).fillMaxSize().testTag("results"))
            }
        }
        val field = compose.onNodeWithTag("field").getUnclippedBoundsInRoot()
        val results = compose.onNodeWithTag("results").getUnclippedBoundsInRoot()
        assertTrue("field is ${field.height} tall", field.height < 80.dp)
        assertTrue("results get ${results.height}", results.height > 600.dp)
    }
}
