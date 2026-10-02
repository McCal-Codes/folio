package com.mccal.folio

import android.app.Application
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tapping Folio's own clock or date. It opened the widget's options, which holding it already does; it opens the app
 * now, as iOS does, while editing still opens the options and the preview in Settings opens nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class WidgetTapTest {
    @get:Rule val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private var options = 0

    private fun show(id: Int, opensApp: Boolean = true, edit: HomeEditMode = HomeEditMode(), hold: (() -> Unit)? = null) =
        compose.setContent {
            ProvideJiggle(edit) {
                CompositionLocalProvider(LocalWidgetHold provides hold) { BuiltinWidgetCard(id, 0, opensApp) { options++ } }
            }
        }

    private fun tap() = compose.onNode(hasClickAction()).performClick()
    private fun opened(): Intent? = shadowOf(app).nextStartedActivity

    @Test fun `tapping the clock opens Clock`() {
        show(CLOCK_WIDGET)
        tap()
        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, opened()?.action)
        assertEquals("a tap is not the long press", 0, options)
    }

    @Test fun `tapping the Big Clock opens Clock too`() {
        show(BIG_CLOCK_WIDGET)
        tap()
        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, opened()?.action)
    }

    @Test fun `tapping the date opens the calendar, and so does Up Next`() {
        show(DATE_WIDGET)
        tap()
        assertTrue(opened()?.selector?.hasCategory(Intent.CATEGORY_APP_CALENDAR) == true)
        assertEquals(0, options)
        // Up Next's own rows open an event or ask for calendar access; the rest of the card is the same tap.
        assertTrue(builtinWidgetApp(UP_NEXT_WIDGET)?.selector?.hasCategory(Intent.CATEGORY_APP_CALENDAR) == true)
    }

    @Test fun `while editing, a tap opens the widget's options as before`() {
        show(CLOCK_WIDGET, edit = HomeEditMode().apply { start() })
        tap()
        assertNull(opened())
        assertEquals(1, options)
    }

    @Test fun `with nothing to open it with, a tap opens the options`() {
        shadowOf(app).checkActivities(true)
        show(CLOCK_WIDGET)
        tap()
        assertEquals(1, options)
    }

    @Test fun `the preview in Settings opens no app`() {
        show(CLOCK_WIDGET, opensApp = false)
        tap()
        assertNull(opened())
        assertEquals(1, options)
    }

    @Test fun `in the Today View, holding a card starts editing`() {
        var held = false
        show(DATE_WIDGET, hold = { held = true })
        compose.onNode(hasClickAction()).performTouchInput { longClick() }
        assertTrue(held)
        assertNull("holding isn't tapping", opened())
        assertFalse(options > 0)
    }
}
