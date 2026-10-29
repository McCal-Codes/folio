package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageInfoTest {
    private fun volume(
        id: String = "v", total: Long? = 1000L, free: Long? = 400L,
        removable: Boolean = false, primary: Boolean = false, mounted: Boolean = true, label: String = "Volume",
    ) = VolumeInfo(id, label, total, free, removable, primary, mounted)

    @Test fun `used is total minus free and the fraction follows`() {
        val v = volume(total = 1000L, free = 250L)
        assertEquals(750L, v.usedBytes)
        assertEquals(0.75f, v.usedFraction!!, 0.0001f)
    }

    @Test fun `free larger than total clamps to nothing used`() {
        val v = volume(total = 1000L, free = 1500L)
        assertEquals(0L, v.usedBytes)
        assertEquals(0f, v.usedFraction!!, 0f)
    }

    @Test fun `a negative free value clamps to a full volume`() {
        val v = volume(total = 1000L, free = -5L)
        assertEquals(1000L, v.usedBytes)
        assertEquals(1f, v.usedFraction!!, 0f)
    }

    @Test fun `zero total is unknown, not zero percent`() {
        val v = volume(total = 0L, free = 0L)
        assertNull(v.usedBytes)
        assertNull(v.usedFraction)
        assertFalse(v.nearlyFull)
    }

    @Test fun `unknown total or free gives unknown used`() {
        assertNull(volume(total = null, free = 10L).usedBytes)
        assertNull(volume(total = 10L, free = null).usedFraction)
        assertFalse(volume(total = null, free = null).nearlyFull)
    }

    @Test fun `fraction rounds sensibly on a large volume`() {
        val total = 256_000_000_000L
        val v = volume(total = total, free = 100_000_000_000L)
        assertEquals(156_000_000_000L, v.usedBytes)
        assertEquals(0.609375f, v.usedFraction!!, 0.0001f)
    }

    @Test fun `nearly full starts at ninety percent`() {
        assertFalse(volume(total = 1000L, free = 101L).nearlyFull)
        assertTrue(volume(total = 1000L, free = 100L).nearlyFull)
        assertTrue(volume(total = 1000L, free = 0L).nearlyFull)
    }

    @Test fun `destinations keep only removable mounted volumes`() {
        val internal = volume(id = "internal", primary = true)
        val usb = volume(id = "usb", removable = true)
        val sd = volume(id = "sd", removable = true)
        val unmounted = volume(id = "gone", removable = true, mounted = false)
        val readOnly = volume(id = "ro", removable = true, mounted = false) // read-only reads as not mounted
        val adoptedInternal = volume(id = "adopted", removable = false)
        val result = destinationsFrom(listOf(internal, usb, unmounted, sd, readOnly, adoptedInternal))
        assertEquals(listOf("usb", "sd"), result.map { it.id })
    }

    @Test fun `no volumes means no destinations`() {
        assertTrue(destinationsFrom(emptyList()).isEmpty())
    }

    @Test fun `label uses the description Android gives, trimmed`() {
        assertEquals("SanDisk", volumeLabel("  SanDisk ", "Removable storage"))
    }

    @Test fun `label falls back when Android gives none`() {
        assertEquals("Removable storage", volumeLabel(null, "Removable storage"))
        assertEquals("Removable storage", volumeLabel("   ", "Removable storage"))
        assertEquals("Removable storage", volumeLabel("", "Removable storage"))
    }

    @Test fun `top apps are biggest first, limited, and skip empty sizes`() {
        val apps = listOf(
            AppSize("a", "A", 10L), AppSize("b", "B", 50L), AppSize("c", "C", 0L),
            AppSize("d", "D", 30L), AppSize("e", "E", 20L), AppSize("f", "F", 40L), AppSize("g", "G", 5L),
        )
        assertEquals(listOf("b", "f", "d", "e", "a"), topApps(apps).map { it.packageName })
        assertEquals(listOf("b", "f"), topApps(apps, limit = 2).map { it.packageName })
        assertTrue(topApps(emptyList()).isEmpty())
    }

    @Test fun `a Storage row is named for the app, not for the launcher activity that happened to come first`() {
        assertEquals("Folio", storageAppLabel("Folio", "Folio Notification Shade"))
        assertEquals("Maps", storageAppLabel("Maps", "Maps"))
    }

    @Test fun `a blank or missing application label falls back to the activity's`() {
        assertEquals("Folio Notification Shade", storageAppLabel(null, "Folio Notification Shade"))
        assertEquals("Camera", storageAppLabel("  ", "Camera"))
    }
}
