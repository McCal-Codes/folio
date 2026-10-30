package com.mccal.folio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrackgateReduceMotionTest {
    @Test fun `either Folio's switch or Android's Remove animations turns Reduce Motion on`() {
        assertFalse(crackgateReduceMotion(folioSwitch = false, androidRemovesAnimations = false))
        assertTrue(crackgateReduceMotion(folioSwitch = true, androidRemovesAnimations = false))
        assertTrue(crackgateReduceMotion(folioSwitch = false, androidRemovesAnimations = true))
        assertTrue(crackgateReduceMotion(folioSwitch = true, androidRemovesAnimations = true))
    }
}