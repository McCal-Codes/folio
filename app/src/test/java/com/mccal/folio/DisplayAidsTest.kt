package com.mccal.folio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Folio Dev's Display aids: Android's own show-taps, pointer-location and don't-keep-activities switches. What an app may
 * write depends on two grants, and getting that wrong would show a live switch that silently does nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DisplayAidsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun `the switches use Android's own setting names`() {
        assertEquals(listOf("show_touches", "pointer_location", "always_finish_activities"), DisplayAid.entries.map { it.key })
        assertEquals(listOf(false, false, true), DisplayAid.entries.map { it.global })
    }

    @Test fun `system switches need Modify system settings, the global one needs the secure grant`() {
        assertEquals(AidAccess.WRITABLE, aidAccess(DisplayAid.SHOW_TAPS, canWriteSystem = true, canWriteSecure = false))
        assertEquals(AidAccess.ALLOW_MODIFY_SETTINGS, aidAccess(DisplayAid.POINTER_LOCATION, canWriteSystem = false, canWriteSecure = false))
        assertEquals(AidAccess.NEEDS_SECURE_GRANT, aidAccess(DisplayAid.KEEP_ACTIVITIES, canWriteSystem = true, canWriteSecure = false))
    }

    @Test fun `the secure grant unlocks every switch`() {
        DisplayAid.entries.forEach { assertEquals(AidAccess.WRITABLE, aidAccess(it, canWriteSystem = false, canWriteSecure = true)) }
    }

    @Test fun `the grant command names the package it was asked for`() {
        assertEquals("adb shell pm grant com.mccal.folio.dev android.permission.WRITE_SECURE_SETTINGS", secureGrantCommand("com.mccal.folio.dev"))
    }

    @Test fun `a switch reads back what was written, on both tables`() {
        DisplayAid.entries.forEach { aid ->
            assertFalse(DisplayAids.isOn(context, aid))
            assertTrue(DisplayAids.set(context, aid, true))
            assertTrue("${aid.key} should read on", DisplayAids.isOn(context, aid))
            assertTrue(DisplayAids.set(context, aid, false))
            assertFalse(DisplayAids.isOn(context, aid))
        }
    }
}
