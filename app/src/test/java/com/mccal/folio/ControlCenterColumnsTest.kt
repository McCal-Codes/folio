package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Control Center keeps four columns unless a wide, short window would squeeze them; then it uses six. */
class ControlCenterColumnsTest {
    private val sizeCell = 66f

    @Test fun `a tall window keeps four columns`() {
        assertEquals(4, controlCenterColumns(460f, 900f, 8, false, sizeCell))
        assertEquals(4, controlCenterColumns(560f, 700f, 8, true, sizeCell))
    }

    @Test fun `a panel with no height limit keeps four columns`() {
        assertEquals(4, controlCenterColumns(560f, null, 12, true, sizeCell))
    }

    @Test fun `a wide short window uses six columns and its cells get bigger`() {
        assertEquals(6, controlCenterColumns(560f, 300f, 8, false, sizeCell))
        val four = controlCenterCell(4, 560f, 300f, 8, false, sizeCell)
        val six = controlCenterCell(6, 560f, 300f, 8, false, sizeCell)
        assertTrue("four $four, six $six", six >= four + 6f)
    }

    @Test fun `six columns need the gain, so a window that is barely short stays at four`() {
        // About 2 dp short of fitting four comfortable rows: six would give almost the same cell.
        val barely = (60f + 12f * 5 + 66f * 6) - 2f
        assertEquals(4, controlCenterColumns(560f, barely, 8, false, sizeCell))
    }

    @Test fun `six columns need one row fewer for the same controls`() {
        assertEquals(6, controlCenterRows(4, 8, false))
        assertEquals(5, controlCenterRows(6, 8, false))
        assertEquals(7, controlCenterRows(4, 8, true))
        assertEquals(6, controlCenterRows(6, 8, true))
        assertEquals(3, controlCenterRows(6, 0, false))
        assertEquals(5, controlCenterRows(4, 0, false))
    }
}
