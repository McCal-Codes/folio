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
        assertEquals(listOf("show_touches", "pointer_location", "always_finish_activities", "stay_on_while_plugged_in", "force_resizable_activities"), DisplayAid.entries.map { it.key })
        assertEquals(listOf(false, false, true, true, true), DisplayAid.entries.map { it.global })
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

    @Test fun `stay awake writes the AC, USB and wireless bits and reads any bit as on`() {
        assertEquals(7, DisplayAid.STAY_AWAKE.onValue)
        assertEquals(listOf(1, 1, 1, 7, 1), DisplayAid.entries.map { it.onValue })
        android.provider.Settings.Global.putInt(context.contentResolver, DisplayAid.STAY_AWAKE.key, 2)
        assertTrue(DisplayAids.isOn(context, DisplayAid.STAY_AWAKE))
        assertTrue(DisplayAids.set(context, DisplayAid.STAY_AWAKE, true))
        assertEquals(7, android.provider.Settings.Global.getInt(context.contentResolver, DisplayAid.STAY_AWAKE.key, 0))
        assertTrue(DisplayAids.set(context, DisplayAid.STAY_AWAKE, false))
        assertFalse(DisplayAids.isOn(context, DisplayAid.STAY_AWAKE))
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
