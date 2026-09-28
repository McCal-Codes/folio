package com.mccal.folio

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Restoring a backup clears the art of wallpaper packages the backup doesn't carry (#148 review).
 *
 * Remove and Undo pruned art already, but a restore removes this phone's packages through the installer directly,
 * so a wallpaper that went that way stayed on disk and kept showing in Wallpaper & Appearance's Installed grid.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestorePrunesArtTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val session = MarketSession(context, object : MarketLauncher {
        override val state = LauncherState()
        override fun installTweak(feature: TweakFeature) = Unit
        override fun removeTweak(feature: TweakFeature) = Unit
        override fun setFeatureScope(id: String, screen: FolioScreen, value: ScopeValue) = Unit
        override fun applyTheme(theme: FolioTheme) = Unit
        override fun applyArtBackground(art: Artwork, bytes: ByteArray, sha256: String): String = ""
        override fun restoreArtBackground(artId: String, snapshot: String) = Unit
    })

    @Test fun `art whose package the backup doesn't carry is gone after the restore`() {
        // Art left from a package that is no longer installed, as a restore used to leave it.
        val id = "com.example.left-behind"
        assertTrue(BackgroundLibrary.record(context, Artwork(id, "Waves", "An Artist", "CC0")))
        BackgroundLibrary.installedDir(context).mkdirs()
        BackgroundLibrary.artFile(context, id).writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(listOf(id), BackgroundLibrary.installed(context).map { it.id })

        var layoutPutBack = false
        session.restorePackages("""{"format":1,"packages":[],"changes":{}}""", "restored") { layoutPutBack = true }

        assertTrue("the launcher's own restore still runs", layoutPutBack)
        assertTrue(BackgroundLibrary.installed(context).isEmpty())
        assertFalse(BackgroundLibrary.artFile(context, id).exists())
    }
}
