package com.mccal.folio

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject
import java.io.File

/*
 * The pictures Folio can put behind Home, and which one is chosen.
 *
 * **One picture, from one of two places.** A background is either a photo the user picked or a piece of art from
 * Folio's library. The library holds art that shipped inside the app and art installed from the Market, and the two
 * are the same thing once installed: a file on disk with a credit beside it. Nothing here draws anything. Folio
 * draws a background the way it always has, through `drawLauncherBackground`, and this decides which file that is.
 *
 * **Why art is not a second kind of background.** Before this, Folio's own backgrounds were code that drew a scene,
 * so they needed their own switch, their own settings and their own draw path beside the photo one. Under
 * [DES-2a](../../../../../../docs/standards/design.md) they are pictures instead, which means a piece of art and the
 * user's own photo differ only in where the file came from. So they share the decode, the cache, the scrim, the
 * crop and the parallax, and the picker is one grid rather than two lists that behave differently.
 *
 * **Who owns this.** The choice lives in the `launcher_background` preferences beside the photo that was already
 * there, and `LauncherBackgroundController` remains its only writer (STA-1). That file answers "which picture";
 * `LauncherState.systemWallpaper` answers the different question of whether Folio draws a background at all or lets
 * Android's own wallpaper show through the window. Keeping those two apart is deliberate: one is a choice of
 * picture, the other is a choice of who paints. `docs/standards/state-data.md` Gap 6 has the history, which is that
 * this used to be spread over three places and was about to become four.
 */

/** Where the picture behind Home comes from. */
internal sealed interface BackgroundChoice {
    /** Nothing of Folio's: either Android's wallpaper shows through, or Home is bare. */
    data object None : BackgroundChoice

    /** The one photo the user picked, in [launcherPhotoFile]. */
    data object Photo : BackgroundChoice

    /** A piece of art from the library, by its [Artwork.id]. */
    data class Art(val id: String) : BackgroundChoice

    companion object {
        /**
         * Reads a choice back from what was saved.
         *
         * An id that no longer names anything in the library is [None] rather than an error. Art can leave: a
         * Market package is removed, or a piece that shipped inside the app is retired in a later version. When
         * that happens the background should quietly go away, not crash the launcher on the first frame after an
         * update (STA-6 is about failing visibly for the user's *layout*; a missing picture has nothing to tell
         * them and everything to recover from).
         */
        fun parse(saved: String?, known: (String) -> Boolean): BackgroundChoice = when {
            saved == null || saved == NONE -> None
            saved == PHOTO -> Photo
            saved.startsWith(ART_PREFIX) -> saved.removePrefix(ART_PREFIX)
                .takeIf(known)?.let(::Art) ?: None
            else -> None
        }

        const val NONE = "none"
        const val PHOTO = "photo"
        const val ART_PREFIX = "art:"
    }

    /** How this is written to preferences. Stable across versions: it is saved on the user's phone. */
    fun save(): String = when (this) {
        None -> NONE
        Photo -> PHOTO
        is Art -> ART_PREFIX + id
    }
}

/**
 * A picture with its credit.
 *
 * Every field except [id] is the credit [DES-2b](../../../../../../docs/standards/design.md) requires, and Folio
 * refuses art that cannot fill in [artist] and [license]. They are read off the package that brought the art, where
 * the artist is the package's author and the license is the package's license, so a wallpaper package records this
 * without the format needing a field for it. See `docs/sdk/format-v1.md`.
 *
 * The strings are not translated. A painting's title, its painter's name and the name of the museum holding it are
 * the same in every language, and translating "Public domain" into a claim Folio has not checked in that language
 * would be worse than leaving it.
 */
