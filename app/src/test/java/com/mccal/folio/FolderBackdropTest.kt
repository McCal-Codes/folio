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
}
