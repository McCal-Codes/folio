package com.mccal.folio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Control Center draws its smallest tile at CC_MIN_CELL_DP (44 dp), under the 48 dp target of A11Y-1. Compose answers a touch within
 * ViewConfiguration.minimumTouchTargetSize (48 dp) of a smaller control, so the tile still has a 48 dp target; this keeps that true
 * (a view configuration that dropped it, or a clip that cut the hit area, would fail here) and that a touch well outside still misses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class CcTouchTargetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `the smallest tile answers a touch within 48 dp and not one well outside`() {
        var taps = 0
        compose.setContent {
            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.testTag("tile")) { RoundToggle(Icons.Rounded.Alarm, "Alarm", false, Color.White, CC_MIN_CELL_DP.dp) { taps++ } }
            }
        }
        compose.onNodeWithTag("tile").performTouchInput { click(Offset(-1.5.dp.toPx(), height / 2f)) }
        assertEquals("a touch 1.5 dp outside the 44 dp tile is inside its 48 dp target", 1, taps)
        compose.onNodeWithTag("tile").performTouchInput { click(Offset(width + 1.5.dp.toPx(), height / 2f)) }
        assertEquals(2, taps)
        compose.onNodeWithTag("tile").performTouchInput { click(Offset(-20.dp.toPx(), height / 2f)) }
        assertEquals("a touch 20 dp outside is not", 2, taps)
    }
}
