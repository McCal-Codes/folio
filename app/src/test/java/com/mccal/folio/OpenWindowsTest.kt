package com.mccal.folio

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Home blurs while one of its sheets is open. A stopped Home instance's sheet must never keep another one blurred with
 * nothing in front (seen on the Fold8 after installing a Folio Dev build): every entry belongs to the activity that
 * showed it and goes when that sheet or that activity does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class OpenWindowsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val homeA = Any()
    private val homeB = Any()

    @Test fun `a sheet counts for its own Home only`() {
        val windows = OpenWindows()
        windows.open(homeA)
        assertTrue(windows.isOpen(homeA))
        assertFalse(windows.isOpen(homeB))
        assertTrue(windows.anyOpen())
    }

    @Test fun `a stopped Home's sheet does not blur the Home in front`() {
        val windows = OpenWindows()
        windows.open(homeA)          // the first instance's sheet, never closed
        val b = windows.open(homeB)
        windows.close(b)             // the sheet on screen is dismissed
        assertFalse("Home B stays sharp", windows.isOpen(homeB))
    }

    @Test fun `closing an entry twice cannot take another sheet's place`() {
        val windows = OpenWindows()
        val first = windows.open(homeA)
        windows.open(homeA)
        windows.close(first)
        windows.close(first)
        assertTrue("the second sheet is still open", windows.isOpen(homeA))
    }

    @Test fun `an activity going away clears what its windows left`() {
        val windows = OpenWindows()
        windows.open(homeA)
        windows.open(homeA)
        windows.open(homeB)
        windows.closeAllOf(homeA)
        assertFalse(windows.isOpen(homeA))
        assertTrue(windows.isOpen(homeB))
    }

    @Test fun `a sheet shown in composition is open until it leaves`() {
        val windows = OpenWindows()
        var shown by mutableStateOf(true)
        compose.setContent { if (shown) RegisterOpenWindow(windows) }
        compose.waitForIdle()
        assertTrue(windows.isOpen(compose.activity))
        shown = false
        compose.waitForIdle()
        assertFalse(windows.isOpen(compose.activity))
        assertEquals(false, windows.anyOpen())
    }

    @Test fun `a page counts only once it covers Home, and stops when it no longer does`() {
        val windows = OpenWindows()
        var covering by mutableStateOf(false)
        compose.setContent { RegisterOpenWindow(windows, active = covering) }
        compose.waitForIdle()
        assertFalse(windows.isOpen(compose.activity))
        covering = true
        compose.waitForIdle()
        assertTrue(windows.isOpen(compose.activity))
        covering = false
        compose.waitForIdle()
        assertFalse(windows.isOpen(compose.activity))
    }
}
