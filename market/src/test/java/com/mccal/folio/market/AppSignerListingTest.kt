package com.mccal.folio.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** An app's listing may name the keys its APK is signed with and its lowest versionCode, for the first-install check. */
class AppSignerListingTest {
    private val sha = "a".repeat(64)
    private val key = "b".repeat(64)

    private fun index(extra: String, url: Boolean = true): String {
        val manifest = """{"format":1,"id":"com.mccal.keyd","name":"Keyd","version":"0.3.0","author":{"name":"McCal"},
            "minFolio":"0.6.6","section":"tweaks","kind":["externalApp"],"permissions":[],
            "via":[{"store":"obtainium","repoUrl":"https://example.com/r","id":"com.mccal.keyd"}]}"""
        val apk = if (url) """"url":"https://example.com/Keyd.apk","sha256":"$sha","size":1024,""" else ""
        return """{"format":1,"name":"E","packages":[{"id":"com.mccal.keyd","version":"0.3.0",$apk$extra"manifest":$manifest}]}"""
    }

    private fun parsed(text: String) = (RepoIndex.parse(text) as ParseResult.Ok).value.packages.single()

    @Test fun `signers and versionCode are read`() {
        val entry = parsed(index(""""signerSha256":["$key"],"versionCode":3000,"""))
        assertEquals(setOf(key), entry.signerSha256)
        assertEquals(3000L, entry.versionCode)
    }

    @Test fun `a listing without them still reads, and names nothing`() {
        val entry = parsed(index(""))
        assertTrue(entry.signerSha256.isEmpty())
        assertNull(entry.versionCode)
    }

    @Test fun `a signer that isn't a lowercase SHA-256 is refused`() {
        assertTrue(RepoIndex.parse(index(""""signerSha256":["${key.uppercase()}"],""")) is ParseResult.Invalid)
        assertTrue(RepoIndex.parse(index(""""signerSha256":["abc"],""")) is ParseResult.Invalid)
        assertTrue(RepoIndex.parse(index(""""signerSha256":[],""")) is ParseResult.Invalid)
    }

    @Test fun `they describe an APK, so they need its url`() {
        assertTrue(RepoIndex.parse(index(""""signerSha256":["$key"],""", url = false)) is ParseResult.Invalid)
    }
}
