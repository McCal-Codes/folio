package com.mccal.folio

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Per-app icon looks: only real choices are kept, they survive saving and backups, and old saves load unchanged. */
class AppIconOverrideTest {
    private val mail = "com.example.mail/.Main"

    @Test fun `an override that follows the launcher is no entry at all`() {
        assertTrue(AppIconOverride().isDefault)
        assertEquals(emptyMap<String, AppIconOverride>(), editAppIcon(emptyMap(), mail, AppIconOverride()))
        val chosen = editAppIcon(emptyMap(), mail, AppIconOverride(shape = IconShape.CIRCLE))
        assertEquals(mapOf(mail to AppIconOverride(shape = IconShape.CIRCLE)), chosen)
        assertEquals("choosing like-other-icons again removes it", emptyMap<String, AppIconOverride>(), editAppIcon(chosen, mail, AppIconOverride()))
    }

    @Test fun `an own look replaces only the parts it sets`() {
        val base = IconLook(style = IconStyle.TINTED, shape = IconShape.SQUIRCLE)
        assertEquals(base.copy(shape = IconShape.CIRCLE), AppIconOverride(shape = IconShape.CIRCLE).applyTo(base))
        assertEquals(base.copy(style = IconStyle.DARK), AppIconOverride(style = IconStyle.DARK).applyTo(base))
        assertEquals(base, AppIconOverride().applyTo(base))
    }

    @Test fun `choices round trip through JSON, and damaged ones are dropped`() {
        val map = mapOf(mail to AppIconOverride(IconStyle.CLEAR, IconShape.ROUNDED), "b" to AppIconOverride(style = IconStyle.DARK))
        assertEquals(map, appIconStylesFromJson(appIconStylesToJson(map)))
        val damaged = JSONObject("""{"a":{"style":"SPARKLE"},"b":"nope","c":{"shape":"CIRCLE"},"d":{}}""")
        assertEquals(mapOf("c" to AppIconOverride(shape = IconShape.CIRCLE)), appIconStylesFromJson(damaged))
        assertEquals(emptyMap<String, AppIconOverride>(), appIconStylesFromJson(null))
    }

    @Test fun `a save from before the setting has none, and a saved one is read back`() {
        assertTrue(decodeLauncherState("{}", legacyRaw = null).appIconStyles.isEmpty())
        val saved = JSONObject().put("appIconStyles", JSONObject().put(mail, JSONObject().put("shape", "CIRCLE"))).toString()
        assertEquals(AppIconOverride(shape = IconShape.CIRCLE), decodeLauncherState(saved, legacyRaw = null).appIconStyles[mail])
    }

    @Test fun `nothing set writes nothing`() {
        assertFalse(appIconStylesToJson(mapOf(mail to AppIconOverride())).has(mail))
    }

    @Test fun `an app that is only remembered for its icon look is still tracked, so uninstalling it clears the look`() {
        val state = LauncherState(appIconStyles = mapOf(mail to AppIconOverride(shape = IconShape.CIRCLE)), appNames = mapOf("renamed" to "X"),
            dock = listOf("docked", null, null, null), homeSlots = listOf("homed"))
        assertEquals(setOf(mail, "renamed", "docked", "homed"), state.trackedAppIds().toSet())
    }
}
