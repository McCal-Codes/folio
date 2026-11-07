package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * StandBy keeps its clock and glow still unless "Keep the clock steady" is switched off. Nothing may flash, blink or
 * pulse by default, so every StandBy loop goes through one helper, [standByPulse], which holds the still value for the
 * switch, for night and for Reduce Motion. This fails if a loop is added anywhere else in the StandBy files.
 */
class StandBySteadyGuardTest {
    private val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
    private val standByFiles = java.io.File(root, "app/src/main/java/com/mccal/folio").listFiles { f -> f.name.startsWith("StandBy") && f.extension == "kt" }!!.toList()

    private val loop = Regex("""rememberInfiniteTransition\(|infiniteRepeatable\(|RepeatMode\.""")

    @Test
    fun theHelperIsTheOnlyPlaceAStandByLoopRuns() {
        assertTrue("no StandBy files found under $root", standByFiles.isNotEmpty())
        val stray = standByFiles.flatMap { file ->
            file.readLines().withIndex().filter { (_, line) -> loop.containsMatchIn(line) && !line.trim().startsWith("import ") && !line.trim().startsWith("*") && !line.trim().startsWith("//") }
                .map { (i, line) -> "${file.name}:${i + 1} ${line.trim()}" }
        }
        // The helper has one line with the transition and one with the repeat spec and mode.
        val inHelper = stray.filter { it.startsWith("StandBy.kt:") }
        assertEquals("loops outside standByPulse (or too many inside it): $stray", 2, stray.size)
        assertEquals(2, inHelper.size)
        val text = java.io.File(root, "app/src/main/java/com/mccal/folio/StandBy.kt").readText()
        val start = text.indexOf("fun standByPulse(")
        assertTrue("standByPulse is missing", start >= 0)
        val body = text.substring(start, text.indexOf("\n}\n", start))
        inHelper.forEach { hit ->
            val line = hit.substringAfter(" ")
            assertTrue("loop is outside standByPulse: $hit", line in body)
        }
    }

    @Test
    fun theHelperStaysStillBeforeItStartsAnything() {
        val text = java.io.File(root, "app/src/main/java/com/mccal/folio/StandBy.kt").readText()
        val start = text.indexOf("fun standByPulse(")
        val body = text.substring(start, text.indexOf("\n}\n", start))
        val gate = body.indexOf("if (steady || night || reduceMotion) return")
        assertTrue("the still-value gate must hold steady, night and Reduce Motion", gate >= 0)
        assertTrue("the gate has to come before the transition", gate < body.indexOf("rememberInfiniteTransition("))
        assertTrue("steady must default from the switch", "steady: Boolean = LocalStandBySteady.current" in body)
        assertTrue("LocalStandBySteady must default to steady", Regex("""LocalStandBySteady = staticCompositionLocalOf \{ true \}""").containsMatchIn(text))
    }

    /** The page draws its Look section by hand (TweakOptionsCard skips it), so an option added there alone is never shown. */
    @Test
    fun theSwitchIsDrawnOnTheStandBy2Page() {
        val sheet = java.io.File(root, "app/src/main/java/com/mccal/folio/CustomizationSheet.kt").readText()
        assertTrue("the steady switch is not drawn on the StandBy 2 page", """STANDBY2_ID, "steady"""" in sheet)
    }

    /** The same trap caught the ambient light switch: declared and read by StandBy, but never put on the page. */
    @Test
    fun everyLookOptionIsDrawnOnTheStandBy2Page() {
        val sheet = java.io.File(root, "app/src/main/java/com/mccal/folio/CustomizationSheet.kt").readText()
        val undrawn = TweakOptions.of(STANDBY2_ID).filter { it.section == R.string.look }
            .map { it.id }.filter { "STANDBY2_ID, \"$it\"" !in sheet }
        assertTrue("Look options declared but not drawn on the StandBy 2 page: $undrawn", undrawn.isEmpty())
    }

    @Test
    fun theSwitchIsOnByDefaultAndTurnsOff() {
        val option = TweakOptions.of(STANDBY2_ID).firstOrNull { it.id == "steady" }
        assertTrue("the steady option is missing", option is TweakOption.Toggle)
        assertTrue((option as TweakOption.Toggle).on)
        assertTrue(StandByEffectiveConfig.LEGACY_0_6_9.steadyClock)
        assertTrue(resolveStandByConfig(gateOpen = true, packageInstalled = true, packageEnabled = true).steadyClock)
        assertFalse(resolveStandByConfig(gateOpen = true, packageInstalled = true, packageEnabled = true, steadyClock = false).steadyClock)
    }
}
