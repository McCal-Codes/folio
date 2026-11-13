package com.mccal.folio

import android.content.Context

/** The Memories photo widget's id: built-in widgets are negative, and -9 was Music. */
const val PHOTO_WIDGET = -10

/** The slideshow never changes photos faster than this (the user is epileptic; WCAG 2.3.1 allows far more, we stay slow). */
const val PHOTO_MIN_INTERVAL_SECONDS = 5

/** Intervals offered in Settings, in seconds. */
internal val PHOTO_INTERVALS = listOf(5, 10, 30)

/**
 * What the photo widget remembers: the photos the person picked (content URIs with persisted read grants), which one is
 * showing, and the few options. Pure data so the next-photo and clamping rules are unit tested.
 */
internal data class PhotoWidgetState(
    val uris: List<String> = emptyList(),
    val index: Int = 0,
    val showCaption: Boolean = true,
    val shuffle: Boolean = false,
    val intervalSeconds: Int = 10,
)

/** The interval actually used: never faster than [PHOTO_MIN_INTERVAL_SECONDS]. */
internal fun photoInterval(seconds: Int): Int = seconds.coerceAtLeast(PHOTO_MIN_INTERVAL_SECONDS)

/** The index after [index] in a list of [count]; wraps, and is 0 for an empty list. */
internal fun nextPhotoIndex(index: Int, count: Int): Int = if (count <= 0) 0 else (index + 1).mod(count)

/** The index before [index]; wraps. */
internal fun previousPhotoIndex(index: Int, count: Int): Int = if (count <= 0) 0 else (index - 1).mod(count)

/**
 * The next photo when shuffling: any index but the current one (so a tick always changes the picture), chosen by [pick]
 * from the candidates so a test can make it deterministic.
 */
internal fun shuffledNextIndex(index: Int, count: Int, pick: (Int) -> Int): Int {
    if (count <= 1) return 0
    val others = (0 until count).filter { it != index }
    return others[pick(others.size).mod(others.size)]
}

/** Drops URIs whose grant is gone, keeping the showing index on a valid photo. */
internal fun PhotoWidgetState.withoutLost(isReadable: (String) -> Boolean): PhotoWidgetState {
    val kept = uris.filter(isReadable)
    return copy(uris = kept, index = if (kept.isEmpty()) 0 else index.coerceIn(0, kept.size - 1))
}

/** Screen-reader text: the caption when there is one, else "Photo 2 of 6" (1-based). */
internal fun photoDescription(caption: String?, index: Int, count: Int, template: (Int, Int) -> String): String =
    caption?.takeIf { it.isNotBlank() } ?: template(index + 1, count)

/** A caption's date line from a photo's capture time in millis, or null when the file has none. */
internal fun photoCaptionDate(millis: Long?, format: (Long) -> String): String? =
    millis?.takeIf { it > 0L }?.let(format)

/** Stored in the app's private preferences; URIs are newline separated (a content URI never contains one). */
internal object PhotoWidgetStore {
    private const val PREFS = "photo_widget"

    fun load(context: Context): PhotoWidgetState {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val uris = p.getString("uris", "").orEmpty().split('\n').filter { it.isNotBlank() }
        return PhotoWidgetState(
            uris = uris,
            index = p.getInt("index", 0).coerceIn(0, (uris.size - 1).coerceAtLeast(0)),
            showCaption = p.getBoolean("caption", true),
            shuffle = p.getBoolean("shuffle", false),
            intervalSeconds = photoInterval(p.getInt("interval", 10)),
        )
    }

    fun save(context: Context, s: PhotoWidgetState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("uris", s.uris.joinToString("\n")).putInt("index", s.index)
            .putBoolean("caption", s.showCaption).putBoolean("shuffle", s.shuffle)
            .putInt("interval", photoInterval(s.intervalSeconds)).apply()
    }
}
