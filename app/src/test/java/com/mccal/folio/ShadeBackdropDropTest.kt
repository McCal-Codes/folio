package com.mccal.folio

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** PERMISSIONS.md promises the blurred picture behind a panel is dropped when the panel closes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShadeBackdropDropTest {
    @Test fun `closing the panels lets go of the blurred picture`() {
        val service = Robolectric.buildService(SystemShadeAccessibilityService::class.java).create().get()
        val overlay = EverywhereOverlay(service)
        overlay.shadeBackdrop.value = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).asImageBitmap()
        assertNotNull(overlay.shadeBackdrop.value)
        overlay.removeShade()
        assertNull(overlay.shadeBackdrop.value)
    }
}
