package com.mccal.folio

import com.mccal.folio.market.HttpClient
import com.mccal.folio.market.HttpResult
import com.mccal.folio.market.InstallResult
import com.mccal.folio.market.InstalledStore
import com.mccal.folio.market.MemoryStore
import com.mccal.folio.market.PackageInstaller
import com.mccal.folio.market.RefreshResult
import com.mccal.folio.market.RepoClient
import com.mccal.folio.market.Source
import com.mccal.folio.market.SourceKey
import com.mccal.folio.market.SourceList
import com.mccal.folio.market.SourceStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Adding a source and getting a package from it, end to end: the key is shown before it's trusted, the list is read,
 * and a downloaded package is checked against what the source promised before it's opened.
 */
class MarketSourcesTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private val cabinetDir = File(root, "docs/sdk/source/packages/cabinet")
    private val base = "https://maya.example/folio/"
    private val keys: KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val key = requireNotNull(SourceKey.parse(Base64.getEncoder().encodeToString(keys.public.encoded)))
    private val now = 1_789_000_000L

    private class Host : HttpClient {
        val files = HashMap<String, ByteArray>()
        val requests = mutableListOf<String>()
        override fun get(url: String, maxBytes: Int, etag: String?): HttpResult {
            requests += url
            val bytes = files[url] ?: return HttpResult.Failed("not found", 404)
            if (bytes.size > maxBytes) return HttpResult.TooLarge
            return HttpResult.Body(bytes, null)
        }
    }

    /** The same hash the source pins with; computed here so the test doesn't reach into the module's internals. */
    private fun sha256Hex(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private val host = Host()
    private val store = MemoryStore()
    private val sources = MarketSources(
        client = RepoClient(host, SourceStore(store)) { now },
        list = SourceList(store),
        http = host,
        io = kotlinx.coroutines.Dispatchers.Unconfined,
    )

    /** A package packed the way the publishing tool will, plus an index and a signed entry that pin it. */
    private fun publish(version: String = "1.0.0", hero: String = "first hero") {
        val pkg = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                for (name in listOf("manifest.json", "depiction.json", "tweaks.json")) {
                    val text = File(cabinetDir, name).readText().replace("\"version\": \"1.0.0\"", "\"version\": \"$version\"")
                    zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
                }
                // An image that changes with the release, so an update can be seen to bring its own.
                zip.putNextEntry(ZipEntry("assets/hero.png")); zip.write(hero.toByteArray()); zip.closeEntry()
            }
        }.toByteArray()
        host.files[base + "packages/cabinet.foliopkg"] = pkg
        val manifest = File(cabinetDir, "manifest.json").readText()
            .replace("\"\$schema\": \"https://folio.mccal.dev/schema/v1/manifest.schema.json\",", "")
            .replace("\"version\": \"1.0.0\"", "\"version\": \"$version\"")
        val index = """
            {"format":1,"name":"Maya's packages","packages":[
              {"id":"com.mccal.folio.cabinet","version":"$version","url":"packages/cabinet.foliopkg",
               "sha256":"${sha256Hex(pkg)}","size":${pkg.size},"manifest":$manifest}]}
        """.trimIndent()
        host.files[base + "index.json"] = index.toByteArray()
        val entry = """
            {"format":1,"keyId":"${key.keyId}","timestamp":${now - 60},"maxAge":604800,
             "index":{"path":"index.json","sha256":"${sha256Hex(index.toByteArray())}","size":${index.toByteArray().size}}}
        """.trimIndent()
        host.files[base + "entry.json"] = entry.toByteArray()
        host.files[base + "entry.json.sig"] = Base64.getEncoder().encodeToString(
            Signature.getInstance(SourceKey.ALGORITHM).run { initSign(keys.private); update(entry.toByteArray()); sign() },
        ).toByteArray()
        host.files[base + "key.pub"] = key.base64.toByteArray()
    }

    @Test fun `adding a source shows its key first, then reads its list`() = runTest {
        publish()
        val inspected = sources.inspect(base)
        assertTrue("$inspected", inspected is RefreshResult.NeedsTrust)
        val request = inspected as RefreshResult.NeedsTrust
        assertEquals(key, request.key)
        assertNull(request.previous)
        // Nothing is trusted or added until the fingerprint is confirmed.
        assertTrue(sources.sources().isEmpty())

        val trusted = sources.trust(base, request.key)
        assertTrue("$trusted", trusted is RefreshResult.Updated)
        assertEquals(listOf(base), sources.sources().map { it.url })
        assertEquals("Maya's packages", sources.sources().single().name)
        assertEquals(1, sources.cached().single().packages.size)
    }

    @Test fun `a source refreshed by an address spelled differently is the same source`() = runTest {
        publish()
        sources.trust(base, (sources.inspect(base) as RefreshResult.NeedsTrust).key)
        assertEquals(listOf(base), sources.sources().map { it.url })

        // The list, the cache and the pinned key are all keyed on the address, and each normalizes it at its own
        // door. This holds them to it: a trailing slash left off must not turn one source into two.
        val same = base.trimEnd('/')
        val again = sources.refresh(same, force = true)
        assertTrue("$again", again is RefreshResult.Updated || again is RefreshResult.Unchanged)
        assertEquals("still one source", listOf(base), sources.sources().map { it.url })
        assertEquals("and it still has its name", "Maya's packages", sources.sources().single().name)
        assertEquals(1, sources.cached().single().packages.size)
    }

    @Test fun `a package from a source is checked against what the source promised`() = runTest {
        publish()
        sources.trust(base, key)
        val entry = sources.cached().single().packages.single()
        val host2 = RecordingHost()
        val installer = PackageInstaller(InstalledStore(MemoryStore()), host2)
        val result = sources.download(entry, sources.sources().single(), installer)
        assertTrue("$result", result is InstallResult.Installed)
        assertEquals("Cabinet", (result as InstallResult.Installed).installed.name)
        assertEquals(base, result.installed.sourceUrl)
        assertTrue(host2.applied.isNotEmpty())
    }

    @Test fun `a source that publishes a newer version offers an update, and the update brings its own files`() = runTest {
        publish(version = "1.0.0", hero = "first hero")
        sources.trust(base, key)
        val store = InstalledStore(MemoryStore())
        val launcher = RecordingHost()
        val installer = PackageInstaller(store, launcher)
        val first = sources.download(sources.cached().single().packages.single(), sources.sources().single(), installer)
        assertEquals("1.0.0", (first as InstallResult.Installed).installed.version.toString())

        // The source publishes 1.1.0 at the same address, with a different image inside.
        publish(version = "1.1.0", hero = "second hero")
        assertTrue(sources.refresh(base, force = true) is RefreshResult.Updated)
        val offered = sources.cached().single().packages.single()
        assertEquals("1.1.0", offered.version.toString())
        // Higher than what's installed: this is what the Updates group is built from.
        assertTrue(offered.version > store.find("com.mccal.folio.cabinet")!!.version)

        val update = sources.download(offered, sources.sources().single(), installer)
        assertTrue("$update", update is InstallResult.Installed)
        assertEquals("1.1.0", (update as InstallResult.Installed).installed.version.toString())
        assertEquals("1.0.0", update.replaced?.version.toString())
        assertEquals("1.1.0", store.find("com.mccal.folio.cabinet")?.version.toString())
        // The bytes that were applied came from the new archive, not the one already on the phone.
        assertEquals(2, launcher.applied.size)
    }

    @Test fun `a download that doesn't match the index is refused`() = runTest {
        publish()
        sources.trust(base, key)
        val entry = sources.cached().single().packages.single()
        // The host serves something else at the same address.
        host.files[base + "packages/cabinet.foliopkg"] = "not the package".toByteArray()
        val installer = PackageInstaller(InstalledStore(MemoryStore()), RecordingHost())
        val result = sources.download(entry, sources.sources().single(), installer)
        assertEquals(InstallResult.Reason.SIZE, (result as InstallResult.Failed).reason)
    }

    @Test fun `two sources using one package id are both shown, and an impostor of Folio's own is refused`() = runTest {
        publish()
        sources.trust(base, key)
        val snapshot = requireNotNull(sources.cached().single().snapshot)
        val mine = Source("folio://built-in/", name = "Folio", kind = Source.Kind.BUILT_IN)
        val theirs = sources.sources().single()
        val other = Source("https://someone.example/folio/", name = "Someone else")

        // The source publishes Cabinet's real id, which is a package inside Folio.
        val merged = mergeEntries(snapshot.index.packages, mine, null, listOf(theirs to snapshot))
        val ours = merged.first { it.source.kind == Source.Kind.BUILT_IN }
        val copy = merged.first { it.source.url == base }
        assertEquals(null, ours.clash)
        assertEquals(MarketEntry.Impostor.BUILT_IN, copy.clash)
        // Both are listed: hiding one would leave the user wondering where their package went.
        assertEquals(2, merged.size)

        // Two sources the user added, neither of them Folio: they're told, and neither is picked for them.
        val between = mergeEntries(emptyList(), mine, null, listOf(theirs to snapshot, other to snapshot))
        assertTrue(between.all { it.clash == MarketEntry.Impostor.ANOTHER_SOURCE })
        assertEquals(listOf(base, other.url), between.map { it.source.url })
    }

    @Test fun `an installed version is pulled by Folio's list or its own source's, never by another source's`() = runTest {
        publish()
        sources.trust(base, key)
        val plain = requireNotNull(sources.cached().single())
        val snapshot = requireNotNull(plain.snapshot)
        val pkg = snapshot.index.packages.single()
        val revoked = com.mccal.folio.market.RevocationList(now, listOf(com.mccal.folio.market.RevokedPackage(pkg.id, listOf(pkg.version.text), "Malware")), emptyList())
        val pulledHere = plain.copy(snapshot = snapshot.copy(revocation = revoked))
        val elsewhere = plain.copy(source = Source("https://someone.example/folio/", name = "Someone else"), snapshot = snapshot.copy(revocation = revoked))
        fun installed(origin: com.mccal.folio.market.InstalledPackage.Origin, url: String?, version: String = pkg.version.text) =
            com.mccal.folio.market.InstalledPackage(pkg.id, requireNotNull(com.mccal.folio.market.DebVersion.parse(version)), "Cabinet", origin, url, 0L, emptyList())
        val fromHere = installed(com.mccal.folio.market.InstalledPackage.Origin.FOLIO_SOURCE, base)

        assertEquals("Malware", pulledReason(fromHere, null, listOf(pulledHere)))
        // The address is matched with or without its last slash.
        assertEquals("Malware", pulledReason(installed(com.mccal.folio.market.InstalledPackage.Origin.FOLIO_SOURCE, base.trimEnd('/')), null, listOf(pulledHere)))
        assertEquals(null, pulledReason(fromHere, null, listOf(plain, elsewhere)))
        // Another version isn't pulled.
        assertEquals(null, pulledReason(installed(com.mccal.folio.market.InstalledPackage.Origin.FOLIO_SOURCE, base, "9.0"), null, listOf(pulledHere)))
        // A file has no source, so any source's list counts; a built-in package answers to Folio's list alone.
        assertEquals("Malware", pulledReason(installed(com.mccal.folio.market.InstalledPackage.Origin.FILE, null), null, listOf(elsewhere)))
        assertEquals(null, pulledReason(installed(com.mccal.folio.market.InstalledPackage.Origin.FOLIO_SOURCE, null), null, listOf(pulledHere)))
        assertEquals("Malware", pulledReason(installed(com.mccal.folio.market.InstalledPackage.Origin.FOLIO_SOURCE, null), revoked, emptyList()))
    }

    @Test fun `a version its own source marks broken on this Folio is unavailable, and only its own source can say so`() = runTest {
        publish()
        sources.trust(base, key)
        val plain = requireNotNull(sources.cached().single().snapshot)
        val pkg = plain.index.packages.single()
        val broken = com.mccal.folio.market.IncompatiblePackage(pkg.id, listOf(null), requireNotNull(com.mccal.folio.market.FolioRelation.parse("(>= 0.8)")), "The panel opens off screen")
        val marked = plain.copy(revocation = com.mccal.folio.market.RevocationList(now, emptyList(), emptyList(), listOf(broken)))
        val mine = Source("folio://built-in/", name = "Folio", kind = Source.Kind.BUILT_IN)
        val theirs = sources.sources().single()
        val on08 = com.mccal.folio.market.FolioVersion(0, 8, 0)

        val listed = mergeEntries(emptyList(), mine, null, listOf(theirs to marked), folio = on08).single()
        assertEquals("The panel opens off screen", listed.incompatibleReason)
        assertTrue(listed.unavailable)
        assertEquals(null, listed.revokedReason)

        // On a Folio the range doesn't cover, it's fine.
        assertEquals(null, mergeEntries(emptyList(), mine, null, listOf(theirs to marked), folio = com.mccal.folio.market.FolioVersion(0, 7, 9)).single().incompatibleReason)
        // Folio's own list speaks for Folio's packages, not for a package another source offers (ADR 0013).
        val folioList = com.mccal.folio.market.RevocationList(now, emptyList(), emptyList(), listOf(broken))
        val fromFolio = mergeEntries(emptyList(), mine, folioList, listOf(theirs to plain), folio = on08).single()
        assertEquals(null, fromFolio.incompatibleReason)
        assertTrue(!fromFolio.unavailable)
    }

    @Test fun `an installed version's own lists say whether it works with this Folio, as a listing's do`() = runTest {
        publish()
        sources.trust(base, key)
        val plain = requireNotNull(sources.cached().single())
        val snapshot = requireNotNull(plain.snapshot)
        val pkg = snapshot.index.packages.single()
        val broken = com.mccal.folio.market.IncompatiblePackage(pkg.id, listOf(null), requireNotNull(com.mccal.folio.market.FolioRelation.parse("(>= 0.8)")), "The panel opens off screen")
        val list = com.mccal.folio.market.RevocationList(now, emptyList(), emptyList(), listOf(broken))
        val markedHere = plain.copy(snapshot = snapshot.copy(revocation = list))
        val on08 = com.mccal.folio.market.FolioVersion(0, 8, 0)
        fun installed(origin: com.mccal.folio.market.InstalledPackage.Origin, url: String?) =
            com.mccal.folio.market.InstalledPackage(pkg.id, pkg.version, "Cabinet", origin, url, 0L, emptyList())
        val fromHere = installed(com.mccal.folio.market.InstalledPackage.Origin.FOLIO_SOURCE, base)
        val fromFile = installed(com.mccal.folio.market.InstalledPackage.Origin.FILE, null)
        val builtIn = installed(com.mccal.folio.market.InstalledPackage.Origin.FOLIO_SOURCE, null)

        assertEquals("The panel opens off screen", incompatibleReason(fromHere, null, listOf(markedHere), on08))
        assertEquals("The panel opens off screen", incompatibleReason(fromFile, null, listOf(markedHere), on08))
        assertEquals(null, incompatibleReason(fromHere, null, listOf(markedHere), com.mccal.folio.market.FolioVersion(0, 7, 9)))
        // Folio's list speaks for Folio's packages, a source's for its own (ADR 0013).
        assertEquals(null, incompatibleReason(fromHere, list, listOf(plain), on08))
        assertEquals(null, incompatibleReason(builtIn, null, listOf(markedHere), on08))
        assertEquals("The panel opens off screen", incompatibleReason(builtIn, list, emptyList(), on08))
    }

    @Test fun `a package from a source is checked against that source alone, one from a file against them all`() = runTest {
        publish()
        sources.trust(base, key)
        val here = requireNotNull(sources.cached().single())
        val other = here.copy(source = here.source.copy(url = "https://other.example/folio/"))
        val pkg = requireNotNull(here.snapshot).index.packages.single()
        fun installed(origin: com.mccal.folio.market.InstalledPackage.Origin, url: String?) =
            com.mccal.folio.market.InstalledPackage(pkg.id, pkg.version, "Cabinet", origin, url, 0L, emptyList())
        val both = listOf(here, other)
        assertEquals(listOf(here), sourcesFor(installed(com.mccal.folio.market.InstalledPackage.Origin.FOLIO_SOURCE, base), both))
        assertEquals(listOf(here), sourcesFor(installed(com.mccal.folio.market.InstalledPackage.Origin.FOLIO_SOURCE, base.trimEnd('/') + "/"), both))
        assertEquals(both, sourcesFor(installed(com.mccal.folio.market.InstalledPackage.Origin.FILE, null), both))
        assertEquals(emptyList<SourceStatus>(), sourcesFor(installed(com.mccal.folio.market.InstalledPackage.Origin.FOLIO_SOURCE, null), both))
    }

    @Test fun `removing a source forgets its list and its key`() = runTest {
        publish()
        sources.trust(base, key)
        assertEquals(1, sources.cached().size)
        sources.forget(base)
        assertTrue(sources.sources().isEmpty())
        assertTrue(sources.cached().isEmpty())
        // Adding it again asks about the key again.
        assertTrue(sources.inspect(base) is RefreshResult.NeedsTrust)
    }

    @Test fun `an http source is refused`() = runTest {
        val result = sources.inspect("http://maya.example/folio/")
        assertEquals(RefreshResult.Reason.INSECURE, (result as RefreshResult.Failed).reason)
        assertTrue(host.requests.isEmpty())
    }

    private class RecordingHost : com.mccal.folio.market.PackageHost {
        override val capabilities = com.mccal.folio.market.Capability.entries.toSet()
        val applied = mutableListOf<com.mccal.folio.market.PackageChange>()
        override fun apply(change: com.mccal.folio.market.PackageChange): String = "before".also { applied += change }
        override fun restore(change: com.mccal.folio.market.PackageChange, snapshot: String) = Unit
    }
}
