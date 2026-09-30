package com.mccal.folio.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The safety label is built from the manifest, so an author can't talk their way out of it. */
class PackageSafetyTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }

    private fun manifest(path: String) =
        (PackageManifest.parse(File(root, path).readText()) as ParseResult.Ok).value

    @Test fun `a tweak package says what it changes`() {
        val safety = PackageSafety.of(manifest("docs/sdk/source/packages/cabinet/manifest.json"))
        assertTrue(!safety.runsCode)
        assertEquals(listOf("Changes Folio tweaks"), safety.changes)
        assertEquals("No code · No network · No personal data · Changes 1 thing", safety.summary)
    }

    @Test fun `a theme package is appearance only`() {
        val safety = PackageSafety.of(manifest("docs/sdk/source/packages/theme-clear/manifest.json"))
        assertEquals(emptyList<String>(), safety.changes)
        assertEquals("No code · No network · No personal data · Appearance only", safety.summary)
    }

    @Test fun `the label ignores what the author wrote and follows the permissions`() {
        val json = """
            {"format":1,"id":"dev.example.test.honest","name":"Honest","version":"1.0","author":{"name":"Example"},
             "minFolio":"0.7.0","section":"tweaks","kind":["tweakBundle"],
             "description":"Collects no data whatsoever, we promise, and never changes anything.",
             "permissions":["tweaks","home.layout","apps.open","island.messages"]}
        """.trimIndent()
        val safety = PackageSafety.of((PackageManifest.parse(json) as ParseResult.Ok).value)
        assertEquals(
            listOf("Changes Folio tweaks", "Changes your Home layout", "Opens apps", "Shows island messages"),
            safety.changes,
        )
    }

    @Test fun `a script package says it runs code`() {
        val json = """
            {"format":1,"id":"dev.example.test.script","name":"Script","version":"1.0","author":{"name":"Example"},
             "minFolio":"0.7.0","section":"scripts","kind":["script"],"permissions":["fold.state","focus.switch"]}
        """.trimIndent()
        val safety = PackageSafety.of((PackageManifest.parse(json) as ParseResult.Ok).value)
        assertTrue(safety.runsCode)
        // Folio doesn't run scripts (the kind is reserved and refused), so the label mustn't say it does.
        assertTrue(safety.summary.startsWith("Has a script Folio doesn't run · No network · No personal data"))
    }

    @Test fun `every package says what it cannot reach`() {
        val safety = PackageSafety.of(manifest("docs/sdk/source/packages/cabinet/manifest.json"))
        assertEquals(PackageSafety.CANNOT_ACCESS, safety.cannotAccess)
        assertTrue(safety.cannotAccess.any { it.contains("network") })
        // No permission in the format can grant personal data, so the line is always true.
        assertTrue(PackagePermission.entries.none { it.label?.contains("Reads your", ignoreCase = true) == true })
    }
}
