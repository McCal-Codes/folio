package com.mccal.folio

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** An icon's saved swipes and double tap, on the real tile: they run, taps stay instant without a double tap, and nothing changes without them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class IconActionsTileTest {
    @get:Rule val compose = createComposeRule()

    private val app = AppEntry("com.example.camera/.Main", "Camera", Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
    private val torch = ActionRef("TORCH")
    private val spotlight = ActionRef("SPOTLIGHT")

    private val ran = mutableListOf<String>()
    private var opened = 0
    private var panels = 0

    private fun host(actions: IconActions?) = actions?.let { IconActionHost(actionsFor = { id -> if (id == app.id) it else null }, run = { ref -> ran += ref.id }) }

    private fun show(actions: IconActions?, panel: Boolean = false) = compose.setContent {
        CompositionLocalProvider(
            LocalIconActions provides host(actions),
            LocalAppPanel provides (if (panel) ({ _: AppEntry -> panels++ }) else null),
        ) {
            AppTile(app, 56f, false, Modifier.testTag("tile"), onClick = { opened++ }, onLongClick = {})
        }
    }

    @Test fun `a swipe up runs the action the person chose`() {
        show(IconActions(up = torch))
        compose.onNodeWithTag("tile").performTouchInput { swipeUp() }
        assertEquals(listOf("TORCH"), ran)
        assertEquals(0, opened)
    }

    @Test fun `a swipe down runs its action and a swipe up with none set does not`() {
        show(IconActions(down = spotlight))
        compose.onNodeWithTag("tile").performTouchInput { swipeDown() }
        assertEquals(listOf("SPOTLIGHT"), ran)
        compose.onNodeWithTag("tile").performTouchInput { swipeUp() }
        assertEquals("nothing is set for up", listOf("SPOTLIGHT"), ran)
    }

    @Test fun `a chosen swipe up replaces the app panel for that icon only`() {
        show(IconActions(up = torch), panel = true)
        compose.onNodeWithTag("tile").performTouchInput { swipeUp() }
        assertEquals(listOf("TORCH"), ran)
        assertEquals("the panel stays for icons with no action", 0, panels)
    }

    @Test fun `with no action set the swipe keeps opening the app panel`() {
        show(null, panel = true)
        compose.onNodeWithTag("tile").performTouchInput { swipeUp() }
        assertEquals(1, panels)
        assertTrue(ran.isEmpty())
    }

    @Test fun `an action this build does not know is ignored and the icon keeps today's behavior`() {
        show(IconActions(up = ActionRef("from.the.future")), panel = true)
        compose.onNodeWithTag("tile").performTouchInput { swipeUp() }
        assertTrue(ran.isEmpty())
        assertEquals(1, panels)
    }

    @Test fun `a double tap runs its action and does not open the app`() {
        show(IconActions(double = torch))
        compose.onNodeWithTag("tile").performTouchInput { doubleClick() }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(listOf("TORCH"), ran)
        assertEquals(0, opened)
    }

    @Test fun `an icon with no double tap opens on the first tap, with no wait`() {
        show(IconActions(up = torch))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("tile").performClick()
        assertEquals(1, opened)
    }

    @Test fun `an icon with a double tap still opens on a single tap after the wait`() {
        show(IconActions(double = torch))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("tile").performClick()
        assertEquals(0, opened)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(1, opened)
    }

    @Test fun `every set action is also a TalkBack action with its gesture and name`() {
        show(IconActions(up = torch, down = spotlight, double = ActionRef("LOCK")))
        val labels = compose.onNodeWithTag("tile").fetchSemanticsNode().config
            .getOrNull(SemanticsActions.CustomActions)!!.map { it.label }
        assertEquals(listOf("Swipe up: Flashlight", "Swipe down: Spotlight", "Double tap: Lock Screen"), labels)
    }

    @Test fun `Home provides no host while editing, in Safe Mode, with the gate shut or with nothing saved`() {
        assertTrue(iconActionsAvailable(editing = false, safeMode = false, anySaved = true, gateOpen = true))
        assertFalse("editing", iconActionsAvailable(editing = true, safeMode = false, anySaved = true, gateOpen = true))
        assertFalse("Safe Mode", iconActionsAvailable(editing = false, safeMode = true, anySaved = true, gateOpen = true))
        assertFalse("nothing saved", iconActionsAvailable(editing = false, safeMode = false, anySaved = false, gateOpen = true))
        assertFalse("gate shut", iconActionsAvailable(editing = false, safeMode = false, anySaved = true, gateOpen = false))
    }

    /** The dock builds its own slot but uses the same helper, so an app in the dock keeps its actions. */
    @Test fun `the shared gesture helper gives a dock slot the same swipes and double tap`() {
        compose.setContent {
            CompositionLocalProvider(LocalIconActions provides host(IconActions(up = torch, double = spotlight))) {
                val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val gestures = rememberIconGestures(app, interaction, null, null, null, { opened++ },
                    plain = Modifier.clickable { opened++ })
                androidx.compose.foundation.layout.Box(Modifier.size(64.dp).testTag("slot").then(gestures.modifier)
                    .semantics { if (gestures.customActions.isNotEmpty()) customActions = gestures.customActions })
            }
        }
        compose.onNodeWithTag("slot").performTouchInput { swipeUp() }
        assertEquals(listOf("TORCH"), ran)
        compose.onNodeWithTag("slot").performTouchInput { doubleClick() }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(listOf("TORCH", "SPOTLIGHT"), ran)
        assertEquals(0, opened)
        assertEquals(2, compose.onNodeWithTag("slot").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)!!.size)
    }

    @Test fun `with no actions there are no extra TalkBack actions`() {
        show(null)
        assertNull(compose.onNodeWithTag("tile").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)
            ?.takeIf { it.isNotEmpty() })
    }
}
