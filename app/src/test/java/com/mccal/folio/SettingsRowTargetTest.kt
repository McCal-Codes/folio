package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRowTargetTest {
    @Test fun anEntryWithKeywordsThatIsAlsoARowStillScrollsToItsRow() {
        assertTrue(isRowEntry(R.string.folder_background, R.string.settings_keywords_folder_background))
    }

    @Test fun aTitleInBothListsIsOneSearchResult() {
        val results = SettingsEntries.map { it.first to it.third }
        assertEquals("Search lists a setting twice: ${results.groupBy { it }.filterValues { it.size > 1 }.keys}", results.distinct().size, results.size)
        assertEquals(1, SettingsEntries.count { it.first == R.string.folder_background })
    }

    @Test fun anEntryWithNoKeywordsIsARow() = assertTrue(isRowEntry(R.string.reduce_transparency, null))

    @Test fun aTopicWithKeywordsThatIsNotARowOpensItsPageAtTheTop() {
        assertFalse(isRowEntry(R.string.settings_appearance_light_dark_sunset, R.string.settings_keywords_appearance_light_dark_sunset))
    }
}
