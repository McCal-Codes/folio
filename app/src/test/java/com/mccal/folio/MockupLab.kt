package com.mccal.folio

import java.io.File

/**
 * Where the Mockup Lab is on this machine, for the tests that hold it to the app.
 *
 * The lab is its own private repository (folio-mockups), so a worktree of Folio doesn't contain it, and the parity
 * tests used to skip everywhere except one old checkout. Point them at a clone of the lab with the Gradle property
 * `folio.labDir` (in `~/.gradle/gradle.properties`, so every worktree finds it, or `-Pfolio.labDir=...` once) or the
 * `FOLIO_LAB_DIR` environment variable. Without either they look in `docs/mockups/lab`, where the lab used to live,
 * and skip when it isn't there.
 */
internal object MockupLab {
    val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }

    val dir: File = listOfNotNull(System.getProperty("folio.labDir"), System.getenv("FOLIO_LAB_DIR"))
        .firstOrNull { it.isNotBlank() }?.let(::File) ?: File(repoRoot, "docs/mockups/lab")

    fun file(path: String) = File(dir, path)

    const val MISSING = "the Mockup Lab isn't on this machine (set folio.labDir to a clone of folio-mockups/lab)"
}
