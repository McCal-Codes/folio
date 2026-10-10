package com.mccal.folio

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings › Accessibility › Reduce Motion: the step between all motion and Android's Remove animations. */
class ReduceMotionTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private fun source(name: String) = File(root, "app/src/main/java/com/mccal/folio/$name").readText()

    @Test fun `feedback keeps its stiffness and loses its overshoot`() {
        assertEquals(1f to 1500f, FolioMotion.calm(FolioMotion.Control, reduce = true))
        assertEquals(FolioMotion.Snap, FolioMotion.calm(FolioMotion.Control, reduce = true))
        assertEquals(FolioMotion.Control, FolioMotion.calm(FolioMotion.Control, reduce = false))
    }

    @Test fun `the fade is short`() {
        assertEquals(150, FolioMotion.FADE_MS)
    }

    @Test fun `it is off until you turn it on, and it is saved`() {
        assertFalse(LauncherState().reduceMotion)
        val model = source("LauncherModel.kt")
        assertTrue(".put(\"reduceMotion\", s.reduceMotion)" in model)
        assertTrue("reduceMotion = j.optBoolean(\"reduceMotion\", false)" in model)
    }

    @Test fun `the switch and Remove animations reach every screen through one flag`() {
        assertTrue("val reduceMotion = reduceMotionState.value || state.reduceMotion" in source("MainActivity.kt"))
    }

    @Test fun `sheets, pages, alerts and categories fade in under Reduce Motion`() {
        val sheet = source("FolioSheet.kt")
        assertEquals("the page, the form sheet and the alert", 3, Regex("= rememberReducedFade\\(\\)").findAll(sheet).count())
        assertTrue("alpha = pageFade.value" in sheet)
        assertTrue("alpha = appear.value * fade.value; scaleX = 1.12f" in sheet)
        assertTrue("= rememberReducedFade()" in source("LibraryCategories.kt"))
    }

    @Test fun `switches and segments settle without overshoot under Reduce Motion`() {
        val controls = source("IosControls.kt")
        assertEquals(2, Regex("FolioMotion\\.calm\\(FolioMotion\\.Control, LocalReduceMotion\\.current\\)").findAll(controls).count())
    }

    @Test fun `the install check fades in instead of being skipped`() {
        val pieces = source("MotionPieces.kt")
        assertTrue("if (finished && FolioMotion.v2) {" in pieces)
        assertTrue("Animatable(if (reduce) 1f else .2f)" in pieces)
    }
}
