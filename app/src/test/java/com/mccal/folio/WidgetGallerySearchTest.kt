package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetGallerySearchTest {
    @Test fun anEmptySearchMatchesEverything() = assertTrue(matchesWidgetSearch("", "Clock"))

    @Test fun aSearchMatchesAnyPartOfAnyTextIgnoringCase() {
        assertTrue(matchesWidgetSearch("big", "Big Clock", "Folio"))
        assertTrue(matchesWidgetSearch("folio", "Date", "Folio"))
        assertTrue(matchesWidgetSearch("clo", "Big Clock"))
    }

    @Test fun aSearchThatAppearsNowhereMatchesNothing() = assertFalse(matchesWidgetSearch("zzz", "Big Clock", "Folio"))

    @Test fun folioOwnWidgetsAreFoundByLabelLikeAnyAppsWidgets() {
        val labels = listOf("Clock", "Date", "Up Next", "Big Clock", "Suggestions", "Widget Panel")
        assertEquals(listOf("Clock", "Big Clock"), labels.filter { matchesWidgetSearch("clock", it, "Folio") })
        assertEquals(labels, labels.filter { matchesWidgetSearch("folio", it, "Folio") })
    }

    @Test fun noWidgetsFoundOnlyShowsWhenNothingAtAllIsShown() {
        assertTrue(galleryIsEmpty(loaded = true, systemWidgets = 0, folioWidgets = 0))
        assertFalse(galleryIsEmpty(loaded = true, systemWidgets = 0, folioWidgets = 2))
        assertFalse(galleryIsEmpty(loaded = true, systemWidgets = 3, folioWidgets = 0))
        assertFalse(galleryIsEmpty(loaded = false, systemWidgets = 0, folioWidgets = 0))
    }
}
