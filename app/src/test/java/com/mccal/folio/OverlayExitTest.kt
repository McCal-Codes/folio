package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayExitTest {
    @Test fun `the close runs once, whichever of the animation and the timeout ends first`() {
        val gate = ExitGate()
        var closed = 0
        assertTrue(gate.begin { closed++ })
        gate.finish()?.invoke()
        assertNull("the timeout after the animation finds nothing to do", gate.finish())
        assertEquals(1, closed)
    }

    @Test fun `a second tap on the scrim or Done while leaving is ignored`() {
        val gate = ExitGate()
        var first = 0; var second = 0
        assertTrue(gate.begin { first++ })
        assertFalse(gate.begin { second++ })
        gate.finish()?.invoke()
        assertEquals(1, first); assertEquals(0, second)
    }

    @Test fun `a surface the caller kept can close again`() {
        val gate = ExitGate()
        gate.begin { }
        gate.finish()
        gate.reopen()
        assertFalse(gate.closing)
        var closed = 0
        assertTrue(gate.begin { closed++ })
        gate.finish()?.invoke()
        assertEquals(1, closed)
    }

    @Test fun `with nothing leaving, every surface is drawn exactly as before`() {
        listOf(false, true).forEach { reduce ->
            assertEquals(0f, OverlayExits.pageShift(0f, reduce), 0f)
            assertEquals(1f, OverlayExits.alpha(0f, fades = true, reduce = reduce), 0f)
            assertEquals(1f, OverlayExits.alpha(0f, fades = false, reduce = reduce), 0f)
            assertEquals(0f, OverlayExits.formDrop(0f, reduce), 0f)
            assertEquals(1f, OverlayExits.alertScale(0f, reduce), 0f)
        }
    }

    @Test fun `a page slides back out whole, the way it came`() {
        assertEquals(1f, OverlayExits.pageShift(1f, reduce = false), 0f)
        assertEquals(1f, OverlayExits.alpha(1f, fades = false, reduce = false), 0f)
    }

    @Test fun `a form sheet drops back the distance it rose and fades`() {
        assertEquals(.12f, OverlayExits.formDrop(1f, reduce = false), .0001f)
        assertEquals(0f, OverlayExits.alpha(1f, fades = true, reduce = false), 0f)
    }

    @Test fun `an alert shrinks a little as it fades, and Firm's tail never grows it back`() {
        assertEquals(.94f, OverlayExits.alertScale(1f, reduce = false), .0001f)
        assertEquals(.94f, OverlayExits.alertScale(1.02f, reduce = false), .0001f)
        assertEquals(.5f, OverlayExits.alpha(.5f, fades = true, reduce = false), .0001f)
    }

    @Test fun `Reduce Motion only fades, nothing moves or shrinks`() {
        assertEquals(0f, OverlayExits.pageShift(1f, reduce = true), 0f)
        assertEquals(0f, OverlayExits.formDrop(1f, reduce = true), 0f)
        assertEquals(1f, OverlayExits.alertScale(1f, reduce = true), 0f)
        assertEquals(0f, OverlayExits.alpha(1f, fades = false, reduce = true), 0f)
    }

    @Test fun `Home draws again behind a page from the first frame of its exit`() {
        assertTrue(OverlayExits.covering(slide = 0f, exit = 0f))
        assertFalse(OverlayExits.covering(slide = 0f, exit = .01f))
        assertFalse(OverlayExits.covering(slide = .3f, exit = 0f))
    }

    @Test fun `a closing page wakes Home before it moves, so the heavy frames come while nothing slides`() {
        // Emulator, 12 Oct: waking Home on the first slide frame put about two frames over 50 ms into every close (p95 +6 ms).
        assertEquals(2, PAGE_UNCOVER_FRAMES)
        val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
        val sheet = java.io.File(root, "app/src/main/java/com/mccal/folio/FolioSheet.kt").readText()
        assertTrue("the page waits for Home", "rememberOverlayExit(uncoverFrames = PAGE_UNCOVER_FRAMES)" in sheet)
        assertTrue("Home wakes when the close starts, not when the page first moves", "if (exit.leaving) 1f else 0f" in sheet)
    }

    @Test fun `outside a page, sheet or alert a close happens at once`() {
        var closed = false
        null.closeWithExit { closed = true }
        assertTrue(closed)
    }
}
