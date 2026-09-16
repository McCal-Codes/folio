package com.mccal.folio

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
}
