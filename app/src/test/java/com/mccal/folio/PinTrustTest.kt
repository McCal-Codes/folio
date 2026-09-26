package com.mccal.folio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A pin request is trusted on Android's word, not its own. */
class PinTrustTest {
    // Plain strings stand in for ComponentName, which is only a stub in a unit test. The checks only compare.
    private val clock = "com.example.clock/.Widget"
    private val other = "com.example.bank/.Widget"

    @Test fun `a widget that isn't installed is turned away`() {
        assertTrue(PinTrust.installed(clock, listOf(other, clock)))
        assertFalse(PinTrust.installed(clock, listOf(other)))
        assertFalse(PinTrust.installed(clock, emptyList()))
    }

    @Test fun `an id is trusted only when it is bound to the widget the request named`() {
        assertTrue(PinTrust.bound(clock, clock))
        assertFalse("a forged accept() binds nothing", PinTrust.bound(clock, null))
        assertFalse("bound, but to something else", PinTrust.bound(clock, other))
    }
}
