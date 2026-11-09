package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandSizePresetTest {
    private fun scale(preset: IslandSizePreset) = preset.scaleFor(null, 2f)

    @Test fun `standard is the island as it always was`() {
        assertEquals(islandGeometry(null as IntArray?, 800, 2f), islandGeometry(null as IntArray?, 800, 2f, scale(IslandSizePreset.STANDARD)))
        assertEquals(34f, islandGeometry(null as IntArray?, 800, 2f, scale(IslandSizePreset.STANDARD)).pillH, 0.001f)
    }

    @Test fun `a free pill is shorter when small and taller when large`() {
        val small = islandGeometry(null as IntArray?, 800, 2f, scale(IslandSizePreset.SMALL)).pillH
        val large = islandGeometry(null as IntArray?, 800, 2f, scale(IslandSizePreset.LARGE)).pillH
        assertTrue(small < 34f && large > 34f)
    }

    @Test fun `a camera hole taller than the preset keeps the same pill for every size`() {
        val tall = intArrayOf(900, 20, 940, 100)
        val heights = listOf(IslandSizePreset.SMALL, IslandSizePreset.STANDARD, IslandSizePreset.LARGE)
            .map { islandGeometry(tall, 2000, 2f, it.scaleFor(tall, 2f)).pillH }
        assertEquals(heights[0], heights[1], 0.001f)
        assertEquals(heights[1], heights[2], 0.001f)
    }

    @Test fun `a dragged pill follows the preset too`() {
        val spot = IslandPosition(.5f, 40f)
        val standard = islandPlacement(null, spot, 1000, 2000, 2f).pillH
        val large = islandPlacement(null, spot, 1000, 2000, 2f, scale = scale(IslandSizePreset.LARGE)).pillH
        assertTrue(large > standard)
    }

    @Test fun `an unknown or missing saved size is standard`() {
        assertEquals(IslandSizePreset.STANDARD, IslandSizePreset.of(null))
        assertEquals(IslandSizePreset.STANDARD, IslandSizePreset.of("huge"))
        assertEquals(IslandSizePreset.LARGE, IslandSizePreset.of("large"))
        assertEquals(IslandSizePreset.AUTO, IslandSizePreset.of("auto"))
    }

    @Test fun `automatic goes small around a punch hole on a phone`() {
        val punchHole = intArrayOf(520, 40, 556, 76) // 18 x 18 dp at 2x, 20 dp down the screen
        assertEquals(0.88f, IslandSizePreset.AUTO.scaleFor(punchHole, 2f), 0.001f)
    }

    @Test fun `automatic stays standard with no camera, a notch or the Fold strip`() {
        assertEquals(1f, IslandSizePreset.AUTO.scaleFor(null, 2f), 0.001f)
        assertEquals("a notch is wide and starts at the edge", 1f, IslandSizePreset.AUTO.scaleFor(intArrayOf(300, 0, 700, 60), 2f), 0.001f)
        assertEquals("the Fold's strip is tall", 1f, IslandSizePreset.AUTO.scaleFor(intArrayOf(589, 0, 659, 104), 2.625f), 0.001f)
    }

    @Test fun `automatic never goes large`() {
        assertTrue(listOf(null, intArrayOf(520, 40, 556, 76), intArrayOf(0, 0, 10, 10)).all { IslandSizePreset.AUTO.scaleFor(it, 2f) <= 1f })
    }
}
