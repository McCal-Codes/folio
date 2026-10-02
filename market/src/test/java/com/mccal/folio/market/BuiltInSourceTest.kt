package com.mccal.folio.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The built-in source, read straight from `docs/sdk/source` — the same files the build copies into the app's assets.
 * If a package there stops parsing, or an image a page points at goes missing, this fails before anyone sees it.
 */
class BuiltInSourceTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private val sourceDir = File(root, "docs/sdk/source")

    /** Stands in for the app's assets: `market/source/...` maps onto the folder in the repository. */
    private val source = BuiltInSource(
        read = { path -> File(sourceDir, path.removePrefix(BuiltInSource.ROOT + "/")).takeIf { it.isFile }?.readBytes() },
        list = { path -> File(sourceDir, path.removePrefix(BuiltInSource.ROOT + "/")).list()?.sorted().orEmpty() },
    )

    @Test fun `the bundled index lists every package Folio ships`() {
        val index = requireNotNull(source.index()) { "the bundled index is missing" }
        assertEquals("Folio", index.name.english)
        assertEquals(10, index.packages.size)
        assertTrue("every package can be read", index.packages.all { it.manifest != null && it.needs.isEmpty() })
        assertEquals(6, index.packages.count { it.manifest?.section == Section.TWEAKS })
        assertEquals(4, index.packages.count { it.manifest?.section == Section.THEMES })
        // Built-in packages have nothing to download.
        assertTrue(index.packages.none { it.installable })
    }

    @Test fun `every package's files are there, and its id matches its manifest`() {
        val packages = source.packages()
        assertEquals(10, packages.size)
        assertEquals(source.index()!!.packages.map { it.id }.toSet(), packages.keys)
        for ((id, files) in packages) {
            assertTrue("$id has a manifest", PackageArchive.MANIFEST in files)
            assertTrue("$id has a page", "depiction.json" in files)
            val manifest = (PackageManifest.parse(files.getValue(PackageArchive.MANIFEST).decodeToString()) as ParseResult.Ok).value
            assertEquals(id, manifest.id)
            // A tweak package carries the bundle it applies.
            if (manifest.kinds.contains(PackageKind.TWEAK_BUNDLE)) assertTrue("$id has tweaks.json", "tweaks.json" in files)
        }
    }

    @Test fun `every image the source points at exists`() {
        val index = source.index()!!
        val wanted = buildList {
            index.icon?.let(::add)
            index.featured.mapNotNull { it.image }.forEach(::add)
            for (files in source.packages().values) {
                val page = files["depiction.json"]?.decodeToString()?.let { Depiction.parse(it) as? ParseResult.Ok }?.value ?: continue
                for (block in page.blocks) when (block) {
                    is DepictionBlock.Hero -> add(block.image)
                    is DepictionBlock.Screenshots -> addAll(block.images)
                    else -> Unit
                }
            }
        }
        assertTrue("the source points at some images", wanted.isNotEmpty())
        for (path in wanted) assertNotNull("missing image: $path", source.asset(path))
    }

    @Test fun `a built-in package installs without a download`() {
        val host = object : PackageHost {
            override val capabilities = Capability.entries.toSet()
            val applied = mutableListOf<PackageChange>()
            override fun apply(change: PackageChange): String = "before".also { applied += change }
            override fun restore(change: PackageChange, snapshot: String) = Unit
        }
        val installer = PackageInstaller(InstalledStore(MemoryStore()), host)
        val files = requireNotNull(source.filesFor("com.mccal.folio.cabinet"))
        val result = installer.installBuiltIn(files)
        assertTrue("$result", result is InstallResult.Installed)
        assertEquals("Cabinet", (result as InstallResult.Installed).installed.name)
        assertEquals(InstalledPackage.Origin.FOLIO_SOURCE, result.installed.origin)
        assertEquals(listOf(TweakId.APP_PANELS), (host.applied.single() as PackageChange.Tweaks).bundle.tweaks.map { it.id })
    }

    @Test fun `the bundled revocation list is empty, and paths that climb out are refused`() {
        val revocations = requireNotNull(source.revocations())
        assertTrue(revocations.packages.isEmpty())
        assertNull(source.asset("../../../etc/passwd"))
        assertNull(source.asset("/etc/passwd"))
    }
}
