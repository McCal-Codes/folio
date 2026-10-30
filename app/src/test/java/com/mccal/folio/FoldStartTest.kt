package com.mccal.folio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldStartTest {
    @Test fun `a phone that folds starts on the cover so an open screen at launch plays the unfold`() {
        assertFalse(foldStartsExpanded(hasHinge = true, expanded = true))
        assertFalse(foldStartsExpanded(hasHinge = true, expanded = false))
    }

    @Test fun `a tablet starts on the screen it is on so nothing plays at launch`() {
        assertTrue(foldStartsExpanded(hasHinge = false, expanded = true))
        assertFalse(foldStartsExpanded(hasHinge = false, expanded = false))
    }
}
