package com.mccal.folio

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Simple Mode's people are saved as JSON inside the setup, and added one at a time from the contact picker. */
class SimpleFavoritesTest {
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

/** The people editor's list operations, none of which need a screen. */
class SimplePeopleEditingTest {
    private val a = SimpleFavorite("A", "1")
    private val b = SimpleFavorite("B", "2")
    private val c = SimpleFavorite("C", "3")

    @Test fun `moving up or down at an edge, or from a bad place, changes nothing`() {
        val list = listOf(a, b, c)
        assertEquals(list, list.movedUp(0))
        assertEquals(list, list.movedDown(2))
        assertEquals(list, list.movedUp(5))
        assertEquals(list, list.movedDown(-1))
        assertEquals(emptyList<SimpleFavorite>(), emptyList<SimpleFavorite>().movedUp(0))
    }

    @Test fun `moving swaps neighbours and leaves the rest where they were`() {
        assertEquals(listOf(b, a, c), listOf(a, b, c).movedUp(1))
        assertEquals(listOf(a, c, b), listOf(a, b, c).movedDown(1))
    }

    @Test fun `removing keeps the others in order, and a bad place removes nobody`() {
        assertEquals(listOf(a, c), listOf(a, b, c).removedAt(1))
        assertEquals(listOf(a, b, c), listOf(a, b, c).removedAt(3))
        assertEquals(listOf(a, b, c), listOf(a, b, c).removedAt(-1))
    }
}

/** People live in [SimpleSetup], so they are saved, restored and limited with the rest of Simple's setup. */
class SimpleSetupPeopleTest {
    private val jane = SimpleFavorite("Jane", "412-555-0100")
    private val bob = SimpleFavorite("Bob", "412-555-0101")

    @Test fun `people are saved with the rest of the setup and read back in order`() {
        val setup = SimpleSetup(listOf("a", "b"), SimpleStyle.GRID, SimpleTextSize.LARGER, listOf(bob, jane))
        assertEquals(setup, SimpleSetup.fromJson(JSONObject(setup.toJson().toString())))
    }

    @Test fun `a setup saved before people existed reads as nobody, with everything else as it was`() {
        val old = JSONObject("""{"apps":["a","b"],"style":"GRID","textSize":"LARGEST"}""")
        assertEquals(SimpleSetup(listOf("a", "b"), SimpleStyle.GRID, SimpleTextSize.LARGEST), SimpleSetup.fromJson(old))
        assertEquals(emptyList<SimpleFavorite>(), SimpleSetup.fromJson(old).people)
    }

    @Test fun `sanitizing keeps each number once, drops a blank number, and stops at four people`() {
        val many = (1..6).map { SimpleFavorite("P$it", "$it") }
        assertEquals(many.take(SIMPLE_MAX_FAVORITES), SimpleSetup(people = many).sanitized().people)
        assertEquals(listOf(jane), SimpleSetup(people = listOf(jane, jane.copy(name = "Jane again"), SimpleFavorite("No number", " "))).sanitized().people)
    }

    @Test fun `changing the apps or the style leaves the people alone`() {
        val setup = SimpleSetup(listOf("a"), people = listOf(jane, bob))
        assertEquals(listOf(jane, bob), setup.copy(apps = listOf("b", "a")).people)
        assertEquals(listOf(jane, bob), setup.copy(style = SimpleStyle.GRID).sanitized().people)
    }
}
