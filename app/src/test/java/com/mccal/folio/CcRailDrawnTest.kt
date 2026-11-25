package com.mccal.folio

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CcRailTest checks the rail with a model of its heights; this draws the real connection marks so the model cannot drift from them. The
 * overlap on the Fold (beta 6) slipped through because the fit test modeled the rail as the battery ring and the page dots only: the marks and
 * their words were drawn but never measured. Here the tallest marks (Wi-Fi with a long network name, cellular with a carrier) are drawn at every
 * system text size, and their height must stay within [ccRailReadoutsHeight], the number the rail uses to decide whether they fit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class CcRailDrawnTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `the drawn connection marks are never taller than the rail plans for`() {
        val status = DeviceStatus(battery = 96, wifiConnected = true, wifiLevel = 4, wifiName = "Verizon_6NJT a long network name",
            cellularLevel = 4, carrierName = "A carrier with a long name")
        val scale = androidx.compose.runtime.mutableFloatStateOf(1f)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale.floatValue)) {
                Box(Modifier.testTag("marks")) { RailReadouts(status) }
            }
        }
        val problems = mutableListOf<String>()
        for (fontScale in listOf(.85f, 1f, 1.3f, 1.5f, 2f)) {
            scale.floatValue = fontScale
            compose.waitForIdle()
            val drawn = compose.onNodeWithTag("marks").getUnclippedBoundsInRoot().let { it.bottom - it.top }.value
            val planned = ccRailReadoutsHeight(fontScale)
            if (drawn > planned + .5f) problems += "text %.2f: drawn %.1f dp, planned %.1f dp".format(fontScale, drawn, planned)
            // The model should not be so loose that it hides the marks on rails that could hold them.
            if (fontScale == 1f && drawn < planned * .8f) problems += "text 1.0: drawn %.1f dp is far under the planned %.1f dp".format(drawn, planned)
        }
        assertTrue("The rail's marks outgrow their planned height: " + problems.joinToString(" | "), problems.isEmpty())
    }
}
