package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * An app turned off in Settings, put to sleep, or briefly unavailable keeps its Home and Dock place and has it again when
 * it comes back; one that is uninstalled loses it (BI-1 of the 27 Oct break-it test: Chrome disabled then enabled left a
 * hole in the Dock).
 */
class DisabledAppKeepsItsPlaceTest {
    private val me = 10L
    private val chrome = "com.android.chrome/.Main"
    private val maps = "com.maps/.Main"
    private val saved = listOf(chrome, maps)

    /** What a refresh decides: the saved ids that go, given what Android lists, which apps are turned off and which were removed. */
    private fun removedAfterRefresh(listed: Set<String>, disabled: Set<Pair<Long, String>>, removed: Set<Pair<Long, String>> = emptySet(),
        unavailable: Set<Pair<Long, String>> = emptySet()): Set<String> {
        val missing = saved.filter { it !in listed }.map { me to it.substringBefore('/') }
        val away = unavailable + keptWhileDisabled(missing) { it in disabled }
        return removedAppIds(saved, listed, setOf(me), away, removed, me)
    }

    @Test fun `a disabled app keeps its slot`() {
        assertEquals(emptySet<String>(), removedAfterRefresh(listed = setOf(maps), disabled = setOf(me to "com.android.chrome")))
    }

    @Test fun `an uninstalled app loses its slot`() {
        assertEquals(setOf(chrome), removedAfterRefresh(listed = setOf(maps), disabled = emptySet(), removed = setOf(me to "com.android.chrome")))
        // Even with no removal broadcast (Folio was not running), an app that is not installed any more is dropped.
        assertEquals(setOf(chrome), removedAfterRefresh(listed = setOf(maps), disabled = emptySet()))
    }

    @Test fun `a disabled app that is then uninstalled loses its slot`() {
        assertEquals(setOf(chrome), removedAfterRefresh(listed = setOf(maps), disabled = setOf(me to "com.android.chrome"),
            removed = setOf(me to "com.android.chrome")))
    }

    @Test fun `a re-enabled app is listed again and still has the slot`() {
        assertEquals(emptySet<String>(), removedAfterRefresh(listed = setOf(chrome, maps), disabled = emptySet()))
    }

    @Test fun `an app Android reports unavailable keeps its slot as before`() {
        assertEquals(emptySet<String>(), removedAfterRefresh(listed = setOf(maps), disabled = emptySet(),
            unavailable = setOf(me to "com.android.chrome")))
    }

    @Test fun `only missing apps are asked`() {
        val asked = mutableListOf<Pair<Long, String>>()
        val kept = keptWhileDisabled(listOf(me to "a", me to "b")) { asked += it; it.second == "a" }
        assertEquals(setOf(me to "a"), kept)
        assertEquals(listOf(me to "a", me to "b"), asked)
    }
}
