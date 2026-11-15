package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoWidgetModelTest {
    @Test fun nextWrapsAndHandlesEmpty() {
        assertEquals(1, nextPhotoIndex(0, 3))
        assertEquals(0, nextPhotoIndex(2, 3))
        assertEquals(0, nextPhotoIndex(5, 0))
    }

    @Test fun previousWraps() {
        assertEquals(2, previousPhotoIndex(0, 3))
        assertEquals(0, previousPhotoIndex(1, 3))
        assertEquals(0, previousPhotoIndex(0, 0))
    }

    @Test fun shuffleNeverRepeatsCurrent() {
        for (i in 0 until 4) for (p in 0 until 10) assertNotEquals(i, shuffledNextIndex(i, 4) { p })
        assertEquals(0, shuffledNextIndex(0, 1) { 0 })
    }

    @Test fun intervalIsNeverFasterThanFiveSeconds() {
        assertEquals(5, photoInterval(1))
        assertEquals(5, photoInterval(-3))
        assertEquals(30, photoInterval(30))
        assert(PHOTO_INTERVALS.all { it >= PHOTO_MIN_INTERVAL_SECONDS })
    }

    @Test fun lostGrantsAreDroppedAndIndexStaysValid() {
        val s = PhotoWidgetState(uris = listOf("a", "b", "c"), index = 2).withoutLost { it != "c" }
        assertEquals(listOf("a", "b"), s.uris)
        assertEquals(1, s.index)
        assertEquals(0, PhotoWidgetState(uris = listOf("a"), index = 0).withoutLost { false }.index)
    }

    @Test fun removingTheShowingPhotoKeepsAValidIndex() {
        assertEquals(listOf("a", "c"), PhotoWidgetState(uris = listOf("a", "b", "c"), index = 1).withoutCurrent().uris)
        val last = PhotoWidgetState(uris = listOf("a", "b", "c"), index = 2).withoutCurrent()
        assertEquals(listOf("a", "b"), last.uris)
        assertEquals(1, last.index)
        val none = PhotoWidgetState(uris = listOf("a"), index = 0).withoutCurrent()
        assertEquals(emptyList<String>(), none.uris)
        assertEquals(0, none.index)
        assertEquals(PhotoWidgetState(), PhotoWidgetState().withoutCurrent())
    }

    @Test fun descriptionPrefersCaption() {
        val t = { n: Int, c: Int -> "Photo $n of $c" }
        assertEquals("Calabasas", photoDescription("Calabasas", 1, 6, t))
        assertEquals("Photo 2 of 6", photoDescription(null, 1, 6, t))
        assertEquals("Photo 2 of 6", photoDescription("  ", 1, 6, t))
    }

    @Test fun captionDateNeedsATime() {
        assertNull(photoCaptionDate(null) { "x" })
        assertNull(photoCaptionDate(0L) { "x" })
        assertEquals("d5", photoCaptionDate(5L) { "d$it" })
    }
}
