package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterAppsTest {
    private fun phone(vararg packages: String) = packages.mapIndexed { i, pkg -> "id$i" to pkg }

    @Test fun onePerJobInOrder() {
        val installed = phone("com.google.android.apps.maps", "com.google.android.dialer", "com.android.chrome", "com.google.android.apps.messaging")
        // Order follows the groups (dialer, messages, camera, browser, ... maps), not the order the phone lists them.
        assertEquals(listOf("id1", "id3", "id2", "id0"), starterAppIds(installed))
    }

    @Test fun theFirstInstalledAppOfAJobWins() {
        val installed = phone("com.samsung.android.dialer", "com.google.android.dialer")
        // Both are dialers; the group lists Google's first, so that one is ticked and Samsung's is not.
        assertEquals(listOf("id1"), starterAppIds(installed))
    }

    @Test fun aPhoneWithNoneOfThemGetsNothingTicked() {
        assertTrue(starterAppIds(phone("com.example.notes", "org.other.game")).isEmpty())
    }

    @Test fun atMostEightAreTicked() {
        val everything = STARTER_APP_GROUPS.map { it.first() }
        assertEquals(8, starterAppIds(everything.mapIndexed { i, pkg -> "id$i" to pkg }).size)
        assertEquals(3, starterAppIds(everything.mapIndexed { i, pkg -> "id$i" to pkg }, max = 3).size)
    }

    @Test fun twoCopiesOfOneAppKeepTheFirstId() {
        // A work-profile clone shares the package name; the first entry in the list is the one used.
        assertEquals(listOf("personal"), starterAppIds(listOf("personal" to "com.android.chrome", "work" to "com.android.chrome")))
    }

    @Test fun rowsFillFourAcross() {
        assertEquals(0, homeRowsFor(0))
        assertEquals(1, homeRowsFor(1))
        assertEquals(1, homeRowsFor(4))
        assertEquals(2, homeRowsFor(5))
        assertEquals(2, homeRowsFor(8))
        assertEquals(3, homeRowsFor(9))
    }
}
