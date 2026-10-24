package com.mccal.folio

import com.mccal.folio.market.HttpResult
import com.mccal.folio.market.UrlHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** The dev code goes to the worker's /dev/ addresses and nowhere else, redirects included. */
class DevSourceRoutingTest {
    private val base = "https://folio-supporter-codes.mccal.workers.dev"

    @Test fun `only the exact host and the dev prefix are dev routes`() {
        assertTrue(DevRouting.isDevRoute("$base/dev/file/builds.json", base))
        assertTrue(DevRouting.isDevRoute("$base/dev/apk/tag", base))
        assertFalse(DevRouting.isDevRoute("$base/beta/releases", base))
        assertFalse(DevRouting.isDevRoute("$base/dev", base))
        assertFalse(DevRouting.isDevRoute("$base/dev/../beta/releases", base))
        assertFalse(DevRouting.isDevRoute("https://evil.example/dev/file/x", base))
        assertFalse(DevRouting.isDevRoute("https://folio-supporter-codes.mccal.workers.dev.evil.example/dev/x", base))
        assertFalse(DevRouting.isDevRoute("https://folio-supporter-codes.mccal.workers.dev@evil.example/dev/x", base))
        assertFalse(DevRouting.isDevRoute("http://folio-supporter-codes.mccal.workers.dev/dev/x", base))
        assertFalse(DevRouting.isDevRoute("$base:8443/dev/x", base))
    }

    @Test fun `the loopback stub is a dev route only for itself`() {
        assertTrue(DevRouting.isDevRoute("http://127.0.0.1:8787/dev/file/a", DevSource.LOOPBACK_BASE))
        assertFalse(DevRouting.isDevRoute("http://127.0.0.1:9999/dev/file/a", DevSource.LOOPBACK_BASE))
        assertFalse(DevRouting.isDevRoute("$base/dev/file/a", DevSource.LOOPBACK_BASE))
    }

    private class Fake(url: URL, private val status: Int, private val location: String? = null, private val body: String = "") : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getHeaderField(name: String?): String? = if (name == "Location") location else null
        override fun getHeaderFieldLong(name: String?, default: Long) = default
        override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray())
    }

    @Test fun `the header is set on a dev route and not on any other address`() {
        val opened = mutableListOf<Fake>()
        val open = DevRouting.opener({ base }, { "CODE" }) { url -> Fake(url, 200).also(opened::add) }
        open(URL("$base/dev/file/builds.json"))
        open(URL("$base/beta/releases"))
        open(URL("https://api.github.com/x"))
        assertEquals("Bearer CODE", opened[0].getRequestProperty("Authorization"))
        assertNull(opened[1].getRequestProperty("Authorization"))
        assertNull(opened[2].getRequestProperty("Authorization"))
    }

    @Test fun `no code stored means no header at all`() {
        val opened = mutableListOf<Fake>()
        DevRouting.opener({ base }, { null }) { url -> Fake(url, 200).also(opened::add) }(URL("$base/dev/file/x"))
        assertNull(opened[0].getRequestProperty("Authorization"))
    }

    @Test fun `a redirect off the worker never carries the code`() {
        val opened = mutableListOf<Fake>()
        val open = DevRouting.opener({ base }, { "CODE" }) { url ->
            val fake = if (url.host == "evil.example") Fake(url, 200, body = "payload") else Fake(url, 302, location = "https://evil.example/dev/file/x")
            fake.also(opened::add)
        }
        val result = UrlHttpClient(open = open).get("$base/dev/file/builds.json", 1000, null as String?)
        assertTrue(result is HttpResult.Body)
        assertEquals(2, opened.size)
        assertEquals("Bearer CODE", opened[0].getRequestProperty("Authorization"))
        assertNull("the second hop is another host", opened[1].getRequestProperty("Authorization"))
    }

    @Test fun `a redirect to a plain-http address is refused by the client`() {
        val open = DevRouting.opener({ base }, { "CODE" }) { url -> Fake(url, 302, location = "http://evil.example/x") }
        assertTrue(UrlHttpClient(open = open).get("$base/dev/file/a", 1000, null as String?) is HttpResult.Failed)
    }
}
