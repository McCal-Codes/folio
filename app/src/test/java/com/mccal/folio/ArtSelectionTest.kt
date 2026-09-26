package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What is behind Home as a wallpaper package comes and goes.
 *
 * Each test drives [ArtSelection] the way the installer drives the host: an install applies, a remove or Safe Mode
 * restores, an update restores the old version and applies the new one, and Undo of an update restores the new one
 * and applies the old one again.
 */
class ArtSelectionTest {
    private val hills = "dev.example.hills"
    private val art = BackgroundChoice.Art(hills)

    private class Memory(override var choice: BackgroundChoice) : ArtSelection.Store {
        override var shownAtRestore: Set<String> = emptySet()
    }

    private fun phone(start: BackgroundChoice = BackgroundChoice.None) = Memory(start).let { it to ArtSelection(it) }

    @Test fun `getting a wallpaper shows it, and removing it puts back what was there`() {
        val (store, selection) = phone(BackgroundChoice.Photo)
        val was = selection.applied(hills, fresh = true)
        assertEquals(art, store.choice)

        selection.restored(hills, was)
        assertEquals(BackgroundChoice.Photo, store.choice)
    }

    @Test fun `removing a wallpaper keeps the background picked since`() {
        val (store, selection) = phone()
        val was = selection.applied(hills, fresh = true)
        store.choice = BackgroundChoice.Photo

        selection.restored(hills, was)
        assertEquals(BackgroundChoice.Photo, store.choice)
    }

    @Test fun `updating a wallpaper the user moved away from leaves their choice alone`() {
        val (store, selection) = phone()
        val v1 = selection.applied(hills, fresh = true)
        store.choice = BackgroundChoice.Photo

        // The update: the old version comes off, the new one goes on.
        selection.restored(hills, v1)
        selection.applied(hills, fresh = false)
        assertEquals(BackgroundChoice.Photo, store.choice)
    }

    @Test fun `updating the wallpaper that is showing keeps it showing, and so does undoing the update`() {
        val (store, selection) = phone(BackgroundChoice.Photo)
        val v1 = selection.applied(hills, fresh = true)

        selection.restored(hills, v1)
        val v2 = selection.applied(hills, fresh = false)
        assertEquals(art, store.choice)

        // Undo: the new version comes off, the old one goes back on.
        selection.restored(hills, v2)
        selection.applied(hills, fresh = false)
        assertEquals(art, store.choice)
        // And removing it after all that still finds the photo that was there before any of it.
        selection.restored(hills, v1)
        assertEquals(BackgroundChoice.Photo, store.choice)
    }

    @Test fun `Safe Mode turning a wallpaper off and back on`() {
        val (store, selection) = phone()
        val was = selection.applied(hills, fresh = true)

        selection.restored(hills, was)
        assertEquals(BackgroundChoice.None, store.choice)
        selection.applied(hills, fresh = false)
        assertEquals("it was showing, so it shows again", art, store.choice)

        store.choice = BackgroundChoice.Photo
        selection.restored(hills, was)
        selection.applied(hills, fresh = false)
        assertEquals("it wasn't, so the photo stays", BackgroundChoice.Photo, store.choice)
    }

    @Test fun `forgotten art is not shown again`() {
        val (store, selection) = phone()
        selection.restored(hills, selection.applied(hills, fresh = true))
        assertTrue(hills in store.shownAtRestore)

        selection.forget(hills)
        assertTrue(store.shownAtRestore.isEmpty())
    }

    @Test fun `art the size Folio ships is taken, and much bigger art is not`() {
        assertNull(BackgroundLibrary.sizeProblem(2448, 3796))
        assertNull(BackgroundLibrary.sizeProblem(2448, 3749))
        assertNotNull("a 6000 px square decodes to 144 MB", BackgroundLibrary.sizeProblem(6000, 6000))
        assertNotNull("too tall even though it is thin", BackgroundLibrary.sizeProblem(1000, 5000))
        assertNotNull("not an image at all", BackgroundLibrary.sizeProblem(-1, -1))
    }

    @Test fun `a picture that got past the check is decoded inside the limits`() {
        assertEquals(1, BackgroundLibrary.sampleSize(2448, 3796))
        for ((w, h) in listOf(6000 to 6000, 20000 to 20000, 1000 to 9000, 5000 to 3000)) {
            val sample = BackgroundLibrary.sampleSize(w, h)
            assertTrue("$w x $h at 1/$sample", BackgroundLibrary.sizeProblem(w / sample, h / sample) == null)
        }
    }
}
