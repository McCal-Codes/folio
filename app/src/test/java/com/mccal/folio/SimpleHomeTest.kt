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

/** The people editor's list operations, and setup's choice, none of which need a screen. */
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

    @Test fun `setup saves who it is for and turns Simple Mode on, keeping what it replaces`() {
        val before = LauncherState(island = false)
        val after = before.withSimpleSetup("HELPING")
        assertEquals("HELPING", after.simpleFor)
        assertEquals(true, after.simpleMode)
        assertEquals(false, after.preSimpleIsland)
        assertEquals("ME", before.withSimpleSetup("anything else").simpleFor)
        // Leaving puts back what the person sees; the saved pre-Simple copy may stay behind, unused until next time.
        val left = after.withoutSimpleMode()
        assertEquals(false, left.simpleMode)
        assertEquals(listOf(before.island, before.folioPanels, before.swipeDownHome), listOf(left.island, left.folioPanels, left.swipeDownHome))
    }

    @Test fun `cancelling setup is not calling it, so the state is the one before`() {
        // Cancel and Back never reach the model; the only writer is withSimpleSetup, which the original state is untouched by.
        val before = LauncherState(island = false)
        before.withSimpleSetup("HELPING")
        assertEquals(LauncherState(island = false), before)
        assertEquals(false, before.simpleMode)
    }
}
