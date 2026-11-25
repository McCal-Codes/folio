package com.mccal.folio

import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The snap loader: an endless loop that must not jump, flash or run when motion is off. */
class SnapLoaderTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private fun source(name: String) = File(root, "app/src/main/java/com/mccal/folio/$name").readText()
    private val frames = (0..60).map { snapPose(it / 60f) }

    @Test fun `the end of the loop meets the start so repeating it has no seam`() {
        val first = snapPose(0f); val last = snapPose(1f)
        assertEquals(first.gap, last.gap, .1f)
        assertEquals(first.scaleX, last.scaleX, .001f)
        assertEquals(first.ringAlpha, last.ringAlpha, .001f)
    }

    @Test fun `the halves close to a hairline, and open a little past rest before settling`() {
        assertEquals(SNAP_SHUT_GAP, frames.minOf { it.gap }, .01f)
        val widest = frames.maxOf { it.gap }
        assertTrue("overshoot is felt but small: $widest", widest in SNAP_OPEN_GAP + 1f..SNAP_OPEN_GAP + 8f)
    }

    @Test fun `the click happens at the hit and the squash is slight`() {
        val hit = snapPose(14f / 60f)
        assertEquals(SNAP_SHUT_GAP, hit.gap, .01f)
        assertTrue(hit.scaleX in 1.05f..1.12f && hit.scaleY in .9f..0.96f)
        assertTrue(frames.all { it.scaleX in .9f..1.12f && it.scaleY in .9f..1.12f && it.drop in 0f..3.01f })
    }

    @Test fun `the ring is faint, fades in over two frames and never flashes`() {
        assertTrue("never brighter than the peak", frames.all { it.ringAlpha <= SNAP_RING_PEAK + 1e-4f })
        val steps = frames.zipWithNext { a, b -> abs(b.ringAlpha - a.ringAlpha) }
        assertTrue("no frame changes it by more than half the peak: ${steps.max()}", steps.max() <= SNAP_RING_PEAK / 2f + 1e-4f)
        assertEquals("quiet before the hit", 0f, snapPose(10f / 60f).ringAlpha, 0f)
        assertEquals("quiet by the end", 0f, snapPose(30f / 60f).ringAlpha, 0f)
    }

    @Test fun `one loop is two seconds and the spinners it replaces are gone`() {
        assertEquals(2000, SNAP_LOOP_MS)
        assertTrue("SnapLoader(size = 26.dp)" in source("MarketScreen.kt"))
        val picker = source("WidgetPicker.kt")
        assertTrue("SnapLoader(size = 26.dp, color = Color.White)" in picker)
        assertTrue("SnapLoader(size = 40.dp, color = ink)" in picker)
    }

    @Test fun `motion off keeps the halves still and only fades them`() {
        val loader = source("SnapLoader.kt")
        assertTrue("if (still) SnapPose(gap = 20f" in loader)
        assertTrue("LocalReduceMotion.current" in loader)
        assertTrue("MotionSpeed.current.factor" in loader)
    }
}
