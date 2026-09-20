package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The two locks on the developer switches. They decide whether one build can be made to look like a supporter's, so
 * the interesting cases are the ones where the answer has to be no.
 */
class DevTest {
    private val key = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE"

    @Test fun `only a development build with a development key can unlock`() {
        assertEquals(true, devPossible("com.mccal.folio.dev", key))
        // A release build: no suffix, and nothing compiled in to check a code against.
        assertEquals(false, devPossible("com.mccal.folio", key))
        assertEquals(false, devPossible("com.mccal.folio.dev", ""))
        assertEquals(false, devPossible("com.mccal.folio", ""))
        // Not fooled by a package that merely contains the word.
        assertEquals(false, devPossible("com.mccal.folio.devious.app", key))
    }

    @Test fun `locked always behaves as free, whatever was stored`() {
        assertEquals(Dev.Face.FREE, devFace(unlocked = false, stored = "supporter"))
        assertEquals(Dev.Face.FREE, devFace(unlocked = false, stored = "dev"))
    }

    @Test fun `unlocked reads the face back, and falls to free when it is unreadable`() {
        assertEquals(Dev.Face.SUPPORTER, devFace(unlocked = true, stored = "supporter"))
        assertEquals(Dev.Face.DEV, devFace(unlocked = true, stored = "dev"))
        assertEquals(Dev.Face.FREE, devFace(unlocked = true, stored = null))
        assertEquals(Dev.Face.FREE, devFace(unlocked = true, stored = "whatever"))
    }
}
