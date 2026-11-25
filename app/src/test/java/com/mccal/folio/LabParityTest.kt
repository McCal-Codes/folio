package com.mccal.folio

import com.mccal.folio.market.PackagePermission
import com.mccal.folio.market.PackageSafety
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The Mockup Lab is the design reference, so what it shows has to be what Folio says.
 *
 * The lab is its own private repository, so this skips when it isn't on the machine ([MockupLab] says how to point
 * it at a clone). Where it is, it fails as soon as the two drift apart.
 */
class LabParityTest {
    private val scene = MockupLab.file("scenes/market.js")

    private fun sceneText(): String {
        assumeTrue(MockupLab.MISSING, scene.isFile)
        return scene.readText()
    }

    @Test fun `the lab lists exactly what Folio says a package can't reach`() {
        val text = sceneText()
        for (line in PackageSafety.CANNOT_ACCESS) {
            assertTrue("the lab is missing \"$line\"", line in text)
        }
    }

    @Test fun `every permission the lab shows is a real one`() {
        val text = sceneText()
        val labels = PackagePermission.entries.mapNotNull { it.label }.toSet()
        // The scene lists them as perm:['Changes Folio tweaks', …].
        val used = Regex("perm:\\[(.*?)]").findAll(text)
            .flatMap { match -> Regex("'([^']+)'").findAll(match.groupValues[1]).map { it.groupValues[1] } }
            .toSet()
        assertTrue("the lab shows no permissions at all", used.isNotEmpty())
        val invented = used - labels
        assertTrue("the lab invents permission wording: $invented", invented.isEmpty())
    }

    @Test fun `the lab uses Folio's own tweak names`() {
        val text = sceneText()
        for (tweak in TweakFeatures.filter { it.gate != FeatureGate.STANDBY_2 }) {
            assertTrue("the lab doesn't mention ${tweak.name}", tweak.name in text)
        }
    }
}
