package com.mccal.folio

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The StandBy glow on another display lives in a window with no activity behind it, so Compose finds no lifecycle owner
 * unless the glow brings one ("ViewTreeLifecycleOwner not found", reported on a Fold 8 on 0.7.0-beta.1).
 */
class StandByRearOwnerTest {
    private val source: String = generateSequence(java.io.File("").absoluteFile) { it.parentFile }
        .first { java.io.File(it, "CHANGELOG.md").exists() }
        .let { java.io.File(it, "app/src/main/java/com/mccal/folio/StandBy.kt").readText() }

    @Test fun `both rear-display glow windows give their ComposeView a lifecycle owner`() {
        assertTrue("the Presentation and the window-area session each create one", source.windowed("OverlayOwner()".length).count { it == "OverlayOwner()" } >= 2)
        assertTrue(source.windowed("setViewTreeLifecycleOwner".length).count { it == "setViewTreeLifecycleOwner" } >= 3)
        assertTrue(source.windowed("setViewTreeSavedStateRegistryOwner".length).count { it == "setViewTreeSavedStateRegistryOwner" } >= 3)
    }

    @Test fun `the owners are stopped when the glow ends`() {
        assertTrue(source.windowed("owner?.stop()".length).count { it == "owner?.stop()" } >= 2)
    }
}
