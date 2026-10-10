package com.mccal.folio

import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Home and the App Library sit centered (a report on 0.6.8: off-center in Full-Width Home, more so with smaller icons
 * or a smaller Space between columns). The grid's columns are centered in the grid, the grid in its page, and the
 * library keeps the same margin on both sides.
 */
class HomeCenteringTest {
    private val screens = listOf(475f to 751f, 411f to 891f, 360f to 780f)

    /** Where the middle of the four columns lands, from the middle of a page centered the way Home centers it. */
    private fun gridOffset(g: HomeGeometry) =
        HOME_OPTIONS_MARGIN / 2f + HOME_PANE_SHIFT + g.columnsInset + 2f * g.cellWidth - g.gridWidth / 2f

    @Test fun `the columns are centered whatever the icon size and the space between columns, with or without the Side Bar`() {
        for ((w, h) in screens) for (fullWidth in listOf(true, false)) for (icon in listOf(68f, 56f, 44f)) for (gap in listOf(40f, 16f, 12f, 8f)) {
            val p = LayoutPreset(iconSize = icon, columnGap = gap, dockPlacement = if (fullWidth) DockPlacement.BOTTOM else DockPlacement.AUTOMATIC)
            val g = homeGeometry(w, h, p, labels = true, statusRail = !fullWidth, statusHeight = if (fullWidth) 0f else 160f)
            val case = "${w}x$h fullWidth=$fullWidth icon=$icon gap=$gap"
            assertEquals(case, g.gridWidth, 2f * g.columnsInset + 4f * g.cellWidth, .01f)
            assertEquals(case, 0f, gridOffset(g), .01f)
        }
    }

    @Test fun `two columns keep their own fit and need no inset`() {
        val g = homeGeometry(751f, 475f, LayoutPreset(columnGap = 8f), labels = true, statusRail = true, statusHeight = 160f)
        assertEquals(true, g.splitColumns)
        assertEquals(0f, g.columnsInset)
    }

    @Test fun `the App Library keeps the same margin on both sides, and room for the status capsule where it sits`() {
        fun sides(statusInCorner: Boolean, leftHanded: Boolean) = libraryEdges(statusInCorner, 68f, leftHanded).let {
            it.calculateStartPadding(LayoutDirection.Ltr) to it.calculateEndPadding(LayoutDirection.Ltr)
        }
        // Full-Width Home and the Side Bar on the side: no capsule in the corner.
        assertEquals(16.dp to 16.dp, sides(statusInCorner = false, leftHanded = false))
        assertEquals(16.dp to 16.dp, sides(statusInCorner = false, leftHanded = true))
        // The capsule in the top corner: its side keeps room for it, the other side Home's 16dp.
        assertEquals(16.dp to 96.dp, sides(statusInCorner = true, leftHanded = false))
        assertEquals(96.dp to 16.dp, sides(statusInCorner = true, leftHanded = true))
    }
}
