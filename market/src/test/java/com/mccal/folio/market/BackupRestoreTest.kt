package com.mccal.folio.market

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** What a change looks like on Home, for both the fake launcher and the checks against it. */
private fun describe(change: PackageChange) = when (change) {
    is PackageChange.Tweaks -> "tweaks:" + change.bundle.tweaks.joinToString { it.id.id }
    is PackageChange.Theme -> "theme"
    is PackageChange.Layout -> "layout"
    is PackageChange.Wallpaper -> "wallpaper"
    is PackageChange.IconPack -> "iconpack"
    is PackageChange.PageEffect -> "pageEffect:" + change.id
}

/**
 * A layout backup carries the packages the Market installed, and restoring it on another phone puts them back.
 *
 * The rule from [InstallStateMachineTest] has to survive the trip: on the new phone too, a package the list calls
 * enabled has its changes on Home, and one that is off does not. What makes that worth its own test is that the
 * snapshots in a backup were taken on the *old* phone - so nothing can be replayed from them, and everything has to
 * come from the recorded changes, applied again from where the new phone is.
 */
class BackupRestoreTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private val cabinet = "com.mccal.folio.cabinet"
    private val darkTheme = "com.mccal.folio.theme.dark"
    private val offReason = "Folio couldn't put this one back."

    /** Remembers what is on Home, and in what order it went on and off, so the ordering can be checked. */
    private class Host(override val capabilities: Set<Capability> = Capability.entries.toSet()) : PackageHost {
        val on = mutableSetOf<String>()
        val log = mutableListOf<String>()
        override fun apply(change: PackageChange): String {
            val before = on.toList().sorted().joinToString(",")
            on += describe(change)
            log += "+${describe(change)}"
            return before
        }
        override fun restore(change: PackageChange, snapshot: String) {
            on.clear()
            on += snapshot.split(",").filter { it.isNotEmpty() }
            log += "-${describe(change)}"
        }
    }

    /** A phone: its store, what is on its Home, and the installer between them. */
    private class Phone(val host: Host = Host()) {
        val store = InstalledStore(MemoryStore())
        val installer = PackageInstaller(store, host, authors = AuthorTrust(MemoryStore()))
    }

    private fun pack(name: String = "cabinet", version: String = "1.0.0"): ByteArray {
        val dir = File(root, "docs/sdk/source/packages/$name")
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for (file in dir.listFiles().orEmpty().filter { it.isFile }) {
                val text = file.readText().replace("\"version\": \"1.0.0\"", "\"version\": \"$version\"")
                zip.putNextEntry(ZipEntry(file.name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** The rule: the list and Home agree about every package. */
    private fun holds(phone: Phone, after: String) {
        val expected = phone.store.installed().filter { it.enabled }.flatMap {
            phone.store.changesFor(it.id, it.version).orEmpty().map(::describe)
        }.toSet()
        assertEquals("$after: the list and Home should agree", expected, phone.host.on.toSet())
    }


    @Test fun `a package that was on comes back on, from its changes and not from its snapshots`() {
        val old = Phone()
        old.installer.install(pack())
        val backup = old.store.export()
        // The snapshots in that backup describe the old phone. Nothing may be replayed from them, so the new phone
        // starts somewhere else entirely and still has to end up right.
        val new = Phone()
        new.host.on += "something the old phone never saw"
        val restored = new.installer.restoreBackup(backup, offReason) {}!!
        assertEquals(listOf(cabinet), restored.on.map { it.id })
        assertEquals(emptyList<InstalledPackage>(), restored.failed)
        assertTrue(new.store.find(cabinet)!!.enabled)
        // Applied here, over what was really here: the snapshot it recorded is this phone's, so removing it puts
        // back this phone's Home and not the other one's.
        assertEquals(setOf("something the old phone never saw", "tweaks:appPanels"), new.host.on)
        assertTrue(new.installer.remove(cabinet))
        assertEquals(setOf("something the old phone never saw"), new.host.on)
    }

    @Test fun `a package Safe Mode turned off comes back off, with its changes not applied`() {
        val old = Phone()
        old.installer.install(pack())
        old.installer.disable(cabinet, "it crashed twice")
        val new = Phone()
        val restored = new.installer.restoreBackup(old.store.export(), offReason) {}!!
        assertEquals(listOf(cabinet), restored.off.map { it.id })
        assertEquals(emptyList<InstalledPackage>(), restored.on)
        val record = new.store.find(cabinet)!!
        assertTrue("it is in the list, so it can be tried again or removed", !record.enabled)
        assertEquals("it crashed twice", record.disabledReason)
        assertEquals(emptySet<String>(), new.host.on)
        holds(new, "restored a package that was off")
        // And Try Again still works, because what it changed travelled with it.
        assertTrue(new.installer.enable(cabinet))
        assertEquals(setOf("tweaks:appPanels"), new.host.on)
    }

    @Test fun `the layout goes back between this phone's packages coming off and the backup's going on`() {
        val old = Phone()
        old.installer.install(pack())
        val backup = old.store.export()
        // This phone already has a package of its own, applied over its own Home.
        val new = Phone()
        new.installer.install(pack("theme-dark"))
        new.host.log.clear()
        val restored = new.installer.restoreBackup(backup, offReason) { new.host.log += "layout" }!!
        // Off, then the layout, then on: a package's changes always sit on top of the layout they were applied over.
        assertEquals(listOf("-theme", "layout", "+tweaks:appPanels"), new.host.log)
        assertEquals(listOf(cabinet), new.store.installed().map { it.id })
        assertEquals(listOf(cabinet), restored.on.map { it.id })
        assertNull("this phone's own package is gone, not orphaned", new.store.find(darkTheme))
        holds(new, "restored over a phone that had its own package")
    }

    @Test fun `this phone's packages come off newest first, so none of them is left behind on Home`() {
        // The backup has the theme and nothing else; this phone has a tweak with the theme stacked on top of it.
        val old = Phone()
        old.installer.install(pack("theme-dark"))
        val new = Phone()
        new.installer.install(pack())
        new.installer.install(pack("theme-dark"))
        new.host.log.clear()
        new.installer.restoreBackup(old.store.export(), offReason) { new.host.log += "layout" }

        // Taking the tweak off first would restore the Home screen from before it, putting the theme back on top of
        // a package that is no longer installed - and leaving the tweak itself on after the theme came off.
        assertEquals(listOf("-theme", "-tweaks:appPanels", "layout", "+theme"), new.host.log)
        assertEquals(setOf("theme"), new.host.on)
        assertEquals(listOf(darkTheme), new.store.installed().map { it.id })
        holds(new, "restored over a phone with two packages")
    }

    @Test fun `a package this Folio can't apply comes back turned off rather than half on`() {
        val old = Phone()
        old.installer.install(pack())
        old.installer.install(pack("theme-dark"))
        // This build does themes but not app panels: the tweak bundle has nowhere to go.
        val new = Phone(Host(setOf(Capability.THEME)))
        val restored = new.installer.restoreBackup(old.store.export(), offReason) {}!!
        assertEquals(listOf(darkTheme), restored.on.map { it.id })
        assertEquals(listOf(cabinet), restored.failed.map { it.id })
        val record = new.store.find(cabinet)!!
        assertTrue(!record.enabled)
        assertEquals(offReason, record.disabledReason)
        assertEquals(setOf("theme"), new.host.on)
        holds(new, "restored a package this build can't apply")
    }

    @Test fun `a backup Folio can't read leaves this phone's packages alone, and the layout still goes back`() {
        val phone = Phone()
        phone.installer.install(pack())
        var layout = 0
        for (bad in listOf("not a backup", """{"format":2,"packages":[]}""", """{"format":1}""")) {
            assertNull(phone.installer.restoreBackup(bad, offReason) { layout++ })
            assertTrue("the package it already had is untouched", phone.store.find(cabinet)!!.enabled)
            assertEquals(setOf("tweaks:appPanels"), phone.host.on)
        }
        assertEquals("the launcher still puts its own layout back", 3, layout)
    }

    @Test fun `a backup that names one package twice still leaves the list and Home agreeing`() {
        val old = Phone()
        old.installer.install(pack())
        // A backup someone edited, or one written by a Folio with a bug: the same package, listed twice.
        val doubled = JSONObject(old.store.export())
        val packages = doubled.getJSONArray("packages")
        packages.put(packages.getJSONObject(0))
        val new = Phone()
        val restored = new.installer.restoreBackup(doubled.toString(), offReason) {}!!
        assertEquals(listOf(cabinet), restored.on.map { it.id })
        assertEquals(emptyList<InstalledPackage>(), restored.failed)
        holds(new, "restored a backup listing one package twice")
    }

    @Test fun `a backup may not name keys of its own in Folio's store`() {
        val phone = Phone()
        val backup = """
            {"format":1,"packages":[{"id":"$cabinet","version":"1.0.0","name":"Cabinet","origin":"folio-source",
            "installedAt":1,"enabled":false}],
            "changes":{"market:safe-mode":[{"kind":"theme","json":"{}"}],"$cabinet@1.0.0":[{"kind":"theme","json":"{}"}]}}
        """.trimIndent()
        val read = phone.store.readBackup(backup)!!
        assertEquals(setOf("$cabinet@1.0.0"), read.changes.keys)
    }

    @Test fun `however the old phone got there, the new one ends up agreeing with its own list`() {
        val steps: Map<String, (PackageInstaller) -> Unit> = mapOf(
            "install" to { it.install(pack()) },
            "update" to { it.install(pack(version = "1.1.0")) },
            "theme" to { it.install(pack("theme-dark")) },
            "off" to { it.disable(cabinet, "crashed") },
            "on" to { it.enable(cabinet) },
            "remove" to { it.remove(cabinet) },
        )
        val names = steps.keys.toList()
        var walked = 0
        for (a in names) for (b in names) for (c in names) {
            val old = Phone()
            for (step in listOf(a, b, c)) steps.getValue(step)(old.installer)
            val new = Phone()
            val done = "$a $b $c"
            new.installer.restoreBackup(old.store.export(), offReason) {}
            holds(new, done)
            // And the new phone's list says what the old one's did.
            assertEquals(done, old.store.installed().map { it.id to it.version to it.enabled }.toSet(),
                new.store.installed().map { it.id to it.version to it.enabled }.toSet())
            walked++
        }
        assertEquals(216, walked)
    }
}
