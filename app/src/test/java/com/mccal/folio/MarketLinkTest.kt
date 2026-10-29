package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Shared links: what Folio opens, and what it refuses to guess at. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarketLinkTest {
    @Test fun `a package link opens that package`() {
        assertEquals(MarketLink.Package("com.mccal.folio.cabinet"), MarketLink.parse("folio://package/com.mccal.folio.cabinet"))
        // Query and fragment are ignored, so a link pasted from a chat still works.
        assertEquals(MarketLink.Package("dev.maya.sunset-icons"), MarketLink.parse("folio://package/dev.maya.sunset-icons?from=chat#top"))
    }

    @Test fun `a source link carries an https url`() {
        assertEquals(
            MarketLink.Source("https://folio.mccal.dev/source/"),
            MarketLink.parse("folio://source/https%3A%2F%2Ffolio.mccal.dev%2Fsource%2F"),
        )
        // An insecure source is not a link Folio follows.
        assertNull(MarketLink.parse("folio://source/http%3A%2F%2Fexample.com%2F"))
    }

    @Test fun `anything else is ignored`() {
        for (bad in listOf(
            null, "", "   ", "folio://", "folio://package/", "folio://package/Not An Id", "folio://package/../etc",
            "folio://nonsense/thing", "https://folio.mccal.dev/source/", "javascript:alert(1)",
        )) {
            assertNull(bad, MarketLink.parse(bad))
        }
    }

    @Test fun `a supporter code isn't a store link`() {
        // Redeeming belongs to Settings, not the store: folio://redeem is RedeemActivity's, and the store
        // shouldn't quietly accept a second spelling of it. RedeemLinkTest covers the link that does work.
        assertNull(MarketLink.parse("folio://early/" + "X".repeat(40)))
        assertNull(MarketLink.parse("folio://redeem?c=" + "X".repeat(40)))
    }

    @Test fun `Android's settings gear opens the Market on its Settings tab, the Folio icon does not`() {
        val gear = android.content.Intent.ACTION_APPLICATION_PREFERENCES
        assertEquals(MarketLink.Settings, linkForSettingsRequest(gear, marketEnabled = true))
        // Without the Market, Settings is already the whole sheet.
        assertNull(linkForSettingsRequest(gear, marketEnabled = false))
        assertNull(linkForSettingsRequest(android.content.Intent.ACTION_MAIN, marketEnabled = true))
        assertNull(linkForSettingsRequest(null, marketEnabled = true))
        // Nothing a link can say reaches it: it is set in code only.
        assertNull(MarketLink.parse("folio://market/settings"))
    }
}
