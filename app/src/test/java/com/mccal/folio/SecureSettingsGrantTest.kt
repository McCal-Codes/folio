package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureSettingsGrantTest {
    private fun broker(granted: Boolean, off: Boolean = false, safe: Boolean = false) =
        CapabilityBroker({ listOf(SecureSettingsGrantProvider { granted }) }, integrationOff = { off }, safeMode = { safe })

    @Test fun `the commands name the package and the one permission`() {
        assertEquals("adb shell pm grant com.mccal.folio android.permission.WRITE_SECURE_SETTINGS", SecureSettingsGrant.grantCommand("com.mccal.folio"))
        assertEquals("adb shell pm revoke com.mccal.folio.dev android.permission.WRITE_SECURE_SETTINGS", SecureSettingsGrant.revokeCommand("com.mccal.folio.dev"))
    }

    @Test fun `anything that is not a plain package name gets no command`() {
        listOf("", "folio", "com.mccal.folio; reboot", "com.mccal.folio && ls", "com mccal.folio", "com.mccal.folio\n", "1com.a", "com..a", ".com.a", "com.a.", "\$(id).a")
            .forEach { assertNull("'$it'", SecureSettingsGrant.grantCommand(it)); assertNull("'$it'", SecureSettingsGrant.revokeCommand(it)) }
    }

    @Test fun `it is a shell-tier capability that waits for the owner's grant`() {
        assertEquals(PrivilegeTier.SHIZUKU, FolioCapability.SETTINGS_SECURE_WRITE.minTier)
        val no = broker(granted = false).state(FolioCapability.SETTINGS_SECURE_WRITE)
        assertFalse(no.available); assertTrue(no.setupRequired); assertEquals(UnavailableReason.NOT_GRANTED, no.reason)
        val yes = broker(granted = true).state(FolioCapability.SETTINGS_SECURE_WRITE)
        assertTrue(yes.available); assertEquals(PrivilegeTier.SHIZUKU, yes.via)
    }

    @Test fun `the system access switch and Safe Mode turn it off even when granted`() {
        assertEquals(UnavailableReason.DISABLED_BY_USER, broker(granted = true, off = true).state(FolioCapability.SETTINGS_SECURE_WRITE).reason)
        assertEquals(UnavailableReason.SAFE_MODE, broker(granted = true, safe = true).state(FolioCapability.SETTINGS_SECURE_WRITE).reason)
    }

    @Test fun `a call that needs it takes the standard way when it is not granted`() {
        assertEquals("standard", broker(granted = false).perform(FolioCapability.SETTINGS_SECURE_WRITE, fallback = { "standard" }, op = { "changed" }))
        assertEquals("changed", broker(granted = true).perform(FolioCapability.SETTINGS_SECURE_WRITE, fallback = { "standard" }, op = { "changed" }))
    }
}
