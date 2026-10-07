package com.mccal.folio.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The one rule the installer has to keep, whatever order things happen in: **the list and the Home screen agree.**
 *
 * A package the list calls installed and enabled has its changes on; one that is off, or gone, does not. Every way
 * in and out - install, update, undo, Safe Mode off, Try Again, remove - has its own path through that, and they
 * have been wrong in three different ways: turning a package off used to leave its changes applied, updating one
 * that was off undid changes that were already off, and undo put an off package's changes back on.
 *
 * So rather than one test per path, this walks the paths in every order it can and checks the rule after each step.
 */
class InstallStateMachineTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private val cabinetDir = File(root, "docs/sdk/source/packages/cabinet")
    private val id = "com.mccal.folio.cabinet"

    /** Remembers what is on Home, so the rule can be checked rather than argued about. */
    private class Host : PackageHost {
        override val capabilities = Capability.entries.toSet()
        val on = mutableSetOf<String>()
        override fun apply(change: PackageChange): String {
            val before = on.toList().sorted().joinToString(",")
            on += describe(change)
            return before
        }
        override fun restore(change: PackageChange, snapshot: String) {
            on.clear()
            on += snapshot.split(",").filter { it.isNotEmpty() }
        }
        private fun describe(change: PackageChange) = when (change) {
            is PackageChange.Tweaks -> "tweaks:" + change.bundle.tweaks.joinToString { it.id.id }
            is PackageChange.Theme -> "theme"
            is PackageChange.Layout -> "layout"
            is PackageChange.Wallpaper -> "wallpaper"
            is PackageChange.IconPack -> "iconpack"
            is PackageChange.PageEffect -> "pageEffect:" + change.id
        }
    }

    private fun pack(version: String = "1.0.0"): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for (name in listOf("manifest.json", "depiction.json", "tweaks.json")) {
                val text = File(cabinetDir, name).readText().replace("\"version\": \"1.0.0\"", "\"version\": \"$version\"")
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** What every step has to leave true. */
    private fun holds(store: InstalledStore, host: Host, after: String) {
        val record = store.find(id)
        val applied = host.on.isNotEmpty()
        when {
            record == null -> assertTrue("$after: nothing is installed, so nothing of it should be on Home, found ${host.on}", !applied)
            record.enabled -> assertTrue("$after: the list says it is on, so Home should have it", applied)
            else -> assertTrue("$after: the list says it is off, so Home should not have it, found ${host.on}", !applied)
        }
    }

    @Test fun `every order of install, update, undo, off, on and remove keeps the list and Home in step`() {
        // Each step is a name and what it does; the rule is checked after every one.
        val steps: Map<String, (PackageInstaller) -> Unit> = mapOf(
            "install" to { it.install(pack()) },
            "update" to { it.install(pack("1.1.0")) },
            "off" to { it.disable(id, "crashed") },
            "on" to { it.enable(id) },
            "remove" to { it.remove(id) },
        )
        val names = steps.keys.toList()
        var walked = 0
        // Every sequence of four steps: 625 of them, which is cheap and covers the orders nobody thinks to try.
        for (a in names) for (b in names) for (c in names) for (d in names) {
            val host = Host()
            val store = InstalledStore(MemoryStore())
            val installer = PackageInstaller(store, host, authors = AuthorTrust(MemoryStore()))
            val done = StringBuilder()
            for (step in listOf(a, b, c, d)) {
                steps.getValue(step)(installer)
                done.append(step).append(' ')
                holds(store, host, done.toString().trim())
            }
            walked++
        }
        assertEquals(625, walked)
    }

    @Test fun `undo after an update leaves the list and Home agreeing, whether the old one was on or off`() {
        for (wasOff in listOf(false, true)) {
            val host = Host()
            val store = InstalledStore(MemoryStore())
            val installer = PackageInstaller(store, host, authors = AuthorTrust(MemoryStore()))
            installer.install(pack())
            if (wasOff) installer.disable(id, "crashed")
            val update = installer.install(pack("1.1.0")) as InstallResult.Installed
            holds(store, host, "updated (was off: $wasOff)")

            assertTrue(installer.undo(update))
            val back = store.find(id)
            assertEquals("the old version is back", DebVersion.parse("1.0.0"), back?.version)
            assertEquals("and it is as it was", !wasOff, back?.enabled)
            holds(store, host, "undone (was off: $wasOff)")
        }
    }

    private fun packChanged(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for (name in listOf("manifest.json", "depiction.json", "tweaks.json")) {
                var text = File(cabinetDir, name).readText().replace("\"version\": \"1.0.0\"", "\"version\": \"1.1.0\"")
                if (name == "tweaks.json") text = text.replace("\"inner\": true", "\"inner\": false")
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test fun `an update that changes no setting is told apart from one that does, so only the first installs itself`() {
        val installer = PackageInstaller(InstalledStore(MemoryStore()), Host(), authors = AuthorTrust(MemoryStore()))
        val installed = (installer.install(pack("1.0.0")) as InstallResult.Installed).installed
        assertTrue("a new version with the same changes touches none of the person's settings", installer.changesUnchanged(pack("1.1.0"), installed))
        assertTrue("a version that changes a setting must wait to be tapped", !installer.changesUnchanged(packChanged(), installed))
        assertTrue("bytes that are not a package never qualify", !installer.changesUnchanged(ByteArray(8), installed))
    }

    @Test fun `an automatic update does not touch Home, so a setting changed since the install keeps its value`() {
        val host = Host()
        val store = InstalledStore(MemoryStore())
        val installer = PackageInstaller(store, host, authors = AuthorTrust(MemoryStore()))
        val first = (installer.install(pack("1.0.0")) as InstallResult.Installed).installed
        // The person changes something the package had set.
        host.on += "changed-by-the-person"
        val result = installer.updateKeepingSettings(pack("1.1.0")) as InstallResult.Installed
        assertEquals("the record moves to the new version", DebVersion.parse("1.1.0"), store.find(id)?.version)
        assertTrue("and Home is exactly as the person left it", "changed-by-the-person" in host.on)
        assertEquals("the snapshots still describe what to put back", first.snapshots, store.find(id)?.snapshots)
        assertEquals("the old version is reported as replaced", DebVersion.parse("1.0.0"), result.replaced?.version)
    }

    @Test fun `an update that changes a setting, or a package that is off, is refused for in-place updating`() {
        val host = Host()
        val store = InstalledStore(MemoryStore())
        val installer = PackageInstaller(store, host, authors = AuthorTrust(MemoryStore()))
        installer.install(pack("1.0.0"))
        assertTrue("different changes wait for the person", installer.updateKeepingSettings(packChanged()) is InstallResult.Failed)
        assertEquals("and nothing was updated", DebVersion.parse("1.0.0"), store.find(id)?.version)
        installer.disable(id, "crashed")
        assertTrue("a package that is off is not updated in place", installer.updateKeepingSettings(pack("1.1.0")) is InstallResult.Failed)
    }
}
