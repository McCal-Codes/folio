package com.mccal.folio.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADR 0013's fields: the manifest decides (`minSdk`, `maxSdk`, `maxFolio`, `slot`, `breaks`), the depiction describes
 * (`testedOn`, `fallback`), and the source speaks later (`incompatible` in revoked.json).
 */
class PackageLimitsTest {
    private fun parse(extra: String, features: String = """"tweaks.foldTransition""""): ParseResult<PackageManifest> = PackageManifest.parse(
        """{"format":1,"id":"dev.example.swing","name":"Swing","version":"1.0","author":{"name":"Example"},
            "minFolio":"0.7.0","section":"tweaks","kind":["tweakBundle"],"permissions":["tweaks"],
            "requires":{"features":[$features]}$extra}""",
    )

    private fun manifest(extra: String = ""): PackageManifest {
        val parsed = parse(extra)
        assertTrue("$parsed", parsed is ParseResult.Ok)
        return (parsed as ParseResult.Ok).value
    }

    private fun errors(extra: String): List<String> {
        val parsed = parse(extra)
        assertTrue("expected errors for $extra, got $parsed", parsed is ParseResult.Invalid)
        return (parsed as ParseResult.Invalid).errors
    }

    private fun installed(id: String, version: String) = InstalledPackage(
        id = id, version = DebVersion.parse(version)!!, name = "Harborline", origin = InstalledPackage.Origin.FOLIO_SOURCE,
        sourceUrl = null, installedAt = 0L, snapshots = emptyList())

    private fun context(sdk: Int? = 37, folio: String? = "0.7.0", installed: List<InstalledPackage> = emptyList()) =
        CompatContext(Capability.entries.toSet(), folio?.let(FolioVersion::parse), installed, androidSdk = sdk)

    @Test fun `every new field is read`() {
        val m = manifest(""","minSdk":33,"maxSdk":37,"maxFolio":"0.7","slot":"tweaks.foldTransition",
            "breaks":["com.mccal.folio.harborline (<< 1.1)"]""")
        assertEquals(33, m.minSdk)
        assertEquals(37, m.maxSdk)
        assertEquals(FolioMinor(0, 7), m.maxFolio)
        assertEquals(Capability.FOLD_TRANSITION, m.slot)
        assertEquals(listOf(PackageRelation.parse("com.mccal.folio.harborline (<< 1.1)")), m.breaks)
    }

    @Test fun `a package without them is read as before`() {
        val m = manifest()
        assertNull(m.minSdk); assertNull(m.maxSdk); assertNull(m.maxFolio); assertNull(m.slot)
        assertTrue(m.breaks.isEmpty())
    }

    @Test fun `limits that contradict themselves are refused`() {
        assertTrue(errors(""","minSdk":37,"maxSdk":33""").any { "minSdk" in it })
        assertTrue(errors(""","minSdk":30""").any { "minSdk" in it })
        assertTrue(errors(""","maxFolio":"0.6"""").any { "maxFolio" in it })
        assertTrue(errors(""","maxFolio":"0.7.2"""").any { "maxFolio" in it })
        assertTrue(errors(""","slot":"tweaks.pageEffects"""").any { "slot" in it })
        assertTrue(errors(""","breaks":["Harborline < 1.1"]""").any { "breaks" in it })
    }

    @Test fun `the Android range blocks outside it, first, and never inside it`() {
        val m = manifest(""","minSdk":33,"maxSdk":36""")
        val below = PackageCompatibility.check(m, context(sdk = 32))
        assertEquals(CompatCheck.Android(33, 36, 32), below.first())
        assertEquals(CompatCheck.Android(33, 36, 32), PackageCompatibility.blocking(below))
        assertEquals(CompatState.BLOCKED, PackageCompatibility.check(m, context(sdk = 37)).first().state)
        assertNull(PackageCompatibility.blocking(PackageCompatibility.check(m, context(sdk = 34))))
        assertNull("a test that gives no Android skips the check", PackageCompatibility.blocking(PackageCompatibility.check(m, context(sdk = null))))
    }

    @Test fun `past maxFolio is a note, never a block`() {
        val m = manifest(""","maxFolio":"0.7"""")
        val later = PackageCompatibility.check(m, context(folio = "0.8.1"))
        assertEquals(CompatState.NOTE, later.single { it is CompatCheck.FolioRange }.state)
        assertNull(PackageCompatibility.blocking(later))
        assertEquals(CompatState.OK, PackageCompatibility.check(m, context(folio = "0.7.4")).single { it is CompatCheck.FolioRange }.state)
    }

    @Test fun `breaks blocks only the versions it names`() {
        val m = manifest(""","breaks":["com.mccal.folio.harborline (<< 1.1)"]""")
        val old = installed("com.mccal.folio.harborline", "1.0.0")
        assertEquals(CompatCheck.Breaks(m.breaks, listOf(old)), PackageCompatibility.blocking(PackageCompatibility.check(m, context(installed = listOf(old)))))
        assertNull(PackageCompatibility.blocking(PackageCompatibility.check(m, context(installed = listOf(installed("com.mccal.folio.harborline", "1.1"))))))
        assertNull(PackageCompatibility.blocking(PackageCompatibility.check(m, context())))
    }

    @Test fun `Android names follow the API level`() {
        assertEquals("Android 13 to 17", AndroidRelease.range(33, 37))
        assertEquals("Android 16 or later", AndroidRelease.range(36, null))
        assertEquals("API 40", AndroidRelease.name(40))
    }

    @Test fun `a compatibility block reads testedOn and fallback`() {
        val page = Depiction.parse("""{"format":1,"blocks":[{"type":"compatibility","screens":["cover","inner"],
            "testedOn":[{"device":"Galaxy Z Fold8","android":37,"folio":"0.7.0","date":"2026-10-03"}],
            "fallback":"The fold animation keeps using the phone's normal hinge reading."}]}""")
        assertTrue("$page", page is ParseResult.Ok)
        val block = (page as ParseResult.Ok).value.blocks.single() as DepictionBlock.Compatibility
        assertEquals(37, block.testedOn.single().android)
        assertEquals(FolioVersion(0, 7, 0), block.testedOn.single().folio)
        assertEquals("The fold animation keeps using the phone's normal hinge reading.", block.fallback?.english)
    }

    @Test fun `revoked json reads incompatible entries and matches by Folio release`() {
        val list = RevocationList.parse("""{"format":1,"timestamp":1789660320,"packages":[],
            "incompatible":[{"id":"dev.example.swing","versions":["1.0"],"folio":"(>= 0.8)","reason":"Stops halfway"}]}""")
        assertTrue("$list", list is ParseResult.Ok)
        val revocations = (list as ParseResult.Ok).value
        val v = DebVersion.parse("1.0")!!
        assertEquals("Stops halfway", revocations.incompatibleReason("dev.example.swing", v, FolioVersion(0, 8, 0)))
        assertNull(revocations.incompatibleReason("dev.example.swing", v, FolioVersion(0, 7, 9)))
        assertNull(revocations.incompatibleReason("dev.example.swing", DebVersion.parse("1.1")!!, FolioVersion(0, 8, 0)))
        assertTrue(RevocationList.parse("""{"format":1,"timestamp":1,"packages":[],
            "incompatible":[{"id":"dev.example.swing","versions":["1.0"],"folio":">= 0.8","reason":"x"}]}""") is ParseResult.Invalid)
    }
}
