package com.mccal.folio

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

/** Simple Mode's people are saved as JSON inside the launcher's state, and added one at a time from the contact picker. */
class SimpleHomeTest {
    @Test fun `people survive being saved and read back, whatever is in their names`() {
        val people = listOf(
            SimpleFavorite("Smith, Jane", "+1 (412) 555-0100"),
            SimpleFavorite("Dr. \"Bob\" O'Neil", "412-555-0101"),
            SimpleFavorite("Zoë, \\ 母", "5550102"))
        assertEquals(people, simpleFavoritesFromJson(JSONArray(simpleFavoritesToJson(people).toString())))
    }

    @Test fun `nothing saved reads as nobody, and an entry without a number is dropped`() {
        assertEquals(emptyList<SimpleFavorite>(), simpleFavoritesFromJson(null))
        assertEquals(listOf(SimpleFavorite("A", "1")), simpleFavoritesFromJson(JSONArray("""[{"name":"B","phone":""},{"name":"A","phone":"1"}]""")))
    }

    @Test fun `only four people are kept, and adding a known number moves it to the end`() {
        val four = (1..4).map { SimpleFavorite("P$it", "$it") }
        assertEquals(four, simpleFavoritesFromJson(simpleFavoritesToJson(four + SimpleFavorite("P5", "5"))))
        assertEquals(listOf(four[1], four[2], four[3], SimpleFavorite("P5", "5")), four.withFavorite(SimpleFavorite("P5", "5")))
        assertEquals(listOf(four[1], four[2], four[3], SimpleFavorite("One", "1")), four.withFavorite(SimpleFavorite("One", "1")))
    }
}
