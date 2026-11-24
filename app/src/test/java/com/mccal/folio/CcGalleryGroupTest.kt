package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CcGalleryGroupTest {
    @Test fun everyControlHasAGroup() {
        CcControl.entries.forEach { assertNotNull("${it.name} has no gallery group", ccGalleryGroup(it.name)) }
    }

    @Test fun noControlIsInTwoGroups() {
        val all = CcGalleryGroup.entries.flatMap { it.ids }
        assertEquals(all.size, all.toSet().size)
    }

    @Test fun groupsOnlyNameRealControls() {
        val names = CcControl.entries.map { it.name }.toSet()
        assertTrue(CcGalleryGroup.entries.flatMap { it.ids }.all { it in names })
    }
}
