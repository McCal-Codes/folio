package com.mccal.folio.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The answer a page shows before Get is the one the installer refuses on. */
class PackageCompatibilityTest {
    private fun manifest(extra: String = "", kind: String = "tweakBundle"): PackageManifest {
        val json = """{"format":1,"id":"dev.example.test","name":"Example","version":"1.0","author":{"name":"Example"},
            "minFolio":"0.6.8","section":"tweaks","kind":["$kind"],"permissions":[]$extra}"""
        val parsed = PackageManifest.parse(json)
        assertTrue("$parsed", parsed is ParseResult.Ok)
        return (parsed as ParseResult.Ok).value
    }

    private fun installed(id: String, version: String = "1.0", enabled: Boolean = true) = InstalledPackage(
        id = id, version = DebVersion.parse(version)!!, name = "Installed $id", origin = InstalledPackage.Origin.FOLIO_SOURCE,
        sourceUrl = null, installedAt = 0L, snapshots = emptyList(), enabled = enabled)

    private fun context(
        have: String? = "0.6.8", installed: List<InstalledPackage> = emptyList(), tweaks: Set<String> = emptySet(),
        capabilities: Set<Capability> = Capability.entries.toSet(), screens: Set<Screen>? = null,
    ) = CompatContext(capabilities, have?.let(FolioVersion::parse), installed, { it in tweaks }, screens)

    private fun check(m: PackageManifest, c: CompatContext, builtIn: Boolean = false) = PackageCompatibility.check(m, c, builtIn = builtIn)

    @Test fun `a package that asks for nothing special works`() {
        val checks = check(manifest(), context())
        assertNull(PackageCompatibility.blocking(checks))
        assertTrue(checks.all { it.state == CompatState.OK })
    }

    @Test fun `a package that needs a later Folio is blocked, unless it ships inside Folio`() {
        val m = manifest().copy(minFolio = FolioVersion(0, 8, 0))
        val stop = PackageCompatibility.blocking(check(m, context(have = "0.6.8")))
        assertEquals(CompatCheck.Release(FolioVersion(0, 8, 0), FolioVersion(0, 6, 8)), stop)
        assertNull(PackageCompatibility.blocking(check(m, context(have = "0.6.8"), builtIn = true)))
        assertNull("a test that gives no version skips the check", PackageCompatibility.blocking(check(m, context(have = null))))
    }

    @Test fun `a capability this build lacks is blocked and named`() {
        val m = manifest(""","requires":{"features":["tweaks.pageEffects","theme"]}""")
        val stop = PackageCompatibility.blocking(check(m, context(capabilities = setOf(Capability.THEME))))
        assertEquals(CompatCheck.Features(listOf(Capability.PAGE_EFFECTS)), stop)
    }

    @Test fun `an add-on without its host tweak is blocked, and fine once the host is on the phone`() {
        val m = manifest(kind = "pageEffect")
        assertEquals(CompatCheck.Hosts(listOf("pageEffects"), listOf("pageEffects")), PackageCompatibility.blocking(check(m, context())))
        assertNull(PackageCompatibility.blocking(check(m, context(tweaks = setOf("pageEffects")))))
    }

    @Test fun `a conflict with an installed package is blocked and names it, ignoring a version it does not match`() {
        val m = manifest(""","conflicts":["dev.other.pack (<< 2.0)"]""")
        val old = installed("dev.other.pack", "1.5")
        assertEquals(CompatCheck.Replaces(old), PackageCompatibility.blocking(check(m, context(installed = listOf(old)))))
        assertNull(PackageCompatibility.blocking(check(m, context(installed = listOf(installed("dev.other.pack", "2.1"))))))
        assertNull(PackageCompatibility.blocking(check(m, context())))
    }

    @Test fun `a dependency has to be installed, on, and in range`() {
        val m = manifest(""","depends":["dev.host.base (>= 1.2)"]""")
        val need = PackageRelation.parse("dev.host.base (>= 1.2)")!!
        val blocked = CompatCheck.Needs(listOf(need), listOf(need))
        assertEquals(blocked, PackageCompatibility.blocking(check(m, context())))
        assertEquals(blocked, PackageCompatibility.blocking(check(m, context(installed = listOf(installed("dev.host.base", "1.0"))))))
        assertEquals("turned off does not count", blocked,
            PackageCompatibility.blocking(check(m, context(installed = listOf(installed("dev.host.base", "1.3", enabled = false))))))
        assertNull(PackageCompatibility.blocking(check(m, context(installed = listOf(installed("dev.host.base", "1.3"))))))
    }

    @Test fun `screens only ever add a note, and only when the phone's are known`() {
        val inner = manifest(""","screens":["inner"]""")
        assertTrue("unknown screens say nothing", check(inner, context()).none { it is CompatCheck.Screens })
        val both = setOf(Screen.COVER, Screen.INNER)
        val note = check(inner, context(screens = both)).filterIsInstance<CompatCheck.Screens>().single()
        assertEquals(CompatState.NOTE, note.state)
        assertNull(PackageCompatibility.blocking(check(inner, context(screens = both))))
        val full = check(manifest(), context(screens = both)).filterIsInstance<CompatCheck.Screens>().single()
        assertEquals(CompatState.OK, full.state)
    }

    @Test fun `when several things are wrong, the first is the one the installer reports`() {
        // Same order the installer always refused in: capabilities, then release, hosts, conflicts, dependencies.
        val m = manifest(""","requires":{"features":["theme"]},"depends":["dev.host.base"]""", kind = "pageEffect")
            .copy(minFolio = FolioVersion(9, 0, 0))
        val c = context(capabilities = emptySet())
        assertTrue(PackageCompatibility.blocking(check(m, c)) is CompatCheck.Features)
        val order = check(m, context()).filter { it.state == CompatState.BLOCKED }.map { it::class.simpleName }
        assertEquals(listOf("Release", "Hosts", "Needs"), order)
    }
}
