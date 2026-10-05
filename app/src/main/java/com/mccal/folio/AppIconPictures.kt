package com.mccal.folio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Your own picture for one app's icon. Copied into Folio at [SIZE] pixels square so it keeps working if the photo is
 * deleted, kept in `filesDir/app-icons` (never in a backup), and drawn ahead of every other look for that app. The
 * file's name comes from the app's id, so the saved settings only need a stamp that says there is a picture and when it
 * changed; a stamp with no file (a restored backup, another phone) simply draws the normal icon.
 */
internal object AppIconPictures {
    const val SIZE = 192

    /** How to scale and crop a [width] x [height] picture to a [size] square, from its middle: the scaled size and the crop. */
    data class Crop(val scaledWidth: Int, val scaledHeight: Int, val left: Int, val top: Int)

    fun centerCrop(width: Int, height: Int, size: Int = SIZE): Crop {
        require(width > 0 && height > 0 && size > 0) { "empty picture" }
        val scale = size.toFloat() / minOf(width, height)
        val w = maxOf(size, Math.round(width * scale))
        val h = maxOf(size, Math.round(height * scale))
        return Crop(w, h, (w - size) / 2, (h - size) / 2)
    }

    private fun dir(context: Context) = File(context.filesDir, "app-icons")

    fun fileFor(context: Context, appId: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(appId.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir(context), "$hash.png")
    }

    private val cache = object : LruCache<String, Bitmap>(24) {}

    /** The picture, from memory if it has been drawn before at this [stamp]. */
    fun cached(appId: String, stamp: Long): Bitmap? = cache.get("$appId:$stamp")

    suspend fun load(context: Context, appId: String, stamp: Long): Bitmap? = withContext(Dispatchers.IO) {
        cached(appId, stamp) ?: fileFor(context, appId).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }?.also { cache.put("$appId:$stamp", it) }
    }

    /** Copies the picture at [uri] into Folio, cropped to a square from its middle. Returns the new stamp, or null if it can't be read. */
    suspend fun save(context: Context, appId: String, uri: Uri): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val crop = centerCrop(info.size.width, info.size.height)
                decoder.setTargetSize(crop.scaledWidth, crop.scaledHeight)
                decoder.setCrop(Rect(crop.left, crop.top, crop.left + SIZE, crop.top + SIZE))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val file = fileFor(context, appId).apply { parentFile?.mkdirs() }
            // Written to a temporary file and moved into place, so a cancelled save or a full disk leaves the old picture, not half a PNG.
            val temp = File(file.path + ".tmp")
            try {
                temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "could not write the picture" } }
                check(temp.renameTo(file)) { "could not move the picture into place" }
            } finally { temp.delete() }
            System.currentTimeMillis()
        }.getOrNull()
    }

    fun delete(context: Context, appId: String) { fileFor(context, appId).delete() }

    fun deleteAll(context: Context, appIds: Collection<String>) { appIds.forEach { delete(context, it) } }
}
