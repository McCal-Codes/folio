package com.mccal.folio

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A photo shared to Folio is untrusted: only someone else's content:// image gets as far as the preview. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WallpaperShareTest {
    private val own = "com.mccal.folio"
    private fun send(type: String?, stream: Uri?) = Intent(Intent.ACTION_SEND).apply {
        if (type != null) setType(type)
        if (stream != null) putExtra(Intent.EXTRA_STREAM, stream)
    }

    @Test fun anotherAppsContentImageIsAccepted() {
        val photo = Uri.parse("content://com.google.android.apps.photos.contentprovider/1/2/mediakey/3")
        assertEquals(photo, WallpaperShareActivity.sharedPhoto(send("image/jpeg", photo), own))
    }

    @Test fun onlyContentLinksPass() {
        for (bad in listOf("file:///sdcard/a.jpg", "http://example.com/a.jpg", "https://example.com/a.jpg", "android.resource://x/1", "/sdcard/a.jpg"))
            assertNull(bad, WallpaperShareActivity.sharedPhoto(send("image/png", Uri.parse(bad)), own))
    }

    @Test fun foliosOwnProvidersAreRefused() {
        assertFalse(WallpaperShareActivity.acceptable(Uri.parse("content://com.mccal.folio/private/state"), own))
        assertFalse(WallpaperShareActivity.acceptable(Uri.parse("content://com.mccal.folio.fileprovider/x"), own))
        assertFalse(WallpaperShareActivity.acceptable(Uri.parse("content://com.mccal.folio.dev.fileprovider/x"), "com.mccal.folio.dev"))
        // A different app whose name merely starts the same is someone else's.
        assertTrue(WallpaperShareActivity.acceptable(Uri.parse("content://com.mccal.folioish.photos/x"), own))
    }

    @Test fun notAnImageNotAShareOrNoPhotoIsRefused() {
        val photo = Uri.parse("content://media/external/images/media/1")
        assertNull(WallpaperShareActivity.sharedPhoto(send("application/json", photo), own))
        assertNull(WallpaperShareActivity.sharedPhoto(send(null, photo), own))
        assertNull(WallpaperShareActivity.sharedPhoto(send("image/jpeg", null), own))
        assertNull(WallpaperShareActivity.sharedPhoto(Intent(Intent.ACTION_VIEW).setDataAndType(photo, "image/jpeg"), own))
        assertNull(WallpaperShareActivity.sharedPhoto(null, own))
    }
}
