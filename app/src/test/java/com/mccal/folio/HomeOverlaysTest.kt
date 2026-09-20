package com.mccal.folio

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What is open over Home is saved, so a fold or a rotation doesn't close a folder or a long-press menu. Eight
 * fields go into one list and have to come back in the same order, which is the kind of thing that silently
 * swaps two of them.
 */
class HomeOverlaysTest {
    private val everything = HomeOverlays(
        menu = "menu-app", rename = "rename-app", panel = "panel-app", stackFan = "fan-anchor",
        stackEditor = "editor-anchor", folder = "folder-7", newFolder = "first-app", emptyCell = 12)

    private fun roundTrip(overlays: HomeOverlays): HomeOverlays {
        val saved = with(HomeOverlays.Saver) { SaverScope { true }.save(overlays) }
        return HomeOverlays.Saver.restore(requireNotNull(saved))!!
    }

    @Test fun `every overlay comes back as itself`() {
        val back = roundTrip(everything)
        assertEquals("menu-app", back.menu)
        assertEquals("rename-app", back.rename)
        assertEquals("panel-app", back.panel)
        assertEquals("fan-anchor", back.stackFan)
        assertEquals("editor-anchor", back.stackEditor)
        assertEquals("folder-7", back.folder)
        assertEquals("first-app", back.newFolder)
        assertEquals(12, back.emptyCell)
    }

    @Test fun `nothing open stays nothing open`() {
        val back = roundTrip(HomeOverlays())
        assertEquals(listOf(null, null, null, null, null, null, null, null),
            listOf(back.menu, back.rename, back.panel, back.stackFan, back.stackEditor, back.folder,
                back.newFolder, back.emptyCell))
    }

    @Test fun `one open overlay doesn't drag the others back with it`() {
        val back = roundTrip(HomeOverlays(folder = "folder-3"))
        assertEquals("folder-3", back.folder)
        assertEquals(null, back.menu)
        assertEquals(null, back.emptyCell)
    }
}
