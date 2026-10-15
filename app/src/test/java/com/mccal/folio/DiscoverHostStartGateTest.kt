package com.mccal.folio

import org.junit.Assert.*
import org.junit.Test

class DiscoverHostStartGateTest {
    @Test fun discoverHostStartsOnlyWhileHomeIsResumed() {
        // #371: after a Spotlight launch Home is started but not resumed; starting Discover then covered the new app.
        assertFalse(mayStartDiscoverHost(androidx.lifecycle.Lifecycle.State.STARTED))
        assertFalse(mayStartDiscoverHost(androidx.lifecycle.Lifecycle.State.CREATED))
        assertTrue(mayStartDiscoverHost(androidx.lifecycle.Lifecycle.State.RESUMED))
    }

    @Test fun skippedLaunchCannotBlockAReplacementMainActivity() {
        val gate = DiscoverHostStartGate(10)
        val oldMain = Any()
        val replacementMain = Any()
        val skipped = requireNotNull(gate.request(oldMain))

        assertNull(gate.request(oldMain))
        val replacement = requireNotNull(gate.request(replacementMain))
        assertNotEquals(skipped, replacement)
        assertFalse(gate.attached(skipped))
        assertTrue(gate.attached(replacement))
    }

    @Test fun missingHostRetriesOnceThenWaitsForAnExplicitRetry() {
        val gate = DiscoverHostStartGate(20)
        val main = Any()
        val first = requireNotNull(gate.request(main))
        assertEquals(DiscoverStartTimeout.RETRY, gate.timedOut(main, first))
        val second = requireNotNull(gate.request(main))
        assertEquals(DiscoverStartTimeout.FAILED, gate.timedOut(main, second))
        assertNull(gate.request(main))

        gate.reset(main)
        assertNotNull(gate.request(main))
    }

    @Test fun staleTimeoutCannotReplaceAnAttachedHost() {
        val gate = DiscoverHostStartGate(30)
        val main = Any()
        val token = requireNotNull(gate.request(main))
        assertTrue(gate.attached(token))
        assertTrue(gate.attached(token))
        assertEquals(DiscoverStartTimeout.STALE, gate.timedOut(main, token))
    }
}
