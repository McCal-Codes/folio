package com.mccal.folio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where the Market's tabs go on a big screen. The Fold8's inner screen is wide enough for the labelled sidebar the
 * Mockup Lab draws, instead of a bar of five icons under a 932 dp window.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w932dp-h704dp")
class MarketLayoutRenderTest {
    @get:Rule val compose = createComposeRule()

    private class NoopLauncher : MarketLauncher {
        override var state = LauncherState()
        override fun installTweak(feature: TweakFeature) = Unit
        override fun removeTweak(feature: TweakFeature) = Unit
        override fun setFeatureScope(id: String, screen: FolioScreen, value: ScopeValue) = Unit
        override fun applyTheme(theme: FolioTheme) = Unit
        override fun applyArtBackground(art: Artwork, bytes: ByteArray, sha256: String): String = ""
        override fun restoreArtBackground(artId: String, snapshot: String) = Unit
    }

    @Test fun `the unfolded screen gets a labelled sidebar`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val session = MarketSession(context, NoopLauncher())
        session.prefs.introductionSeen = true
        compose.setContent { MarketScreen(session, emptySet(), onClose = {}) }
        // The title only exists in the sidebar, and every tab is still one control with the same tag.
        compose.onNodeWithText("Market").assertIsDisplayed()
        MarketTab.entries.forEach { compose.onNodeWithTag("market-tab-${it.name.lowercase()}").assertIsDisplayed() }
    }
}
