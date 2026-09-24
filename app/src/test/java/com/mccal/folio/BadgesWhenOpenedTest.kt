package com.mccal.folio

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Clear Badges When Opened: opening an app remembers the badge that was showing as seen, the badge stays away while
 * nothing new arrives, and a new notification brings it straight back ([BadgesWhenOpened]).
 */
class BadgesWhenOpenedTest {
    private val mail = "com.example.mail"
    private val chat = "com.example.chat"

    @Test fun `opening an app takes its badge away and leaves every other app alone`() {
        val posted = mapOf(mail to 3, chat to 2)
        // Mail was opened while three notifications were showing: three is seen.
        val seen = mapOf(mail to 3)
        assertEquals(mapOf(chat to 2), BadgesWhenOpened.visible(posted, seen))
    }

    @Test fun `a new notification brings the badge back, with every notification counted`() {
        val seen = mapOf(mail to 3)
        // A fourth arrives: the badge is back, and it says four rather than the one new one.
        assertEquals(mapOf(mail to 4), BadgesWhenOpened.visible(mapOf(mail to 4), seen))
    }

    @Test fun `an app updating the notifications you already saw keeps its badge away`() {
        // A download progressing or music playing replaces a notification without adding one.
        assertEquals(emptyMap<String, Int>(), BadgesWhenOpened.visible(mapOf(mail to 2), mapOf(mail to 3)))
    }

    @Test fun `with the feature off or the gate shut, every badge is drawn exactly as posted`() {
        val posted = mapOf(mail to 3, chat to 2)
        assertEquals(posted, BadgesWhenOpened.visible(posted, emptyMap()))
    }

    @Test fun `notifications cleared elsewhere lower what counts as seen, so the next one shows`() {
        val seen = mapOf(mail to 5, chat to 2)
        // Three of Mail's five were swiped away in the shade, and Chat's two are gone entirely.
        val trimmed = BadgesWhenOpened.trimmed(mapOf(mail to 2), seen)
        assertEquals(mapOf(mail to 2), trimmed)
        // Without the trim, Mail's next three notifications would have been hidden as well.
        assertEquals(mapOf(mail to 3), BadgesWhenOpened.visible(mapOf(mail to 3), trimmed))
        assertEquals(emptyMap<String, Int>(), BadgesWhenOpened.visible(mapOf(mail to 2), trimmed))
    }

    @Test fun `a seen count survives a restart`() {
        // The smallest save the decoder accepts, plus the switch and one seen count.
        val empty = JSONArray().apply { repeat(HOME_CELLS) { put(JSONObject.NULL) } }
        val saved = JSONObject().put("schema", STATE_SCHEMA)
            .put("pinned", JSONArray()).put("homeSlots", empty).put("leadingSlots", empty)
            .put("dock", JSONArray()).put("widgets", JSONArray()).put("folders", JSONArray())
            .put("badgesWhenOpened", true)
            .put("badgesSeen", JSONObject().put(mail, 3).put(chat, 0))
        val state = decodeLauncherState(saved.toString(), legacyRaw = null)
        assertTrue(state.badgesWhenOpened)
        assertEquals(3, state.badgesSeen[mail])
        assertTrue("an app with nothing seen is not kept", chat !in state.badgesSeen)
    }

    @Test fun `a save from before the feature reads as off, with nothing seen`() {
        val empty = JSONArray().apply { repeat(HOME_CELLS) { put(JSONObject.NULL) } }
        val saved = JSONObject().put("schema", STATE_SCHEMA)
            .put("pinned", JSONArray()).put("homeSlots", empty).put("leadingSlots", empty)
            .put("dock", JSONArray()).put("widgets", JSONArray()).put("folders", JSONArray())
        val state = decodeLauncherState(saved.toString(), legacyRaw = null)
        assertEquals(false, state.badgesWhenOpened)
        assertEquals(emptyMap<String, Int>(), state.badgesSeen)
    }

    @Test fun `Home's badges go through the seen ones, and both entry points are gated`() {
        val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
        val main = java.io.File(root, "app/src/main/java/com/mccal/folio/MainActivity.kt").readText()
        assertTrue("the badges Home draws should pass through BadgesWhenOpened.visible", "BadgesWhenOpened.visible(" in main)
        assertTrue("launching an app should remember the badge it was showing", "noteBadgeSeen(app.packageName)" in main)
        // REL-4a: hidden work that still costs battery is wrong, so the gate is asked where the work starts.
        assertTrue("the work itself should be gated, not only the switch", "FeatureGate.BADGES_WHEN_OPENED.isOpen" in main)
    }
}
