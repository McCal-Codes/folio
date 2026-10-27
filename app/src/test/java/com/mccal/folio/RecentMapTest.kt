package com.mccal.folio

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentMapTest {
    @Test fun keepsOnlyTheMostRecentlyUsedEntries() {
        val m = RecentMap<String, Int>(3)
        m["a"] = 1; m["b"] = 2; m["c"] = 3
        m["a"]            // a read refreshes it, so b is now the oldest
        m["d"] = 4
        assertNull(m["b"])
        assertEquals(1, m["a"]); assertEquals(3, m["c"]); assertEquals(4, m["d"])
        assertEquals(3, m.size())
    }

    @Test fun removeTakesAnEntryOut() {
        val m = RecentMap<String, Int>(4)
        m["k"] = 9
        assertEquals(9, m.remove("k")); assertNull(m["k"]); assertNull(m.remove("k"))
    }

    // The outside review's bug 4: the worker thread read and wrote the maps while the main thread removed from one. On a plain
    // access-ordered LinkedHashMap this corrupts it; with the lock it must finish with no exception at all.
    @Test fun twoThreadsHammeringItNeverThrow() {
        val m = RecentMap<String, Long>(64)
        val failure = AtomicReference<Throwable?>(null)
        val done = CountDownLatch(2)
        val pool = Executors.newFixedThreadPool(2)
        fun worker(seed: Long, writes: Boolean) = pool.execute {
            try {
                var x = seed
                repeat(300_000) {
                    x = x * 6364136223846793005L + 1442695040888963407L
                    val key = "n${(x ushr 33) % 96}"
                    if (writes) { m[key] = x; m[key] } else { m.remove(key); m[key] }
                }
            } catch (t: Throwable) { failure.compareAndSet(null, t) } finally { done.countDown() }
        }
        worker(1L, writes = true); worker(2L, writes = false)
        assertTrue("both threads finished in time", done.await(60, TimeUnit.SECONDS))
        pool.shutdownNow()
        assertNull("no exception from either thread: ${failure.get()}", failure.get())
        assertTrue("never larger than its capacity", m.size() <= 64)
    }
}
