package com.mccal.folio

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The packages Folio ships have to be readable from the app's own assets, not just from the repository. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarketAssetsTest {
    private val session = MarketSession(ApplicationProvider.getApplicationContext<android.app.Application>(), object : MarketLauncher {
        override val state = LauncherState()
        override fun installTweak(feature: TweakFeature) = Unit
        override fun removeTweak(feature: TweakFeature) = Unit
        override fun setFeatureScope(id: String, screen: FolioScreen, value: ScopeValue) = Unit
        override fun applyTheme(theme: FolioTheme) = Unit
        override fun applyArtBackground(art: Artwork, bytes: ByteArray): String = ""
        override fun restoreArtBackground(artId: String, snapshot: String) = Unit
    })

    @Test fun `the bundled source loads from assets`() {
        val index = requireNotNull(session.index()) { "assets/market/source/index.json didn't load" }
        assertEquals(9, index.packages.size)
        val packages = session.source.packages()
        println("packages from assets: " + packages.keys.sorted())
        println("cabinet files: " + packages["com.mccal.folio.cabinet"]?.keys)
        assertNotNull(session.read("com.mccal.folio.cabinet"))
    }
}
