package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderOpeningTest {
    @Test fun `the folder's tile hands over to the panel in the first fifth of the growth`() {
        assertEquals(1f, FolderOpening.tileAlpha("f1", openId = "f1", progress = 0f), 0f)
        assertEquals(.5f, FolderOpening.tileAlpha("f1", openId = "f1", progress = .1f), .001f)
        assertEquals(0f, FolderOpening.tileAlpha("f1", openId = "f1", progress = .2f), 0f)
        assertEquals(0f, FolderOpening.tileAlpha("f1", openId = "f1", progress = 1.04f), 0f)
    }

    @Test fun `other folders' tiles stay as they are`() {
        assertEquals(1f, FolderOpening.tileAlpha("f2", openId = "f1", progress = .8f), 0f)
        assertEquals(1f, FolderOpening.tileAlpha("f2", openId = null, progress = 0f), 0f)
    }

    @Test fun `the contents come in after the panel has mostly grown`() {
        assertEquals(0f, FolderOpening.contentAlpha(.3f), 0f)
        assertEquals(.5f, FolderOpening.contentAlpha(.6f), .001f)
        assertEquals(1f, FolderOpening.contentAlpha(.9f), 0f)
    }

    @Test fun `Home steps back to 94 percent and no further, even when the spring overshoots`() {
        assertEquals(1f, FolderOpening.homeScale(0f), 0f)
        assertEquals(.94f, FolderOpening.homeScale(1f), .0001f)
        assertEquals(.94f, FolderOpening.homeScale(1.05f), .0001f)
    }
}
