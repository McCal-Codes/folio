package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Supporters' betas are read through the worker, which is the only thing holding a token for the private repository.
 * Folio sends its supporter code as the credential, so where that code is allowed to travel matters.
 */
class BetaSourceTest {
    private val broker = "https://codes.example"
    private val code = "FOLIO-ABCDE-FGHJK"

    @Test fun `a broker and a code give the releases address and the credential`() {
        assertEquals("$broker/beta/releases" to code, SoftwareUpdate.betaSource(broker, code))
    }

    @Test fun `a trailing slash doesn't double up`() {
        assertEquals("$broker/beta/releases", SoftwareUpdate.betaSource("$broker/", code)?.first)
    }

    @Test fun `no broker deployed, or no code, means the public releases`() {
        assertNull(SoftwareUpdate.betaSource("", code))
        assertNull(SoftwareUpdate.betaSource("   ", code))
        assertNull(SoftwareUpdate.betaSource(broker, null))
        assertNull(SoftwareUpdate.betaSource(broker, "  "))
    }

    @Test fun `a code never travels over plain http`() {
        assertNull(SoftwareUpdate.betaSource("http://codes.example", code))
    }

    @Test fun `a beta download link from the broker carries the code, because the broker checks it again`() {
        assertEquals(code, SoftwareUpdate.downloadCredential("$broker/beta/asset/42?t=abc", broker, code))
        assertEquals(code, SoftwareUpdate.downloadCredential("$broker/beta/asset/42?t=abc", "$broker/", code))
    }

    @Test fun `nothing but the broker ever sees the code`() {
        // Public releases, GitHub's own file addresses, and an address that only starts like the broker's.
        assertNull(SoftwareUpdate.downloadCredential("https://github.com/McCal-Codes/folio/releases/download/v0.6.6/Folio.apk", broker, code))
        assertNull(SoftwareUpdate.downloadCredential("https://objects.githubusercontent.com/x", broker, code))
        assertNull(SoftwareUpdate.downloadCredential("https://codes.example.evil/beta/asset/42", broker, code))
        assertNull(SoftwareUpdate.downloadCredential("$broker/beta/asset/42", "", code))
        assertNull(SoftwareUpdate.downloadCredential("$broker/beta/asset/42", broker, null))
    }
}
