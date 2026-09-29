package com.mccal.folio

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.mccal.folio.market.DebVersion
import com.mccal.folio.market.IndexPackage
import com.mccal.folio.market.PackageManifest
import com.mccal.folio.market.ParseResult
import com.mccal.folio.market.Source
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.security.MessageDigest

/**
 * Updating an app Folio can see on the phone: when a listing counts as newer, when Android may update it without
 * asking, and what is refused whatever the checksum says.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarketAppUpdateTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun v(text: String) = requireNotNull(DebVersion.parse(text))

    private fun keyd(version: String = "0.3.0", sha: String = "a".repeat(64), size: Int? = 1024, needs: List<String> = emptyList()): IndexPackage {
        val json = """
            {
              "format": 1, "id": "com.mccal.keyd", "name": "Keyd", "version": "$version",
              "author": { "name": "McCal" }, "minFolio": "0.6.6", "section": "tweaks",
              "kind": ["externalApp"], "permissions": [],
              "via": [{ "store": "obtainium", "repoUrl": "https://github.com/McCal-Codes/folio-keyd", "id": "com.mccal.keyd" }]
            }
        """.trimIndent()
        val manifest = (PackageManifest.parse(json) as ParseResult.Ok).value
        return IndexPackage(
            id = "com.mccal.keyd", version = v(version), url = "https://example.test/Keyd.apk",
            sha256 = sha, size = size, provenance = null, manifest = manifest, needs = needs,
        )
    }

    private val source = Source("https://mccal-codes.github.io/folio-keyd/", kind = Source.Kind.ADDED)

    private fun onThePhone(versionName: String) {
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                packageName = "com.mccal.keyd"
                this.versionName = versionName
                applicationInfo = ApplicationInfo().apply { packageName = "com.mccal.keyd" }
            },
        )
    }

    private val installed = MarketAppUpdate.OnPhone("com.mccal.keyd", "0.2.0", "com.mccal.folio", setOf("key-a"))

    @Test fun `a newer release is an update`() {
        assertTrue(MarketAppUpdate.isNewer(v("0.3.0"), "0.2.0"))
        assertTrue(MarketAppUpdate.isNewer(v("0.10.0"), "0.9.3"))
    }

    @Test fun `a pre-release comes before its release, the way an app's versionName means it`() {
        // In dpkg's order `-beta.3` is a revision and would sort after 0.3.0; Keyd means the opposite.
        assertTrue("0.3.0 replaces its own beta", MarketAppUpdate.isNewer(v("0.3.0"), "0.3.0-beta.3"))
        assertFalse("and a beta never replaces the release", MarketAppUpdate.isNewer(v("0.3.0-beta.3"), "0.3.0"))
        assertTrue(MarketAppUpdate.isNewer(v("0.3.0-beta.10"), "0.3.0-beta.3"))
        assertTrue("a listing written the dpkg way reads the same", MarketAppUpdate.isNewer(v("0.3.0"), "0.3.0~beta.3"))
    }

    @Test fun `the same version, or an older listing, is no update`() {
        assertFalse(MarketAppUpdate.isNewer(v("0.3.0"), "0.3.0"))
        assertFalse(MarketAppUpdate.isNewer(v("0.3.0"), "0.4.0"))
        assertFalse(MarketAppUpdate.isNewer(v("0.3.0"), "v0.3.0"))
        assertFalse("build metadata isn't a version of its own", MarketAppUpdate.isNewer(v("0.3.0"), "0.3.0+abc123"))
    }

    @Test fun `a version Folio can't read is left alone`() {
        assertFalse(MarketAppUpdate.isNewer(v("0.3.0"), null))
        assertFalse(MarketAppUpdate.isNewer(v("0.3.0"), ""))
        assertFalse(MarketAppUpdate.isNewer(v("0.3.0"), "nightly build"))
    }

    @Test fun `silent only when this Folio installed it and the signer matches`() {
        assertEquals(
            MarketAppUpdate.Verdict.SILENT,
            MarketAppUpdate.verdict("com.mccal.folio", installed, "com.mccal.keyd", setOf("key-a")),
        )
        // McCal's phone: Keyd was installed by Folio, and Folio Dev is the one updating it. Android asks.
        assertEquals(
            MarketAppUpdate.Verdict.ASK,
            MarketAppUpdate.verdict("com.mccal.folio.dev", installed, "com.mccal.keyd", setOf("key-a")),
        )
        // Installed from anywhere else, or with no installer recorded, Android asks too.
        assertEquals(
            MarketAppUpdate.Verdict.ASK,
            MarketAppUpdate.verdict("com.mccal.folio", installed.copy(installer = null), "com.mccal.keyd", setOf("key-a")),
        )
    }

    @Test fun `a different signer is refused, even from the installer of record`() {
        assertEquals(
            MarketAppUpdate.Verdict.WRONG_SIGNER,
            MarketAppUpdate.verdict("com.mccal.folio", installed, "com.mccal.keyd", setOf("key-b")),
        )
        assertEquals(
            "an extra signer isn't the same set",
            MarketAppUpdate.Verdict.WRONG_SIGNER,
            MarketAppUpdate.verdict("com.mccal.folio", installed, "com.mccal.keyd", setOf("key-a", "key-b")),
        )
        assertEquals(
            "an unsigned APK matches nothing",
            MarketAppUpdate.Verdict.WRONG_SIGNER,
            MarketAppUpdate.verdict("com.mccal.folio", installed.copy(signers = emptySet()), "com.mccal.keyd", emptySet()),
        )
    }

    @Test fun `an APK that is some other app is refused`() {
        assertEquals(
            MarketAppUpdate.Verdict.WRONG_APP,
            MarketAppUpdate.verdict("com.mccal.folio", installed, "com.example.other", setOf("key-a")),
        )
        assertEquals(
            "and so is one Android can't read",
            MarketAppUpdate.Verdict.WRONG_APP,
            MarketAppUpdate.verdict("com.mccal.folio", installed, null, setOf("key-a")),
        )
    }

    @Test fun `an older Keyd on the phone is offered the listing, and a current one isn't`() {
        onThePhone("0.2.0")
        val offered = MarketAppUpdate.offered(context, MarketEntry(keyd("0.3.0"), source))
        assertNotNull(offered)
        assertEquals("0.2.0", offered!!.versionName)
        onThePhone("0.3.0")
        assertNull(MarketAppUpdate.offered(context, MarketEntry(keyd("0.3.0"), source)))
        onThePhone("0.4.0")
        assertNull(MarketAppUpdate.offered(context, MarketEntry(keyd("0.3.0"), source)))
    }

    @Test fun `nothing is offered for an app that isn't there`() {
        assertNull(MarketAppUpdate.offered(context, MarketEntry(keyd("0.3.0"), source)))
    }

    @Test fun `pulled, impostor and needs-a-newer-Folio listings are never offered as updates`() {
        onThePhone("0.2.0")
        assertNull(MarketAppUpdate.offered(context, MarketEntry(keyd("0.3.0"), source, revokedReason = "pulled")))
        assertNull(MarketAppUpdate.offered(context, MarketEntry(keyd("0.3.0"), source, clash = MarketEntry.Impostor.BUILT_IN)))
        assertNull(MarketAppUpdate.offered(context, MarketEntry(keyd("0.3.0", needs = listOf("keyboard.v2")), source)))
        // Another source using the same name is shown with both named, not hidden, so it still offers the update.
        assertNotNull(MarketAppUpdate.offered(context, MarketEntry(keyd("0.3.0"), source, clash = MarketEntry.Impostor.ANOTHER_SOURCE)))
    }

    @Test fun `the installer of record is read from Android`() {
        onThePhone("0.2.0")
        shadowOf(context.packageManager).setInstallSourceInfo("com.mccal.keyd", "com.mccal.folio", "com.mccal.folio")
        assertEquals("com.mccal.folio", MarketAppUpdate.onPhone(context, "com.mccal.keyd")?.installer)
    }

    @Test fun `an update with the wrong checksum never reaches Android`() = runTest {
        val ok = MarketApkInstall.install(context, "Keyd", keyd(), installed) { _, _ -> "not keyd".toByteArray() }
        assertFalse(ok)
        val status = MarketApkInstall.status.value as MarketApkInstall.Status.Failed
        assertTrue(status.message, status.message.contains("didn't match"))
    }

    @Test fun `bytes that match the checksum but aren't the app are still refused`() = runTest {
        // The checksum only says the source served what it listed. Whether that is the Keyd on this phone, signed
        // by the same key, is checked after it and before Android sees anything.
        val bytes = "a checksum-perfect stranger".toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val ok = MarketApkInstall.install(context, "Keyd", keyd(sha = sha, size = bytes.size), installed) { _, _ -> bytes }
        assertFalse(ok)
        val status = MarketApkInstall.status.value as MarketApkInstall.Status.Failed
        assertTrue(status.message, status.message.contains("different app"))
        assertFalse("the copy it read is gone", java.io.File(context.cacheDir, "market-update-check.apk").exists())
    }

    @Test fun `a download of the wrong size is refused`() = runTest {
        val bytes = "sized wrong".toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val ok = MarketApkInstall.install(context, "Keyd", keyd(sha = sha, size = bytes.size + 1), installed) { _, _ -> bytes }
        assertFalse(ok)
        val status = MarketApkInstall.status.value as MarketApkInstall.Status.Failed
        assertTrue(status.message, status.message.contains("didn't match"))
    }
}
