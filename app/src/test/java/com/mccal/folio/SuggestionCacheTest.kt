package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionCacheTest {
    private val key = RankingCache.Key(listOf("a", "b", "c"), launchStamp = 7, usageAccess = true)

    @Test fun `opening Spotlight again within a quarter hour reuses the ranking`() {
        val cache = RankingCache()
        var reads = 0
        assertEquals(listOf("b", "a"), cache.get(key, now = 0L) { reads++; listOf("b", "a") })
        assertEquals(listOf("b", "a"), cache.get(key, now = 14 * 60_000L) { reads++; listOf("c") })
        assertEquals(1, reads)
    }

    @Test fun `after a quarter hour it ranks again`() {
        val cache = RankingCache()
        cache.get(key, now = 0L) { listOf("b", "a") }
        assertEquals(listOf("c"), cache.get(key, now = RankingCache.FRESH_MS) { listOf("c") })
    }

    @Test fun `a new launch, a changed app list or Usage Access ranks again at once`() {
        val cache = RankingCache()
        cache.get(key, now = 0L) { listOf("b", "a") }
        assertEquals(listOf("1"), cache.get(key.copy(launchStamp = 8), now = 1L) { listOf("1") })
        assertEquals(listOf("2"), cache.get(key.copy(appIds = listOf("a", "b")), now = 2L) { listOf("2") })
        assertEquals(listOf("3"), cache.get(key.copy(usageAccess = false), now = 3L) { listOf("3") })
    }

    @Test fun `Spotlight's first frame gets the fresh ranking for the same key, and nothing otherwise`() {
        val cache = RankingCache()
        assertNull("nothing ranked yet", cache.peek(key, now = 0L))
        cache.get(key, now = 0L) { listOf("b", "a") }
        assertEquals(listOf("b", "a"), cache.peek(key, now = 60_000L))
        assertNull(cache.peek(key.copy(launchStamp = 8), now = 60_000L))
        assertNull(cache.peek(key.copy(appIds = listOf("a")), now = 60_000L))
        assertNull("Usage Access turned off since", cache.peek(key.copy(usageAccess = false), now = 60_000L))
        assertNull("a new time zone", cache.peek(key.copy(zoneOffset = 3_600_000), now = 60_000L))
        assertNull(cache.peek(key, now = RankingCache.FRESH_MS + 1))
    }

    @Test fun `the widget's ranking and Spotlight's don't push each other out`() {
        val cache = RankingCache()
        val widget = key.copy(appIds = listOf("a", "b", "c", "hidden"))
        var reads = 0
        repeat(3) { minute ->
            cache.get(key, now = minute * 60_000L) { reads++; listOf("b") }
            cache.get(widget, now = minute * 60_000L) { reads++; listOf("hidden") }
        }
        assertEquals(2, reads)
    }

    @Test fun `a ranking that finishes late does not replace a newer one`() {
        val cache = RankingCache()
        cache.get(key, now = 10L) { listOf("new") }
        // Made for an earlier time, so it isn't fresh at 5 and is ranked, but must not be kept over the one from 10.
        assertEquals(listOf("old"), cache.get(key, now = 5L) { listOf("old") })
        assertEquals(listOf("new"), cache.peek(key, now = 11L))
    }

    @Test fun `peek does not wait for a ranking in progress`() {
        val cache = RankingCache()
        cache.get(key, now = 0L) { listOf("b", "a") }
        val ranking = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val other = key.copy(launchStamp = 8)
        val slow = Thread { cache.get(other, now = 1L) { ranking.countDown(); release.await(); listOf("c") } }.apply { start() }
        try {
            assertTrue(ranking.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val started = System.nanoTime()
            assertEquals(listOf("b", "a"), cache.peek(key, now = 2L))
            assertTrue("peek waited for the ranking", System.nanoTime() - started < 1_000_000_000L)
        } finally {
            release.countDown(); slow.join(5_000)
        }
        assertEquals(listOf("c"), cache.peek(other, now = 2L))
    }

    @Test fun `a clock that went backwards is not fresh`() {
        assertTrue(RankingCache.fresh(madeAt = 1_000L, now = 1_000L))
        assertFalse(RankingCache.fresh(madeAt = 1_000L, now = 999L))
    }
}
