package com.mccal.folio

import android.graphics.Bitmap
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // The outside review (20 Oct 2026, bug 2): export never checked what import requires, so a backup could save and then refuse to restore.
    @Test fun `an app whose label is blank still round-trips, named by its package`() {
        val blank = AppEntry("com.example.quiet/com.example.quiet.Main", " ", icon)
        val empty = AppEntry("com.example.silent/com.example.silent.Main", "", icon)
        val withBlanks = state.copy(apps = state.apps + blank + empty,
            homeSlots = state.homeSlots.toMutableList().also { it[2] = blank.id; it[3] = empty.id })
        val raw = encodeLayoutBackup(withBlanks, emptyList(), "this-phone")
        val preview = decodeLayoutBackup(raw, withBlanks.apps, emptyList(), "this-phone")
        assertEquals(emptyList<String>(), preview.missingApps)
        assertTrue(preview.layout.slots.contains(blank.id) && preview.layout.slots.contains(empty.id))
        val labels = JSONObject(raw).getJSONArray("apps").let { a -> (0 until a.length()).associate { a.getJSONObject(it).getString("id") to a.getJSONObject(it).getString("label") } }
        assertEquals("com.example.quiet", labels[blank.id])
        assertEquals("com.example.silent", labels[empty.id])
    }

    @Test fun `an app with a blank profile label still round-trips`() {
        val odd = AppEntry("com.example.odd/com.example.odd.Main", "Odd", icon, profileLabel = "")
        val withOdd = state.copy(apps = state.apps + odd, homeSlots = state.homeSlots.toMutableList().also { it[2] = odd.id })
        val raw = encodeLayoutBackup(withOdd, emptyList(), "this-phone")
        val preview = decodeLayoutBackup(raw, withOdd.apps, emptyList(), "this-phone")
        // The export wrote a name for the profile instead of a blank one (import refuses blanks), and the app is still where it was.
        val saved = JSONObject(raw).getJSONArray("apps").let { a -> (0 until a.length()).map { a.getJSONObject(it) }.first { it.getString("id") == odd.id } }
        assertEquals("personal", saved.getString("profileLabel"))
        assertEquals(odd.id, preview.layout.slots[2])
        assertEquals(emptyList<String>(), preview.missingApps)
    }

    // The Music widget is a Folio-drawn widget like the Big Clock: the export used to drop it ("left out because its app is missing").
    @Test fun `a Music widget is kept by the export and comes back on import`() {
        val music = WidgetPlacement(7, MUSIC_WIDGET, 0, 0, 1, 3, 1)
        val withMusic = state.copy(widgetPlacements = listOf(music))
        val (portable, skipped) = withoutUnportableWidgets(withMusic, emptyList())
        assertEquals(0, skipped)
        assertEquals(listOf(MUSIC_WIDGET), portable.widgetPlacements.map { it.id })
        val preview = decodeLayoutBackup(encodeLayoutBackup(portable, emptyList(), "this-phone"), state.apps, emptyList(), "this-phone")
        assertEquals(listOf(music), preview.layout.widgetPlacements)
        assertEquals(0, preview.skippedWidgets)
    }

    // A backup from a newer build can carry a widget this one does not know. It used to refuse the whole file; now that item is left out and the rest restored.
    @Test fun `a widget of a kind this build does not have is skipped and the rest is restored`() {
        val withTwo = state.copy(widgetPlacements = listOf(WidgetPlacement(7, MUSIC_WIDGET, 0, 0, 1, 3, 1), WidgetPlacement(8, CLOCK_WIDGET, 0, 2, 2, 2, 2)))
        val root = JSONObject(encodeLayoutBackup(withTwo, emptyList(), "this-phone"))
        val items = root.getJSONArray("widgets")
        (0 until items.length()).map { items.getJSONObject(it) }.first { it.getInt("slot") == 7 }.put("builtinId", -4242)
        val preview = decodeLayoutBackup(root.toString(), state.apps, emptyList(), "this-phone")
        assertEquals(1, preview.skippedWidgets)
        assertEquals(listOf(CLOCK_WIDGET), preview.layout.widgetPlacements.map { it.id })
        assertEquals(1, preview.widgetCount)
        assertEquals(home.id, preview.layout.slots[0])
    }

    @Test fun `a malformed widget is still refused, not skipped`() {
        val root = JSONObject(encodeLayoutBackup(state.copy(widgetPlacements = listOf(WidgetPlacement(7, CLOCK_WIDGET, 0, 0, 1, 2, 2))), emptyList(), "this-phone"))
        root.getJSONArray("widgets").getJSONObject(0).put("spanX", 99)
        val refused = runCatching { decodeLayoutBackup(root.toString(), state.apps, emptyList(), "this-phone") }.isFailure
        assertTrue(refused)
    }

    // The outside review's bug 1: one widget whose app is missing stopped the whole export.
    @Test fun `a widget with no provider is left out and the rest of the backup is still made`() {
        val gone = WidgetPlacement(5, 4101, 0, 0, 1, 2, 2)
        val live = WidgetPlacement(6, 4102, 0, 2, 1, 2, 2)
        val withWidgets = state.copy(widgetPlacements = listOf(gone, live))
        val descriptors = listOf(BackupWidgetDescriptor(6, "com.example.clock/com.example.clock.Widget", 0L, "Clock", "Personal"))
        val (portable, skipped) = withoutUnportableWidgets(withWidgets, descriptors)
        assertEquals(1, skipped)
        assertEquals(listOf(6), portable.widgetPlacements.map { it.slot })
        // Without the fix this threw "Widget 5 has no portable provider descriptor" (and the controller said "Widget 5 is unavailable").
        val raw = encodeLayoutBackup(portable, descriptors, "this-phone")
        decodeLayoutBackup(raw, state.apps, emptyList(), "this-phone")
    }

    @Test fun `a saved restore entry keeps a widget whose provider is gone`() {
        val gone = WidgetPlacement(5, 4101, 0, 0, 1, 2, 2)
        val restore = WidgetRestore(5, "com.example.old/com.example.old.Widget", 0L, "Old", "Personal", false, "this-phone")
        val kept = state.copy(widgetPlacements = listOf(gone), widgetRestores = listOf(restore))
        val (portable, skipped) = withoutUnportableWidgets(kept, emptyList())
        assertEquals(0, skipped)
        assertEquals(1, portable.widgetPlacements.size)
    }

    @Test fun `with every widget described nothing is left out`() {
        val a = WidgetPlacement(5, CLOCK_WIDGET, 0, 0, 1, 2, 2)
        val (_, skipped) = withoutUnportableWidgets(state.copy(widgetPlacements = listOf(a)), emptyList())
        assertEquals(0, skipped)
    }

    // Bug 10: people were shown the exception's own text.
    @Test fun `the failures a person can act on are named, the rest are not leaked`() {
        assertEquals(BackupFailure.TOO_LARGE, backupFailureOf(IllegalArgumentException(BACKUP_TOO_LARGE)))
        assertEquals(BackupFailure.NEWER_VERSION, backupFailureOf(IllegalArgumentException(BACKUP_NEWER_VERSION)))
        assertEquals(BackupFailure.OTHER, backupFailureOf(IllegalArgumentException("Failed requirement.")))
        assertEquals(BackupFailure.OTHER, backupFailureOf(IllegalStateException("Widget 3 is unavailable")))
        assertEquals(BackupFailure.OTHER, backupFailureOf(IllegalStateException()))
    }

    @Test fun `an oversized or too-new file is refused with the named failure`() {
        val tooNew = JSONObject(encodeLayoutBackup(state, emptyList(), "this-phone")).put("version", 9999).toString()
        val error = runCatching { decodeLayoutBackup(tooNew, state.apps, emptyList(), "this-phone") }.exceptionOrNull()!!
        assertEquals(BackupFailure.NEWER_VERSION, backupFailureOf(error))
    }
}
