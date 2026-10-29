package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedLabelTest {
    @Test fun appsWithOneNameAreBothMarked() {
        val apps = listOf("a/.Cal" to "Calendar", "b/.Cal" to "Calendar", "c/.Cam" to "Camera")
        assertEquals(setOf("a/.Cal", "b/.Cal"), idsWithSharedLabel(apps))
    }

    @Test fun caseAndEdgeSpacesDoNotHideAMatch() {
        assertEquals(setOf("a", "b"), idsWithSharedLabel(listOf("a" to "Calendar", "b" to " calendar ")))
    }

    @Test fun uniqueNamesAreLeftAlone() {
        assertTrue(idsWithSharedLabel(listOf("a" to "Camera", "b" to "Clock")).isEmpty())
        assertTrue(idsWithSharedLabel(emptyList()).isEmpty())
    }
}
