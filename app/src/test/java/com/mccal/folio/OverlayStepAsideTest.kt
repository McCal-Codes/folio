package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the island in every app steps aside (Settings › Dynamic Island › In Every App). */
class OverlayStepAsideTest {
    @Test fun `a full-screen film hides the island by default`() {
        assertTrue(overlayStepsAside(hideFullScreen = true, hideLandscape = false, fullScreen = true, landscape = true))
        assertFalse(overlayStepsAside(hideFullScreen = true, hideLandscape = false, fullScreen = false, landscape = true))
    }

    @Test fun `landscape only hides it when you ask`() {
        assertFalse(overlayStepsAside(hideFullScreen = false, hideLandscape = false, fullScreen = false, landscape = true))
        assertTrue(overlayStepsAside(hideFullScreen = false, hideLandscape = true, fullScreen = false, landscape = true))
    }

    @Test fun `turning both off keeps the island where it was`() {
        assertFalse(overlayStepsAside(hideFullScreen = false, hideLandscape = false, fullScreen = true, landscape = true))
    }

    @Test fun `the cover turned sideways hides the island, the unfolded screen does not unless asked`() {
        assertTrue(overlayStepsAside(false, hideLandscape = true, fullScreen = false, landscape = true, onUnfoldedScreen = false, hideLandscapeUnfolded = false))
        assertFalse(overlayStepsAside(false, hideLandscape = true, fullScreen = false, landscape = true, onUnfoldedScreen = true, hideLandscapeUnfolded = false))
        assertTrue(overlayStepsAside(false, hideLandscape = true, fullScreen = false, landscape = true, onUnfoldedScreen = true, hideLandscapeUnfolded = true))
    }

    @Test fun `with Hide in Landscape off the unfolded switch changes nothing`() {
        for (inner in listOf(false, true)) for (unfolded in listOf(false, true))
            assertFalse(overlayStepsAside(false, hideLandscape = false, fullScreen = false, landscape = true, onUnfoldedScreen = unfolded, hideLandscapeUnfolded = inner))
    }

    @Test fun `a full-screen app still hides it on the unfolded screen`() {
        assertTrue(overlayStepsAside(true, hideLandscape = true, fullScreen = true, landscape = true, onUnfoldedScreen = true, hideLandscapeUnfolded = false))
    }

    @Test fun `the unfolded switch saves and loads, and only a save with it needs schema 11`() {
        val on = LauncherState(islandHideLandscape = true, islandHideLandscapeInner = true)
        assertEquals(11, stateSchemaFor(on))
        assertEquals(9, stateSchemaFor(LauncherState(islandHideLandscape = true)))
        assertEquals(10, stateSchemaFor(on.copy(islandHideLandscapeInner = false, iconActions = mapOf("a" to IconActions(up = ActionRef("TORCH"))))))
        assertTrue(v10BackupNeeded(sourceSchema = 10, writtenSchema = 11, hasBackup = false))
        assertFalse(v10BackupNeeded(10, 11, hasBackup = true))
        assertFalse(v10BackupNeeded(11, 11, hasBackup = false))
        val saved = LayoutLoadTest().schema8Fixture().put(SettingKeys.ISLAND_HIDE_LANDSCAPE_INNER, true)
        assertTrue(decodeLauncherState(saved.toString(), legacyRaw = null).islandHideLandscapeInner)
    }

    @Test fun `a saved layout keeps full screen on and landscape off`() {
        val state = decodeLauncherState("{}", legacyRaw = null)
        assertTrue(state.islandHideFullScreen)
        assertFalse(state.islandHideLandscape)
        assertFalse(state.islandHideLandscapeInner)
    }
}
