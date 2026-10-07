package com.mccal.folio.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoUpdateLogTest {
    private fun u(id: String, from: String, to: String, at: Long) = AutoUpdate(id, id.uppercase(), DebVersion.parse(from)!!, DebVersion.parse(to)!!, at)

    @Test fun `newest first, trimmed to the limit, and read back after a restart`() {
        val store = MemoryStore()
        val log = AutoUpdateLog(store, max = 3)
        listOf(u("a", "1", "2", 1), u("b", "1", "2", 2), u("c", "1", "2", 3), u("d", "1", "2", 4)).forEach(log::record)
        assertEquals(listOf("d", "c", "b"), AutoUpdateLog(store, max = 3).recent().map { it.id })
    }

    @Test fun `marking one undone changes only that entry`() {
        val log = AutoUpdateLog(MemoryStore())
        log.record(u("a", "1", "2", 1)); log.record(u("b", "1", "2", 2))
        log.markUndone("a", DebVersion.parse("2")!!)
        assertEquals(listOf(false, true), log.recent().map { it.undone })
    }

    @Test fun `damaged data reads as empty, not a crash`() {
        val store = MemoryStore(); store.set("market:auto-update-log", "not json")
        assertTrue(AutoUpdateLog(store).recent().isEmpty())
    }

    @Test fun `a package can be turned off on its own, and the global switch is checked first`() {
        val prefs = MarketPrefs(MemoryStore())
        assertTrue("off globally means off for every package", !prefs.autoUpdateFor("x"))
        prefs.autoUpdatePackages = true
        assertTrue(prefs.autoUpdateFor("x"))
        prefs.setAutoUpdateFor("x", false)
        assertTrue(!prefs.autoUpdateFor("x") && prefs.autoUpdateFor("y") && prefs.autoUpdateTurnedOff("x"))
        prefs.setAutoUpdateFor("x", true)
        assertTrue(prefs.autoUpdateFor("x") && !prefs.autoUpdateTurnedOff("x"))
    }
}
