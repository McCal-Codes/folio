package com.mccal.folio

import com.mccal.folio.market.BuiltInSource
import com.mccal.folio.market.DebVersion
import com.mccal.folio.market.FileRef
import com.mccal.folio.market.FolioRelation
import com.mccal.folio.market.FolioVersion
import com.mccal.folio.market.IncompatiblePackage
import com.mccal.folio.market.IndexPackage
import com.mccal.folio.market.RepoIndex
import com.mccal.folio.market.RevocationList
import com.mccal.folio.market.RevokedPackage
import com.mccal.folio.market.Source
import com.mccal.folio.market.SourceEntry
import com.mccal.folio.market.SourceKey
import com.mccal.folio.market.SourceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * Folio's own tweaks source (folio-tweaks) and the packages built into Folio: a copy of a built-in package from that
 * source, signed with the key Folio ships, is an update to it rather than a claim on its name. Every other source
 * using a built-in id is still an impostor.
 */
class FirstPartySourceTest {
    private val now = 1_789_000_000L
    private val on069 = FolioVersion(0, 6, 9)
    private val folio = Source("folio://built-in/", name = "Folio", kind = Source.Kind.BUILT_IN)
    private val tweaks = Source(FIRST_PARTY_SOURCE, name = "Folio Community Tweaks")
    private val someone = Source("https://someone.example/folio/", name = "Someone else")
    private val firstPartyKey = requireNotNull(SourceKey.parse(FIRST_PARTY_SOURCE_KEY))
    private val otherKey = requireNotNull(
        SourceKey.parse(
            Base64.getEncoder().encodeToString(
                KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair().public.encoded,
            ),
        ),
    )

    /** Folio's own index, read straight from docs/sdk/source, which is what the build copies into the app. */
    private val builtIn: List<IndexPackage> = run {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
        val dir = File(root, "docs/sdk/source")
        val prefix = BuiltInSource.ROOT + "/"
        val source = BuiltInSource(
            read = { path -> File(dir, path.removePrefix(prefix)).takeIf { it.isFile }?.readBytes() },
            list = { path -> File(dir, path.removePrefix(prefix)).list()?.sorted().orEmpty() },
        )
        requireNotNull(source.index()).packages
    }
    private val cabinet = builtIn.first { it.id == "com.mccal.folio.cabinet" }

    /** Cabinet as a source lists it: [version], downloadable, and with [minFolio] in its manifest. */
    private fun listed(version: String, minFolio: FolioVersion = FolioVersion(0, 6, 6), installable: Boolean = true): IndexPackage {
        val v = requireNotNull(DebVersion.parse(version))
        return cabinet.copy(
            version = v,
            url = if (installable) "packages/com.mccal.folio.cabinet.foliopkg" else null,
            sha256 = if (installable) "a".repeat(64) else null,
            size = if (installable) 1024 else null,
            manifest = requireNotNull(cabinet.manifest).copy(version = v, minFolio = minFolio),
        )
    }

    private fun snapshot(source: Source, vararg packages: IndexPackage, revocation: RevocationList? = null) = SourceSnapshot(
        url = source.url,
        entry = SourceEntry("7028F4F54A35A055", now, 14 * 86_400L, FileRef("index.json", "b".repeat(64), 10)),
        index = RepoIndex(com.mccal.folio.market.LocalizedText.of("Folio Community Tweaks"), null, null, null, emptyList(), packages.toList()),
        revocation = revocation,
        fetchedAt = now,
    )

    /** What the store lists, with folio-tweaks counted as first-party exactly when [pinned] is the key Folio ships. */
    private fun merge(vararg from: Pair<Source, SourceSnapshot>, pinned: SourceKey? = firstPartyKey, folioList: RevocationList? = null) =
        mergeEntries(builtIn, folio, folioList, from.toList(), folio = on069, isFirstParty = { isFirstParty(it.url, pinned) })

    private fun cabinets(entries: List<MarketEntry>) = entries.filter { it.id == cabinet.id }

    @Test fun `the key built into Folio is folio-tweaks' published key`() {
        // Read from https://mccal-codes.github.io/folio-tweaks/key.pub on 12 Oct 2026; entry.json names this key id.
        assertEquals("7028F4F54A35A055", firstPartyKey.keyId)
        assertEquals(FIRST_PARTY_SOURCE, com.mccal.folio.market.normalizeSourceUrl(FIRST_PARTY_SOURCE))
    }

    @Test fun `only that address with that key is first-party`() {
        assertTrue(isFirstParty(FIRST_PARTY_SOURCE, firstPartyKey))
        // The same address however it was typed in: a GitHub repository address is its Pages site.
        assertTrue(isFirstParty("github.com/McCal-Codes/folio-tweaks", firstPartyKey))
        assertTrue(isFirstParty("https://mccal-codes.github.io/folio-tweaks", firstPartyKey))
        // Another key at that address, no key pinned yet, or Folio's key somewhere else: not first-party.
        assertFalse(isFirstParty(FIRST_PARTY_SOURCE, otherKey))
        assertFalse(isFirstParty(FIRST_PARTY_SOURCE, null))
        assertFalse(isFirstParty(someone.url, firstPartyKey))
        assertFalse(isFirstParty(SUPPORTER_SOURCE, firstPartyKey))
    }

