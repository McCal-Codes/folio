package com.mccal.folio

import com.mccal.folio.market.HttpClient
import com.mccal.folio.market.HttpResult
import com.mccal.folio.market.MarketPrefs
import com.mccal.folio.market.MemoryStore
import com.mccal.folio.market.RepoClient
import com.mccal.folio.market.Source
import com.mccal.folio.market.SourceKey
import com.mccal.folio.market.SourceList
import com.mccal.folio.market.SourceStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keyd is listed for everyone once the Market is open, by offering its source one time. The rules that matter: it goes
 * in once, a person who forgets it never sees it again, and a supporter's own copy is left alone.
 */
class KeydSourceOfferTest {
    /** Nothing answers, like a phone with no network: the source still goes into the list, as a supporter's does. */
    private object Offline : HttpClient {
        override fun get(url: String, maxBytes: Int, etag: String?): HttpResult = HttpResult.Failed("offline", null)
    }

    private val store = MemoryStore()
    private val prefs = MarketPrefs(store)
    private val sources = MarketSources(
        client = RepoClient(Offline, SourceStore(store)) { 1_789_000_000L },
        list = SourceList(store),
        http = Offline,
        io = kotlinx.coroutines.Dispatchers.Unconfined,
    )

    @Test fun `the first offer adds Keyd's source as a recommended one and says so once`() = runTest {
        assertTrue(MarketAccess.offerKeydSource(sources, prefs))
        val added = sources.sources().single()
        assertEquals(normalized(SUPPORTER_SOURCE), added.url)
        assertEquals(Source.Kind.RECOMMENDED, added.kind)
        assertTrue(prefs.keydSourceOffered)
        assertFalse("a second look adds nothing", MarketAccess.offerKeydSource(sources, prefs))
        assertEquals(1, sources.sources().size)
    }

    @Test fun `a person who forgets it does not get it back`() = runTest {
        MarketAccess.offerKeydSource(sources, prefs)
        sources.forget(SUPPORTER_SOURCE)
        assertTrue(sources.sources().isEmpty())
        assertFalse(MarketAccess.offerKeydSource(sources, prefs))
        assertTrue("and still nothing in the list", sources.sources().isEmpty())
    }

    @Test fun `a supporter's own copy is left exactly as it is`() = runTest {
        val key = requireNotNull(SourceKey.parse(SUPPORTER_SOURCE_KEY))
        sources.trust(SUPPORTER_SOURCE, key, Source.Kind.SUPPORTER)
        assertFalse(MarketAccess.offerKeydSource(sources, prefs))
        assertEquals(Source.Kind.SUPPORTER, sources.sources().single().kind)
        assertTrue("it counts as offered, so forgetting it later is final too", prefs.keydSourceOffered)
    }

    @Test fun `the offered state is not set until someone asks`() {
        assertFalse(MarketPrefs(MemoryStore()).keydSourceOffered)
    }

    private fun normalized(url: String) = com.mccal.folio.market.normalizeSourceUrl(url)
}
