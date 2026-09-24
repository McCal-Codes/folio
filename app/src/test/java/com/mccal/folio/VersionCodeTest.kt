package com.mccal.folio

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `versionCode` is what Android compares when one build installs over another, and it only ever goes up. A formula
 * that leaves no room between releases is why `0.6.6.1` could not exist and the seven fixes of 23 Sep 2026 had to
 * take the number the next feature release wanted (docs/adr/0007-release-trains.md).
 *
 * So this checks the formula on the build that was actually packaged, not a copy of the arithmetic: the number here
 * comes from the merged manifest, the same place Android reads it from.
 */
@RunWith(RobolectricTestRunner::class)
// SDK 34: Robolectric needs Java 21 for 36, and the build runs on 17. The manifest's versionCode is the same
// whichever SDK the sandbox pretends to be.
@Config(sdk = [34])
class VersionCodeTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val info = context.packageManager.getPackageInfo(context.packageName, 0)

    /** The formula, written once here so a test failure says which part disagrees. */
    private fun expected(version: String): Long {
        val parts = version.substringBefore('-').split('.').map(String::toInt)
        val hotfix = parts.getOrElse(3) { 0 }
        return ((parts[0] * 10000 + parts[1] * 100 + parts[2]) * 10 + hotfix).toLong()
    }

    @Test fun `the build's code comes from its version name`() {
        val name = requireNotNull(info.versionName) { "the build has no versionName" }
        assertEquals("versionCode should be the version name through the formula", expected(name), info.longVersionCode)
    }

    @Test fun `a fix on top of a release sorts above it, and below the next one`() {
        assertTrue("0.6.7.1 should outrank 0.6.7", expected("0.6.7.1") > expected("0.6.7"))
        assertTrue("0.6.8 should outrank every fix of 0.6.7", expected("0.6.8") > expected("0.6.7.9"))
        assertEquals("nine slots per release", 9L, expected("0.6.7.9") - expected("0.6.7"))
        // The name has to agree with the number, or a phone would be offered a build it then refuses to install.
        assertTrue(SoftwareUpdate.isNewer("0.6.7.1", "0.6.7"))
        assertTrue(SoftwareUpdate.isNewer("0.6.8", "0.6.7.9"))
        assertTrue(SoftwareUpdate.isNewer("0.6.7.2", "0.6.7.1"))
    }

    @Test fun `a pre-release shares its release's code, and the release still wins`() {
        assertEquals(expected("0.6.7"), expected("0.6.7-beta.3"))
        assertTrue("the stable has to read as newer than its own beta", SoftwareUpdate.isNewer("0.6.7", "0.6.7-beta.3"))
    }

    @Test fun `every code Folio has published is below this build's`() {
        // 0.6.5, 0.6.6 and 0.6.7-beta.2 went out under the older formula, without the trailing digit. Nothing
        // installs over a larger code, so adopting the new formula must not produce a smaller number than those.
        val published = listOf(605L, 606L, 607L)
        for (old in published) {
            assertTrue("$old should be below ${info.longVersionCode}", info.longVersionCode > old)
        }
    }
}
