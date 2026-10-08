package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticePlacementTest {
    @Test fun `a short notice is the narrowest card and a long one the widest`() {
        assertEquals(220f, noticeWantWidth(5, false), 0f)
        assertEquals(340f, noticeWantWidth(200, true), 0f)
        assertTrue(noticeWantWidth(30, true) > noticeWantWidth(30, false))
    }

    @Test fun `the card is centered on the camera when there is room`() {
        val p = noticePlacement(411f, 891f, 205f, 12f, 300f)
        assertEquals(300f, p.width, 0f)
        assertEquals(55f, noticeLeft(p, 205f, p.width), 0.01f)
    }

    @Test fun `near a corner the card shifts inward but keeps the camera inside it`() {
        val p = noticePlacement(932f, 704f, 880f, 6f, 340f)
        val left = noticeLeft(p, 880f, p.width)
        assertEquals(340f, p.width, 0f)
        assertTrue(left + p.width <= 932f - 8f)
        assertTrue(880f in left..(left + p.width))
    }

    @Test fun `the card never leaves the window on a narrow screen`() {
        val p = noticePlacement(200f, 400f, 100f, 8f, 340f)
        assertTrue(p.width <= 200f - 16f)
        val left = noticeLeft(p, 100f, p.width)
        assertTrue(left >= 8f && left + p.width <= 192f)
    }

    @Test fun `a book fold keeps the card on the camera's side`() {
        // Window 932 wide, fold from 454 to 478, camera at 880 (right half).
        val right = noticePlacement(932f, 704f, 880f, 6f, 340f, listOf(454f..478f), hingeVertical = true)
        assertTrue(noticeLeft(right, 880f, right.width) >= 478f + 8f)
        // Camera at 100 (left half).
        val left = noticePlacement(932f, 704f, 100f, 6f, 340f, listOf(454f..478f), hingeVertical = true)
        assertTrue(noticeLeft(left, 100f, left.width) + left.width <= 454f - 8f)
    }

    @Test fun `a laptop fold limits the height to the top half`() {
        val p = noticePlacement(411f, 891f, 205f, 12f, 300f, listOf(430f..460f), hingeVertical = false)
        assertEquals(430f - 8f - 12f, p.maxHeight, 0.01f)
    }

    @Test fun `no hinge means the whole window is available`() {
        val p = noticePlacement(411f, 891f, 205f, 12f, 300f)
        assertEquals(891f - 8f - 12f, p.maxHeight, 0.01f)
    }
}
