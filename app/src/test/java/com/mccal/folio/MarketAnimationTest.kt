package com.mccal.folio

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A screenshot plays only when it is a moving picture that is not too big; everything else stays a still. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarketAnimationTest {
    private val pngHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    @Test fun `a picture the source has not got is not an animation`() {
        assertNull(MarketImages.bundledAnimation({ null }, "assets/screenshots/missing.webp"))
    }

    @Test fun `a file that is not a picture is not an animation, and does not throw`() {
        assertNull(MarketImages.bundledAnimation({ "not a picture".toByteArray() }, "assets/screenshots/x.webp"))
        assertNull(MarketImages.bundledAnimation({ pngHeader }, "assets/screenshots/x.png"))
    }

    @Test fun `a file over the size cap is never decoded`() {
        var asked = 0
        val big = ByteArray(MarketImages.MAX_ANIMATION_BYTES + 1)
        assertNull(MarketImages.bundledAnimation({ asked++; big }, "assets/screenshots/big.webp"))
        assertTrue("the source was read once, then the bytes were dropped", asked == 1)
    }
}
