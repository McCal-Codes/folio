package com.mccal.folio

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Folio's colors, radii and text sizes are named once in `FolioTokens.kt` and `FolioGlass.kt`, so a screen points at
 * the same value as every other screen (docs/standards/design.md, DES-4 to DES-7). A lot of code still writes its own,
 * so this counts what's left and fails if the count goes up. When you move some onto tokens, lower the limit here to
 * the number the failure prints, and it can't creep back.
 *
 * Run with `LIST_TOKENS=1` to print every hit with its file and line.
 *
 * [tokenFiles] are where a raw value belongs: the token files themselves, the theme and appearance models, and the
 * drawings whose colors are the artwork (`DuneWallpaper`, `LiveIcons`, `AppIcons`).
 */
class DesignTokensTest {
    private val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }

    private val tokenFiles = setOf(
        "FolioTokens.kt", "FolioGlass.kt", "Appearance.kt", "Themes.kt", "LiveIcons.kt", "DuneWallpaper.kt", "AppIcons.kt",
    )

    private val patterns = mapOf(
        "hex color" to Regex("""Color\(0x[0-9A-Fa-f]{8}\)"""),
        // A colour written as a number rather than a Color: a tweak's tint, a Focus mode, a saved layout. They drift
        // from the palette just as easily, so they are counted too (FolioColors.Value is how they reach the tokens).
        "ARGB number" to Regex("""(?<![\w.(])0x[0-9A-Fa-f]{8}L?\b"""),
        "corner radius" to Regex("""RoundedCornerShape\(\d+\.dp\)"""),
        "text size" to Regex("""fontSize = \d+\.sp"""),
    )

    /** What each kind is allowed to have left. Lower these as code moves onto the tokens; never raise them. */
    private val limits = mapOf("hex color" to 57, "ARGB number" to 23, "corner radius" to 132, "text size" to 144)

    private fun hits(kind: String): List<String> {
        val pattern = patterns.getValue(kind)
        return java.io.File(root, "app/src/main/java").walkTopDown()
            .filter { it.extension == "kt" && it.name !in tokenFiles }
            .flatMap { file ->
                file.readLines().withIndex().mapNotNull { (i, line) ->
                    pattern.find(line)?.let { "${file.name}:${i + 1} ${it.value}" }
                }
            }.toList()
    }

    @Test fun `no new raw colors, radii or text sizes outside the token files`() {
        val counted = patterns.keys.associateWith { hits(it) }
        if (System.getenv("LIST_TOKENS") != null) counted.forEach { (kind, list) -> list.forEach { println("$kind $it") } }
        counted.forEach { (kind, list) ->
            val limit = limits.getValue(kind)
            assertTrue(
                "${list.size} raw ${kind}s outside the token files, was $limit. Use FolioColors, FolioRadius or " +
                    "FolioType, or lower the limit in DesignTokensTest to ${list.size} if you moved some over.",
                list.size <= limit,
            )
        }
    }

    @Test fun `the token values Folio's shared components use are the ones the standard names`() {
        // The scales in docs/standards/design.md. A change here is a change to every screen, so it's deliberate.
        // Every palette number and its Color are the same colour: one source of truth for both kinds of call site.
        assertEquals(FolioColors.Blue, Color(FolioColors.Value.Blue))
        assertEquals(FolioColors.Warning, Color(FolioColors.Value.Warning))
        assertEquals(FolioColors.MenuSurface, Color(FolioColors.Value.MenuSurface))
        assertEquals(listOf(10, 14, 16, 20, 24, 28), listOf(FolioRadius.CONTROL, FolioRadius.CARD, FolioRadius.GROUP, FolioRadius.GROUPED_CARD, FolioRadius.PANEL, FolioRadius.SHEET_TOP))
        assertEquals(listOf(28, 17, 15, 13, 12), listOf(FolioType.TITLE, FolioType.BODY, FolioType.SUBHEAD, FolioType.FOOTNOTE, FolioType.GROUP_LABEL))
        assertEquals(listOf(48, 52), listOf(FolioRow.ACTION, FolioRow.NAV))
        assertEquals(
            listOf(2, 4, 6, 8, 10, 12, 14, 16, 20, 24, 32),
            listOf(FolioSpace.HAIR, FolioSpace.TINY, FolioSpace.SNUG, FolioSpace.SMALL, FolioSpace.COMPACT, FolioSpace.MEDIUM, FolioSpace.COMFY, FolioSpace.LARGE, FolioSpace.XL, FolioSpace.XXL, FolioSpace.HUGE),
        )
        // Every row a finger touches clears Android's 48dp minimum (A11Y-1).
        assertTrue(FolioRow.ACTION >= 48 && FolioRow.NAV >= 48)
    }
}
