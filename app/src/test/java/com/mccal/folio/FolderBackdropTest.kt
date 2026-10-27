package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What sits behind an open folder: a dim strong enough for the title on Home, and a card that stays readable. */
class FolderBackdropTest {
    @Test fun dimIsStrongerThanTheGenericOverlayScrim() {
        assertTrue(FolderBackdrop.scrimAlpha(solid = false) > FolioGlass.scrim.alpha)
    }

    @Test fun withoutBlurTheDimIsStronger() {
        assertTrue(FolderBackdrop.scrimAlpha(solid = true) > FolderBackdrop.scrimAlpha(solid = false))
    }

    @Test fun dimNeverFullyHidesHome() {
        assertTrue(FolderBackdrop.scrimAlpha(solid = true) < 1f)
    }

    @Test fun cardIsSolidUnderReduceTransparency() {
        assertEquals(1f, FolderBackdrop.cardAlpha(solid = true), 0f)
        assertEquals(1f, FolderBackdrop.card(solid = true).alpha, 0f)
    }

    @Test fun glassCardIsNearlyOpaque() {
        val a = FolderBackdrop.cardAlpha(solid = false)
        assertTrue(a >= .9f && a < 1f)
        assertTrue(a > FolioGlass.card.alpha)
    }

    @Test fun scrimIsBlack() {
        val c = FolderBackdrop.scrim(solid = false)
        assertEquals(0f, c.red, 0f); assertEquals(0f, c.green, 0f); assertEquals(0f, c.blue, 0f)
        assertEquals(FolderBackdrop.SCRIM, c.alpha, 1e-3f)
    }

    @Test fun blurIsOutBeforeTheCardIsFullyOpen() {
        assertEquals(0f, FolderBackdrop.blurLevel(0f, solid = false), 0f)
        assertEquals(1f, FolderBackdrop.blurLevel(FolderBackdrop.BLUR_FULL_AT, solid = false), 0f)
        assertTrue(FolderBackdrop.BLUR_FULL_AT < 1f)
    }

    @Test fun blurLeadsTheCardAndOnlyGrows() {
        // The blur is ahead of the card (more than half in when the card is half open), never past full, and only grows with the opening.
        var last = -1f
        for (i in 0..20) {
            val level = FolderBackdrop.blurLevel(i / 20f, solid = false)
            assertTrue(level in 0f..1f)
            assertTrue(level >= last)
            last = level
        }
        assertTrue(FolderBackdrop.blurLevel(.5f, solid = false) > .5f)
    }

    @Test fun blurClampsOutOfRangeProgress() {
        assertEquals(0f, FolderBackdrop.blurLevel(-.2f, solid = false), 0f)
        assertEquals(1f, FolderBackdrop.blurLevel(1.2f, solid = false), 0f)
    }

    @Test fun reduceTransparencyHasNoBlur() {
        assertEquals(0f, FolderBackdrop.blurLevel(1f, solid = true), 0f)
    }

    @Test fun folderBlurIsPerActivityAndLetsGo() {
        val a = Any(); val b = Any()
        assertEquals(0f, FolderBlur.level(a), 0f)
        var progress = .3f
        val release = FolderBlur.register(a) { progress }
        assertEquals(.3f, FolderBlur.level(a), 0f)
        assertEquals(0f, FolderBlur.level(b), 0f)
        progress = 2f
        assertEquals(1f, FolderBlur.level(a), 0f)
        release()
        assertEquals(0f, FolderBlur.level(a), 0f)
        assertEquals(0f, FolderBlur.level(null), 0f)
    }

    @Test fun anOldFolderCannotReleaseTheNewOnesBlur() {
        val owner = Any()
        val releaseOld = FolderBlur.register(owner) { .4f }
        val releaseNew = FolderBlur.register(owner) { .9f }
        releaseOld()
        assertEquals(.9f, FolderBlur.level(owner), 0f)
        releaseNew()
        assertEquals(0f, FolderBlur.level(owner), 0f)
    }
}
