package com.mccal.folio.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Market lists an app up to 100 MiB (2026-10-06) and everything else still at 20 MiB, so package zip limits (T7) hold. */
class AppSizeLimitTest {
    private val sha = "a".repeat(64)

    private fun index(kind: String, size: Long): String {
        val via = if (kind == "externalApp") ""","via":[{"store":"obtainium","repoUrl":"https://example.com/r","id":"com.example.app"}]""" else ""
        val manifest = """{"format":1,"id":"dev.example.test.pkg","name":"P","version":"1.0","author":{"name":"E"},
            "minFolio":"0.6.6","section":"tweaks","kind":["$kind"],"permissions":[]$via}"""
        return """{"format":1,"name":"E","packages":[{"id":"dev.example.test.pkg","version":"1.0","url":"https://example.com/x",
            "sha256":"$sha","size":$size,"manifest":$manifest}]}"""
    }

    private val mib = 1024L * 1024L

    @Test fun `an app may be listed up to 100 MiB`() {
        assertTrue(RepoIndex.parse(index("externalApp", 82 * mib)) is ParseResult.Ok)
        assertTrue(RepoIndex.parse(index("externalApp", 100 * mib)) is ParseResult.Ok)
    }

    @Test fun `an app over 100 MiB is refused`() {
        assertTrue(RepoIndex.parse(index("externalApp", 100 * mib + 1)) is ParseResult.Invalid)
    }

    @Test fun `a package is still at most 20 MiB`() {
        assertTrue(RepoIndex.parse(index("tweakBundle", 20 * mib)) is ParseResult.Ok)
        val over = RepoIndex.parse(index("tweakBundle", 21 * mib))
        assertTrue("$over", over is ParseResult.Invalid)
        assertTrue((over as ParseResult.Invalid).errors.any { "at most 20 MB" in it })
    }

    @Test fun `the limits are the ones the docs and schema say`() {
        assertEquals(20 * 1024 * 1024, IndexPackage.MAX_PACKAGE_BYTES)
        assertEquals(100 * 1024 * 1024, IndexPackage.MAX_APP_BYTES)
    }
}
