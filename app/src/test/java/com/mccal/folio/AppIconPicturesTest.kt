package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconPicturesTest {
    @Test fun `a wide picture is cropped from its middle`() {
        val c = AppIconPictures.centerCrop(1920, 1080)
        assertEquals(AppIconPictures.SIZE, c.scaledHeight)
        assertTrue(c.scaledWidth > AppIconPictures.SIZE)
        assertEquals(0, c.top)
        assertEquals((c.scaledWidth - AppIconPictures.SIZE) / 2, c.left)
    }

    @Test fun `a tall picture is cropped from its middle and a square is not cropped`() {
        val tall = AppIconPictures.centerCrop(1000, 4000)
        assertEquals(AppIconPictures.SIZE, tall.scaledWidth)
        assertEquals(0, tall.left)
        assertTrue(tall.top > 0)
        val square = AppIconPictures.centerCrop(500, 500)
        assertEquals(0, square.left)
        assertEquals(0, square.top)
        assertEquals(AppIconPictures.SIZE, square.scaledWidth)
    }

    @Test fun `the crop is always at least the icon size, even for a tiny picture`() {
        val c = AppIconPictures.centerCrop(10, 10)
        assertTrue(c.scaledWidth >= AppIconPictures.SIZE && c.scaledHeight >= AppIconPictures.SIZE)
    }

    @Test fun `a picture is part of the choice, saved and read back`() {
        val o = AppIconOverride(IconStyle.DARK, null, picture = 1234L)
        assertFalse(o.isDefault)
        assertTrue(o.hasPicture)
        assertEquals(o, AppIconOverride.fromJson(o.toJson()))
        // An old save has no picture.
        assertEquals(0L, AppIconOverride.fromJson(AppIconOverride(IconStyle.DARK).toJson()).picture)
        // Only a picture is still a real choice, so the map keeps it.
        assertEquals(setOf("a"), editAppIcon(emptyMap(), "a", AppIconOverride(picture = 5L)).keys)
        assertTrue(editAppIcon(mapOf("a" to AppIconOverride(picture = 5L)), "a", AppIconOverride()).isEmpty())
    }

    @Test fun `backups keep the look but not the pictures`() {
        val map = mapOf("a" to AppIconOverride(IconStyle.CLEAR, picture = 9L), "b" to AppIconOverride(picture = 9L))
        val stripped = withoutPictures(map)
        assertEquals(setOf("a"), stripped.keys)
        assertEquals(AppIconOverride(IconStyle.CLEAR), stripped["a"])
    }
}
