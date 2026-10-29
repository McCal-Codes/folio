package com.mccal.folio

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * The picture behind Folio's own panels (Notification Center, Control Center) is a small blurred copy of Home, made when a panel opens and dropped
 * when it has closed (PERMISSIONS.md). Dropping means two things: nothing refers to it any more, and its memory is given back, but only once no
 * frame can still be drawing it; recycling a bitmap the render thread is about to use is a crash, not a saving.
 */
internal object PanelBackdrop {
    /**
     * [blur] of [source], then [source] is given back whether or not the blur worked. [source] must be a copy made only for this and drawn by
     * no one; the blurred result is a new bitmap ([FrostBlur.blurred] does not recycle its source).
     */
    fun blurAndRelease(source: Bitmap, blur: (Bitmap) -> Bitmap): Bitmap =
        try { blur(source) } finally { if (!source.isRecycled) source.recycle() }
}

/**
 * The one blurred picture a panel session draws. [put] shows a picture and [drop] stops showing it (the reference is gone at once, so the next frame
 * has nothing to draw); the bitmap itself is [recycleRetired]'d later, by the caller, after the frames that could still hold it.
 */
internal class PanelBackdropHolder {
    /** What the panels draw; null when there is no picture. */
    var image: ImageBitmap? by mutableStateOf(null)
        private set
    private var current: Bitmap? = null
    private val retired = mutableListOf<Bitmap>()

    /** How many bitmaps wait to be recycled. */
    val retiredCount: Int get() = retired.size

    fun put(blurred: Bitmap) {
        retire()
        current = blurred
        image = blurred.asImageBitmap()
    }

    fun drop() {
        retire()
        image = null
    }

    /** Gives back every picture no longer shown. Call it two frames after [drop], when no frame can still be drawing one. */
    fun recycleRetired() {
        retired.forEach { if (!it.isRecycled) it.recycle() }
        retired.clear()
    }

    private fun retire() {
        current?.let { retired += it }
        current = null
    }
}
