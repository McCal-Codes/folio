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

    private val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }

    @Test fun `the header tells a clip from a still without decoding it`() {
        val clips = java.io.File(root, "docs/sdk/source/assets/screenshots").listFiles { f -> f.extension == "webp" }.orEmpty()
        assertTrue("the shipped clips are there", clips.size >= 3)
        clips.forEach { assertTrue("${it.name} is animated", MarketImages.isAnimated(it.readBytes())) }
        val still = java.io.File(root, "docs/sdk/source/assets/home-clear.webp").readBytes()
        assertTrue("a still WebP is not", !MarketImages.isAnimated(still))
        assertTrue("a GIF is worth decoding to find out", MarketImages.isAnimated("GIF89a".toByteArray() + ByteArray(20)))
        assertTrue(!MarketImages.isAnimated(pngHeader))
        assertTrue(!MarketImages.isAnimated(ByteArray(0)))
        assertTrue("a cut-off header is not", !MarketImages.isAnimated("RIFF".toByteArray()))
    }
}
