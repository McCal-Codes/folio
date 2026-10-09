package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DYN-6 and DES-4: springs come from [FolioMotion], so Settings › Animation Speed reaches every one and the numbers are chosen in one place.
 * This counts springs written by hand and fails if there is one. A new motion picks a token (`FolioMotion.spring(FolioMotion.Appear)`); if none fits,
 * add a token with a reason next to the others. `tween` is not counted: Folio keeps it for fades, timed sequences and endless loops, which
 * a spring cannot do (DYN-7 prefers a spring for movement, not for a crossfade).
 */
class MotionRatchetTest {
    private val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
    private val bareSpring = Regex("""(^|[^A-Za-z.])spring\(""")
    /** Where `spring` is defined, not used. */
    private val definitions = setOf("FolioGlass.kt", "FolioTokens.kt")

    @Test fun `no spring is written by hand outside FolioMotion`() {
        val hits = java.io.File(root, "app/src/main/java").walkTopDown().filter { it.extension == "kt" && it.name !in definitions }.flatMap { file ->
            file.readLines().withIndex().filter { (_, line) -> !line.trim().startsWith("//") && !line.trim().startsWith("*") && bareSpring.containsMatchIn(line) }
                .map { "${file.name}:${it.index + 1} ${it.value.trim().take(110)}" }
        }.toList()
        assertEquals("These springs should be FolioMotion tokens:\n" + hits.joinToString("\n"), emptyList<String>(), hits)
    }

    @Test fun `the named springs are real springs and ordered from bouncy to firm`() {
        val all = listOf(FolioMotion.Bounce, FolioMotion.Control, FolioMotion.Appear, FolioMotion.Quick, FolioMotion.Settle, FolioMotion.Firm, FolioMotion.Snap)
        all.forEach { (damping, stiffness) -> assertTrue("$damping $stiffness", damping in .5f..1f && stiffness in 300f..2000f) }
        assertTrue("Bounce has the most overshoot", all.all { FolioMotion.Bounce.first <= it.first })
        assertTrue("Firm and Snap do not overshoot", FolioMotion.Firm.first == 1f && FolioMotion.Snap.first == 1f)
    }
}
