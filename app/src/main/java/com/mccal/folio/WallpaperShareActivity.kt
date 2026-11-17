package com.mccal.folio

import android.content.ClipData
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * Share a photo to Folio (from Photos, Files or Chrome) to try it as Home's background. What's shared is untrusted: only a
 * content:// image, never one of Folio's own, and it is handed on to Home, which decodes it the way a picked photo is
 * (size-capped, off the main thread) and shows the same preview with one Set. Nothing changes until Set.
 */
class WallpaperShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val photo = sharedPhoto(intent, packageName)
        // A sender can name a photo it never granted access to; the system then refuses to pass it on, and that is a
        // photo that can't be used, not a crash.
        val handedOn = photo != null && runCatching {
            startActivity(Intent(this, MainActivity::class.java).putExtra(EXTRA_PHOTO, photo.toString())
                // The sender's read access is passed on with the link, or Home could not open it.
                .apply { clipData = ClipData.newRawUri("", photo) }
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!handedOn) Toast.makeText(this, R.string.that_photo_could_not_be_used, Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val EXTRA_PHOTO = "folio_shared_wallpaper"

        /** The photo a SEND carries, or null when it is not a content:// image from someone else. Pure, so it is tested. */
        internal fun sharedPhoto(intent: Intent?, ownPackage: String): Uri? {
            if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return null
            @Suppress("DEPRECATION") val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            return stream?.takeIf { acceptable(it, ownPackage) }
        }

        /** Home takes a link from any app, so it checks again with this. */
        internal fun acceptable(uri: Uri, ownPackage: String): Boolean =
            uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority?.let { it != ownPackage && !it.startsWith("$ownPackage.") } == true
    }
}
