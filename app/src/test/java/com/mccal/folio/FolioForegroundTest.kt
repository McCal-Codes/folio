package com.mccal.folio

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolioForegroundTest {
    @After fun reset() { FolioForeground.visible.value = false; FolioForeground.pausedAt = 0L }

    @Test fun `a shortcut started right after Home paused sees Home underneath`() {
        FolioForeground.visible.value = false
        FolioForeground.pausedAt = 10_000L
        assertTrue(FolioForeground.justPaused(10_400L))
    }

    @Test fun `a shortcut started long after Home paused is over an app`() {
        FolioForeground.visible.value = false
        FolioForeground.pausedAt = 10_000L
        assertFalse(FolioForeground.justPaused(10_000L + FolioForeground.HOME_JUST_PAUSED_MS + 1))
    }

    @Test fun `Home showing is never just paused`() {
        FolioForeground.visible.value = true
        FolioForeground.pausedAt = 10_000L
        assertFalse(FolioForeground.justPaused(10_100L))
    }
}