    @Test fun `a newer copy from folio-tweaks is an update to the built-in package, listed once`() {
        val newer = listed("1.0.1")
        val merged = merge(tweaks to snapshot(tweaks, newer))

        val row = cabinets(merged).single()
        assertEquals(tweaks, row.source)
        assertEquals(newer.version, row.entry.version)
        assertNull("not an impostor", row.clash)
        assertFalse(row.unavailable)
        // It keeps the built-in package's place in the list.
        assertEquals(builtIn.indexOfFirst { it.id == cabinet.id }, merged.indexOf(row))
        assertEquals(builtIn.size, merged.size)
    }

    @Test fun `the same version, or an older one, leaves the built-in package in place`() {
        for (version in listOf("1.0.0", "0.9.0")) {
            val row = cabinets(merge(tweaks to snapshot(tweaks, listed(version)))).single()
            assertEquals(version, folio, row.source)
            assertNull(row.clash)
        }
    }

    @Test fun `a copy its source pulled leaves the built-in package in place`() {
        val newer = listed("1.0.1")
        val pulled = RevocationList(now, listOf(RevokedPackage(cabinet.id, listOf("1.0.1"), "Breaks the dock")), emptyList())
        assertEquals(folio, cabinets(merge(tweaks to snapshot(tweaks, newer, revocation = pulled))).single().source)
        // Folio's own list pulls it too.
        assertEquals(folio, cabinets(merge(tweaks to snapshot(tweaks, newer), folioList = pulled)).single().source)
    }

    @Test fun `a copy its source marks broken on this Folio leaves the built-in package in place`() {
        val newer = listed("1.0.1")
        val broken = IncompatiblePackage(cabinet.id, listOf("1.0.1"), requireNotNull(FolioRelation.parse("(<< 0.7)")), "The panel opens off screen")
        val list = RevocationList(now, emptyList(), emptyList(), listOf(broken))
        assertEquals(folio, cabinets(merge(tweaks to snapshot(tweaks, newer, revocation = list))).single().source)
    }

    @Test fun `a copy that needs a newer Folio leaves the built-in package in place`() {
        assertEquals(folio, cabinets(merge(tweaks to snapshot(tweaks, listed("1.0.1", minFolio = FolioVersion(0, 7, 0))))).single().source)
        // Needing exactly this Folio is fine.
        assertEquals(tweaks, cabinets(merge(tweaks to snapshot(tweaks, listed("1.0.1", minFolio = on069)))).single().source)
        // A manifest this Folio can't read at all is the same as needing a newer one.
        val unreadable = listed("1.0.1").copy(manifest = null, needs = listOf("a newer Folio"))
        assertEquals(folio, cabinets(merge(tweaks to snapshot(tweaks, unreadable))).single().source)
    }

    @Test fun `a copy that can't be downloaded leaves the built-in package in place`() {
        assertEquals(folio, cabinets(merge(tweaks to snapshot(tweaks, listed("1.0.1", installable = false)))).single().source)
    }

    @Test fun `folio-tweaks pinned with another key is an impostor like any other source`() {
        val rows = cabinets(merge(tweaks to snapshot(tweaks, listed("1.0.1")), pinned = otherKey))
        assertEquals(2, rows.size)
        assertEquals(folio, rows.first().source)
        assertEquals(MarketEntry.Impostor.BUILT_IN, rows.last().clash)
    }

    @Test fun `another source with a built-in id is still an impostor, beside an update from folio-tweaks`() {
        val rows = cabinets(merge(tweaks to snapshot(tweaks, listed("1.0.1")), someone to snapshot(someone, listed("9.0.0"))))
        assertEquals(listOf(tweaks, someone), rows.map { it.source })
        assertNull(rows.first().clash)
        assertEquals(MarketEntry.Impostor.BUILT_IN, rows.last().clash)
    }

    @Test fun `a package of folio-tweaks' own that isn't built in is listed like any source's`() {
        val tilt = listed("1.0.0").let { it.copy(id = "com.mccal.folio.effect.tilt", manifest = it.manifest?.copy(id = "com.mccal.folio.effect.tilt")) }
        val row = merge(tweaks to snapshot(tweaks, tilt)).single { it.id == tilt.id }
        assertEquals(tweaks, row.source)
        assertNull(row.clash)
    }

    @Test fun `without a first-party source, nothing changes`() {
        val rows = cabinets(mergeEntries(builtIn, folio, null, listOf(tweaks to snapshot(tweaks, listed("1.0.1"))), folio = on069))
        assertEquals(listOf(folio, tweaks), rows.map { it.source })
        assertEquals(MarketEntry.Impostor.BUILT_IN, rows.last().clash)
    }
}
