package com.mccal.folio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

/**
 * Which picture a wallpaper package's record gets back.
 *
 * A record keeps a picture's hash, not the picture, and an id has one file. So an update puts the picture it
 * replaces aside, and putting the older version back asks for that picture by hash. Turning a package off moves no
 * files, so turning it back on finds its own picture still in place.
 */
class BackgroundKeptArtTest {
    @get:Rule val folder = TemporaryFolder()

    private val id = "dev.example.hills"
    private val v1 = "version one".toByteArray()
    private val v2 = "version two".toByteArray()
    private val v3 = "version three".toByteArray()

    private fun sha(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** What the host does with a package's picture: keep what was there, write the new one. */
    private fun install(bytes: ByteArray) {
        BackgroundLibrary.keepCurrent(folder.root, id)
        BackgroundLibrary.artFile(folder.root, id).writeBytes(bytes)
    }

    private fun inPlace() = BackgroundLibrary.artFile(folder.root, id).readBytes()

    @Test fun `undoing an update brings back the picture the older version had`() {
        install(v1)
        install(v2)

        assertTrue(BackgroundLibrary.bringBack(folder.root, id, sha(v1)))
        assertArrayEquals(v1, inPlace())
    }

    @Test fun `a package turned back on keeps its own picture`() {
        install(v1)
        install(v2)

        // Turning v2 off moves nothing, so turning it back on asks for v2 and finds it in place.
        assertTrue(BackgroundLibrary.bringBack(folder.root, id, sha(v2)))
        assertArrayEquals(v2, inPlace())
    }

    @Test fun `undoing the second of two updates brings back the first update, not the original`() {
        install(v1)
        install(v2)
        install(v3)

        assertTrue(BackgroundLibrary.bringBack(folder.root, id, sha(v2)))
        assertArrayEquals(v2, inPlace())
        // And the one it replaced is kept in turn, so the update can be applied again from its own record.
        assertTrue(BackgroundLibrary.bringBack(folder.root, id, sha(v3)))
        assertArrayEquals(v3, inPlace())
    }

    @Test fun `a picture this phone never had is not there`() {
        install(v1)

        assertFalse(BackgroundLibrary.bringBack(folder.root, id, sha("someone else's".toByteArray())))
        assertArrayEquals("and the picture in place is left alone", v1, inPlace())
    }

    @Test fun `one kept picture per id, and another id's are left alone`() {
        install(v1)
        install(v2)
        install(v3)
        val other = "dev.example.hills.night"
        BackgroundLibrary.keepCurrent(folder.root, other)
        BackgroundLibrary.artFile(folder.root, other).writeBytes(v1)
        BackgroundLibrary.keepCurrent(folder.root, other)

        val kept = folder.root.list().orEmpty().filter { it.endsWith(".kept.img") }
        assertEquals(listOf("$id.${sha(v2)}.kept.img", "$other.${sha(v1)}.kept.img").sorted(), kept.sorted())
    }
}
