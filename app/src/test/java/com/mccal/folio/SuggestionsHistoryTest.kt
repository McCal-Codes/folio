package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionsHistoryTest {
    private val uses = listOf("a" to 100L, "b" to 200L, "c" to 300L)

    @Test fun noCutoffKeepsEverything() = assertEquals(uses, SuggestionsHistory.keepAfter(uses, 0L))

    @Test fun olderAndEqualUsesAreIgnoredNewerCount() =
        assertEquals(listOf("c" to 300L), SuggestionsHistory.keepAfter(uses, 200L))

    @Test fun clearingSetsTheCutoffToNow() = assertEquals(500L, SuggestionsHistory.nextCutoff(500L, 0L))

    @Test fun aClockGoingBackwardCannotBringOldHistoryBack() {
        val cutoff = SuggestionsHistory.nextCutoff(now = 250L, current = 400L)
        assertEquals(400L, cutoff)
        assertEquals(emptyList<Pair<String, Long>>(), SuggestionsHistory.keepAfter(uses, cutoff))
    }

    @Test fun scoresAfterAClearIgnoreOldUses() {
        val now = 10L * 24 * 60 * 60 * 1000
        val all = listOf("a" to now - 1000L, "b" to now - 5000L)
        val kept = SuggestionsHistory.keepAfter(all, now - 2000L)
        assertEquals(setOf("a"), Suggestions.scores(kept, now, 0L).keys)
    }
    @Test fun aClearBumpsTheGenerationSoShownSuggestionsRebuild() {
        val before = SuggestionsHistory.generation.value
        SuggestionsHistory.markCleared()
        assertEquals(before + 1, SuggestionsHistory.generation.value)
    }
}
