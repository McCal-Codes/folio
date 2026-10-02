package com.mccal.folio

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The art Folio ships, checked against the files that actually ship.
 *
 * `DES-2b` says Folio must be able to name the artist, the work and the license for every image it ships. For art
 * that arrives from the Market that is enforced when the package is read. For art inside the app there is no such
 * moment, so it is enforced here: a piece added without a credit, or with a credit but no picture, fails the build.
 */
class BackgroundArtTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "CHANGELOG.md").exists() }
    private val assets = File(root, "app/src/main/assets")

    /** The real list, so a piece added later is held to the same rules without anyone remembering to add it here. */
    private val builtIn = BackgroundLibrary.builtInIds

    @Test fun `every built-in piece is one Folio can name`() {
        assertTrue("Folio should ship some art", builtIn.isNotEmpty())
        for (id in builtIn) {
            assertTrue("$id should be known as built in", BackgroundLibrary.isBuiltIn(id))
        }
    }

    @Test fun `every built-in piece has its picture`() {
        for (id in builtIn) {
            val file = File(assets, BackgroundLibrary.assetPath(id))
            assertTrue("${file.name} is missing from assets", file.isFile)
            assertTrue("${file.name} is suspiciously small", file.length() > 100_000)
        }
    }

    /**
     * The widest window Folio runs in is the Fold8's inner screen at 2448 px. Art narrower than that is scaled up to
     * cover it, which is the bug the old 2048 px cap caused for every background, so the art Folio chooses itself
     * should not walk into it.
     */
    @Test fun `built-in art is wide enough for the widest screen`() {
        for (id in builtIn) {
            val file = File(assets, BackgroundLibrary.assetPath(id))
            val width = webpWidth(file)
            assertTrue("${file.name} is $width px wide, narrower than the inner screen", width >= 2448)
        }
    }

    @Test fun `the art is not so large that it dominates the download`() {
        val total = builtIn.sumOf { File(assets, BackgroundLibrary.assetPath(it)).length() }
        assertTrue("the bundled art is ${total / 1024} KB", total < 6L * 1024 * 1024)
    }

    /** Reads the canvas width out of a WebP header, so the test does not need a decoder. */
    private fun webpWidth(file: File): Int {
        val head = file.inputStream().use { it.readNBytes(32) }
        require(String(head, 0, 4) == "RIFF" && String(head, 8, 4) == "WEBP") { "${file.name} is not a WebP" }
        return when (String(head, 12, 4)) {
            // Lossy (VP8 ): width is 14 bits at offset 26.
            "VP8 " -> ((head[26].toInt() and 0xFF) or ((head[27].toInt() and 0xFF) shl 8)) and 0x3FFF
            // Lossless (VP8L): width minus one is the low 14 bits at offset 21.
            "VP8L" -> ((((head[21].toInt() and 0xFF) or ((head[22].toInt() and 0xFF) shl 8)) and 0x3FFF) + 1)
            // Extended (VP8X): canvas width minus one is 24 bits at offset 24.
            "VP8X" -> (((head[24].toInt() and 0xFF) or ((head[25].toInt() and 0xFF) shl 8) or
                ((head[26].toInt() and 0xFF) shl 16)) + 1)
            else -> error("${file.name} has an unknown WebP chunk")
        }
    }
}
