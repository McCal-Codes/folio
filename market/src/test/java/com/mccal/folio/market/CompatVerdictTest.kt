package com.mccal.folio.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The verdict the page and the install sheet show is the one the installer acts on. Every scenario here builds a real
 * package, asks [PackageCompatibility.verdict] from its manifest alone (what a page can know before a download), then
 * installs it and checks the two agree.
 */
class CompatVerdictTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private val cabinet = File(root, "docs/sdk/source/packages/cabinet")

    private class Host(override val capabilities: Set<Capability>, val tweaks: Set<String>) : PackageHost {
        override fun hasTweak(id: String) = id in tweaks
        override fun apply(change: PackageChange) = "before"
        override fun restore(change: PackageChange, snapshot: String) {}
    }

    private fun zip(files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> files.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return out.toByteArray()
    }

    /** Cabinet with [replace] applied to its manifest: the package bytes, and the manifest text. */
    private fun cabinetWith(replace: Map<String, String> = emptyMap()): Pair<ByteArray, String> {
        var manifest = File(cabinet, "manifest.json").readText().replace("\r\n", "\n")
        replace.forEach { (from, to) -> assertTrue("$from is in the manifest", from in manifest); manifest = manifest.replace(from, to) }
        val bytes = zip(mapOf(
            "manifest.json" to manifest.toByteArray(),
            "depiction.json" to File(cabinet, "depiction.json").readBytes(),
            "tweaks.json" to File(cabinet, "tweaks.json").readBytes(),
        ))
        return bytes to manifest
    }

    private val name = "\"name\": \"Cabinet\""

    /** A different package id, with [extra] fields in its manifest. */
    private fun other(extra: String): Map<String, String> = mapOf(
        "\"com.mccal.folio.cabinet\"" to "\"dev.example.other\"",
        name to "\"name\": \"Other\",\n  $extra",
    )

    private class Outcome(val verdict: CompatVerdict, val installed: Boolean)

    /** Asks for the verdict from the manifest, then installs the same bytes on the same phone. */
    private fun run(
        pkg: Pair<ByteArray, String>,
        sdk: Int? = 36,
        folio: String = "0.7.0",
        capabilities: Set<Capability> = Capability.entries.toSet(),
        tweaks: Set<String> = emptySet(),
        screens: Set<Screen>? = null,
        beforehand: List<ByteArray> = emptyList(),
    ): Outcome {
        val host = Host(capabilities, tweaks)
        val store = InstalledStore(MemoryStore())
        val version = FolioVersion.parse(folio)
        val installer = PackageInstaller(store, host, folioVersion = version, androidSdk = sdk)
        beforehand.forEach { assertTrue(installer.install(it, origin = InstalledPackage.Origin.FILE) is InstallResult.Installed) }
        val manifest = (PackageManifest.parse(pkg.second) as ParseResult.Ok).value
        val context = CompatContext(capabilities, version, store.installed(), host::hasTweak, screens, sdk)
        val verdict = PackageCompatibility.verdict(manifest, context)
        val result = installer.install(pkg.first, origin = InstalledPackage.Origin.FILE)
        return Outcome(verdict, result is InstallResult.Installed)
    }

    private fun refused(o: Outcome, reason: CompatReason) {
        assertEquals(reason, (o.verdict as? CompatVerdict.CannotInstall)?.reason)
        assertFalse("the installer agrees: not installed", o.installed)
    }

    @Test fun `a package that asks for nothing special works, and the installer installs it`() {
        val o = run(cabinetWith())
        assertEquals(CompatVerdict.Works, o.verdict)
        assertTrue(o.installed)
    }

    @Test fun `Android below minSdk or above maxSdk cannot install`() {
        refused(run(cabinetWith(mapOf(name to "$name, \"minSdk\": 36")), sdk = 33), CompatReason.ANDROID_VERSION)
        refused(run(cabinetWith(mapOf(name to "$name, \"maxSdk\": 34")), sdk = 36), CompatReason.ANDROID_VERSION)
        assertTrue(run(cabinetWith(mapOf(name to "$name, \"minSdk\": 33, \"maxSdk\": 36")), sdk = 35).installed)
    }

    @Test fun `a newer minFolio cannot install`() {
        refused(run(cabinetWith(mapOf("\"minFolio\": \"0.6.6\"" to "\"minFolio\": \"0.9.0\"")), folio = "0.7.0"), CompatReason.NEEDS_NEWER_FOLIO)
        assertTrue(run(cabinetWith(mapOf("\"minFolio\": \"0.6.6\"" to "\"minFolio\": \"0.7.0\"")), folio = "0.7.0").installed)
    }

    @Test fun `a capability this build lacks cannot install`() {
        refused(run(cabinetWith(), capabilities = setOf(Capability.THEME)), CompatReason.MISSING_CAPABILITY)
    }

    @Test fun `a kind Folio reads but does not run cannot install`() {
        val script = """{"format":1,"id":"dev.example.script","name":"Example","version":"1.0","author":{"name":"Example"},
            "minFolio":"0.6.6","section":"tweaks","kind":["script"],"permissions":[]}"""
        refused(run(zip(mapOf("manifest.json" to script.toByteArray())) to script), CompatReason.UNSUPPORTED_KIND)
    }

    @Test fun `an add-on without its host tweak cannot install but offers the fix, and installs once the host is there`() {
        val dir = File(root, "docs/sdk/examples/page-effect-tilt")
        val manifest = File(dir, "manifest.json").readText()
        val bytes = zip(mapOf("manifest.json" to manifest.toByteArray(), "effect.json" to File(dir, "effect.json").readBytes()))
        val without = run(bytes to manifest)
        refused(without, CompatReason.NEEDS_HOST_TWEAK)
        assertFalse("the page offers the host rather than going dead", without.verdict.stopsGet)
        val with = run(bytes to manifest, tweaks = setOf("pageEffects"))
        assertTrue(with.installed)
        assertEquals(CompatVerdict.Works, with.verdict)
    }

    @Test fun `replacing an installed package cannot install`() {
        val rival = cabinetWith(other("\"conflicts\": [\"com.mccal.folio.cabinet\"]"))
        refused(run(rival, beforehand = listOf(cabinetWith().first)), CompatReason.REPLACES_INSTALLED)
        assertTrue("with nothing to replace it installs", run(rival).installed)
    }

    @Test fun `breaking an installed version cannot install`() {
        val breaker = cabinetWith(other("\"breaks\": [\"com.mccal.folio.cabinet (<< 1.1)\"]"))
        refused(run(breaker, beforehand = listOf(cabinetWith().first)), CompatReason.BREAKS_INSTALLED)
    }

    @Test fun `a dependency that is not installed cannot install, and installs once it is`() {
        val needy = cabinetWith(other("\"depends\": [\"com.mccal.folio.cabinet\"]"))
        refused(run(needy), CompatReason.NEEDS_PACKAGE)
        val ok = run(needy, beforehand = listOf(cabinetWith().first))
        assertEquals(CompatVerdict.Works, ok.verdict)
        assertTrue(ok.installed)
    }

    @Test fun `a screen the package does not cover is a limit, and still installs`() {
        val innerOnly = cabinetWith(mapOf("\"cover\",\n    \"inner\"" to "\"inner\""))
        val o = run(innerOnly, screens = setOf(Screen.COVER, Screen.INNER))
        assertTrue("${o.verdict}", o.verdict is CompatVerdict.WorksWithLimit)
        assertTrue((o.verdict as CompatVerdict.WorksWithLimit).limits.single() is CompatCheck.Screens)
        assertTrue(o.installed)
        assertFalse(o.verdict.stopsGet)
    }

    @Test fun `a Folio newer than the package was tried on is a limit, and still installs`() {
        val tried = cabinetWith(mapOf(name to "$name, \"maxFolio\": \"0.6\""))
        val o = run(tried, folio = "0.8.0")
        assertTrue("${o.verdict}", o.verdict is CompatVerdict.WorksWithLimit)
        assertTrue((o.verdict as CompatVerdict.WorksWithLimit).limits.single() is CompatCheck.FolioRange)
        assertTrue(o.installed)
    }

    @Test fun `several problems report the first the installer would, and every reason has a stable key`() {
        val m = cabinetWith(mapOf("\"minFolio\": \"0.6.6\"" to "\"minFolio\": \"0.9.0\"", name to "$name, \"minSdk\": 36"))
        refused(run(m, sdk = 33, folio = "0.7.0"), CompatReason.ANDROID_VERSION)
        assertEquals("keys are unique", CompatReason.entries.size, CompatReason.entries.map { it.key }.toSet().size)
        assertTrue(CompatReason.entries.all { it.key.matches(Regex("[a-z_]+")) })
    }
}
