package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Page effects as data (ADR 0008). The built-in effects, written as specs, have to draw exactly what the enum draws
 * today, and a spec made from a package's numbers can't leave the range the engine draws well.
 */
class PageEffectSpecTest {
    private val positions = listOf(-2f, -1f, -.6f, -.25f, 0f, .25f, .6f, 1f, 2f)

    @Test fun `the built-in effects as specs match the enum at every position`() {
        for (effect in PageEffect.CHOICES) {
            val spec = effect.spec!!
            positions.forEach { p ->
                assertEquals("$effect rotation at $p", effect.rotationY(p), spec.rotationY(p), 1e-4f)
                assertEquals("$effect scale at $p", effect.scale(p), spec.scale(p), 1e-4f)
                assertEquals("$effect pivot at $p", effect.pivotX(p), spec.pivotX(p), 1e-4f)
            }
            assertEquals(effect.cameraWidths, spec.cameraWidths, 1e-4f)
        }
        assertNull("None adds no layer", PageEffect.NONE.spec)
    }

    // A built-in is a spec a package could have written: inside the range PageEffectSpec.of holds a package to.
    @Test fun `every built-in effect is within what the engine draws well`() {
        for (effect in PageEffect.CHOICES) {
            val spec = effect.spec!!
            assertEquals("$effect", spec, PageEffectSpec.of(spec.maxRotation, spec.pivot, spec.shrink, spec.cameraWidths))
        }
    }

    @Test fun `a package's numbers are held to what the engine draws well`() {
        val wild = PageEffectSpec.of(400f, PageEffectSpec.Pivot.CENTER, 5f, .1f)
        assertEquals(PageEffectSpec.MAX_ROTATION, wild.maxRotation, 0f)
        assertEquals(PageEffectSpec.MAX_SHRINK, wild.shrink, 0f)
        assertEquals(PageEffectSpec.MIN_CAMERA, wild.cameraWidths, 0f)
        val broken = PageEffectSpec.of(Float.NaN, PageEffectSpec.Pivot.SEAM, Float.POSITIVE_INFINITY, Float.NaN)
        assertEquals(0f, broken.maxRotation, 0f)
        assertEquals(0f, broken.shrink, 0f)
        assertEquals(3f, broken.cameraWidths, 0f)
    }

    @Test fun `no spec shows a page's back or leaves a settled page changed`() {
        val spec = PageEffectSpec.of(90f, PageEffectSpec.Pivot.SEAM, .3f, 1.5f)
        positions.forEach { p -> assert(kotlin.math.abs(spec.rotationY(p)) <= 90f) }
        assertEquals(0f, spec.rotationY(0f), 0f)
        assertEquals(1f, spec.scale(0f), 0f)
    }

    // Skia puts the camera at cameraDistance x 72 pixels (Sk3DView::setCameraLocation), so a camera of N page widths
    // is N x width / 72, on any screen density. Dividing by the dpi instead put it 72/420 as far on the Fold8.
    @Test fun `the camera sits cameraWidths page widths away on any screen`() {
        val cube = PageEffect.CUBE.spec!!
        for (widthPx in listOf(1248f, 2176f, 1080f)) {
            assertEquals(cube.cameraWidths * widthPx, cube.cameraDistance(widthPx) * PageEffectSpec.POINTS_PER_INCH, .01f)
        }
        assertEquals(2 * 1248f / 72f, cube.cameraDistance(1248f), .001f)
    }
}
