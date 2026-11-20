package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderDisplacementTest {
    @Test fun wideCardOnInnerScreenStaysOnScreen() {
        // 750 dp screen, 640 dp card: room for 39 dp each way, not the 187 dp a quarter of the screen would ask for.
        val right = folderDisplacementDp(187.5f, 750f, 640f)
        val left = folderDisplacementDp(-187.5f, 750f, 640f)
        assertEquals(39f, right, 0.001f)
        assertEquals(-39f, left, 0.001f)
        assertTrue(750f / 2f + right + 640f / 2f <= 750f - 16f + 0.001f)
    }

    @Test fun narrowCardMovesAllTheWayToItsHalf() {
        assertEquals(187.5f, folderDisplacementDp(187.5f, 750f, 280f), 0.001f)
    }

    @Test fun cardTooWideForMarginsStaysCentered() {
        assertEquals(0f, folderDisplacementDp(100f, 600f, 590f), 0f)
    }

    @Test fun noDisplacementStaysZero() {
        assertEquals(0f, folderDisplacementDp(0f, 750f, 640f), 0f)
    }
}
