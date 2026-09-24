package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The cost of holding a feature behind a gate instead of a branch is that a gate is a branch you have to remember to
 * open (docs/adr/0007-release-trains.md). This is the part that remembers for you: when the release a gate was meant
 * to open in arrives, the build fails until someone either opens it or says out loud that it is waiting longer.
 */
class FeatureGateTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }

    private val version = Regex("""^val folioVersion = "([^"]+)"""", RegexOption.MULTILINE)
        .find(File(root, "app/build.gradle.kts").readText())!!.groupValues[1]

    @Test fun `no gate is still shut in the release it was meant to open in`() {
        for (gate in FeatureGate.closed()) {
            assertTrue(
                "The '${gate.key}' gate was meant to open in ${gate.opensIn} and this build is $version. " +
                    "Open it where FeatureGate says it is decided, give the feature a changelog line, or move " +
                    "opensIn further out and say why in the same pull request.",
                SoftwareUpdate.isNewer(gate.opensIn, version),
            )
        }
    }

    @Test fun `every gate says when it was shut and where it is going`() {
        assertEquals("gate keys have to be unique", FeatureGate.entries.size, FeatureGate.entries.map { it.key }.toSet().size)
        for (gate in FeatureGate.entries) {
            assertTrue("${gate.key}: closedSince should be YYYY-MM-DD, was '${gate.closedSince}'",
                Regex("""^\d{4}-\d{2}-\d{2}$""").matches(gate.closedSince))
            assertTrue("${gate.key}: opensIn should be a version, was '${gate.opensIn}'",
                Regex("""^\d+\.\d+\.\d+$""").matches(gate.opensIn))
            assertTrue("${gate.key}: opensIn ${gate.opensIn} should be a release Folio has not passed",
                gate.open || SoftwareUpdate.isNewer(gate.opensIn, version))
        }
    }

    @Test fun `the Market is the gate it used to be`() {
        // The behaviour this replaced: released, or a .dev build, or a supporter code with the beta scope.
        val market = FeatureGate.MARKET
        assertEquals("the gate reads MarketFeature for whether it has shipped", com.mccal.folio.market.MarketFeature.RELEASED, market.open)
        assertTrue("Folio Dev sees it", com.mccal.folio.market.MarketFeature.isDevBuild("com.mccal.folio.dev"))
        assertTrue("a plain build does not, on its own", !com.mccal.folio.market.MarketFeature.isDevBuild("com.mccal.folio"))
    }
}
