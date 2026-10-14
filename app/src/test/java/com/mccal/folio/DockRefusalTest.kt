package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The reason the dock's app picker gives for an app it greys out, and that it agrees with [canPlaceInDock]. */
class DockRefusalTest {
    private val social = FolderEntry("folder:00000000-0000-0000-0000-000000000001", "Social", listOf("chat", "photos"))

    @Test fun `an app in a folder says which folder`() {
        val layout = HomeLayout(listOf("home"), listOf("a", null), folders = listOf(social))
        assertEquals(DockRefusal.InFolder(social.id), dockRefusal(layout, "chat"))
    }

    @Test fun `the folder reason wins over a full dock`() {
        val layout = HomeLayout(listOf("home"), listOf("a", "b", "c", "d", "e", "f"), folders = listOf(social))
        assertEquals(DockRefusal.InFolder(social.id), dockRefusal(layout, "photos"))
    }

    @Test fun `a full dock says full`() {
        val layout = HomeLayout(listOf("home"), listOf("a", "b", "c", "d", "e", "f"))
        assertEquals(DockRefusal.Full, dockRefusal(layout, "new"))
    }

    @Test fun `room in the dock refuses nothing`() {
        assertNull(dockRefusal(HomeLayout(listOf("home"), listOf("a", null)), "new"))
        assertNull(dockRefusal(HomeLayout(listOf("home"), listOf("a", "b")), "new"))
        // Already in a full dock: choosing it again just moves it.
        assertNull(dockRefusal(HomeLayout(listOf("home"), listOf("a", "b", "c", "d", "e", "f")), "c"))
    }

    @Test fun `blanks and folders are not apps`() {
        val layout = HomeLayout(listOf("home"), listOf("a", null), folders = listOf(social))
        assertEquals(DockRefusal.NotAnApp, dockRefusal(layout, ""))
        assertEquals(DockRefusal.NotAnApp, dockRefusal(layout, " "))
        assertEquals(DockRefusal.NotAnApp, dockRefusal(layout, social.id))
    }

    @Test fun `agrees with canPlaceInDock`() {
        val layouts = listOf(
            HomeLayout(listOf("home"), listOf("a", null), folders = listOf(social)),
            HomeLayout(listOf("home"), listOf("a", "b", "c", "d", "e", "f"), folders = listOf(social)),
            HomeLayout(listOf("home"), emptyList()),
            HomeLayout(listOf("home"), listOf("a", "b", "c", "d", "e", "f", "g")),
        )
        val ids = listOf("", " ", "a", "c", "g", "new", "chat", "photos", "home", social.id)
        for (layout in layouts) for (id in ids)
            assertEquals("$id in ${layout.dock}", canPlaceInDock(layout, id), dockRefusal(layout, id) == null)
    }
}
