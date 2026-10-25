package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/** Folio Dev's beta test: the checks, the confirmation, the target package and which code goes where. */
class DevBetaTest {
    private val me = setOf("release-cert")

    private fun check(pkg: String? = "com.mccal.folio", signers: Set<String> = me, code: Long = 6090, installedCode: Long = 6090, sums: Boolean = true) =
        DevBetaCheck.archive(pkg, signers, me, code, installedCode, sums)

    @Test fun `a real beta of the installed Folio passes`() = assertNull(check())
    @Test fun `a newer beta passes`() = assertNull(check(code = 6100))
    @Test fun `the wrong package is refused`() {
        assertEquals(DevRefusal.WRONG_PACKAGE, check(pkg = "com.mccal.folio.dev"))
        assertEquals(DevRefusal.WRONG_PACKAGE, check(pkg = null))
    }
    @Test fun `the wrong signer is refused, and so is no signer`() {
        assertEquals(DevRefusal.WRONG_SIGNER, check(signers = setOf("debug-cert")))
        assertEquals(DevRefusal.WRONG_SIGNER, check(signers = emptySet()))
    }
    @Test fun `a lower versionCode and a bad checksum are refused`() {
        assertEquals(DevRefusal.OLDER_VERSION, check(code = 6080))
        assertEquals(DevRefusal.WRONG_HASH, check(sums = false))
    }

    @Test fun `nothing is confirmed unless it was asked first, and only once`() {
        val gate = DevBetaGate()
        assertNull("no ask, no confirm", gate.confirm())
        gate.ask("0.6.9-beta.5")
        assertEquals("0.6.9-beta.5", gate.asking)
        assertEquals("0.6.9-beta.5", gate.confirm())
        assertNull("a second tap does not install again", gate.confirm())
        gate.ask("0.6.9-beta.6")
        gate.cancel()
        assertNull("a cancelled question cannot be confirmed", gate.confirm())
    }

    @Test fun `the install target defaults to the running app`() {
        assertEquals("com.mccal.folio.dev", SoftwareUpdate.targetPackage("com.mccal.folio.dev"))
        assertEquals("com.mccal.folio", SoftwareUpdate.targetPackage("com.mccal.folio"))
        assertEquals("com.mccal.folio", SoftwareUpdate.targetPackage("com.mccal.folio.dev", "com.mccal.folio"))
    }

    @Test fun `only the running app may be updated without asking`() {
        assertTrue(SoftwareUpdate.userActionNotRequired("com.mccal.folio", "com.mccal.folio"))
        assertFalse(SoftwareUpdate.userActionNotRequired("com.mccal.folio.dev", "com.mccal.folio"))
    }

    @Test fun `the beta code goes only to the beta worker and never to the dev worker`() {
        val code = "BETA-CODE"
        val beta = SoftwareUpdate.BETA_BROKER
        assertEquals(code, SoftwareUpdate.downloadCredential("$beta/beta/asset/x", beta, code))
        assertNull(SoftwareUpdate.downloadCredential("${DevSource.WORKER}/dev/apk/x", beta, code))
        assertNull(SoftwareUpdate.downloadCredential("https://objects.githubusercontent.com/x", beta, code))
        assertNotSame(beta, DevSource.WORKER)
    }

    private fun assertNotSame(a: String, b: String) = assertTrue("$a must differ from $b", a != b)

    private class Fake(url: URL) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
    }

    @Test fun `the dev code is never put on a request to the beta worker`() {
        val opened = mutableListOf<Fake>()
        val open = DevRouting.opener({ DevSource.WORKER }, { "DEV-CODE" }) { url -> Fake(url).also(opened::add) }
        open(URL("${DevSource.WORKER}/dev/file/builds.json"))
        open(URL("${SoftwareUpdate.BETA_BROKER}/beta/releases"))
        open(URL("${SoftwareUpdate.BETA_BROKER}/dev/file/builds.json"))
        assertEquals("Bearer DEV-CODE", opened[0].getRequestProperty("Authorization"))
        assertNull(opened[1].getRequestProperty("Authorization"))
        assertNull("the old host no longer receives the dev code", opened[2].getRequestProperty("Authorization"))
    }
}
