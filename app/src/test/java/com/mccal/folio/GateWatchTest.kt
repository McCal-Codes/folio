package com.mccal.folio

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A screen that stays up sees a supporter change (a code, the Early access switch) without being recreated. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GateWatchTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun `watching tells of a change to the supporter preferences and stops when asked`() {
        var calls = 0
        val stop = Supporter.watch(context) { calls++ }
        Supporter.setBetaOn(context, true)
        assertEquals(1, calls)
        stop()
        Supporter.setBetaOn(context, false)
        assertEquals("nothing after it stopped", 1, calls)
    }

    @Test fun `a flag in a composition that stays up follows the Early access switch`() {
        Supporter.setBetaOn(context, false)
        compose.setContent {
            val on = rememberSupporterFlag("early access") { Supporter.betaOn(it) }
            Text(if (on) "on" else "off", Modifier.testTag("flag"))
        }
        compose.onNodeWithTag("flag").assertTextEquals("off")
        Supporter.setBetaOn(context, true)
        compose.waitForIdle()
        compose.onNodeWithTag("flag").assertTextEquals("on")
        Supporter.setBetaOn(context, false)
        compose.waitForIdle()
        compose.onNodeWithTag("flag").assertTextEquals("off")
    }
}
