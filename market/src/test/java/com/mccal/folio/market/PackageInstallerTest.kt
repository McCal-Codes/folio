package com.mccal.folio.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The installer, against a real package: `docs/sdk/source/packages/cabinet` packed into a `.foliopkg` the way the
 * publishing tool will. Covers T6 (no code), T7 (zip attacks) and T9 (Safe Mode), plus undo and remove.
 */
class PackageInstallerTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private val cabinetDir = File(root, "docs/sdk/source/packages/cabinet")
    private lateinit var host: FakeHost
    private lateinit var store: InstalledStore
    private lateinit var installer: PackageInstaller
    private var now = 1_789_000_000L

    /** Stands in for the launcher: remembers what was applied, and can be told to fail. */
    private class FakeHost(override val capabilities: Set<Capability> = Capability.entries.toSet()) : PackageHost {
        val applied = mutableListOf<PackageChange>()
        val restored = mutableListOf<PackageChange>()
        var failOn: ((PackageChange) -> Boolean)? = null
        var state = "tweaks off"

        override fun apply(change: PackageChange): String {
            if (failOn?.invoke(change) == true) error("the launcher refused that change")
            applied += change
            val before = state
            state = "applied ${describe(change)}"
            return before
        }

        override fun restore(change: PackageChange, snapshot: String) {
            restored += change
            state = snapshot
        }

        private fun describe(change: PackageChange) = when (change) {
            is PackageChange.Tweaks -> change.bundle.tweaks.joinToString { it.id.id }
            is PackageChange.Theme -> "theme"
            is PackageChange.Layout -> "layout"
            is PackageChange.Wallpaper -> change.path
            is PackageChange.IconPack -> change.packageName
        }
    }

    /** A wallpaper package built from the shipped example's manifest, with whatever images the test wants. */
    private fun wallpaperPackage(images: Map<String, ByteArray>, depiction: String? = null): ByteArray {
        val manifest = File(root, "docs/sdk/examples/wallpaper-wooded-hilly-landscape/manifest.json").readBytes()
        val files = LinkedHashMap<String, ByteArray>()
        files["manifest.json"] = manifest
        if (depiction != null) files["depiction.json"] = depiction.toByteArray()
        files.putAll(images)
        return zip(files)
    }

    private fun wallpaperPath(bytes: ByteArray): String {
        val read = installer.read(bytes)
        assertTrue("$read", read is PackageInstaller.ReadResult.Ok)
        return ((read as PackageInstaller.ReadResult.Ok).pkg.changes.single() as PackageChange.Wallpaper).path
    }

    @Test fun `the wallpaper is the image the page does not spend, not the first one in the zip`() {
        // The hero is listed first and is tiny; the wallpaper is second and large. Zip order used to decide.
        val page = """{"format":1,"blocks":[{"type":"hero","image":"assets/hero.png"},{"type":"screenshots","images":["assets/shot.png"]}]}"""
        val bytes = wallpaperPackage(
            linkedMapOf("assets/hero.png" to ByteArray(300), "assets/shot.png" to ByteArray(200), "assets/picture.webp" to ByteArray(9000)),
            depiction = page,
        )
        assertEquals("assets/picture.webp", wallpaperPath(bytes))
    }

    @Test fun `with no page to spend them, the largest image is the wallpaper`() {
        val bytes = wallpaperPackage(linkedMapOf("assets/small.png" to ByteArray(100), "assets/big.jpg" to ByteArray(5000)))
        assertEquals("assets/big.jpg", wallpaperPath(bytes))
    }

    @Test fun `a wallpaper package whose only images the page spends has no wallpaper`() {
        val page = """{"format":1,"blocks":[{"type":"hero","image":"assets/hero.png"}]}"""
        val read = installer.read(wallpaperPackage(linkedMapOf("assets/hero.png" to ByteArray(300)), depiction = page))
        assertTrue("$read", read is PackageInstaller.ReadResult.Failed)
    }

    @Before fun setUp() {
        host = FakeHost()
        store = InstalledStore(MemoryStore())
        installer = PackageInstaller(store, host, clock = { now })
    }

    /** Packs files into a `.foliopkg`, starting from the real Cabinet package. */
    private fun pack(extra: Map<String, ByteArray> = emptyMap(), replace: Map<String, String> = emptyMap(), omit: Set<String> = emptySet()): ByteArray {
        val files = LinkedHashMap<String, ByteArray>()
        for (name in listOf("manifest.json", "depiction.json", "tweaks.json")) {
            if (name in omit) continue
            var text = File(cabinetDir, name).readText()
            replace.forEach { (from, to) -> text = text.replace(from, to) }
            files[name] = text.toByteArray()
        }
        files += extra
        return zip(files)
    }

    private fun zip(files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in files) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun installed(result: InstallResult): InstallResult.Installed {
        assertTrue("expected an install, got $result", result is InstallResult.Installed)
        return result as InstallResult.Installed
    }

    private fun failure(result: InstallResult): InstallResult.Failed {
        assertTrue("expected a failure, got $result", result is InstallResult.Failed)
        return result as InstallResult.Failed
    }

    @Test fun `installs the real Cabinet package and records what it changed`() {
        val result = installed(installer.install(pack()))
        assertEquals("com.mccal.folio.cabinet", result.installed.id)
        assertEquals("Cabinet", result.installed.name)
        assertEquals(DebVersion.parse("1.0.0"), result.installed.version)
        assertNull(result.replaced)
        assertEquals(emptyList<String>(), result.notes)
        val change = host.applied.single() as PackageChange.Tweaks
        assertEquals(listOf(TweakId.APP_PANELS), change.bundle.tweaks.map { it.id })
        assertTrue(change.bundle.tweaks.single().enabled)
        assertEquals("applied appPanels", host.state)
        assertEquals(listOf("com.mccal.folio.cabinet"), store.installed().map { it.id })
    }

    @Test fun `reading a package doesn't change anything`() {
        val read = installer.read(pack())
        assertTrue(read is PackageInstaller.ReadResult.Ok)
        val pkg = (read as PackageInstaller.ReadResult.Ok).pkg
        assertEquals("Cabinet", pkg.manifest.name.english)
        assertEquals(6, pkg.depiction?.blocks?.size)
        assertTrue(host.applied.isEmpty() && store.installed().isEmpty())
    }

    @Test fun `remove puts back what the package replaced`() {
        installer.install(pack())
        assertTrue(installer.remove("com.mccal.folio.cabinet"))
        assertEquals(1, host.restored.size)
        assertEquals("tweaks off", host.state)
        assertTrue(store.installed().isEmpty())
        assertTrue("removing something that isn't there is not an error", !installer.remove("com.mccal.folio.cabinet"))
    }

    @Test fun `an update keeps the previous version until Undo is dismissed`() {
        installed(installer.install(pack()))
        now += 60
        val update = installed(installer.install(pack(replace = mapOf("\"version\": \"1.0.0\"" to "\"version\": \"1.1.0\""))))
        assertEquals(DebVersion.parse("1.1.0"), update.installed.version)
        assertEquals(DebVersion.parse("1.0.0"), update.replaced?.version)
        // Undo goes back to what was there before.
        assertTrue(installer.undo(update))
        assertEquals(DebVersion.parse("1.0.0"), store.find("com.mccal.folio.cabinet")?.version)
        assertEquals("applied appPanels", host.state)
    }

    @Test fun `updating a package that Safe Mode turned off brings it back on, once`() {
        installer.install(pack())
        installer.disable("com.mccal.folio.cabinet", "crashed")
        assertEquals("nothing of it is on Home", "tweaks off", host.state)
        val undone = host.restored.size

        val update = installed(installer.install(pack(replace = mapOf("\"version\": \"1.0.0\"" to "\"version\": \"1.1.0\""))))
        // The old version's changes were already off; undoing them again would put back what Home looked like
        // before it, over whatever the user has done since.
        assertEquals(undone, host.restored.size)
        assertEquals("applied appPanels", host.state)
        val now = store.find("com.mccal.folio.cabinet")!!
        assertTrue("a new version is the fix you hoped for, so it comes back on", now.enabled)
        assertNull(now.disabledReason)

        // And Undo puts the old one back the way it was: off, with nothing of it on Home.
        assertTrue(installer.undo(update))
        val back = store.find("com.mccal.folio.cabinet")!!
        assertEquals(DebVersion.parse("1.0.0"), back.version)
        assertTrue("still off", !back.enabled)
        assertEquals("tweaks off", host.state)
    }

    @Test fun `Undo with nothing recorded for the previous version keeps the one that works`() {
        installed(installer.install(pack()))
        now += 60
        val update = installed(installer.install(pack(replace = mapOf("\"version\": \"1.0.0\"" to "\"version\": \"1.1.0\""))))
        // A previous version Folio has no record of: undoing to it is impossible, so the new one has to stay put
        // rather than both coming off.
        val unknown = update.copy(replaced = update.replaced!!.copy(version = DebVersion.parse("0.9.0")!!))
        assertFalse(installer.undo(unknown))
        assertEquals(DebVersion.parse("1.1.0"), store.find("com.mccal.folio.cabinet")?.version)
        assertEquals("applied appPanels", host.state)
    }

    @Test fun `a download that doesn't match the source is refused before anything is opened`() {
        val bytes = pack()
        val entry = index(sha256 = "0".repeat(64), size = bytes.size)
        assertEquals(InstallResult.Reason.HASH, failure(installer.install(bytes, entry)).reason)
        assertEquals(InstallResult.Reason.SIZE, failure(installer.install(bytes, index(sha256Hex(bytes), bytes.size + 1))).reason)
        // Right hash, but the file inside says something else.
        val other = index(sha256Hex(bytes), bytes.size, id = "dev.example.other")
        assertEquals(InstallResult.Reason.MISMATCH, failure(installer.install(bytes, other)).reason)
        assertTrue(host.applied.isEmpty())
    }

    @Test fun `a package that isn't what its listing described is refused`() {
        val bytes = pack()
        val real = (installer.read(bytes) as PackageInstaller.ReadResult.Ok).pkg.manifest
        // A mirror describing it as something milder than it is: what was shown is what must be applied.
        val listed = index(sha256Hex(bytes), bytes.size).copy(manifest = real.copy(permissions = emptySet()))
        assertEquals(InstallResult.Reason.MISMATCH, failure(installer.install(bytes, listed)).reason)
        assertTrue(host.applied.isEmpty())
        assertTrue(installer.install(bytes, index(sha256Hex(bytes), bytes.size).copy(manifest = real)) is InstallResult.Installed)
    }

    @Test fun `a package needing a capability this Folio lacks is not applied`() {
        val limited = PackageInstaller(store, FakeHost(capabilities = setOf(Capability.THEME)), clock = { now })
        val result = limited.install(pack())
        assertTrue("$result", result is InstallResult.NeedsNewerFolio)
        assertEquals(listOf("tweaks.appPanels"), (result as InstallResult.NeedsNewerFolio).missing)
    }

    @Test fun `when applying fails, everything already applied goes back`() {
        // Two changes: the theme applies, the tweaks fail.
        val twoKinds = pack(
            extra = mapOf("theme.json" to """{"folioTheme":1,"name":"Test"}""".toByteArray()),
            replace = mapOf("\"kind\": [\n    \"tweakBundle\"\n  ]" to "\"kind\": [\n    \"theme\",\n    \"tweakBundle\"\n  ]"),
        )
        host.failOn = { it is PackageChange.Tweaks }
        val failed = failure(installer.install(twoKinds))
        assertEquals(InstallResult.Reason.APPLY, failed.reason)
        assertEquals(1, host.applied.size)
        assertEquals(1, host.restored.size)
        assertEquals("tweaks off", host.state)
        assertTrue("nothing is recorded as installed", store.installed().isEmpty())
    }

    @Test fun `an update that fails leaves the version that was working in place`() {
        installer.install(pack())
        assertEquals("applied appPanels", host.state)
        // Only the new version's change fails; putting the old one back still works.
        var attempts = 0
        host.failOn = { attempts++ == 0 }
        val failed = failure(installer.install(pack(replace = mapOf("\"version\": \"1.0.0\"" to "\"version\": \"1.1.0\""))))
        assertEquals(InstallResult.Reason.APPLY, failed.reason)
        // The old version is still installed and still applied.
        assertEquals(DebVersion.parse("1.0.0"), store.find("com.mccal.folio.cabinet")?.version)
        host.failOn = null
        assertEquals("applied appPanels", host.state)
        // And it can still be removed cleanly afterwards.
        assertTrue(installer.remove("com.mccal.folio.cabinet"))
        assertEquals("tweaks off", host.state)
    }

    @Test fun `a conflicting package is refused with the name of what it replaces`() {
        installer.install(pack())
        val rival = pack(
            replace = mapOf(
                "\"com.mccal.folio.cabinet\"" to "\"dev.example.panels\"",
                "\"name\": \"Cabinet\"" to "\"name\": \"Panels\",\n  \"conflicts\": [\"com.mccal.folio.cabinet\"]",
            ),
        )
        assertEquals("that package replaces Cabinet", failure(installer.install(rival)).message)
    }

    @Test fun `a package whose dependency isn't installed is refused`() {
        val needsBase = pack(
            replace = mapOf(
                "\"com.mccal.folio.cabinet\"" to "\"dev.example.panels\"",
                "\"name\": \"Cabinet\"" to "\"name\": \"Panels\",\n  \"depends\": [\"com.mccal.folio.cabinet (>= 1.0)\"]",
            ),
        )
        val failed = failure(installer.install(needsBase))
        assertEquals(InstallResult.Reason.DEPENDS, failed.reason)
        assertTrue(failed.message.contains("com.mccal.folio.cabinet (>= 1.0)"))
        assertTrue(host.applied.isEmpty())
        // With the dependency in place it installs.
        installer.install(pack())
        assertTrue(installer.install(needsBase) is InstallResult.Installed)
    }

    @Test fun `T6 a package carrying code is refused`() {
        val withCode = pack(extra = mapOf("classes.dex" to ByteArray(64)))
        assertEquals(InstallResult.Reason.ARCHIVE, failure(installer.install(withCode)).reason)
        assertTrue(failure(installer.install(withCode)).message.contains(".dex"))
    }

    @Test fun `T7 zip attacks are refused`() {
        val cases = mapOf(
            "escapes the package" to zip(mapOf("../../evil.json" to "{}".toByteArray())),
            "absolute path" to zip(mapOf("/etc/passwd.json" to "{}".toByteArray())),
            "windows path" to zip(mapOf("..\\evil.json" to "{}".toByteArray())),
            "no manifest" to zip(mapOf("depiction.json" to "{}".toByteArray())),
            "empty" to ByteArray(0),
            "not a zip" to "this is not a zip file".toByteArray(),
        )
        for ((name, bytes) in cases) {
            assertEquals(name, InstallResult.Reason.ARCHIVE, failure(installer.install(bytes)).reason)
        }
        // A zip bomb: a small file that unpacks to more than the cap.
        val bomb = zip(mapOf("manifest.json" to File(cabinetDir, "manifest.json").readBytes(), "assets/big.png" to ByteArray(PackageArchive.MAX_UNCOMPRESSED + 1)))
        assertTrue(bomb.size < 1024 * 1024)
        assertTrue(failure(installer.install(bomb)).message.contains("unpacks to more than"))
        // Too many files.
        val many = zip((1..PackageArchive.MAX_ENTRIES + 1).associate { "assets/f$it.png" to ByteArray(1) })
        assertTrue(failure(installer.install(many)).message.contains("at most ${PackageArchive.MAX_ENTRIES} files"))
    }

    @Test fun `a broken or mismatched payload is refused`() {
        assertEquals(
            InstallResult.Reason.MANIFEST,
            failure(installer.install(pack(omit = setOf("tweaks.json")))).reason,
        )
        // A broken value is an error...
        assertEquals(
            InstallResult.Reason.MANIFEST,
            failure(installer.install(pack(replace = mapOf("\"enabled\": true" to "\"enabled\": \"yes\"")))).reason,
        )
        // ...but a tweak id or format number this Folio doesn't know is a newer package, not a broken one.
        assertTrue(installer.install(pack(replace = mapOf("\"appPanels\"" to "\"appPanelz\""))) is InstallResult.NeedsNewerFolio)
        assertTrue(installer.install(pack(replace = mapOf("\"format\": 1" to "\"format\": 2"))) is InstallResult.NeedsNewerFolio)
    }

    @Test fun `T9 two crashes while a package is being changed turn that package off`() {
        val keyValue = MemoryStore()
        val safeMode = PackageSafeMode(keyValue) { now }
        assertNull("no change in flight", safeMode.noteCrash())
        safeMode.beginChange("com.mccal.folio.cabinet")
        assertNull("one crash isn't enough", safeMode.noteCrash())
        assertEquals("com.mccal.folio.cabinet", safeMode.noteCrash())
        assertNull("the marker is cleared once it has acted", safeMode.noteCrash())
        // A crash long after the change isn't the package's fault.
        safeMode.beginChange("com.mccal.folio.cabinet")
        now += 300
        assertNull(safeMode.noteCrash())
        // A change that finished cleanly leaves no marker.
        safeMode.beginChange("com.mccal.folio.cabinet")
        safeMode.endChange()
        assertNull(safeMode.noteCrash())
    }

    @Test fun `Safe Mode takes a package's changes off Home, and keeps its settings`() {
        installer.install(pack())
        val applied = host.state
        assertTrue("the package is on Home", applied != "tweaks off")

        assertTrue(installer.disable("com.mccal.folio.cabinet", "Folio crashed twice after this package changed"))
        val off = store.find("com.mccal.folio.cabinet")!!
        assertTrue(!off.enabled)
        assertEquals("Folio crashed twice after this package changed", off.disabledReason)
        // Off means off: marking the record and leaving a theme applied turns nothing off, and Folio would start,
        // crash on the same thing, and say it had already dealt with it.
        assertEquals("tweaks off", host.state)
        // Its settings are still there, so Try Again can put it back without downloading anything.
        assertEquals(1, store.changesFor(off.id, off.version)?.size)
        assertTrue("it's still in the list", store.installed().any { it.id == off.id })
        assertTrue("and turning it off twice is not a thing", !installer.disable(off.id, "again"))
    }

    @Test fun `Try Again puts a package Safe Mode turned off back on`() {
        installer.install(pack())
        val applied = host.state
        installer.disable("com.mccal.folio.cabinet", "crashed")

        assertTrue(installer.enable("com.mccal.folio.cabinet"))
        assertEquals("what it changed is back", applied, host.state)
        val on = store.find("com.mccal.folio.cabinet")!!
        assertTrue(on.enabled)
        assertNull("and the reason goes with it", on.disabledReason)
        assertTrue("one that is already on has nothing to put back", !installer.enable(on.id))
    }

    @Test fun `Try Again that fails leaves the package off rather than half on`() {
        installer.install(pack())
        installer.disable("com.mccal.folio.cabinet", "crashed")
        val off = host.state
        host.failOn = { true }

        assertTrue(!installer.enable("com.mccal.folio.cabinet"))
        assertEquals("Home is where it was", off, host.state)
        assertTrue("and it is still off", store.find("com.mccal.folio.cabinet")?.enabled == false)
    }

    @Test fun `removing a package that is already off doesn't undo its changes twice`() {
        installer.install(pack())
        installer.disable("com.mccal.folio.cabinet", "crashed")
        val off = host.state
        val undone = host.restored.size

        assertTrue(installer.remove("com.mccal.folio.cabinet"))
        // Its changes came off when it was turned off. Undoing them again would put back whatever Home looked like
        // before it, over whatever the user has done since.
        assertEquals(undone, host.restored.size)
        assertEquals(off, host.state)
        assertTrue(store.installed().none { it.id == "com.mccal.folio.cabinet" })
    }

    @Test fun `what a package changed survives a restart`() {
        val keyValue = MemoryStore()
        val first = PackageInstaller(InstalledStore(keyValue), host, clock = { now })
        first.install(pack())
        val later = PackageInstaller(InstalledStore(keyValue), host, clock = { now })
        assertEquals("Cabinet", InstalledStore(keyValue).find("com.mccal.folio.cabinet")?.name)
        assertTrue(later.remove("com.mccal.folio.cabinet"))
        assertEquals("tweaks off", host.state)
    }

    @Test fun `installed packages travel in a layout backup`() {
        installer.install(pack())
        val backup = store.export()
        val fresh = InstalledStore(MemoryStore())
        fresh.restore(fresh.readBackup(backup)!!)
        val restored = fresh.find("com.mccal.folio.cabinet")!!
        assertEquals("Cabinet", restored.name)
        assertEquals(DebVersion.parse("1.0.0"), restored.version)
        // Off, with no snapshots: what this package replaced, it replaced on the phone the backup came from.
        assertFalse(restored.enabled)
        assertEquals(emptyList<String>(), restored.snapshots)
        // What it changed comes back too, so Folio can put the tweak back without downloading anything.
        val change = fresh.changesFor(restored.id, restored.version)?.single() as PackageChange.Tweaks
        assertEquals(listOf(TweakId.APP_PANELS), change.bundle.tweaks.map { it.id })
        assertNull("a backup Folio can't read changes nothing", fresh.readBackup("not a backup"))
        assertNull(fresh.readBackup("""{"format":2,"packages":[]}"""))
        assertEquals("Cabinet", fresh.find("com.mccal.folio.cabinet")?.name)
    }

    private fun index(sha256: String, size: Int, id: String = "com.mccal.folio.cabinet", version: String = "1.0.0") = IndexPackage(
        id = id,
        version = DebVersion.parse(version)!!,
        url = "packages/$id" + "_$version.foliopkg",
        sha256 = sha256,
        size = size,
        provenance = null,
        manifest = null,
    )
}
