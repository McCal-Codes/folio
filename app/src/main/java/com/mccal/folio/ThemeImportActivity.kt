package com.mccal.folio

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Share a theme file to Folio (from Files, Chrome or a chat). What's shared is untrusted: only content:// streams or
 * text, at most 64 KB, and only a real Folio theme is passed on. Home then asks before applying it.
 */
class ThemeImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This activity shares Home's process, so the untrusted file is read off the main thread and given up on
        // after a couple of seconds: a stream that never ends can't freeze Home.
        lifecycleScope.launch {
            val theme = kotlinx.coroutines.withTimeoutOrNull(2_000) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { readTheme(intent) }
            }
            if (theme == null) Toast.makeText(this@ThemeImportActivity, R.string.that_file_isn_t_a_folio_theme, Toast.LENGTH_SHORT).show()
            else startActivity(Intent(this@ThemeImportActivity, MainActivity::class.java).putExtra(EXTRA_THEME, theme.toJson().toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }
    }

    private fun readTheme(intent: Intent?): FolioTheme? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val raw = runCatching {
            @Suppress("DEPRECATION") val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (stream != null) {
                if (stream.scheme != ContentResolver.SCHEME_CONTENT) return null
                contentResolver.openInputStream(stream)?.use { input ->
                    // readNBytes needs API 33; Folio supports 31.
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    while (out.size() <= MAX_BYTES) { val n = input.read(buffer); if (n < 0) break; out.write(buffer, 0, n) }
                    if (out.size() > MAX_BYTES) null else out.toByteArray().decodeToString()
                }
            } else intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.length <= MAX_BYTES }
        }.getOrNull() ?: return null
        return FolioTheme.fromJson(raw)
    }

    companion object {
        const val EXTRA_THEME = "folio_shared_theme"
        const val MAX_BYTES = 64_000
    }
}
