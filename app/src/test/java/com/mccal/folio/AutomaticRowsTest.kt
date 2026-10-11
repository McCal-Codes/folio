package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How many rows Automatic lands on, and what Settings says about it.
 *
 * The sizes are the window Home actually gets, logged on a Galaxy Z Fold8: the cover screen is 475 x 751 dp, but
 * Android's bars and gesture area take about 56 dp of it, so Home is laid out in 695. Measuring against the physical
 * screen instead says five rows fit when four do, which is how this test came to exist.
 */
class AutomaticRowsTest {
    private val cover = 475f to 695f   // 751 dp of screen, less ~56 dp of system insets
    private val inner = 932f to 648f

    /** The same call Home makes: default settings, the search pill's space, and Folio as the Home app. */
    private fun fit(size: Pair<Float, Float>, labelHeight: Float, icon: Float = LayoutPreset().iconSize): Int =
        homeGeometry(size.first, size.second, LayoutPreset(iconSize = icon), labels = true, statusHeight = 0f, labelHeight = labelHeight,
            inLibrary = false, homeBottomSpace = 44f, railControls = 0, classScale = 1f,
            appRows = BASE_APP_ROWS, foldAtCenter = false, fillSpace = true).fitAppRows

    @Test fun `the Fold8 fits four rows at the default icon size`() {
        assertEquals(4, fit(cover, 20f))
        assertEquals(4, effectiveHomeRows(setting = 0, fitCompact = fit(cover, 20f), fitExpanded = fit(inner, 20f)))
    }

    /** What Settings promises when it says smaller icons make room: at 60 dp the cover gains its fifth row. */
    @Test fun `smaller icons make room for another row`() {
        assertEquals(5, fit(cover, 20f, icon = 60f))
        assertEquals(5, fit(cover, 20f, icon = 52f))
    }

    @Test fun `a screen Folio hasn't measured doesn't drag the count down`() {
        assertEquals(5, effectiveHomeRows(setting = 0, fitCompact = 5, fitExpanded = 0))
        assertEquals(BASE_APP_ROWS, effectiveHomeRows(setting = 0, fitCompact = 0, fitExpanded = 0))
        assertEquals(4, effectiveHomeRows(setting = 4, fitCompact = 7, fitExpanded = 7))
    }

    @Test fun `Settings says where the number came from`() {
        val unmeasured = LauncherState()
        fun note(state: LauncherState) = automaticRowsNote(state, EnglishStrings)
        assertTrue("hasn't measured" in note(unmeasured))
        assertTrue("cover" in note(unmeasured.copy(homeFitCompact = 5)))
        assertTrue("inner screen fits 6" in note(unmeasured.copy(homeFitExpanded = 6)))
        assertTrue("Both screens fit 5" in note(unmeasured.copy(homeFitCompact = 5, homeFitExpanded = 5)))
        val mixed = note(unmeasured.copy(homeFitCompact = 5, homeFitExpanded = 4))
        assertTrue("cover screen fits 5" in mixed && "inner screen fits 4" in mixed && "shows 4" in mixed)
        assertTrue("Fixed at 4" in note(unmeasured.copy(homeRows = 4, homeFitCompact = 6)))
    }
}
