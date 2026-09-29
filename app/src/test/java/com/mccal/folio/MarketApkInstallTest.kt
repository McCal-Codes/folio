package com.mccal.folio

import androidx.test.core.app.ApplicationProvider
import com.mccal.folio.market.DebVersion
import com.mccal.folio.market.IndexPackage
import com.mccal.folio.market.Source
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

/**
 * Folio installing an app itself: when it may, and what it refuses to hand to Android.
 *
 * McCal's call (2026-09-20) is that any source the user added may offer an app, the way Cydia and Sileo work.
 * So the checks that remain are the ones that carry the weight: the setting, and the checksum.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarketApkInstallTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun entry(url: String? = "packages/keyd.apk", sha: String? = "a".repeat(64), size: Int? = 1024) =
        IndexPackage(
            id = "com.mccal.keyd", version = requireNotNull(DebVersion.parse("0.1.0")),
            url = url, sha256 = sha, size = size, provenance = null, manifest = null,
        )

    private val added = Source("https://maya.example/folio/", kind = Source.Kind.ADDED)

    @Test fun `off by default, and the setting is the whole gate`() {
        assertFalse("a source may offer, but nothing happens until it is turned on",
            MarketApkInstall.canInstall(added, entry(), on = false))
        assertTrue(MarketApkInstall.canInstall(added, entry(), on = true))
    }

    @Test fun `a source the user added may offer an app`() {
        // The earlier draft allowed only sources whose key ships in Folio. McCal chose Cydia's bargain instead:
        // you chose the source, so you chose what it may hand you.
        for (kind in listOf(Source.Kind.ADDED, Source.Kind.SUPPORTER)) {
            assertTrue("$kind", MarketApkInstall.canInstall(Source("https://x.example/", kind = kind), entry(), on = true))
        }
        // Except Folio's own, which is inside the APK and has nothing to download.
        assertFalse(MarketApkInstall.canInstall(Source("built-in", kind = Source.Kind.BUILT_IN), entry(), on = true))
        // And a local source, which is unsigned http any app on the phone could answer.
        assertFalse(MarketApkInstall.canInstall(Source("http://localhost:8787/", kind = Source.Kind.LOCAL_DEV), entry(), on = true))
        // And a listing its source pulled.
        assertFalse(MarketApkInstall.canInstall(Source("https://x.example/", kind = Source.Kind.ADDED), entry(), on = true, revoked = true))
    }

    @Test fun `a listing with no checksum is never installed, whatever the setting says`() {
        assertFalse("nothing to check the download against",
            MarketApkInstall.canInstall(added, entry(sha = null), on = true))
        assertFalse(MarketApkInstall.canInstall(added, entry(url = null), on = true))
    }

    @Test fun `bytes that don't match the checksum never reach Android`() = runTest {
        val wrong = "not the app that was listed".toByteArray()
        val ok = MarketApkInstall.install(context, "Keyd", entry()) { _, _ -> wrong }
        assertFalse(ok)
        val status = MarketApkInstall.status.value
        assertTrue("$status", status is MarketApkInstall.Status.Failed)
        assertTrue((status as MarketApkInstall.Status.Failed).message.contains("didn't match"))
        // And nothing was left behind for a later install to pick up.
        assertFalse(java.io.File(context.cacheDir, "market-install.apk").exists())
    }

    @Test fun `a download that fails says so rather than installing nothing quietly`() = runTest {
        val ok = MarketApkInstall.install(context, "Keyd", entry()) { _, _ -> null }
        assertFalse(ok)
        assertTrue(MarketApkInstall.status.value is MarketApkInstall.Status.Failed)
    }

    @Test fun `what Android answers is what the store says, not what handing it over returned`() {
        // The install screen is a different app and its answer comes back afterwards, so the outcome lives here
        // rather than in the call that started it. Before this, the store said "Android is installing it" the
        // moment the bytes were handed over, and left that on the screen whatever happened next.
        MarketApkInstall.status.value = MarketApkInstall.Status.Handed("Keyd")
        MarketInstallReceiver().onReceive(
            context,
            android.content.Intent()
                .putExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, android.content.pm.PackageInstaller.STATUS_SUCCESS)
                .putExtra(android.content.pm.PackageInstaller.EXTRA_PACKAGE_NAME, "com.mccal.keyd"),
        )
        val done = MarketApkInstall.status.value
        assertTrue("$done", done is MarketApkInstall.Status.Installed)
        done as MarketApkInstall.Status.Installed
        assertEquals("Keyd", done.name)
        // The package name is Android's answer, not something Folio told it: the session never named one.
        assertEquals("com.mccal.keyd", done.appId)
    }

    @Test fun `an outcome nobody has read waits, and is only said once`() {
        MarketApkInstall.status.value = MarketApkInstall.Status.Installed("Keyd", "com.mccal.keyd")
        MarketApkInstall.seen()
        assertEquals(MarketApkInstall.Status.Idle, MarketApkInstall.status.value)
        // An install that is still running is not something anyone has read, so seen() leaves it alone.
        MarketApkInstall.status.value = MarketApkInstall.Status.Working("Keyd")
        MarketApkInstall.seen()
        assertEquals(MarketApkInstall.Status.Working("Keyd"), MarketApkInstall.status.value)
    }

    @Test fun `the checksum it checks is the one the index promised`() = runTest {
        val bytes = "pretend this is an apk".toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        // Robolectric has no package installer to hand it to, so this gets as far as trying and fails there -
        // which is one step past the checksum, and the checksum is what this is about.
        MarketApkInstall.install(context, "Keyd", entry(sha = sha, size = bytes.size)) { _, _ -> bytes }
        val status = MarketApkInstall.status.value
        val message = (status as? MarketApkInstall.Status.Failed)?.message.orEmpty()
        assertEquals("it got past the checksum", false, message.contains("didn't match"))
    }
}
