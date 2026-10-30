package com.mccal.folio

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullTargetTest {
    @Test fun `the half a pull starts in still picks the panel`() {
        assertEquals(ShadePanel.NOTIFICATIONS, pullPanel(0f, 1080f))
        assertEquals(ShadePanel.NOTIFICATIONS, pullPanel(539.9f, 1080f))
        assertEquals(ShadePanel.QUICK_SETTINGS, pullPanel(540f, 1080f))
        assertEquals(ShadePanel.QUICK_SETTINGS, pullPanel(1079f, 1080f))
    }

    @Test fun `both leaves both panels to Folio`() {
        assertTrue(pullOpensFolio(ShadePanel.NOTIFICATIONS, PullTarget.BOTH))
        assertTrue(pullOpensFolio(ShadePanel.QUICK_SETTINGS, PullTarget.BOTH))
    }

    @Test fun `Folio notifications leaves quick settings to the phone`() {
        assertTrue(pullOpensFolio(ShadePanel.NOTIFICATIONS, PullTarget.FOLIO_NOTIFICATIONS))
        assertFalse(pullOpensFolio(ShadePanel.QUICK_SETTINGS, PullTarget.FOLIO_NOTIFICATIONS))
    }

    @Test fun `Folio Control Center leaves notifications to the phone`() {
        assertFalse(pullOpensFolio(ShadePanel.NOTIFICATIONS, PullTarget.FOLIO_CONTROL_CENTER))
        assertTrue(pullOpensFolio(ShadePanel.QUICK_SETTINGS, PullTarget.FOLIO_CONTROL_CENTER))
    }

    @Test fun `a saved name loads and anything else is both`() {
        PullTarget.entries.forEach { assertEquals(it, pullTargetFrom(it.name)) }
        assertEquals(PullTarget.BOTH, pullTargetFrom(null))
        assertEquals(PullTarget.BOTH, pullTargetFrom("NOTIFICATIONS_FROM_THE_FUTURE"))
    }

    private fun load(vararg extra: Pair<String, Any>): LauncherState {
        // The smallest save the decoder accepts: an empty Home, an empty unfolded-only page, no widgets.
        val empty = JSONArray().apply { repeat(HOME_CELLS) { put(JSONObject.NULL) } }
        val saved = JSONObject().put("schema", STATE_SCHEMA)
            .put("pinned", JSONArray()).put("homeSlots", empty).put("leadingSlots", empty)
            .put("dock", JSONArray()).put("widgets", JSONArray()).put("folders", JSONArray())
        extra.forEach { (k, v) -> saved.put(k, v) }
        return decodeLauncherState(saved.toString(), legacyRaw = null)
    }

    @Test fun `a saved choice loads, and an old or garbled save loads as both`() {
        assertEquals(PullTarget.FOLIO_CONTROL_CENTER, load("pullTarget" to PullTarget.FOLIO_CONTROL_CENTER.name).pullTarget)
        assertEquals(PullTarget.FOLIO_NOTIFICATIONS, load("pullTarget" to PullTarget.FOLIO_NOTIFICATIONS.name).pullTarget)
        assertEquals(PullTarget.BOTH, load().pullTarget)
        assertEquals(PullTarget.BOTH, load("pullTarget" to "garbage").pullTarget)
        assertEquals(PullTarget.BOTH, decodeLauncherState("{}", legacyRaw = null).pullTarget)
    }
}