internal data class Artwork(
    val id: String,
    val title: String,
    val artist: String,
    val license: String,
    /** Date and holding collection, as one line: "1785. Yale Center for British Art." Blank when unknown. */
    val detail: String = "",
    /** Where the file came from, for the credit to link to. */
    val source: String = "",
    /** True when the art shipped inside the app rather than arriving from the Market. */
    val builtIn: Boolean = false,
) {
    /** A package with no artist or no license is not shippable art, so it is not offered. */
    val credited: Boolean get() = artist.isNotBlank() && license.isNotBlank()
}

internal object BackgroundLibrary {
    /** Art installed from the Market. One image and one record per package. */
    private const val INSTALLED_DIR = "backgrounds"
    private const val RECORD = "credits.json"

    fun installedDir(context: Context) = File(context.filesDir, INSTALLED_DIR)

    fun artFile(context: Context, id: String) = File(installedDir(context), "${safe(id)}.img")

    private fun recordFile(context: Context) = File(installedDir(context), RECORD)

    /**
     * An id becomes a file name, so it may not wander out of the directory. Package ids are already reverse-DNS and
     * cannot contain a separator, but this is the boundary where a name from a downloaded package becomes a path,
     * and a boundary that trusts its input is the one that gets exploited.
     */
    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)

    /** Every piece of art available, built-in first. */
    fun all(context: Context): List<Artwork> = (builtIn() + installed(context)).filter { it.credited }

    /**
     * Art that shipped inside the app.
     *
     * Two woodblock prints, both by an artist who died in 1858, both from a library that publishes its scans with no
     * restriction. They are stored under `assets/wallpapers` rather than `res/`, so no density qualifier applies to
     * them and the file is the same one on every phone, and they are cropped off their paper mounts and sized to
     * 2448 px wide, which is the widest window Folio runs in. That width is the point: a center crop never has to
     * scale them up.
     *
     * The credit is written out here rather than read from a file because these ship with the app, so there is no
     * later moment at which it could be missing, and a constant is the one form of it that cannot go stale or fail
     * to parse.
     *
     * Each credit line is marked `// english-only`, which is why it is not in `strings.xml`. A painting's title, its
     * painter's name and the museum holding it are the same in every language, and "Public domain" translated into a
     * language nobody checked would be Folio making a legal claim it has not verified. The words around them in the
     * picker are translated as usual; these four fields are the work's own name for itself.
     */
    private fun builtIn(): List<Artwork> = BUILT_IN

    private val BUILT_IN = listOf(
        Artwork(
            id = "night-view-of-saruwaka-machi",
            title = "Night View of Saruwaka-machi", // english-only
            artist = "Utagawa Hiroshige", // english-only
            license = "Public domain", // english-only
            detail = "1856. Library of Congress.", // english-only
            source = "https://www.loc.gov/pictures/item/2008660961/",
            builtIn = true,
        ),
        Artwork(
            id = "naruto-whirlpools",
            title = "Naruto Whirlpools", // english-only
            artist = "Utagawa Hiroshige", // english-only
            license = "Public domain", // english-only
            detail = "1855. National Library of New Zealand.", // english-only
            source = "https://natlib.govt.nz/records/22811404",
            builtIn = true,
        ),
    )

    /** The art inside the app, by id. Read by the test that holds each piece to its credit and its picture. */
    val builtInIds: List<String> get() = BUILT_IN.map { it.id }

    fun isBuiltIn(id: String) = BUILT_IN.any { it.id == id }

    /** Where a built-in piece lives inside the app. */
    fun assetPath(id: String) = "wallpapers/$id.webp"

    /** Whether the picture for [id] is actually here, wherever it lives. */
    fun exists(context: Context, id: String) = isBuiltIn(id) || artFile(context, id).isFile

    fun installed(context: Context): List<Artwork> {
        val text = runCatching { recordFile(context).takeIf { it.isFile }?.readText() }.getOrNull() ?: return emptyList()
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        return json.keys().asSequence().mapNotNull { id ->
            val entry = json.optJSONObject(id) ?: return@mapNotNull null
            if (!artFile(context, id).isFile) return@mapNotNull null
            Artwork(
                id = id,
                title = entry.optString("title"),
                artist = entry.optString("artist"),
                license = entry.optString("license"),
                detail = entry.optString("detail"),
                source = entry.optString("source"),
            )
        }.toList()
    }

    fun find(context: Context, id: String): Artwork? = all(context).firstOrNull { it.id == id }

    /**
     * Records a piece of art installed from the Market, and returns false without writing anything when it has no
     * artist or no license. Refusing here rather than at the picker means uncredited art never reaches the disk.
     */
    fun record(context: Context, art: Artwork): Boolean {
        if (!art.credited) return false
        // A package claiming a name that belongs to art inside Folio is claiming to be that art, the same refusal
        // the Market already makes for package ids.
        if (isBuiltIn(art.id)) return false
        val dir = installedDir(context)
        if (!dir.isDirectory && !dir.mkdirs()) return false
        val json = runCatching { JSONObject(recordFile(context).readText()) }.getOrNull() ?: JSONObject()
        json.put(art.id, JSONObject()
            .put("title", art.title).put("artist", art.artist).put("license", art.license)
            .put("detail", art.detail).put("source", art.source))
        return runCatching { recordFile(context).writeText(json.toString()) }.isSuccess
    }

    /**
     * Drops art whose package is no longer installed.
     *
     * Nothing deletes a picture at the moment its package is removed, on purpose: removing and Safe Mode turning a
     * package off both go through the same restore, and only one of them means gone. So the installed list is the
     * authority, and this is run against it. Art that never came from a package, which today means nothing and
     * later means the pieces that ship inside the app, is not in [installed] and so is never considered.
     */
    fun prune(context: Context, installedIds: Set<String>) {
        installed(context).map { it.id }.filterNot { it in installedIds }.forEach { forget(context, it) }
    }

    /** Forgets a piece of art and deletes its file. Used when its package is removed. */
    fun forget(context: Context, id: String) {
        artFile(context, id).delete()
        val json = runCatching { JSONObject(recordFile(context).readText()) }.getOrNull() ?: return
        json.remove(id)
        runCatching { recordFile(context).writeText(json.toString()) }
    }
}

