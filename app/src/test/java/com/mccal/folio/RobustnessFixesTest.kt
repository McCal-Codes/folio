package com.mccal.folio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/** Small fixes from the 5 Oct sweep: a theme file is read with a cap, and a few lifecycle hooks stay in place. */
class RobustnessFixesTest {
    private val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
    private fun source(path: String) = java.io.File(root, "app/src/main/java/com/mccal/folio/$path").readText()

    @Test fun `a file at the limit is read whole`() {
        val bytes = ByteArray(MAX_THEME_BYTES) { 'a'.code.toByte() }
        assertArrayEquals(bytes, readCapped(ByteArrayInputStream(bytes), MAX_THEME_BYTES))
    }

    @Test fun `a file over the limit is refused instead of read whole`() {
        assertNull(readCapped(ByteArrayInputStream(ByteArray(MAX_THEME_BYTES + 1)), MAX_THEME_BYTES))
    }

    @Test fun `an empty file reads as empty`() {
        assertNotNull(readCapped(ByteArrayInputStream(ByteArray(0)), MAX_THEME_BYTES))
    }

    @Test fun `a change waiting out its delay is written when Home stops or the model is cleared`() {
        assertTrue(source("MainActivity.kt").contains("model.flushPending()"))
        val model = source("LauncherModel.kt")
        assertTrue(model.substringAfter("override fun onCleared()").take(200).contains("flushPending()"))
    }

    @Test fun `the update is only ready when the verified file was renamed`() {
        assertTrue(source("SoftwareUpdate.kt").contains("check(apk.renameTo("))
    }

    @Test fun `the installed icon packs are only asked for off the main thread`() {
        listOf("CustomizationSheet.kt", "LauncherModel.kt").forEach { file ->
            val lines = source(file).lines()
            lines.forEachIndexed { i, line ->
                if (line.contains("IconPacks.installed(")) {
                    val around = lines.subList(maxOf(0, i - 3), i + 1).joinToString("\n")
                    assertTrue("$file:${i + 1} reads icon packs on the main thread", around.contains("Dispatchers.IO"))
                }
            }
        }
    }

    @Test fun `the checkpoint is written off the main thread`() {
        val text = source("Diagnostics.kt").substringAfter("fun checkpoint(").substringBefore("private fun bootCount")
        assertTrue(text.contains("checkpointWriter.execute"))
    }
}
