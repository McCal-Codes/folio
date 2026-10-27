package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DYN-6 and DES-4: springs come from [FolioMotion], so Settings › Animation Speed reaches every one and the numbers are chosen in one place.
 * This counts springs written by hand and fails if there is one. A new motion picks a token (`FolioMotion.spring(FolioMotion.Appear)`); if none fits,
 * add a token with a reason next to the others. `tween` is not counted: Folio keeps it for fades, timed sequences and endless loops, which
 * a spring cannot do (DYN-7 prefers a spring for movement, not for a crossfade).
 *
 * A spring counts as written by hand in every spelling Kotlin allows: `spring(`, `spring<Dp>(`, and the fully qualified
 * `androidx.compose.animation.core.spring(`. An earlier version of this test missed the last two, and 17 slipped by until 0.6.9.
 * Numbers that are not a token yet go through `FolioMotion.spring(.85f to 380f)`, so Animation Speed still reaches them.
 * A line ending in `// motion-ratchet` is let through on purpose, with the reason on the line above; there may be at most [ALLOWED].
 */
class MotionRatchetTest {
    private val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
    /** Where `spring` is defined, not used. */
    private val definitions = setOf("FolioGlass.kt", "FolioTokens.kt")

    private fun lines() = java.io.File(root, "app/src/main/java").walkTopDown().filter { it.extension == "kt" && it.name !in definitions }.flatMap { file ->
        file.readLines().withIndex().filter { (_, line) -> !line.trim().startsWith("//") && !line.trim().startsWith("*") }
            .map { "${file.name}:${it.index + 1} ${it.value.trim()}" to it.value }
    }.toList()

    @Test fun `no spring is written by hand outside FolioMotion`() {
        val hits = lines().filter { (_, line) -> handWritten(line) && !line.trimEnd().endsWith(MARKER) }.map { it.first.take(140) }
        assertEquals("These springs should be FolioMotion tokens:\n" + hits.joinToString("\n"), emptyList<String>(), hits)
    }

    @Test fun `the deliberate exceptions stay few`() {
        val allowed = lines().filter { (_, line) -> handWritten(line) && line.trimEnd().endsWith(MARKER) }.map { it.first.take(140) }
        assertTrue("At most $ALLOWED springs may be let through:\n" + allowed.joinToString("\n"), allowed.size <= ALLOWED)
    }

    @Test fun `every spelling of a hand-written spring is caught`() {
        listOf("spring(dampingRatio = .8f)", "x, spring<Dp>(dampingRatio = .9f)", "androidx.compose.animation.core.spring(stiffness = 1400f)",
            "androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntSize>(dampingRatio = .72f)", "spring (stiffness = 1f)")
            .forEach { assertTrue(it, handWritten(it)) }
        listOf("FolioMotion.spring(FolioMotion.Snap)", "FolioMotion.spring<Dp>(.9f to 380f)", "MotionSpeed.spring(1f, 700f)",
            "PressFeedback.springFor(kind, v2)", "val spring = 1", "Spring.StiffnessMedium")
            .forEach { assertTrue(it, !handWritten(it)) }
    }

    @Test fun `the named springs are real springs and ordered from bouncy to firm`() {
        val all = listOf(FolioMotion.Bounce, FolioMotion.Control, FolioMotion.Appear, FolioMotion.Quick, FolioMotion.Settle, FolioMotion.Firm, FolioMotion.Snap)
        all.forEach { (damping, stiffness) -> assertTrue("$damping $stiffness", damping in .5f..1f && stiffness in 300f..2000f) }
        assertTrue("Bounce has the most overshoot", all.all { FolioMotion.Bounce.first <= it.first })
        assertTrue("Firm and Snap do not overshoot", FolioMotion.Firm.first == 1f && FolioMotion.Snap.first == 1f)
    }

    private companion object {
        const val MARKER = "// motion-ratchet"
        const val ALLOWED = 1
        val call = Regex("""\bspring\s*(<[^()]*>)?\s*\(""")
        /** A call to Compose's `spring`, unless it is [FolioMotion.spring] or [MotionSpeed.spring], which apply Animation Speed. */
        fun handWritten(line: String) = call.findAll(line).any { match ->
            val before = line.substring(0, match.range.first)
            !before.endsWith("FolioMotion.") && !before.endsWith("MotionSpeed.")
        }
    }
}
