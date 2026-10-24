package com.mccal.folio

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/** The signed builds list: good, tampered, wrong key, stale, rolled back. Every key here is a throwaway made in the test. */
class DevSourceListTest {
    private val now = 1_800_000_000L

    private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    private fun publicOf(pair: KeyPair) = Base64.getEncoder().encodeToString(pair.public.encoded)
    private fun sign(pair: KeyPair, bytes: ByteArray): String = Base64.getEncoder().encodeToString(
        Signature.getInstance("SHA256withECDSA").run { initSign(pair.private); update(bytes); sign() })

    private fun row(tag: String, branch: String = "main", builtAt: String = "2026-10-24T10:00:00Z") = JSONObject()
        .put("name", tag).put("tag", tag).put("branch", branch).put("sha", "abcd1234").put("builtAt", builtAt)
        .put("versionCode", 6090).put("versionName", "0.6.9-beta.5").put("size", 1000).put("sha256", "a".repeat(64))
        .put("subject", "A change").put("buildType", "debug")

    private fun list(timestamp: Long = now - 60, maxAge: Long = 86_400, vararg rows: JSONObject) = JSONObject()
        .put("format", 1).put("timestamp", timestamp).put("maxAge", maxAge)
        .put("builds", JSONArray(rows.toList())).toString().toByteArray()

    private fun verify(bytes: ByteArray, sig: String, key: KeyPair, floor: Long = 0) =
        DevBuilds.verify(bytes, sig, listOf(publicOf(key)), now, floor)

    @Test fun `a good list verifies and is sorted newest first`() {
        val key = keyPair()
        val bytes = list(rows = arrayOf(row("old", builtAt = "2026-10-01T10:00:00Z"), row("new", builtAt = "2026-10-24T10:00:00Z")))
        val verdict = verify(bytes, sign(key, bytes), key)
        assertTrue(verdict is DevBuilds.Verdict.Good)
        assertEquals(listOf("new", "old"), (verdict as DevBuilds.Verdict.Good).builds.map { it.tag })
    }

    @Test fun `a tampered list is refused`() {
        val key = keyPair()
        val bytes = list(rows = arrayOf(row("one")))
        val sig = sign(key, bytes)
        val tampered = bytes.decodeToString().replace("A change", "A chance").toByteArray()
        assertEquals(DevBuilds.Verdict.Bad(DevBuilds.Problem.BAD_SIGNATURE), verify(tampered, sig, key))
    }

    @Test fun `a list signed by another key is refused`() {
        val bytes = list(rows = arrayOf(row("one")))
        assertEquals(DevBuilds.Verdict.Bad(DevBuilds.Problem.BAD_SIGNATURE), verify(bytes, sign(keyPair(), bytes), keyPair()))
    }

    @Test fun `a garbled signature is refused, not a crash`() {
        val key = keyPair()
        assertEquals(DevBuilds.Verdict.Bad(DevBuilds.Problem.BAD_SIGNATURE), verify(list(), "not base64 !!", key))
    }

    @Test fun `a stale list is refused`() {
        val key = keyPair()
        val bytes = list(timestamp = now - 90_000, maxAge = 86_400, rows = arrayOf(row("one")))
        assertEquals(DevBuilds.Verdict.Bad(DevBuilds.Problem.STALE), verify(bytes, sign(key, bytes), key))
    }

    @Test fun `a list older than one already seen is refused as a rollback`() {
        val key = keyPair()
        val bytes = list(timestamp = now - 500, rows = arrayOf(row("one")))
        assertEquals(DevBuilds.Verdict.Bad(DevBuilds.Problem.ROLLBACK), verify(bytes, sign(key, bytes), key, floor = now - 100))
        assertTrue(verify(bytes, sign(key, bytes), key, floor = now - 500) is DevBuilds.Verdict.Good)
    }

    @Test fun `a signed list with a bad row or an absurd age is unreadable`() {
        val key = keyPair()
        val badRow = list(rows = arrayOf(row("../escape")))
        assertEquals(DevBuilds.Verdict.Bad(DevBuilds.Problem.UNREADABLE), verify(badRow, sign(key, badRow), key))
        val forever = list(maxAge = DevBuilds.MAX_AGE_LIMIT_SECONDS + 1, rows = arrayOf(row("one")))
        assertEquals(DevBuilds.Verdict.Bad(DevBuilds.Problem.UNREADABLE), verify(forever, sign(key, forever), key))
    }

    @Test fun `the display version is text only and counts builds per branch`() {
        val key = keyPair()
        val bytes = list(rows = arrayOf(row("a1", "duet/x", "2026-10-01T10:00:00Z"), row("a2", "duet/x", "2026-10-02T10:00:00Z")))
        val builds = (verify(bytes, sign(key, bytes), key) as DevBuilds.Verdict.Good).builds
        assertEquals("0.6.9-dev.duet-x.2", DevBuilds.displayVersion(builds.first { it.tag == "a2" }, builds))
        assertEquals("0.6.9-dev.duet-x.1", DevBuilds.displayVersion(builds.first { it.tag == "a1" }, builds))
    }

    @Test fun `the compiled-in keys are real P-256 keys and all distinct`() {
        assertTrue(com.mccal.folio.market.SourceKey.parse(DevSourceKeys.CODE) != null)
        assertTrue(com.mccal.folio.market.SourceKey.parse(DevSourceKeys.PUBLISHER) != null)
        assertTrue(DevSourceKeys.CODE != DevSourceKeys.PUBLISHER && DevSourceKeys.CODE != BetaKeys.TEST && DevSourceKeys.CODE != BetaKeys.SUPPORTER)
    }
}
