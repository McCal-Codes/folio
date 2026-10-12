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

    @Test fun `Spotlight's first frame gets the fresh ranking for the same apps and launches, and nothing otherwise`() {
        val cache = RankingCache()
        assertNull("nothing ranked yet", cache.peek(key.appIds, key.launchStamp, now = 0L))
        cache.get(key, now = 0L) { listOf("b", "a") }
        assertEquals(listOf("b", "a"), cache.peek(key.appIds, key.launchStamp, now = 60_000L))
        assertNull(cache.peek(key.appIds, launchStamp = 8, now = 60_000L))
        assertNull(cache.peek(listOf("a"), key.launchStamp, now = 60_000L))
        assertNull(cache.peek(key.appIds, key.launchStamp, now = RankingCache.FRESH_MS + 1))
    }

    @Test fun `a clock that went backwards is not fresh`() {
        assertTrue(RankingCache.fresh(madeAt = 1_000L, now = 1_000L))
        assertFalse(RankingCache.fresh(madeAt = 1_000L, now = 999L))
    }
}
