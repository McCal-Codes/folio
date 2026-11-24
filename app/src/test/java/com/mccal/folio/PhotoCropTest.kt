package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The wallpaper editor saves exactly the part of the photo that was on screen when Set was tapped. */
class PhotoCropTest {
    // A 1000 x 500 photo under a 200 x 400 screen: it is scaled to cover (height 400, so 0.8x), showing 250 x 500 of its pixels.
    private fun crop(zoom: Float, x: Float = 0f, y: Float = 0f) = photoCrop(200f, 400f, 1000, 500, zoom, x, y)

    @Test fun untouchedKeepsTheCenterStripThatFillsTheScreen() {
        assertEquals(PhotoCrop(375, 0, 250, 500), crop(1f))
    }

    @Test fun zoomingInKeepsLessOfThePhotoAroundTheSameCenter() {
        val zoomed = crop(2f)
        assertEquals(125, zoomed.width); assertEquals(250, zoomed.height)
        assertEquals(438, zoomed.left) // 437.5 rounds up
    }

    @Test fun draggingRightShowsTheLeftOfThePhoto() {
        // Content moved 80 screen px right at 0.8x means 100 photo px further left.
        assertEquals(crop(1f).left - 100, crop(1f, x = 80f).left)
    }

    @Test fun anOffsetPastTheEdgeCannotLeaveThePhoto() {
        val far = crop(2f, x = 100000f, y = -100000f)
        assertEquals(0, far.left)
        assertEquals(500 - far.height, far.top)
        assertTrue(far.fitsIn(1000, 500))
    }

    @Test fun zoomBelowOneIsTreatedAsOne() { assertEquals(crop(1f), crop(.2f)) }

    @Test fun aPhotoThatIsTheScreensShapeIsKeptWhole() {
        assertEquals(PhotoCrop(0, 0, 400, 800), photoCrop(200f, 400f, 400, 800, 1f, 0f, 0f))
    }

    @Test fun cropAlwaysFitsItsPhoto() {
        listOf(1f, 1.5f, 3f, 5f).forEach { zoom ->
            listOf(-9999f, -50f, 0f, 50f, 9999f).forEach { dx ->
                assertTrue(photoCrop(1080f, 2340f, 4000, 3000, zoom, dx, -dx).fitsIn(4000, 3000))
            }
        }
    }

    @Test fun panLimitIsHalfTheOverhang() {
        // Cover scale 0.8: the photo is 800 wide on a 200 screen at zoom 1, so it can move 300 each way.
        assertEquals(300f, maxPan(200f, 1000, .8f, 1f), 0.001f)
        assertEquals(0f, maxPan(800f, 1000, .8f, 1f), 0.001f)
    }
}