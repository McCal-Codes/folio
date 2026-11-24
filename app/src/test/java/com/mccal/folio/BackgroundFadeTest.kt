package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundFadeTest {
    @Test fun aPictureThatArrivesLateFades() = assertTrue(backgroundFades(arrivedLate = true, reduceMotion = false, changed = true))

    @Test fun theFirstPictureOfAColdStartDoesNot() = assertFalse(backgroundFades(arrivedLate = false, reduceMotion = false, changed = true))

    @Test fun reduceMotionSwitchesAtOnce() = assertFalse(backgroundFades(arrivedLate = true, reduceMotion = true, changed = true))

    @Test fun theSamePictureHasNothingToFade() = assertFalse(backgroundFades(arrivedLate = true, reduceMotion = false, changed = false))

    @Test fun aSecondChangeKeepsTheFirstUnderIt() {
        val layers = BackgroundLayers<String>()
        layers.change("B", fades = true)
        layers.fade = androidx.compose.animation.core.Animatable(.4f)
        layers.change("C", fades = true)
        assertEquals(listOf("B" to .4f), layers.under.toList())
    }

    @Test fun aFinishedFadeLeavesNothingUnderTheNextOne() {
        val layers = BackgroundLayers<String>()
        layers.change("B", fades = true)
        layers.fade = androidx.compose.animation.core.Animatable(1f)
        layers.change("C", fades = true)
        assertTrue(layers.under.isEmpty())
    }

    @Test fun aChangeWithNoFadeClearsEverything() {
        val layers = BackgroundLayers<String>()
        layers.change("B", fades = true)
        layers.fade = androidx.compose.animation.core.Animatable(.4f)
        layers.change("C", fades = true)
        layers.change("D", fades = false)
        assertTrue(layers.under.isEmpty())
    }
}
