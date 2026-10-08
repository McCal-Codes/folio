package com.mccal.folio

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Icon swipe and double tap actions: only set gestures are kept, they survive saving and backups, and old saves load unchanged. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IconActionsTest {
    private val mail = "com.example.mail/.Main"
    private val torch = IconActions(up = ActionRef("TORCH"), double = ActionRef("app.open", mapOf("pkg" to "com.example.camera")))

    @Test fun `actions round trip and only set gestures are written`() {
        val json = torch.toJson()
        assertFalse(json.has("down"))
        assertEquals(torch, IconActions.fromJson(json))
        val map = mapOf(mail to torch, "b" to IconActions(down = ActionRef("SPOTLIGHT")))
        assertEquals(map, iconActionsFromJson(iconActionsToJson(map)))
    }

    @Test fun `an unknown action id is kept and written back unchanged`() {
        val saved = JSONObject("""{"$mail":{"up":{"id":"future.thing","a":{"x":"1"}},"down":{"id":"TORCH"}}}""")
        val loaded = iconActionsFromJson(saved)
        assertEquals(ActionRef("future.thing", mapOf("x" to "1")), loaded.getValue(mail).up)
        assertEquals(saved.toString(), iconActionsToJson(loaded).toString())
    }

    @Test fun `a damaged entry is dropped and the rest stay`() {
        val saved = JSONObject("""{"a":"nope","b":{"up":{"id":""},"down":{"x":1}},"c":{"up":5,"double":{"id":"TORCH"}},"d":{}}""")
        assertEquals(mapOf("c" to IconActions(double = ActionRef("TORCH"))), iconActionsFromJson(saved))
        assertEquals(emptyMap<String, IconActions>(), iconActionsFromJson(null))
    }

    @Test fun `an empty map writes nothing, and an empty entry is never stored`() {
        assertEquals(0, iconActionsToJson(emptyMap()).length())
        assertEquals(0, iconActionsToJson(mapOf(mail to IconActions())).length())
        assertTrue(IconActions().isEmpty)
        assertFalse(torch.isEmpty)
    }

    @Test fun `editing sets, replaces, clears and removes empty`() {
        val set = editIconActions(emptyMap(), mail, torch)
        assertEquals(mapOf(mail to torch), set)
        val replaced = editIconActions(set, mail, torch.copy(up = null))
        assertNull(replaced.getValue(mail).up)
        assertEquals("clearing every gesture removes the entry", emptyMap<String, IconActions>(), editIconActions(set, mail, IconActions()))
        assertEquals(emptyMap<String, IconActions>(), editIconActions(emptyMap(), mail, IconActions()))
    }

    @Test fun `a save from before this feature loads with none`() {
        assertTrue(decodeLauncherState("{}", legacyRaw = null).iconActions.isEmpty())
        val saved = JSONObject().put("iconActions", iconActionsToJson(mapOf(mail to torch))).toString()
        assertEquals(torch, decodeLauncherState(saved, legacyRaw = null).iconActions[mail])
    }

    @Test fun `an app that only has actions is tracked, so uninstalling it clears them`() {
        val state = LauncherState(iconActions = mapOf(mail to torch), homeSlots = listOf("homed"))
        assertEquals(setOf(mail, "homed"), state.trackedAppIds().toSet())
        assertEquals(emptyMap<String, IconActions>(), state.iconActions - setOf(mail))
    }

    @Test fun `a backup carries actions and a restore merges them over what is on the phone`() {
        val base = LauncherState(homeSlots = List(HOME_CELLS) { null }, widgetPlacements = emptyList(), loading = false)
        val withActions = base.copy(iconActions = mapOf(mail to torch))
        val preview = decodeLayoutBackup(encodeLayoutBackup(withActions, emptyList(), "phone"), emptyList(), emptyList(), "other")
        assertEquals(mapOf(mail to torch), preview.iconActions)
        val onPhone = mapOf("b" to IconActions(down = ActionRef("SPOTLIGHT")))
        assertEquals(setOf("b", mail), (onPhone + preview.iconActions).keys)
        val plain = decodeLayoutBackup(encodeLayoutBackup(base, emptyList(), "phone"), emptyList(), emptyList(), "other")
        assertTrue(plain.iconActions.isEmpty())
        assertFalse(JSONObject(encodeLayoutBackup(base, emptyList(), "phone")).has("iconActions"))
    }

    @Test fun `a save with icon actions is written as schema 10 and one without stays schema 9`() {
        assertEquals(9, stateSchemaFor(LauncherState()))
        assertEquals(10, stateSchemaFor(LauncherState(iconActions = mapOf("a" to IconActions(up = ActionRef("TORCH"))))))
        assertEquals("the newest schema this build reads", 10, STATE_SCHEMA)
    }

    @Test fun `icon actions load from a save, and a schema newer than this build reads is still refused`() {
        val saved = LayoutLoadTest().schema8Fixture().put("iconActions", iconActionsToJson(mapOf(mail to torch)))
        assertEquals(torch, decodeLauncherState(saved.toString(), legacyRaw = null).iconActions[mail])
        // An older build runs this same check against a schema 10 save: its newest is 9, so it refuses the file visibly.
        assertThrows(Exception::class.java) { decodeLauncherState(saved.put("schema", STATE_SCHEMA + 1).toString(), legacyRaw = null) }
    }

    @Test fun `the first save that writes schema 10 keeps the schema 9 file once`() {
        assertTrue(v9BackupNeeded(sourceSchema = 9, writtenSchema = 10, hasBackup = false))
        assertTrue("an older source still counts", v9BackupNeeded(sourceSchema = 8, writtenSchema = 10, hasBackup = false))
        assertFalse("kept once, never overwritten", v9BackupNeeded(9, 10, hasBackup = true))
        assertFalse("a save that stays schema 9 has nothing to keep", v9BackupNeeded(9, 9, hasBackup = false))
        assertFalse("a file that is already schema 10 has nothing older to keep", v9BackupNeeded(10, 10, hasBackup = false))
    }
}