/** The key holding [BackgroundChoice], in the same preferences the picked photo already used. */
private const val BACKGROUND_CHOICE = "backgroundChoice"

/**
 * Which picture is behind Home.
 *
 * **The migration.** Before this key existed, the only background was a photo and a single boolean said whether it
 * was showing. A phone updating into this version has that boolean and no choice, so the boolean is read once and
 * written forward as [BackgroundChoice.Photo] (STA-5). The old key is left alone rather than deleted, so a downgrade
 * to the previous version still finds the photo it expects.
 */
internal fun backgroundChoice(context: Context): BackgroundChoice {
    val prefs = launcherBackgroundPreferences(context)
    val saved = prefs.getString(BACKGROUND_CHOICE, null)
    if (saved == null) {
        val hadPhoto = prefs.getBoolean("photoEnabled", false) && launcherPhotoFile(context).isFile
        val migrated = if (hadPhoto) BackgroundChoice.Photo else BackgroundChoice.None
        prefs.edit { putString(BACKGROUND_CHOICE, migrated.save()) }
        return migrated
    }
    // exists(), not artFile().isFile: art that ships with Folio is an asset inside the APK and has no file on
    // disk, so testing for a file read every built-in choice straight back as "none" and Home fell through to the
    // default. Caught by picking one on a phone, which is the only place it showed.
    return BackgroundChoice.parse(saved) { id -> BackgroundLibrary.exists(context, id) }
}

internal fun setBackgroundChoice(context: Context, choice: BackgroundChoice) {
    launcherBackgroundPreferences(context).edit {
        putString(BACKGROUND_CHOICE, choice.save())
        // The old boolean is kept in step so the wallpaper service, which reads it directly, agrees with Home.
        putBoolean("photoEnabled", choice is BackgroundChoice.Photo)
    }
}
