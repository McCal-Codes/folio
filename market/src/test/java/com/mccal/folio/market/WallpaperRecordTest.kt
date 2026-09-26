package com.mccal.folio.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a wallpaper package leaves written down.
 *
 * A record says what a package changed. It is not a second copy of the package, which for a wallpaper would mean
 * carrying the whole picture as base64 at 1.33x its size, beside the copy the host already wrote to disk. So the
 * record keeps the credit and the id and drops the bytes, and the host is what knows whether the picture is still
 * on this phone.
 */
class WallpaperRecordTest {
    private fun store() = InstalledStore(MemoryStore())

    private val change = PackageChange.Wallpaper(
        path = "assets/hills.webp",
        bytes = ByteArray(4096) { it.toByte() },
        id = "dev.example.hills",
        title = "Green Hills",
        artist = "A Painter",
        license = "CC0-1.0",
        detail = "1857. A Museum.",
        source = "https://example.org/hills",
    )

    private val installed = InstalledPackage(
        id = "dev.example.hills",
        version = DebVersion.parse("1.0.0")!!,
        name = "Green Hills",
        origin = InstalledPackage.Origin.FOLIO_SOURCE,
        sourceUrl = "https://example.org/source/",
        installedAt = 0,
        snapshots = listOf("none"),
    )

    @Test fun `the record keeps the credit and drops the picture`() {
        val store = store()
        assertTrue(store.put(installed, listOf(change)))

        val read = store.changesFor(installed.id, installed.version)
        val wallpaper = read?.single() as? PackageChange.Wallpaper

        requireNotNull(wallpaper) { "the change should read back as a wallpaper" }
        assertEquals("the picture is not in the record", 0, wallpaper.bytes.size)
        assertEquals("dev.example.hills", wallpaper.id)
        assertEquals("A Painter", wallpaper.artist)
        assertEquals("CC0-1.0", wallpaper.license)
        assertEquals("1857. A Museum.", wallpaper.detail)
        assertEquals("https://example.org/hills", wallpaper.source)
        assertEquals("assets/hills.webp", wallpaper.path)
        assertTrue("and it still counts as credited", wallpaper.credited)
        assertEquals("but it names the picture, so an older version gets its own back", sha256Hex(change.bytes), wallpaper.pictureSha256)
    }

    @Test fun `a record written before wallpapers carried a credit is not credited`() {
        // The shape an older Folio wrote: a path and the picture, and nothing about who made it. Reading one has to
        // produce something the host will refuse, or a restore could put back a picture Folio cannot say anything
        // about, which is the whole point of DES-2b.
        val store = InstalledStore(MemoryStore())
        val old = PackageChange.Wallpaper(path = "assets/old.webp", bytes = ByteArray(0))
        assertTrue(store.put(installed, listOf(old)))

        val read = store.changesFor(installed.id, installed.version)?.single() as? PackageChange.Wallpaper

        requireNotNull(read)
        assertTrue("no artist and no license", !read.credited)
    }

    @Test fun `the record is small however big the picture was`() {
        val store = MemoryStore()
        val big = change.copy(bytes = ByteArray(2 * 1024 * 1024))
        assertTrue(InstalledStore(store).put(installed, listOf(big)))

        val written = requireNotNull(store.get("installed:changes:${installed.id}@${installed.version}"))

        // Base64 of two megabytes would be about 2.7 million characters. A few hundred is the credit and nothing else.
        assertTrue("a record of ${written.length} characters is carrying the picture", written.length < 1_000)
    }
}
