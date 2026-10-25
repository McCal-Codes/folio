package com.mccal.folio

import com.mccal.folio.market.HttpResult
import com.mccal.folio.market.UrlHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** The one plain-http source the Market may use: the dev tweaks folder on the loopback stub, nothing else. */
class DevSourceLoopbackTest {
    @Test fun `only 127_0_0_1 port 8787 and the tweaks folder qualify`() {
        assertTrue(DevRouting.isLoopbackSource("http://127.0.0.1:8787/dev/file/tweaks/"))
        assertFalse(DevRouting.isLoopbackSource("http://localhost:8787/dev/file/tweaks/"))
        assertFalse(DevRouting.isLoopbackSource("http://10.0.0.1:8787/dev/file/tweaks/"))
        assertFalse(DevRouting.isLoopbackSource("http://127.0.0.2:8787/dev/file/tweaks/"))
        assertFalse(DevRouting.isLoopbackSource("http://[::1]:8787/dev/file/tweaks/"))
        assertFalse(DevRouting.isLoopbackSource("http://127.0.0.1:9999/dev/file/tweaks/"))
        assertFalse(DevRouting.isLoopbackSource("http://127.0.0.1/dev/file/tweaks/"))
        assertFalse(DevRouting.isLoopbackSource("https://127.0.0.1:8787/dev/file/tweaks/"))
        assertFalse(DevRouting.isLoopbackSource("http://127.0.0.1:8787/other/"))
        assertFalse(DevRouting.isLoopbackSource("http://127.0.0.1:8787/dev/file/tweaks/../../x"))
        assertFalse(DevRouting.isLoopbackSource("http://evil@127.0.0.1:8787/dev/file/tweaks/"))
    }

    private class Fake(url: URL, private val status: Int, private val location: String? = null) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getHeaderField(name: String?): String? = if (name == "Location") location else null
        override fun getHeaderFieldLong(name: String?, default: Long) = default
        override fun getInputStream(): InputStream = ByteArrayInputStream("ok".toByteArray())
    }

    private fun client(stubOn: Boolean, redirectTo: String) = UrlHttpClient(allowLocalhost = true,
        open = DevRouting.stubGuard({ stubOn }) { url ->
            if (url.protocol == "https") Fake(url, 302, redirectTo) else Fake(url, 200)
        })

    @Test fun `an https source cannot redirect down to plain http`() {
        // Another host: UrlHttpClient itself refuses.
        assertTrue(client(true, "http://evil.example/x").get("https://a.example/s/", 100, null as String?) is HttpResult.Failed)
        // The stub's own address while the stub is off: the guard refuses.
        val result = client(false, "http://127.0.0.1:8787/dev/file/tweaks/")
            .get("https://a.example/s/", 100, null as String?)
        assertTrue(result is HttpResult.Failed)
        // Switched on, the stub is reachable (that is the point of the switch).
        assertEquals(HttpResult.Body("ok".toByteArray(), null),
            client(true, "http://127.0.0.1:8787/dev/file/tweaks/").get("https://a.example/s/", 100, null as String?))
    }
}
