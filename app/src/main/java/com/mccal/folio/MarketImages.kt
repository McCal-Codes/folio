package com.mccal.folio

import android.content.Context
import androidx.compose.ui.graphics.asImageBitmap
import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.memory.MemoryCache
import coil3.request.Options
import coil3.request.crossfade
import com.mccal.folio.market.HttpClient
import com.mccal.folio.market.HttpResult
import com.mccal.folio.market.Source
import com.mccal.folio.market.UrlHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * Pictures that come from a source the user added.
 *
 * Coil does the caching, the decoding and the cancelling, but it never opens a connection itself: the bytes come from
 * [UrlHttpClient], the same client the index and the packages go through. So an image obeys the rules the rest of the
 * Market obeys — https only, a size cap, no cookies, no redirect off https — and the APK carries one network stack
 * rather than two. It also means [PRIVACY.md]'s promise holds for images: Folio only ever contacts the source.
 */
internal object MarketImages {
    /** Bigger than any screenshot needs to be, and small enough that a source can't fill the phone. */
    const val MAX_IMAGE_BYTES = 4 * 1024 * 1024

    private const val DISK_CACHE_BYTES = 32L * 1024 * 1024

    /**
     * Folio's own icons and screenshots, decoded once.
     *
     * These come out of the APK rather than off a source, so Coil never sees them, and the obvious thing —
     * `remember { decode() }` in the row — decodes again every time a row scrolls back into view, on the main
     * thread. A small map of the ones already decoded costs a few hundred kilobytes and makes scrolling free.
     * Bounded, because a source's page can name any number of pictures.
     */
    private val decoded = object : android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(24) {}

    /** The bundled picture at [path], decoded, or null when the source hasn't got one. */
    fun bundled(read: (String) -> ByteArray?, path: String): androidx.compose.ui.graphics.ImageBitmap? =
        decoded[path] ?: read(path)?.let { bytes ->
            runCatching { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
                ?.asImageBitmap()
                ?.also { decoded.put(path, it) }
        }

    /** The most a moving picture may weigh. A package page shouldn't cost a phone more than that to open. */
    const val MAX_ANIMATION_BYTES = 1_500_000

    /**
     * Whether [bytes] say they are a moving picture, from the header alone, so a still screenshot is never decoded twice:
     * a GIF (which may or may not move, so it is decoded to find out), or a WebP whose extended header sets the
     * animation flag.
     */
    fun isAnimated(bytes: ByteArray): Boolean {
        fun at(i: Int, text: String) = bytes.size >= i + text.length && text.indices.all { bytes[i + it] == text[it].code.toByte() }
        return when {
            at(0, "GIF8") -> true
            at(0, "RIFF") && at(8, "WEBP") && at(12, "VP8X") && bytes.size > 20 -> bytes[20].toInt() and 0x02 != 0
            else -> false
        }
    }

    /**
     * The bundled picture at [path] as something that plays, when it is an animated WebP or GIF; null when it is still,
     * missing, too big or not a picture. A still one goes through [bundled], so a package can ship either.
     */
    fun bundledAnimation(read: (String) -> ByteArray?, path: String): android.graphics.drawable.AnimatedImageDrawable? {
        val bytes = read(path)?.takeIf { it.size <= MAX_ANIMATION_BYTES && isAnimated(it) } ?: return null
        return runCatching {
            android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes)))
        }.getOrNull() as? android.graphics.drawable.AnimatedImageDrawable
    }

    @Volatile private var loader: ImageLoader? = null

    fun loader(context: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: build(context.applicationContext).also { loader = it }
    }

    private fun build(context: Context): ImageLoader = ImageLoader.Builder(context)
        // Folio Dev reads a source served off the phone over plain http, so it has to see that source's pictures too:
        // with the default client every icon on a Local Dev source came up blank, which is exactly what an author
        // previewing their listing is there to check. The release build still refuses anything but https.
        .components {
            add(SourceImageFetcher.Factory(
                UrlHttpClient(allowLocalhost = com.mccal.folio.market.MarketFeature.isDevBuild(context.packageName)),
            ))
        }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, .1).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(java.io.File(context.cacheDir, "market-images").toOkioPath())
                .maxSizeBytes(DISK_CACHE_BYTES)
                .build()
        }
        .crossfade(true)
        .build()

    /** Forgets every downloaded image: the "Clear image cache" row, and what removing a source should leave behind. */
    fun clear(context: Context) {
        loader(context).memoryCache?.clear()
        runCatching { loader(context).diskCache?.clear() }
    }

    /**
     * Where a picture a package names lives.
     *
     * Every image a package shows comes from the same host as its index, which is what PRIVACY.md promises. A full
     * address is only followed when it is on that host; one anywhere else would tell a third party who opened which
     * package, so it isn't loaded. Folio's own packages are in the APK and have no address at all.
     */
    fun urlFor(source: Source, path: String): String? = when {
        source.kind == Source.Kind.BUILT_IN -> null
        path.startsWith("https://") -> path.takeIf { sameHost(it, source.url) }
        "://" in path -> null
        else -> source.url.trimEnd('/') + "/" + path.trimStart('/')
    }

    internal fun sameHost(url: String, sourceUrl: String): Boolean = runCatching {
        val a = java.net.URI(url)
        val b = java.net.URI(sourceUrl)
        a.userInfo == null && a.host != null && a.host.equals(b.host, ignoreCase = true) && a.port == b.port
    }.getOrDefault(false)
}

/** Hands Coil the bytes Folio's own client fetched, instead of letting it make its own request. */
private class SourceImageFetcher(private val url: String, private val http: HttpClient) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val bytes = withContext(Dispatchers.IO) {
            (http.get(url, MarketImages.MAX_IMAGE_BYTES, null) as? HttpResult.Body)?.bytes
        } ?: return null
        return SourceFetchResult(
            source = ImageSource(Buffer().apply { write(bytes) }, FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory(private val http: HttpClient) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            // http:// is only ever a local dev source, and those are served from the phone itself.
            val url = data.toString()
            return if (url.startsWith("https://") || url.startsWith("http://localhost")) {
                SourceImageFetcher(url, http)
            } else {
                null
            }
        }
    }
}
