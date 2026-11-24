package com.mccal.folio

import android.provider.Settings
import org.junit.Assert.*
import org.junit.Test

class SearchWidgetTest {
    private val limits = builtinWidgetConstraints(SEARCH_WIDGET)!!

    @Test fun startsAsAStripAndGrowsToACardTwoRowsHigh() {
        assertEquals(WidgetSpan(GRID_COLUMNS, 1), limits.preferred)
        assertEquals(WidgetSpan(3, 1), limits.minimum)
        assertEquals(WidgetSpan(GRID_COLUMNS, 2), limits.maximum)
        assertTrue(limits.canResizeHorizontally && limits.canResizeVertically)
    }

    @Test fun theIdIsItsOwnAndBackedUpWithLayouts() {
        val others = listOf(EMPTY_WIDGET, CLOCK_WIDGET, DATE_WIDGET, INFO_WIDGET, UP_NEXT_WIDGET, SUGGESTIONS_WIDGET, BIG_CLOCK_WIDGET, MUSIC_WIDGET, PHOTO_WIDGET)
        assertFalse(SEARCH_WIDGET in others)
        assertTrue(SEARCH_WIDGET < 0)
        assertTrue(SEARCH_WIDGET in BACKUP_BUILTIN_WIDGETS)
    }

    @Test fun theCardOffersWifiBluetoothAndDisplay() {
        assertEquals(
            listOf(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_BLUETOOTH_SETTINGS, Settings.ACTION_DISPLAY_SETTINGS),
            searchWidgetShortcuts().map { it.second },
        )
    }

    @Test fun theGateStartsClosedForEveryoneButEarlyAccess() {
        assertEquals("0.7.0", FeatureGate.SEARCH_WIDGET.opensIn)
    }
}
