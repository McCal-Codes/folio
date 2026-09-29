package com.mccal.folio.market

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * The repo client against Folio's real source (`docs/sdk/source/`), served over a stand-in for the network and signed
 * with ECDSA P-256 keys the test generates. The Cabinet entry also gets the `url`, `sha256` and `size` a hosted source
 * adds, so both shapes are covered: the built-in source and a downloadable one.
 *
 * Every threat in docs/sdk/threat-model.md that Phase 2 answers for (T1–T5, T13, T14) has a case here, and each has to
 * fail safely: the cached copy stays, and nothing unsigned is ever used.
 */
class RepoClientTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private val realIndex = File(root, "docs/sdk/source/index.json").readText()
    private val realRevocations = File(root, "docs/sdk/source/revoked.json").readText()
    private val base = "https://folio.mccal.dev/source/"
    private lateinit var keys: KeyPair
    private lateinit var key: SourceKey
    private lateinit var source: FakeHost
    private lateinit var store: SourceStore
    private lateinit var client: RepoClient
    private var now = 1_789_000_000L

    /** Serves the source's files without a network, and counts requests (T14: refreshes must stay cheap). */
    private class FakeHost : HttpClient {
        val files = HashMap<String, ByteArray>()
        val requests = mutableListOf<String>()
        var etag: String? = null
        var answer304 = false

        override fun get(url: String, maxBytes: Int, etag: String?): HttpResult {
            requests += url
            if (answer304 && etag != null && etag == this.etag) return HttpResult.NotModified
            val bytes = files[url] ?: return HttpResult.Failed("not found", 404)
            if (bytes.size > maxBytes) return HttpResult.TooLarge
            return HttpResult.Body(bytes, this.etag)
        }
    }

    @Before fun setUp() {
        keys = newKeyPair()
        key = keyOf(keys)
        source = FakeHost()
        store = SourceStore(MemoryStore())
        client = RepoClient(source, store) { now }
        publish(hostedIndex())
        publishRevocations(realRevocations)
        client.trust(base, key)
    }

    private fun newKeyPair() = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private fun keyOf(pair: KeyPair) = requireNotNull(SourceKey.parse(Base64.getEncoder().encodeToString(pair.public.encoded)))

    private fun sign(bytes: ByteArray, pair: KeyPair = keys): ByteArray = Base64.getEncoder().encodeToString(
        Signature.getInstance(SourceKey.ALGORITHM).run { initSign(pair.private); update(bytes); sign() },
    ).toByteArray()

    /**
     * The real index, with the download fields a hosted source adds to Cabinet. Its hash stands in for the `.foliopkg`
     * file's until the publishing tool packs one (Phase 8); everything else is the published content.
     */
    private fun hostedIndex(change: (JSONObject) -> Unit = {}): String {
        val json = JSONObject(realIndex)
        val packages = json.getJSONArray("packages")
        val cabinet = (0 until packages.length()).map { packages.getJSONObject(it) }.first { it.getString("id") == CABINET }
        cabinet.put("url", "packages/${CABINET}_1.0.0.foliopkg")
            .put("sha256", sha256Hex(File(root, "docs/sdk/source/packages/cabinet/manifest.json").readBytes()))
            .put("size", 24680)
        change(json)
        return json.toString()
    }

    /** Publishes an index with a matching signed entry, the way the publishing job will. */
    private fun publish(index: String, timestamp: Long = now - 60, maxAge: Long = 604800, pair: KeyPair = keys, keyId: String = key.keyId) {
        val bytes = index.toByteArray()
        source.files[base + "index.json"] = bytes
        val entry = """
            {"format":1,"keyId":"$keyId","timestamp":$timestamp,"maxAge":$maxAge,
             "index":{"path":"index.json","sha256":"${sha256Hex(bytes)}","size":${bytes.size}}}
        """.trimIndent()
        source.files[base + "entry.json"] = entry.toByteArray()
        source.files[base + "entry.json.sig"] = sign(entry.toByteArray(), pair)
        source.files[base + "key.pub"] = key.base64.toByteArray()
    }

    private fun publishRevocations(json: String, pair: KeyPair = keys) {
        source.files[base + "revoked.json"] = json.toByteArray()
        source.files[base + "revoked.json.sig"] = sign(json.toByteArray(), pair)
    }

    private fun refresh(force: Boolean = true, revocations: RevocationList? = null) = client.refresh(base, force, revocations)

    private fun failure(result: RefreshResult): RefreshResult.Failed {
        assertTrue("expected a failure, got $result", result is RefreshResult.Failed)
        return result as RefreshResult.Failed
    }

    /** A phone that has never seen this source: trusted key, no cached list. */
    private fun freshClient() = RepoClient(source, SourceStore(MemoryStore())) { now }.also { it.trust(base, key) }

    private fun SourceSnapshot.cabinet() = index.packages.first { it.id == CABINET }

    @Test fun `Folio's own source refreshes, with a manifest copy for every package`() {
        val snapshot = (refresh() as RefreshResult.Updated).snapshot
        assertEquals("Folio", snapshot.index.name.english)
        assertEquals(10, snapshot.index.packages.size)
        assertEquals("Theme of the week", snapshot.index.featured.first().label?.english)
        val cabinet = snapshot.cabinet()
        assertEquals("Cabinet", cabinet.manifest?.name?.english)
        assertEquals(setOf(Capability.APP_PANELS), cabinet.manifest?.requiredFeatures)
        assertEquals(setOf(PackagePermission.TWEAKS), cabinet.manifest?.permissions)
        assertTrue("the hosted entry can be downloaded", cabinet.installable)
        // The built-in packages ship inside the app, so they have nothing to download.
        val clear = snapshot.index.packages.first { it.id == "com.mccal.folio.theme.clear" }
        assertTrue(!clear.installable && clear.manifest != null && clear.needs.isEmpty())
        assertEquals(setOf(Capability.THEME), clear.manifest?.requiredFeatures)
        // entry, signature, index, revocation list and its signature: five requests, and no repository API.
        assertEquals(
            listOf("entry.json", "entry.json.sig", "index.json", "revoked.json", "revoked.json.sig"),
            source.requests.map { it.removePrefix(base) },
        )
    }

    @Test fun `a new source has to be trusted by fingerprint first`() {
        store.forget(base)
        val result = client.refresh(base)
        assertTrue("$result", result is RefreshResult.NeedsTrust)
        val trust = result as RefreshResult.NeedsTrust
        assertEquals(key, trust.key)
        assertNull(trust.previous)
        assertEquals(16, trust.key.keyId.length)
        assertEquals(64, trust.key.fingerprint.length)
        assertEquals(16, trust.key.fingerprintGroups.split(" ").size)
        client.trust(base, trust.key)
        assertTrue(client.refresh(base, force = true) is RefreshResult.Updated)
    }

    @Test fun `T1 a tampered index is refused and the cached copy stays`() {
        val good = (refresh() as RefreshResult.Updated).snapshot
        // Swapped after the entry was signed. A phone with no copy yet downloads it, and the size check stops it.
        source.files[base + "index.json"] = hostedIndex { it.put("name", "Folio (not really)") }.toByteArray()
        assertEquals(RefreshResult.Reason.SIZE, failure(freshClient().refresh(base, force = true)).reason)
        // A change of exactly the signed length, so only the hash catches it.
        source.files[base + "index.json"] = hostedIndex().replace("Swipe up on an app icon", "Swipe up on an app ICON").toByteArray()
        assertEquals(RefreshResult.Reason.HASH, failure(freshClient().refresh(base, force = true)).reason)
        // A phone that already has the signed list doesn't download the swapped one, and keeps showing what it had.
        assertEquals(good.index, (refresh() as RefreshResult.Unchanged).snapshot.index)
        assertEquals(setOf(PackagePermission.TWEAKS), client.cachedSnapshot(base)?.cabinet()?.manifest?.permissions)
    }

    @Test fun `T2 an older entry is refused`() {
        refresh()
        publish(hostedIndex(), timestamp = now - 10_000)
        assertEquals(RefreshResult.Reason.ROLLBACK, failure(refresh()).reason)
    }

    @Test fun `T2 re-adding a source with the key it already has keeps its rollback floor`() {
        refresh()
        assertTrue("already trusted, so nothing to confirm", client.readKey(base) !is RefreshResult.NeedsTrust)
        client.trust(base, key)
        publish(hostedIndex(), timestamp = now - 10_000)
        assertEquals(RefreshResult.Reason.ROLLBACK, failure(refresh()).reason)
    }

    @Test fun `T3 a source frozen past maxAge is refused`() {
        publish(hostedIndex(), timestamp = now - 100, maxAge = 3600)
        assertTrue(refresh() is RefreshResult.Updated)
        assertFalse(client.isStale(base))
        now += 7200
        // Still there to browse, but nothing is installed from a list past its maxAge.
        assertTrue(client.isStale(base))
        val failed = failure(refresh())
        assertEquals(RefreshResult.Reason.EXPIRED, failed.reason)
        assertNotNull(failed.cached)
    }

    @Test fun `T4 another key's signature is refused, and a changed key needs the user again`() {
        val attacker = newKeyPair()
        publish(hostedIndex(), pair = attacker)
        assertEquals(RefreshResult.Reason.SIGNATURE, failure(refresh()).reason)
        // Signed with the pinned key but naming a different key id.
        publish(hostedIndex(), keyId = "0123456789ABCDEF")
        assertEquals(RefreshResult.Reason.KEY_MISMATCH, failure(refresh()).reason)
        // A source that rotates its key: the user confirms the new fingerprint, and pinning resets the rollback floor.
        val rotated = keyOf(attacker)
        source.files[base + "key.pub"] = rotated.base64.toByteArray()
        assertEquals(key, (client.readKey(base) as RefreshResult.NeedsTrust).previous)
        client.trust(base, rotated)
        publish(hostedIndex(), pair = attacker, keyId = rotated.keyId)
        assertTrue(refresh() is RefreshResult.Updated)
    }

    @Test fun `an unsigned, missing or insecure source is refused`() {
        source.files.remove(base + "entry.json.sig")
        assertEquals(RefreshResult.Reason.SIGNATURE, failure(refresh()).reason)
        source.files.clear()
        assertEquals(RefreshResult.Reason.MISSING, failure(refresh()).reason)
        assertEquals(RefreshResult.Reason.INSECURE, failure(client.refresh("http://folio.mccal.dev/source/")).reason)
    }

    @Test fun `T5 a revoked package is turned off with its reason, and a source can disown itself`() {
        // Folio's own list is empty; this is what a takedown would look like.
        publishRevocations("""{"format":1,"timestamp":${now - 30},"packages":[{"id":"$CABINET","versions":["1.0.0"],"reason":"Test: pulled at the author's request"}]}""")
        val snapshot = (refresh() as RefreshResult.Updated).snapshot
        assertEquals("Test: pulled at the author's request", snapshot.revokedReason(snapshot.cabinet()))
        assertEquals(9, snapshot.usablePackages.size)
        // "*" covers every version.
        publishRevocations("""{"format":1,"timestamp":${now - 20},"packages":[{"id":"$CABINET","versions":["*"],"reason":"Test: every version"}]}""")
        assertEquals("Test: every version", (refresh() as RefreshResult.Unchanged).snapshot.let { it.revokedReason(it.cabinet()) })
        // An unsigned, older or missing list is ignored, and the newest one Folio has keeps applying.
        fun stillRevoked() = assertEquals("Test: every version", (refresh() as RefreshResult.Unchanged).snapshot.let { it.revokedReason(it.cabinet()) })
        source.files[base + "revoked.json.sig"] = "not a signature".toByteArray()
        stillRevoked()
        publishRevocations("""{"format":1,"timestamp":${now - 40_000},"packages":[]}""")
        stillRevoked()
        source.files.remove(base + "revoked.json")
        stillRevoked()
        assertEquals("Test: every version", client.cachedSnapshot(base)!!.let { it.revokedReason(it.cabinet()) })
        // A source listed in a trusted source's revocation list is dropped before Folio even calls it.
        val known = (RevocationList.parse("""{"format":1,"timestamp":$now,"packages":[],"sources":[{"url":"$base","reason":"Test: hosts malware"}]}""") as ParseResult.Ok).value
        val failed = failure(refresh(revocations = known))
        assertEquals(RefreshResult.Reason.REVOKED, failed.reason)
        assertEquals("Test: hosts malware", failed.message)
    }

    @Test fun `T14 refreshing is cheap - six hours apart, ETags, and no download when the hash already matches`() {
        assertTrue(refresh() is RefreshResult.Updated)
        source.requests.clear()
        // A background refresh within six hours makes no requests at all.
        now += 60
        assertTrue(client.refresh(base) is RefreshResult.Unchanged)
        assertTrue(source.requests.isEmpty())
        // After six hours it asks again, but the entry still pins the hash it has, so the index isn't downloaded.
        now += RepoClient.MIN_REFRESH_SECONDS
        publish(hostedIndex(), timestamp = now - 5)
        assertTrue(client.refresh(base) is RefreshResult.Unchanged)
        assertTrue("index.json" !in source.requests.map { it.removePrefix(base) })
        // A changed index is downloaded with the stored ETag, and a 304 that contradicts the signed hash is refused.
        source.etag = "\"v2\""
        publish(hostedIndex { it.put("description", "Folio's own themes and tweaks.") }, timestamp = now)
        assertTrue(client.refresh(base, force = true) is RefreshResult.Updated)
        source.answer304 = true
        publish(hostedIndex { it.put("description", "Changed again.") }, timestamp = now + 1)
        assertEquals(RefreshResult.Reason.HASH, failure(client.refresh(base, force = true)).reason)
    }

    @Test fun `a source that publishes a list Folio can't read recovers when it publishes a good one`() {
        publish(hostedIndex())
        assertTrue(refresh() is RefreshResult.Updated)

        // A new release whose list this Folio can't read: one field it doesn't know, or a broken file.
        source.etag = "release-2"
        source.answer304 = true
        publish(hostedIndex { it.put("format", 99) }, timestamp = now - 30)
        assertEquals(RefreshResult.Reason.PARSE, failure(refresh()).reason)

        // The next refresh must not report tampering because of that. Folio still has the list it could read, and
        // the source's next good release is taken normally.
        assertEquals(RefreshResult.Reason.PARSE, failure(refresh()).reason)
        source.etag = "release-3"
        publish(hostedIndex { it.put("name", "Maya's packages") }, timestamp = now - 20)
        val recovered = refresh()
        assertTrue("$recovered", recovered is RefreshResult.Updated)
        assertEquals("Maya's packages", (recovered as RefreshResult.Updated).snapshot.index.name.english)
    }

    @Test fun `the cached copy keeps the store working offline`() {
        val fresh = (refresh() as RefreshResult.Updated).snapshot
        source.files.clear()
        val failed = failure(refresh())
        assertEquals(fresh.index, failed.cached?.index)
        assertEquals(fresh.index, client.cachedSnapshot(base)?.index)
        // The trailing slash doesn't matter.
        assertEquals(fresh.index, client.cachedSnapshot("https://folio.mccal.dev/source")?.index)
        client.forget(base)
        assertNull(client.cachedSnapshot(base))
    }

    @Test fun `a broken index is refused, and packages needing a newer Folio don't hide the rest`() {
        publish("""{"format":1,"name":"Folio","packages":"nope"}""")
        assertEquals(RefreshResult.Reason.PARSE, failure(refresh()).reason)
        publish(hostedIndex().replace("\"kind\":[\"tweakBundle\"]", "\"kind\":[\"hologram\"]"))
        val snapshot = (refresh() as RefreshResult.Updated).snapshot
        assertEquals(10, snapshot.index.packages.size)
        assertEquals(6, snapshot.index.packages.count { it.needs == listOf("a newer Folio") })
        assertEquals(4, snapshot.index.packages.count { it.manifest?.section == Section.THEMES })
        assertTrue(snapshot.notes.first().contains("needs a newer Folio"))
    }

    @Test fun `an index entry that disagrees with its manifest copy is refused`() {
        publish(hostedIndex { it.getJSONArray("packages").getJSONObject(0).put("version", "9.9.9") })
        assertEquals(RefreshResult.Reason.PARSE, failure(refresh()).reason)
    }

    @Test fun `state survives a restart through the file store`() {
        val dir = java.nio.file.Files.createTempDirectory("folio-source").toFile().also { it.deleteOnExit() }
        val first = RepoClient(source, SourceStore(FileStore(dir))) { now }
        first.trust(base, key)
        assertTrue(first.refresh(base, force = true) is RefreshResult.Updated)
        val second = RepoClient(source, SourceStore(FileStore(dir))) { now }
        assertEquals("Folio", second.cachedSnapshot(base)?.index?.name?.english)
        // A rollback is still caught after the restart.
        publish(hostedIndex(), timestamp = now - 50_000)
        assertEquals(RefreshResult.Reason.ROLLBACK, failure(second.refresh(base, force = true)).reason)
    }

    private companion object {
        const val CABINET = "com.mccal.folio.cabinet"
    }
}
