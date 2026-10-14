package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where a badge sits on an icon, and how an app inside an open folder keeps its badge clear of the "..." button (#362). */
class BadgePlacementTest {
    private val count = 58f * .34f // a count badge on a 58 dp folder icon (IconBadge's height)
    private val dot = 58f * .22f

    @Test fun defaultIsTheOldTopRightOverhang() {
        val p = BadgePlacement.DEFAULT
        assertEquals(BadgeCorner.TOP_END, p.corner)
        assertEquals(.32f * count, p.offsetX(58f, count), 1e-4f)
        assertEquals(-.32f * count, p.offsetY(count), 1e-4f)
    }

    @Test fun topStartHangsOutToTheLeft() {
        val p = BadgePlacement(BadgeCorner.TOP_START)
        assertEquals(-.32f * count, p.offsetX(58f, count), 1e-4f)
        assertEquals(-.32f * count, p.offsetY(count), 1e-4f)
    }

    @Test fun topEndStopsAtTheLimit() {
        val p = BadgePlacement(BadgeCorner.TOP_END, endLimit = 35f)
        val right = 58f + p.offsetX(58f, count)
        assertEquals(35f, right, 1e-4f)
    }

    @Test fun aFarLimitChangesNothing() {
        val p = BadgePlacement(BadgeCorner.TOP_END, endLimit = 200f)
        assertEquals(.32f * dot, p.offsetX(58f, dot), 1e-4f)
    }

    @Test fun folderLimitIsTheButtonsLeftEdge() {
        // An 84 dp cell (the grid's narrowest adaptive cell): the icon starts at 13, the button at 48, so 35 from the icon.
        assertEquals(35f, FolderBadge.endLimit(84f), 1e-4f)
        // Wider cells leave more room: 120 dp puts the button at 84 and the icon at 31.
        assertEquals(53f, FolderBadge.endLimit(120f), 1e-4f)
    }

    @Test fun aTopRightFolderBadgeNeverReachesTheButton() {
        for (cell in listOf(76f, 84f, 96f, 120f, 160f)) {
            val p = BadgePlacement(BadgeCorner.TOP_END, FolderBadge.endLimit(cell))
            for (h in listOf(dot, count, count * 1.3f)) {
                val iconLeft = (cell - FolderBadge.ICON) / 2f
                val badgeRight = iconLeft + FolderBadge.ICON + p.offsetX(FolderBadge.ICON, h)
                assertTrue("cell $cell, badge $h", badgeRight <= cell - FolderBadge.BUTTON + 1e-3f)
            }
        }
    }

    @Test fun aTopLeftFolderBadgeStaysInsideTheCell() {
        val p = BadgePlacement(BadgeCorner.TOP_START)
        for (cell in listOf(76f, 84f, 120f)) {
            val iconLeft = (cell - FolderBadge.ICON) / 2f
            assertTrue("cell $cell", iconLeft + p.offsetX(FolderBadge.ICON, count * 1.3f) >= 0f)
        }
    }
}
