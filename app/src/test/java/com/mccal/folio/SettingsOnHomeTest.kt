package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Add Folio Settings to Home" pins through the same path as Add to Home: first free place, and once only. */
class SettingsOnHomeTest {
    private val folio = "com.mccal.folio/com.mccal.folio.FolioSettingsApp"

    @Test fun `it takes the first free place on Home`() {
        val slots = pinHomeApp(listOf("a", null, "b"), folio, pinned = true)
        assertEquals(listOf("a", folio, "b"), slots.take(3))
    }

    @Test fun `pinning it again changes nothing`() {
        val once = pinHomeApp(listOf("a", null), folio, pinned = true)
        assertEquals(once, pinHomeApp(once, folio, pinned = true))
    }

    @Test fun `a full page spills to the next one instead of failing`() {
        val full = List(HOME_CELLS) { "app$it" }
        assertTrue(folio in pinHomeApp(full, folio, pinned = true))
    }
}
