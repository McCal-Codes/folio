package com.mccal.folio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundFadeTest {
    @Test fun aPictureThatArrivesLateFades() = assertTrue(backgroundFades(arrivedLate = true, reduceMotion = false, changed = true))

    @Test fun theFirstPictureOfAColdStartDoesNot() = assertFalse(backgroundFades(arrivedLate = false, reduceMotion = false, changed = true))

    @Test fun reduceMotionSwitchesAtOnce() = assertFalse(backgroundFades(arrivedLate = true, reduceMotion = true, changed = true))

    @Test fun theSamePictureHasNothingToFade() = assertFalse(backgroundFades(arrivedLate = true, reduceMotion = false, changed = false))
}
