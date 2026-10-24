package com.mccal.folio.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.util.Base64

/**
 * The only way a plain-http source gets in is a predicate the caller passes. By default there is none, and when there
 * is one it changes which addresses may be asked and nothing else: the signature is still required.
 */
class RepoClientInsecureTest {
    private val stub = "http://127.0.0.1:8787/dev/file/tweaks/"
    private val onlyStub: (String) -> Boolean = { it.startsWith("http://127.0.0.1:8787/") }

    private class Host : HttpClient {
        val files = HashMap<String, ByteArray>()
        val requests = mutableListOf<String>()
        override fun get(url: String, maxBytes: Int, etag: String?): HttpResult {
            requests += url
            return files[url]?.let { HttpResult.Body(it, null) } ?: HttpResult.Failed("not found", 404)
        }
    }

    private fun key(): SourceKey = SourceKey.parse(Base64.getEncoder().encodeToString(
        KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair().public.encoded))!!

    private fun client(host: Host, allow: (String) -> Boolean) =
        RepoClient(host, SourceStore(MemoryStore()), allowInsecure = allow)

    @Test fun `plain http is refused by default`() {
        val host = Host()
        val result = client(host, { false }).refresh(stub)
        assertEquals(RefreshResult.Reason.INSECURE, (result as RefreshResult.Failed).reason)
        assertTrue("nothing was asked", host.requests.isEmpty())
        assertEquals(RefreshResult.Reason.INSECURE, ((client(host, { false }).readKey(stub)) as RefreshResult.Failed).reason)
    }

    @Test fun `an allowed address is asked, others are still refused`() {
        val host = Host()
        val c = client(host, onlyStub)
        for (other in listOf("http://localhost:8787/", "http://10.0.0.1:8787/", "http://127.0.0.2:8787/", "http://127.0.0.1:9999/")) {
            assertEquals(other, RefreshResult.Reason.INSECURE, (c.refresh(other) as RefreshResult.Failed).reason)
        }
        assertTrue(host.requests.isEmpty())
        c.refresh(stub)
        assertTrue("the allowed address is now asked", host.requests.isNotEmpty())
    }

    @Test fun `an allowed address still needs the signature`() {
        val host = Host()
        val store = SourceStore(MemoryStore())
        val c = RepoClient(host, store, allowInsecure = onlyStub)
        c.trust(stub, key())
        host.files[stub + "entry.json"] = "{}".toByteArray()
        // No signature file at all.
        var result = c.refresh(stub, force = true)
        assertEquals(RefreshResult.Reason.SIGNATURE, (result as RefreshResult.Failed).reason)
        // A signature that is not the pinned key's.
        host.files[stub + "entry.json.sig"] = Base64.getEncoder().encodeToString(ByteArray(70) { 1 }).toByteArray()
        result = c.refresh(stub, force = true)
        assertEquals(RefreshResult.Reason.SIGNATURE, (result as RefreshResult.Failed).reason)
        assertFalse(result is RefreshResult.Updated)
    }

    @Test fun `the source list takes a plain-http source only when allowed`() {
        val deny = SourceList(MemoryStore())
        assertFalse(deny.add(Source(stub)))
        val allow = SourceList(MemoryStore()) { it.startsWith("http://127.0.0.1:8787/") }
        assertTrue(allow.add(Source(stub)))
        assertFalse(allow.add(Source("http://localhost:8787/dev/file/tweaks/")))
        assertFalse(allow.add(Source("http://10.0.0.1:8787/")))
    }
}
