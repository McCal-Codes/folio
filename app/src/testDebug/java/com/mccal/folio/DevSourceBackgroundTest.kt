package com.mccal.folio

import com.mccal.folio.market.HttpClient
import com.mccal.folio.market.HttpResult
import com.mccal.folio.market.MemoryStore
import com.mccal.folio.market.RepoClient
import com.mccal.folio.market.Source
import com.mccal.folio.market.SourceList
import com.mccal.folio.market.SourceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A background run never asks the dev source, so the dev code cannot leave from a job nobody opened. */
class DevSourceBackgroundTest {
    private val dev = "https://folio-supporter-codes.mccal.workers.dev/dev/file/tweaks/"
    private val other = "https://other.example/source/"

    private class Host : HttpClient {
        val asked = mutableListOf<String>()
        override fun get(url: String, maxBytes: Int, etag: String?): HttpResult {
            asked += url
            return HttpResult.Failed("not found", 404)
        }
    }

    private fun sources(host: Host): MarketSources {
        val store = MemoryStore()
        val list = SourceList(store)
        list.add(Source(dev)); list.add(Source(other))
        return MarketSources(RepoClient(host, SourceStore(store)), list, host, Dispatchers.Unconfined,
            skipInBackground = { DevSource.isDevAddress(it) })
    }

    @Test fun `a background refresh leaves the dev source alone`() {
        val host = Host()
        runBlocking { sources(host).refreshAll(force = true, background = true) }
        assertTrue(host.asked.isNotEmpty())
        assertTrue(host.asked.all { it.startsWith(other) })
    }

    @Test fun `a refresh the person asked for still reaches it`() {
        val host = Host()
        runBlocking { sources(host).refreshAll(force = true) }
        assertTrue(host.asked.any { it.startsWith(dev) })
        assertTrue(host.asked.any { it.startsWith(other) })
    }

    @Test fun `the dev addresses are recognized on the worker and on the stub only`() {
        assertTrue(DevSource.isDevAddress(dev))
        assertTrue(DevSource.isDevAddress("http://127.0.0.1:8787/dev/file/tweaks/"))
        assertFalse(DevSource.isDevAddress(other))
        assertFalse(DevSource.isDevAddress("https://folio-supporter-codes.mccal.workers.dev/beta/releases"))
        assertEquals(false, DevSource.isDevAddress("https://evil.example/dev/file/tweaks/"))
    }
}
