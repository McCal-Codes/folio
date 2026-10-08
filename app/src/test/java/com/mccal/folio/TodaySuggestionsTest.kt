package com.mccal.folio

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Suggestions row on Today View has a switch, and every save from before it keeps the row. */
class TodaySuggestionsTest {
    @Test fun `the row is on by default, and a save from before the switch keeps it`() {
        assertTrue(LauncherState().todaySuggestions)
        assertTrue(decodeLauncherState(JSONObject().put("leftPage", "TODAY").toString(), legacyRaw = null).todaySuggestions)
        assertTrue(decodeLauncherState("{}", legacyRaw = null).todaySuggestions)
    }

    @Test fun `a saved off stays off`() {
        assertFalse(decodeLauncherState(JSONObject().put("leftPage", "TODAY").put("todaySuggestions", false).toString(), legacyRaw = null).todaySuggestions)
    }
}
