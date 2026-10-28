package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageActionsTest {
    private val manage = IntentSpec(StorageActions.MANAGE_STORAGE)
    private val internal = IntentSpec(StorageActions.INTERNAL_STORAGE)

    @Test fun `free up space tries manage storage first, then the internal storage settings`() {
        assertEquals(listOf(manage, internal), StorageActions.candidates(StorageAction.FreeUpSpace))
    }

    @Test fun `a missing manage storage screen falls back to the internal storage settings`() {
        val tried = mutableListOf<IntentSpec>()
        val result = StorageActions.launch(StorageAction.FreeUpSpace) { spec -> tried += spec; spec == internal }
        assertEquals(listOf(manage, internal), tried)
        assertEquals(StorageLaunch.Opened(internal), result)
    }

    @Test fun `the first screen that opens ends the search`() {
        val tried = mutableListOf<IntentSpec>()
        val result = StorageActions.launch(StorageAction.FreeUpSpace) { spec -> tried += spec; true }
        assertEquals(listOf(manage), tried)
        assertEquals(StorageLaunch.Opened(manage), result)
    }

    @Test fun `nothing resolving gives the unavailable result`() {
        for (action in StorageAction.entries) {
            assertEquals(StorageLaunch.Unavailable, StorageActions.launch(action) { false })
        }
    }

    @Test fun `a resolver that throws counts as not resolving and does not escape`() {
        val result = StorageActions.launch(StorageAction.FreeUpSpace) { throw IllegalStateException("no") }
        assertEquals(StorageLaunch.Unavailable, result)
    }

    @Test fun `the other three actions use the standard system actions`() {
        assertEquals(listOf(internal), StorageActions.candidates(StorageAction.StorageSettings))
        assertEquals(listOf(IntentSpec("android.intent.action.MAIN", "android.intent.category.APP_FILES")), StorageActions.candidates(StorageAction.Files))
        assertEquals(listOf(IntentSpec("android.intent.action.VIEW_DOWNLOADS")), StorageActions.candidates(StorageAction.Downloads))
    }

    @Test fun `every action has at least one candidate and none names a maker component`() {
        for (action in StorageAction.entries) {
            val specs = StorageActions.candidates(action)
            assertTrue("$action has candidates", specs.isNotEmpty())
            assertTrue("$action uses standard actions only", specs.none { "samsung" in it.action.lowercase() })
        }
    }
}
