package com.mccal.folio

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.edit

/*
 * Where a person's color changes to Folio's own wallpapers are kept, and the one place that turns a piece plus its changes into
 * a picture. They live in the same `launcher_background` preferences as the choice of picture (BackgroundLibrary's note on who
 * owns that), one key per piece, and are removed when they go back to no change, so an untouched piece costs nothing and
 * reads as the shipped file.
 */

private fun tweaksKey(id: String) = "art_tweaks_$id"

/** The saved changes for [id]; no change for a piece without a recipe or one never touched. */
internal fun artTweaks(context: Context, id: String): WallpaperTweaks =
    if (!WallpaperRecipes.isCustomizable(id)) WallpaperTweaks.NONE
    else WallpaperTweaks.parse(launcherBackgroundPreferences(context).getString(tweaksKey(id), null))

/** Saves [tweaks] for [id] (or forgets them when there is no change) and drops the decoded Home picture so it is redrawn. */
internal fun setArtTweaks(context: Context, id: String, tweaks: WallpaperTweaks) {
    if (!WallpaperRecipes.isCustomizable(id)) return
    launcherBackgroundPreferences(context).edit {
        if (tweaks.isIdentity) remove(tweaksKey(id)) else putString(tweaksKey(id), tweaks.save())
    }
    LauncherBackgroundCache.changed(null)
}

/**
 * The part of a background's identity that the colors add, so a recolored piece is never mistaken for the shipped one in the
 * decoded-picture cache. Empty for anything untouched.
 */
internal fun artTweaksIdentity(context: Context, id: String): String =
    artTweaks(context, id).takeUnless { it.isIdentity }?.let { "#${it.save()}" } ?: ""

/** [id] drawn with the person's saved colors at [size] pixels, or null when it has none (the shipped picture is the right one). */
internal fun tweakedArtBitmap(context: Context, id: String, size: Int): Bitmap? {
    val tweaks = artTweaks(context, id)
    return if (tweaks.isIdentity) null else renderTweaked(id, tweaks, size)
}
