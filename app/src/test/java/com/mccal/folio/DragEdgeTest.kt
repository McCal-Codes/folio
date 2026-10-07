package com.mccal.folio

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class DragEdgeTest {
    @Test fun `screen edges work beyond the pager and dock centers stay neutral`() {
        val cover = Rect(0f, 0f, 1248f, 1972f)
        val band = 30f * 2.625f
        assertEquals(1, dragEdgeDirection(Offset(1236f, 920f), cover, band))
        assertEquals(-1, dragEdgeDirection(Offset(12f, 920f), cover, band))
        assertEquals(0, dragEdgeDirection(Offset(1119f, 920f), cover, band))
        assertEquals(0, dragEdgeDirection(Offset(970f, 920f), cover, band))
        val inner = Rect(0f, 0f, 2448f, 1848f)
        assertEquals(1, dragEdgeDirection(Offset(2436f, 920f), inner, band))
        assertEquals(0, dragEdgeDirection(Offset(2319f, 920f), inner, band))
    }
    @Test fun `edge regions follow window origin and exclude touches outside the window`() {
        val window = Rect(30f, 100f, 1030f, 1800f)
        assertEquals(-1, dragEdgeDirection(Offset(35f, 800f), window, 60f))
        assertEquals(1, dragEdgeDirection(Offset(1020f, 800f), window, 60f))
        assertEquals(0, dragEdgeDirection(Offset(1020f, 90f), window, 60f))
        assertEquals(0, dragEdgeDirection(Offset(1020f, 1801f), window, 60f))
        assertEquals(0, dragEdgeDirection(Offset(1031f, 800f), window, 60f))
        assertEquals(0, dragEdgeDirection(Offset.Zero, Rect.Zero, 60f))
    }
}

class FolderDragTest {
    private val folder = newFolderId()
    private fun state() = HomeDragState().apply {
        // The dragged folder's tile sits under the finger with its own folder target, over an empty Home cell.
        register("cell", DragRegion(DropTarget.Home(5), Rect(0f, 0f, 100f, 100f), null, 0))
        register("tile", DragRegion(DropTarget.Folder(folder), Rect(0f, 0f, 100f, 100f), null, 0, folderId = folder))
    }

    @Test fun `a dragged folder lands on the Home cell, not on itself`() {
        val drag = state().apply { source = DragRegion(DropTarget.Home(21), Rect.Zero, folder, 0) }
        assertEquals(DropTarget.Home(5), drag.destination(Offset(50f, 50f), setOf(0))?.target)
    }

    @Test fun `an app from the dock, the App Library or another page drops into a folder that already exists`() {
        for (source in listOf(
            DragRegion(DropTarget.Dock(1), Rect.Zero, "pkg/.Dock", null),
            DragRegion(DropTarget.Library("pkg/.Lib"), Rect.Zero, "pkg/.Lib", null),
            DragRegion(DropTarget.Home(40), Rect.Zero, "pkg/.Far", 1),
        )) {
            val drag = state().apply { this.source = source }
            assertEquals(DropTarget.Folder(folder), drag.destination(Offset(50f, 50f), setOf(0))?.target)
        }
    }

    @Test fun `an app dragged out of one folder drops into another`() {
        val other = newFolderId()
        val drag = HomeDragState().apply {
            register("cell", DragRegion(DropTarget.Home(9), Rect(0f, 0f, 100f, 100f), null, 0))
            register("tile", DragRegion(DropTarget.Folder(other), Rect(0f, 0f, 100f, 100f), null, 0, folderId = other))
            source = DragRegion(DropTarget.Library("pkg/.InFolder"), Rect.Zero, "pkg/.InFolder", 0, folderId = folder)
        }
        assertEquals(DropTarget.Folder(other), drag.destination(Offset(50f, 50f), setOf(0))?.target)
    }

    @Test fun `an app carried out of an open folder lands on the Home cell under the finger or the dock slot`() {
        val drag = HomeDragState().apply {
            register("cell", DragRegion(DropTarget.Home(9), Rect(0f, 0f, 100f, 100f), null, 0))
            register("dock", DragRegion(DropTarget.Dock(2), Rect(200f, 0f, 300f, 100f), null, null))
            // The same kind of source the open folder hands over: the app, with the folder it is leaving.
            source = DragRegion(DropTarget.Library("pkg/.InFolder"), Rect.Zero, "pkg/.InFolder", 0, folderId = folder, scope = folder)
        }
        assertEquals(DropTarget.Home(9), drag.destination(Offset(50f, 50f), setOf(0))?.target)
        assertEquals(DropTarget.Dock(2), drag.destination(Offset(250f, 50f), setOf(0))?.target)
        assertEquals(null, drag.destination(Offset(500f, 500f), setOf(0)))
    }

    @Test fun `an app still drops into a folder`() {
        val drag = state().apply { source = DragRegion(DropTarget.Home(3), Rect.Zero, "pkg/.App", 0) }
        assertEquals(DropTarget.Folder(folder), drag.destination(Offset(50f, 50f), setOf(0))?.target)
    }
}
