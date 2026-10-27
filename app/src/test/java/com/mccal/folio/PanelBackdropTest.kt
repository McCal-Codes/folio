package com.mccal.folio

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The picture behind a panel: the copy it is made from is given back at once, the picture itself only after it can no longer be drawn. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PanelBackdropTest {
    private fun bitmap() = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    @Test fun `the copy a blur reads is recycled and the result is not`() {
        val source = bitmap(); val result = bitmap()
        val out = PanelBackdrop.blurAndRelease(source) { assertFalse("not recycled while it is read", it.isRecycled); result }
        assertSame(result, out)
        assertTrue(source.isRecycled)
        assertFalse(result.isRecycled)
    }

    @Test fun `the copy is recycled when the blur fails too`() {
        val source = bitmap()
        val failed = runCatching { PanelBackdrop.blurAndRelease(source) { error("no memory") } }.isFailure
        assertTrue(failed)
        assertTrue(source.isRecycled)
    }

    @Test fun `dropping lets go of the picture at once but recycles it only when asked`() {
        val holder = PanelBackdropHolder()
        val shown = bitmap()
        holder.put(shown)
        assertNotNull(holder.image)
        holder.drop()
        assertNull("nothing refers to the picture any more", holder.image)
        assertFalse("a frame in flight may still be drawing it", shown.isRecycled)
        assertEquals(1, holder.retiredCount)
        holder.recycleRetired()
        assertTrue(shown.isRecycled)
        assertEquals(0, holder.retiredCount)
    }

    @Test fun `a new picture retires the old one without recycling it, and the shown one is never recycled`() {
        val holder = PanelBackdropHolder()
        val first = bitmap(); val second = bitmap()
        holder.put(first)
        holder.put(second)
        assertEquals(1, holder.retiredCount)
        holder.recycleRetired()
        assertTrue(first.isRecycled)
        assertFalse("the picture on show stays", second.isRecycled)
        assertNotNull(holder.image)
    }

    @Test fun `dropping with nothing shown, or twice, is harmless`() {
        val holder = PanelBackdropHolder()
        holder.drop(); holder.drop(); holder.recycleRetired()
        assertNull(holder.image)
        assertEquals(0, holder.retiredCount)
    }
}
