package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftwareUpdateTest {
    @Test fun `newer versions compare part by part, not as text`() {
        assertTrue(SoftwareUpdate.isNewer("0.6.0", "0.5.0"))
        assertTrue(SoftwareUpdate.isNewer("v0.10.0", "0.9.9"))
        assertTrue(SoftwareUpdate.isNewer("1.0", "0.99.99"))
        assertFalse(SoftwareUpdate.isNewer("0.5.0", "0.5.0"))
        assertFalse(SoftwareUpdate.isNewer("0.4.9", "0.5.0"))
    }

    @Test fun `a beta comes before its release and after the one before`() {
        assertTrue(SoftwareUpdate.isNewer("0.7.0-beta.1", "0.6.0"))
        assertTrue(SoftwareUpdate.isNewer("0.7.0", "0.7.0-beta.3"))
        assertTrue(SoftwareUpdate.isNewer("0.7.0-beta.2", "0.7.0-beta.1"))
        assertTrue(SoftwareUpdate.isNewer("0.7.0-beta.10", "0.7.0-beta.9"))
        assertFalse(SoftwareUpdate.isNewer("0.7.0-beta.1", "0.7.0"))
        assertFalse(SoftwareUpdate.isNewer("0.6.0", "0.7.0-beta.1"))
        assertFalse(SoftwareUpdate.isNewer("0.7.0-beta.1", "0.7.0-beta.1"))
    }

    @Test fun `updates default to Automatic, and a choice made with the old switches carries over`() {
        assertEquals(SoftwareUpdate.Mode.AUTOMATIC, SoftwareUpdate.modeFromLegacy(null, false))
        // Nobody is moved to unattended installs by an update: on 0.6.0 both switches were off until you turned
        // them on, so an upgrade with no choice recorded waits to be asked rather than assuming a yes.
        assertEquals(SoftwareUpdate.Mode.MANUAL, SoftwareUpdate.modeFromLegacy(null, false, upgraded = true))
        assertEquals(SoftwareUpdate.Mode.MANUAL, SoftwareUpdate.modeFromLegacy(null, true, upgraded = true))
        // A choice that was made still carries over, upgrade or not.
        assertEquals(SoftwareUpdate.Mode.MANUAL, SoftwareUpdate.modeFromLegacy(false, true, upgraded = true))
        assertEquals(SoftwareUpdate.Mode.AUTOMATIC, SoftwareUpdate.modeFromLegacy(true, true, upgraded = true))
        assertEquals(SoftwareUpdate.Mode.NOTIFY, SoftwareUpdate.modeFromLegacy(true, false, upgraded = true))
        assertEquals(SoftwareUpdate.Mode.MANUAL, SoftwareUpdate.modeFromLegacy(false, true))
        assertEquals(SoftwareUpdate.Mode.NOTIFY, SoftwareUpdate.modeFromLegacy(true, false))
        assertEquals(SoftwareUpdate.Mode.AUTOMATIC, SoftwareUpdate.modeFromLegacy(true, true))
    }

    @Test fun `release notes show what's new as plain lines`() {
        val notes = "Intro\n\n## Install\n- Download it\n\n## What's new in 0.6.1\n### Added\n- **Status** options, see [docs](https://x)\n![gif](a.gif)"
        assertEquals(listOf("Added", "• Status options, see docs"), releaseNoteLines(notes))
        assertEquals(listOf("• one", "two"), releaseNoteLines("* one\ntwo"))
    }
}
