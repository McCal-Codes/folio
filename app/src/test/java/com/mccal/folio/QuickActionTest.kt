package com.mccal.folio

import android.app.Application
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * An app's quick action, from its long-press menu or its panel. One that wouldn't start closed the menu on nothing;
 * it says so now, the way an app that won't open does. Robolectric can't start a shortcut at all, which makes every
 * one here a quick action that won't start.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickActionTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val mail = AppEntry("com.example.mail/.Main", "Mail", Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
    private val compose = ShortcutInfo.Builder(context, "compose").setShortLabel("Compose").build()

    @Test fun `a quick action that won't start says so`() {
        startQuickAction(context, mail, QuickAction("Compose", null, compose))
        assertEquals("Compose is unavailable.", ShadowToast.getTextOfLatestToast())
    }

    @Test fun `one without a name of its own is named for its app`() {
        startQuickAction(context, mail, QuickAction("", null, compose))
        assertEquals("Mail is unavailable.", ShadowToast.getTextOfLatestToast())
    }
}
