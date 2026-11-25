package com.mccal.folio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A setting picked in Settings search scrolls into view, glows once, and is then forgotten. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SettingsFocusTest {
    @get:Rule val compose = createComposeRule()

    @After fun clear() { SettingsFocus.label = null }

    private fun page() = compose.setContent {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            repeat(40) { Text("Filler $it") }
            SettingsSwitch("Badge Size", false, {})
        }
    }

    @Test fun `the row search picked scrolls into view and the target is cleared once it has glowed`() {
        SettingsFocus.label = "Badge Size"
        compose.mainClock.autoAdvance = false
        page()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Badge Size").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(3000)
        compose.waitForIdle()
        assertNull(SettingsFocus.label)
    }

    @Test fun `without a search target the page stays where it is`() {
        compose.mainClock.autoAdvance = false
        page()
        compose.mainClock.advanceTimeBy(3000)
        assertEquals(null, SettingsFocus.label)
        // 40 rows of filler push the switch well past a 891 dp screen, so it is not on screen.
        compose.onNodeWithText("Filler 0").assertIsDisplayed()
    }

    @Test fun `the glow eases in over a moment and is gone well inside the patience window`() {
        // It used to appear at full strength. Rising over about a fifth of a second reads as a highlight, not a flash.
        assertTrue(SettingsFocus.GLOW_IN_MS in 120..300)
        assertTrue(SettingsFocus.GLOW_PEAK in .1f..0.4f)
        assertTrue("one slow pulse, never faster than a second", SettingsFocus.GLOW_IN_MS + SettingsFocus.GLOW_OUT_MS >= 1000)
        assertTrue(250 + SettingsFocus.GLOW_IN_MS + SettingsFocus.GLOW_OUT_MS < SettingsFocus.PATIENCE_MS)
    }
}
