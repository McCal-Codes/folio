package com.mccal.folio

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRenamingTest {
    private val id = profileAppId("com.example/.Main", 0, 0)

    @Test fun renamingKeepsTheTrimmedNamePerApp() {
        val names = editAppName(emptyMap(), id, "  Mail  ")
        assertEquals(mapOf(id to "Mail"), names)
        assertEquals(mapOf(id to "Post"), editAppName(names, id, "Post"))
    }

    @Test fun blankNameClearsTheCustomNameAndLongNamesAreCapped() {
        val names = editAppName(emptyMap(), id, "Mail")
        assertEquals(emptyMap<String, String>(), editAppName(names, id, "   "))
        assertEquals("N".repeat(MAX_APP_NAME), editAppName(names, id, "N".repeat(MAX_APP_NAME + 20))[id])
    }

    @Test fun `a name is cut where a reader sees a character, not mid-emoji`() {
        // "👍" is two chars to Java and the family is eleven, so a plain take() would leave half of one behind.
        val thumb = "N".repeat(MAX_APP_NAME - 1) + "\uD83D\uDC4D"
        assertEquals("N".repeat(MAX_APP_NAME - 1), thumb.takeAppName())
        val family = "N".repeat(MAX_APP_NAME - 4) + "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        assertEquals("N".repeat(MAX_APP_NAME - 4) + "\uD83D\uDC68", family.takeAppName())
        // Nothing to cut: a name inside the cap comes back whole, emoji and all.
        assertEquals("Mail \uD83D\uDC4D", "Mail \uD83D\uDC4D".takeAppName())
    }

    @Test fun `names loaded from a saved state are trimmed, capped and dropped when blank`() {
        // The smallest save the decoder accepts: an empty Home, an empty unfolded-only page, no widgets.
        val empty = JSONArray().apply { repeat(HOME_CELLS) { put(JSONObject.NULL) } }
        val saved = JSONObject().put("schema", STATE_SCHEMA)
            .put("pinned", JSONArray()).put("homeSlots", empty).put("leadingSlots", empty)
            .put("dock", JSONArray()).put("widgets", JSONArray()).put("folders", JSONArray())
            .put("appNames", JSONObject()
            .put(id, "  Mail  ")
            .put("com.long/.L", "N".repeat(MAX_APP_NAME * 3))
            .put("com.blank/.B", "   "))
        val names = decodeLauncherState(saved.toString(), legacyRaw = null).appNames
        assertEquals("Mail", names[id])
        assertEquals(MAX_APP_NAME, names["com.long/.L"]?.length)
        assertTrue("com.blank/.B" !in names)
    }
}

/** A name is cut to fit whatever it is made of, including characters that carry no break of their own. */
class AppNameCapTest {
    @Test fun `a single cluster longer than the cap is still cut`() {
        val stacked = "A" + "́".repeat(60)
        org.junit.Assert.assertTrue("came back ${stacked.takeAppName().length} long", stacked.takeAppName().length <= MAX_APP_NAME)
    }

    @Test fun `ordinary names are left whole`() {
        org.junit.Assert.assertEquals("Calendar", "Calendar".takeAppName())
        org.junit.Assert.assertEquals("", "".takeAppName())
    }
}
