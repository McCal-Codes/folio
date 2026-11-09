package com.mccal.folio.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The promise the format makes: every v1 package in `resources/corpus/v1` keeps installing, whatever Folio does later.
 *
 * These are packed `.foliopkg` files, so this is the whole path a downloaded package takes — zip, manifest, payload,
 * apply, remove — not just the parsers.
 */
class CompatibilityCorpusTest {
    private val corpus = File(javaClass.getResource("/corpus/v1")!!.toURI())

    private class RecordingHost : PackageHost {
        override val capabilities = Capability.entries.toSet()
        // Every tweak is here too, so an add-on (a page effect needs Flipbook) is held to the format, not to a phone.
        override fun hasTweak(id: String) = true
        val applied = mutableListOf<PackageChange>()
        val restored = mutableListOf<PackageChange>()
        override fun apply(change: PackageChange): String = "before".also { applied += change }
        override fun restore(change: PackageChange, snapshot: String) { restored += change }
    }

    @Test fun `every v1 package installs, applies something, and removes cleanly`() {
        val packages = corpus.listFiles { f -> f.name.endsWith(".foliopkg") }!!.sortedBy { it.name }
        assertTrue("the corpus has packages in it", packages.isNotEmpty())
        for (file in packages) {
            val host = RecordingHost()
            val installer = PackageInstaller(InstalledStore(MemoryStore()), host)
            val result = installer.install(file.readBytes(), origin = InstalledPackage.Origin.FILE)
            assertTrue("${file.name}: $result", result is InstallResult.Installed)
            val installed = (result as InstallResult.Installed).installed
            // The file name says which package and version it holds, so a corpus entry can't quietly change.
            assertEquals(file.name, "${installed.id}_${installed.version}.foliopkg")
            assertTrue("${file.name} changed nothing", host.applied.isNotEmpty())
            assertTrue(installer.remove(installed.id))
            assertEquals("${file.name} didn't undo what it applied", host.applied.size, host.restored.size)
        }
    }

    @Test fun `the corpus covers the kinds Folio can apply today`() {
        val kinds = corpus.listFiles { f -> f.name.endsWith(".foliopkg") }!!.flatMap { file ->
            val host = RecordingHost()
            PackageInstaller(InstalledStore(MemoryStore()), host).install(file.readBytes())
            host.applied.map { it::class.simpleName }
        }
        assertTrue("a tweak bundle is covered", "Tweaks" in kinds)
        assertTrue("a theme is covered", "Theme" in kinds)
        assertTrue("a page effect is covered", "PageEffect" in kinds)
    }
}
