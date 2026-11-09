package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandSizePresetTest {
    @Test fun `standard is the island as it always was`() {
        assertEquals(islandGeometry(null as IntArray?, 800, 2f), islandGeometry(null as IntArray?, 800, 2f, IslandSizePreset.STANDARD.scale))
        assertEquals(34f, islandGeometry(null as IntArray?, 800, 2f, IslandSizePreset.STANDARD.scale).pillH, 0.001f)
    }

    @Test fun `a free pill is shorter when small and taller when large`() {
        val small = islandGeometry(null as IntArray?, 800, 2f, IslandSizePreset.SMALL.scale).pillH
        val large = islandGeometry(null as IntArray?, 800, 2f, IslandSizePreset.LARGE.scale).pillH
        assertTrue(small < 34f && large > 34f)
    }

    @Test fun `a camera hole taller than the preset keeps the same pill for every size`() {
        val tall = intArrayOf(900, 20, 940, 100)
        val heights = IslandSizePreset.entries.map { islandGeometry(tall, 2000, 2f, it.scale).pillH }
        assertEquals(heights[0], heights[1], 0.001f)
        assertEquals(heights[1], heights[2], 0.001f)
    }

    @Test fun `a dragged pill follows the preset too`() {
        val spot = IslandPosition(.5f, 40f)
        val standard = islandPlacement(null, spot, 1000, 2000, 2f).pillH
        val large = islandPlacement(null, spot, 1000, 2000, 2f, scale = IslandSizePreset.LARGE.scale).pillH
        assertTrue(large > standard)
    }

    @Test fun `an unknown or missing saved size is standard`() {
        assertEquals(IslandSizePreset.STANDARD, IslandSizePreset.of(null))
        assertEquals(IslandSizePreset.STANDARD, IslandSizePreset.of("huge"))
        assertEquals(IslandSizePreset.LARGE, IslandSizePreset.of("large"))
    }
}
