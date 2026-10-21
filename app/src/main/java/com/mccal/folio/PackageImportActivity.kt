package com.mccal.folio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.mccal.folio.market.MarketFeature
import com.mccal.folio.market.PackageArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A `.foliopkg` file opened or shared with Folio.
 *
 * The file is untrusted and unsigned: it's read off the main thread with a size cap and a timeout, and then the Market
 * shows the same confirm sheet as any other package, with a line saying nobody signed it. Nothing is applied here.
 */
class PackageImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!MarketAccess.isOpen(this)) {
            Toast.makeText(this, getString(R.string.package_import_market_off), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        lifecycleScope.launch {
            val read = readPackageWithin(PACKAGE_READ_MS) { openPackageStream(intent) }
            when (read) {
                is PackageRead.Ok -> {
                    MarketImport.pending = read.bytes
                    startActivity(
                        Intent(this@PackageImportActivity, MainActivity::class.java)
                            .setAction(Intent.ACTION_APPLICATION_PREFERENCES)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                else -> Toast.makeText(this@PackageImportActivity, getString(read.messageRes()), Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    /** Only a stream the user picked, and only through a content provider. */
    private fun openPackageStream(intent: Intent?): java.io.InputStream? {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            else -> null
        } ?: return null
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
        return runCatching { contentResolver.openInputStream(uri) }.getOrNull()
    }
}

private const val PACKAGE_READ_MS = 4_000L

/** What came of reading a shared package file: the bytes, or the one reason it was refused. */
internal sealed interface PackageRead {
    class Ok(val bytes: ByteArray) : PackageRead
    object TooLarge : PackageRead
    object NotAPackage : PackageRead
    object TimedOut : PackageRead
}

private fun PackageRead.messageRes(): Int = when (this) {
    PackageRead.TooLarge -> R.string.package_import_too_large
    PackageRead.TimedOut -> R.string.package_import_too_slow
    else -> R.string.that_file_isn_t_a_folio_package
}

/**
 * Reads [input] up to [max] bytes or until [deadlineNanos] on [now]'s clock, whichever comes first. A read that is
 * interrupted (the caller gave up) counts as too slow, so a stalled provider can't hold the reader forever.
 */
internal fun readPackageBytes(input: java.io.InputStream, max: Int, deadlineNanos: Long, now: () -> Long = System::nanoTime): PackageRead {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    try {
        while (true) {
            if (Thread.currentThread().isInterrupted || now() > deadlineNanos) return PackageRead.TimedOut
            val read = input.read(buffer)
            if (read < 0) break
            if (out.size() + read > max) return PackageRead.TooLarge
            out.write(buffer, 0, read)
        }
    } catch (_: java.io.InterruptedIOException) {
        return PackageRead.TimedOut
    } catch (_: java.io.IOException) {
        return PackageRead.NotAPackage
    }
    return if (out.size() == 0) PackageRead.NotAPackage else PackageRead.Ok(out.toByteArray())
}

/**
 * Opens and reads a package off the main thread, giving up after [timeoutMs]. The read runs interruptibly and the
 * stream is closed when the deadline passes, so a blocked provider read is cut off rather than left running.
 */
internal suspend fun readPackageWithin(timeoutMs: Long, open: () -> java.io.InputStream?): PackageRead {
    val opened = java.util.concurrent.atomic.AtomicReference<java.io.InputStream?>(null)
    val result = withTimeoutOrNull(timeoutMs) {
        runInterruptible(Dispatchers.IO) {
            val stream = open() ?: return@runInterruptible PackageRead.NotAPackage
            opened.set(stream)
            stream.use { readPackageBytes(it, PackageArchive.MAX_COMPRESSED, System.nanoTime() + timeoutMs * 1_000_000L) }
        }
    }
    if (result == null) runCatching { opened.get()?.close() }
    return result ?: PackageRead.TimedOut
}

/**
 * A package file waiting to be looked at. Same process, so the bytes don't go through an intent.
 *
 * Compose state rather than a plain field: a file shared while the Market is already open has to be noticed, and one
 * that arrives while Settings is showing some other page mustn't be left sitting here.
 */
internal object MarketImport {
    var pending: ByteArray? by androidx.compose.runtime.mutableStateOf<ByteArray?>(null)
}
