package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The checks between a download and Android's installer. */
class DevSourceInstallCheckTest {
    private val build = DevBuildRow("n", "t", "main", "abcd1234", "2026-10-24T10:00:00Z", 1, 6090, "0.6.9", 1000, "a".repeat(64), "s", "debug")
    private val me = setOf("cert-a")

    private fun check(size: Long = 1000, sha: String = "a".repeat(64), pkg: String? = "com.mccal.folio.dev",
        signers: Set<String> = me, code: Long = 6090, installedCode: Long = 6090) =
        DevInstallCheck.archive(build, size, sha, pkg, signers, code, "com.mccal.folio.dev", me, installedCode)

    @Test fun `a matching build passes, including the same versionCode`() = assertNull(check())
    @Test fun `a newer versionCode passes`() = assertNull(check(code = 6100))
    @Test fun `a wrong package is refused`() = assertEquals(DevRefusal.WRONG_PACKAGE, check(pkg = "com.mccal.folio"))
    @Test fun `an unreadable archive is refused`() = assertEquals(DevRefusal.WRONG_PACKAGE, check(pkg = null))
    @Test fun `a wrong signer is refused`() = assertEquals(DevRefusal.WRONG_SIGNER, check(signers = setOf("cert-b")))
    @Test fun `an unsigned archive is refused`() = assertEquals(DevRefusal.WRONG_SIGNER, check(signers = emptySet()))
    @Test fun `a lower versionCode is refused`() = assertEquals(DevRefusal.OLDER_VERSION, check(code = 6080))
    @Test fun `a wrong size or hash is refused first`() {
        assertEquals(DevRefusal.WRONG_SIZE, check(size = 999))
        assertEquals(DevRefusal.WRONG_HASH, check(sha = "b".repeat(64)))
    }
    @Test fun `an older line is refused before anything is downloaded`() {
        assertEquals(DevRefusal.OLDER_VERSION, DevInstallCheck.beforeDownload(build, 6100))
        assertNull(DevInstallCheck.beforeDownload(build, 6090))
    }
}
