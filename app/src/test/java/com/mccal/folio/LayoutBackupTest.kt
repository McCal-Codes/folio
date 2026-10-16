package com.mccal.folio

import android.graphics.Bitmap
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What a Layout Backup says about the apps on the phone: only the ones the layout itself names. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LayoutBackupTest {
    private val icon: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private fun app(name: String) = AppEntry("com.example.$name/com.example.$name.Main", name.replaceFirstChar(Char::uppercase), icon)

    private val home = app("mail")
    private val docked = app("phone")
    private val inFolderA = app("notes")
    private val inFolderB = app("photos")
    private val renamed = app("weather")
    private val hidden = app("diary")
    private val unplaced = app("calculator")
    private val work = AppEntry("duo-profile:v1:42:com.example.chat/com.example.chat.Main", "Chat", icon,
        userSerial = 42, profileLabel = "Work", isWork = true)
    private val folder = FolderEntry("folder:11111111-1111-1111-1111-111111111111", "Stuff", listOf(inFolderA.id, inFolderB.id))

    private val state = LauncherState(
        apps = listOf(home, docked, inFolderA, inFolderB, renamed, hidden, unplaced, work),
        homeSlots = MutableList<String?>(HOME_CELLS) { null }.apply { this[0] = home.id; this[1] = folder.id },
        dock = listOf(docked.id, null, null, null),
        folders = listOf(folder),
        appNames = mapOf(renamed.id to "Sky"),
        hiddenApps = setOf(hidden.id),
        widgetPlacements = emptyList(),
        loading = false,
    )

    private fun savedIds(raw: String): Set<String> {
        val apps = JSONObject(raw).getJSONArray("apps")
        return (0 until apps.length()).mapTo(mutableSetOf()) { apps.getJSONObject(it).getString("id") }
    }

    @Test fun `a backup lists only the apps the layout names`() {
        val raw = encodeLayoutBackup(state, emptyList(), "this-phone")
        assertEquals(setOf(home.id, docked.id, inFolderA.id, inFolderB.id, renamed.id), savedIds(raw))
        assertFalse("a hidden app nobody placed is not in the file", hidden.id in savedIds(raw))
        assertFalse("a work app nobody placed is not in the file", work.id in savedIds(raw))
        assertFalse(unplaced.id in savedIds(raw))
    }

    @Test fun `an app with only an icon action or icon look is still described`() {
        val withExtras = state.copy(
            iconActions = mapOf(unplaced.id to IconActions(up = ActionRef("TORCH"))),
            appIconStyles = mapOf(work.id to AppIconOverride(shape = IconShape.CIRCLE)),
        )
        val saved = savedIds(encodeLayoutBackup(withExtras, emptyList(), "this-phone"))
        assertEquals(setOf(home.id, docked.id, inFolderA.id, inFolderB.id, renamed.id, unplaced.id, work.id), saved)
        assertFalse(hidden.id in saved)
    }

    @Test fun `a layout round-trips unchanged with the shorter list`() {
        val raw = encodeLayoutBackup(state, emptyList(), "this-phone")
        val preview = decodeLayoutBackup(raw, state.apps, emptyList(), "this-phone")
        assertEquals(emptyList<String>(), preview.missingApps)
        assertEquals(state.layout.slots.dropLastWhile { it == null }, preview.layout.slots)
        assertEquals(state.dock, preview.layout.dock)
        assertEquals(listOf(folder), preview.layout.folders)
        assertEquals(state.appNames, preview.appNames)
        assertEquals(4, preview.appCount)
    }
}
