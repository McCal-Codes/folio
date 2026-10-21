package com.mccal.folio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRowTargetTest {
    @Test fun anEntryWithKeywordsThatIsAlsoARowStillScrollsToItsRow() {
        assertTrue(isRowEntry(R.string.folder_background, R.string.settings_keywords_folder_background))
    }

    @Test fun anEntryWithNoKeywordsIsARow() = assertTrue(isRowEntry(R.string.reduce_transparency, null))

    @Test fun aTopicWithKeywordsThatIsNotARowOpensItsPageAtTheTop() {
        assertFalse(isRowEntry(R.string.settings_appearance_light_dark_sunset, R.string.settings_keywords_appearance_light_dark_sunset))
    }
}
