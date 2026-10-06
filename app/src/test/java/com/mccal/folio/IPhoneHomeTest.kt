package com.mccal.folio

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.junit.Assert.assertTrue
import org.junit.Test

class IPhoneHomeTest {
    @Test fun `arranges page one and dock without losing apps`() {
        val slots = MutableList<String?>(HOME_CELLS) { null }.apply { this[8] = "old-a"; this[20] = "cal" }
        val layout = HomeLayout(slots = slots, dock = listOf("old-dock", null, null, null),
            widgetPlacements = listOf(WidgetPlacement(0, CLOCK_WIDGET, 0, 0, 0, 4, 2)),
            folders = listOf(FolderEntry("folder:x", "F", listOf("maps"))))
        val result = arrangeLikeIPhone(layout, mapOf(IPhoneApp.FACETIME to "meet", IPhoneApp.CALENDAR to "cal",
            IPhoneApp.MAPS to "maps", IPhoneApp.PHONE to "dialer", IPhoneApp.MUSIC to "spotify"))
        assertEquals("meet", result.slots[8])       // row 3 of the grid = first app row under the widgets
        assertEquals("cal", result.slots[9])        // moved from where it was
        assertEquals(null, result.slots[20])
        assertEquals(null, result.slots[15])        // Maps is in a folder: left alone
        assertEquals(listOf("dialer", null, null, "spotify"), result.dock)
        val moved = result.slots.drop(HOME_CELLS)
        assertTrue("old-a" in moved && "old-dock" in moved)
        assertEquals(1, result.slots.count { it == "cal" })
    }

    @Test fun `a role gets its app, and an app fills one role only`() {
        val roles = assignRoles(listOf(IPhoneApp.PHOTOS to "gallery", IPhoneApp.CAMERA to null, IPhoneApp.TV to "gallery", IPhoneApp.MAIL to "mail"))
        assertEquals(mapOf(IPhoneApp.PHOTOS to "gallery", IPhoneApp.MAIL to "mail"), roles)
    }

    @Test fun `no apps at all gives no roles`() {
        assertEquals(emptyMap<IPhoneApp, String>(), assignRoles(IPhoneApp.entries.map { it to null }))
    }

    @Test fun `earlier roles win a shared app`() {
        val roles = assignRoles(listOf(IPhoneApp.MAIL to "x", IPhoneApp.NOTES to "x"))
        assertEquals(mapOf(IPhoneApp.MAIL to "x"), roles)
    }
}

/**
 * Arrange Like iPhone crashed Folio: finding the first app for any role overflowed the stack. This runs the real
 * lookup with an installed app that matches a role, which is what a phone does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResolveIPhoneAppsTest {
    @Test fun `finding an app for a role does not overflow the stack`() {
        val icon = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val tv = AppEntry("com.example.tv/.Main", "TV", icon)
        val roles = resolveIPhoneApps(RuntimeEnvironment.getApplication(), listOf(tv), messagesApp = null)
        assertEquals(tv.id, roles[IPhoneApp.TV])
    }
}
