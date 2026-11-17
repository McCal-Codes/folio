package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The minus on a folder's corner while icons wiggle: the folder leaves Home, its apps do not leave the phone. */
class FolderRemovalTest {
    private val folderId = "folder:123e4567-e89b-12d3-a456-426614174000"
    private val folder = FolderEntry(folderId, "Daily", listOf("mail", "maps", "notes"))
    private val layout = HomeLayout(listOf("camera", folderId, "clock"), listOf("phone", null, null, null), folders = listOf(folder))

    @Test fun removingAFolderEmptiesItsCellAndDropsTheFolder() {
        val next = removeFolder(layout, folderId)
        assertEquals(listOf("camera", null, "clock"), next.slots)
        assertTrue(next.folders.isEmpty())
        assertNull(next.indexOfShortcut(folderId))
    }

    @Test fun otherShortcutsAndTheDockStayWhereTheyWere() {
        val next = removeFolder(layout, folderId)
        assertEquals(layout.dock, next.dock)
        assertEquals("camera", next.slotAt(0))
        assertEquals("clock", next.slotAt(2))
    }

    @Test fun anUnknownFolderChangesNothing() {
        assertSame(layout, removeFolder(layout, "folder:00000000-0000-0000-0000-000000000000"))
    }

    @Test fun theMinusOnAFolderCellRemovesTheFolder() {
        val next = removePlacement(layout, DropTarget.Home(1))
        assertTrue(next.folders.isEmpty())
        assertNull(next.slotAt(1))
    }

    @Test fun theMinusOnAnAppCellStillOnlyClearsThatCell() {
        val next = removePlacement(layout, DropTarget.Home(0))
        assertNull(next.slotAt(0))
        assertEquals(listOf(folder), next.folders)
        assertEquals(folderId, next.slotAt(1))
    }

    @Test fun removingAFolderTwiceIsTheSameAsOnce() {
        val once = removeFolder(layout, folderId)
        assertEquals(once, removeFolder(once, folderId))
    }
}
