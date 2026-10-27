package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedCaptureTest {
    private var clock = 0L
    private fun capture() = SharedCapture<String, Int>(2_000) { clock }

    @Test fun onlyTheFirstCallerInAnIntervalTakesThePicture() {
        val c = capture()
        assertTrue(c.claim()); assertFalse(c.claim()); assertFalse(c.claim())
        clock += 2_000
        assertTrue(c.claim())
    }

    @Test fun everyRegisteredClockGetsItsOwnResultFromOneCapture() {
        val c = capture()
        c.register("a"); c.register("b")
        assertTrue(c.claim())
        c.store(c.keys().associateWith { it.length + if (it == "a") 1 else 2 })
        // The second clock did not take the picture, and still has its measurement.
        assertEquals(2, c.cached("a")); assertEquals(3, c.cached("b"))
    }

    @Test fun aClockThatJoinsLateWaitsForTheNextPicture() {
        val c = capture()
        c.register("a"); c.claim(); c.store(mapOf("a" to 1))
        c.register("b")
        assertNull(c.cached("b"))
        clock += 2_000
        assertTrue(c.claim())
        assertTrue("b" in c.keys())
    }

    @Test fun onlyTheMostRecentKeysAreKept() {
        val c = capture()
        for (n in 0..SharedCapture.MAX_KEYS) { c.register("k$n"); c.store(mapOf("k$n" to n)) }
        assertEquals(SharedCapture.MAX_KEYS, c.keys().size)
        assertFalse("k0" in c.keys()); assertNull(c.cached("k0"))
        assertEquals(SharedCapture.MAX_KEYS, c.cached("k${SharedCapture.MAX_KEYS}"))
    }
}
