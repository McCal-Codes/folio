package com.mccal.folio

import android.content.Context
import com.mccal.folio.market.Capability
import com.mccal.folio.market.PackageChange
import com.mccal.folio.market.PackageHost
import com.mccal.folio.market.TweakBundle
import com.mccal.folio.market.TweakId
import com.mccal.folio.market.TweakSetting
import org.json.JSONArray
import org.json.JSONObject

/**
 * The part of the launcher a package may change. `:market` knows nothing about Folio's model, and everything a package
 * does goes through here, so what a package can reach is this file and nothing else.
 */
internal interface MarketLauncher {
    val state: LauncherState
    fun installTweak(feature: TweakFeature)
    fun removeTweak(feature: TweakFeature)
    fun setFeatureScope(id: String, screen: FolioScreen, value: ScopeValue)
    fun applyTheme(theme: FolioTheme)

    /**
     * Installs a piece of art, shows it, and returns what was behind Home before so Undo can put it back.
     *
     * The bytes are written under Folio's own files rather than kept in the package record, because a picture is a
     * file and the library lists files. [sha256] names the picture a record means when [bytes] is empty. Throwing
     * means nothing changed.
     */
    fun applyArtBackground(art: Artwork, bytes: ByteArray, sha256: String): String

    /** Puts back what [applyArtBackground] replaced, and forgets the art it installed. */
    fun restoreArtBackground(artId: String, snapshot: String)
}

/** The real launcher behind [MarketLauncher]. */
internal class ModelLauncher(private val model: LauncherModel, private val context: Context) : MarketLauncher {
    override val state get() = model.state.value
    override fun installTweak(feature: TweakFeature) = model.installTweak(feature)
    override fun removeTweak(feature: TweakFeature) = model.removeTweak(feature)
    override fun setFeatureScope(id: String, screen: FolioScreen, value: ScopeValue) = model.setFeatureScope(id, screen, value)
    override fun applyTheme(theme: FolioTheme) = model.applyTheme(theme)

    /**
     * [bytes] is empty when this change came from a record rather than from a package file, because a record does
     * not carry the picture. On the phone that installed it the file is still there and is used as it stands, which
     * is what makes Undo, Safe Mode and Try Again work with no network. On a phone restoring someone else's backup
     * it is not there, and there is nothing honest to do but say so: the package lands in the restore's failed list,
     * turned off, and getting it again from its source is what brings the picture with it.
     */
    override fun applyArtBackground(art: Artwork, bytes: ByteArray, sha256: String): String {
        val was = backgroundChoice(context).save()
        val dir = BackgroundLibrary.installedDir(context)
        val file = BackgroundLibrary.artFile(context, art.id)
        if (bytes.isEmpty()) {
            // A record names its picture by hash, and an update may have put that picture aside, so it comes back
            // here. A record written before hashes has none and uses the picture in place, which is all it had.
            val there = if (sha256.isNotEmpty()) BackgroundLibrary.bringBack(dir, art.id, sha256) else file.isFile
            if (!there) error("that wallpaper's picture isn't on this phone. Get it again to put it back")
        } else {
            file.parentFile?.mkdirs()
            // An update overwrites the one file an id has, and the record carries no picture, so undoing the update
            // would reapply the old version's credit over the new version's picture. The picture being replaced is
            // kept beside it, named by its hash, until an older record asks for it or the package is pruned.
            BackgroundLibrary.keepCurrent(dir, art.id)
            file.writeBytes(bytes)
        }
        if (BackgroundLibrary.isBuiltIn(art.id)) {
            if (bytes.isNotEmpty()) file.delete()
            // Not the credit's fault: this package is using a name that belongs to art inside Folio, which is the
            // same refusal the Market makes for a source claiming a built-in package's id.
            error("that wallpaper uses a name that belongs to art inside Folio")
        }
        if (!BackgroundLibrary.record(context, art)) {
            if (bytes.isNotEmpty()) file.delete()
            error("that wallpaper doesn't say who made it")
        }
        setBackgroundChoice(context, BackgroundChoice.Art(art.id))
        LauncherBackgroundCache.changed(null)
        return was
    }

    /**
     * The picture is left on disk. Restoring runs for Undo and for Safe Mode turning a package off as well as for
     * Remove, and only the last of those means "gone": deleting here would make a package that Safe Mode switched
     * off need downloading again to switch back on. [BackgroundLibrary.prune] is what actually clears art whose
     * package is no longer installed.
     */
    override fun restoreArtBackground(artId: String, snapshot: String) {
        // exists(), not artFile().isFile: the snapshot may name art that ships inside Folio, which is an asset with
        // no file, and testing for a file would read that snapshot as nothing and lose the background it recorded.
        setBackgroundChoice(context, BackgroundChoice.parse(snapshot) { id ->
            id != artId && BackgroundLibrary.exists(context, id)
        })
        // No file moves here. Safe Mode turning an update off comes through this too, and the update's picture has
        // to be the one in place when it's turned back on. Putting an older version back is the installer applying
        // that version's record, and the record's hash is what brings its picture back (applyArtBackground).
        LauncherBackgroundCache.changed(null)
    }
}

/**
 * Applies a package's changes to Folio, and puts back what they replaced.
 *
 * [capabilities] is only what this build really does: a package asking for anything else is told it needs a newer
 * Folio rather than being half applied. Layouts, wallpapers and icon-pack links come in later phases.
 */
internal class MarketHost(private val launcher: MarketLauncher) : PackageHost {
    override val capabilities = setOf(
        Capability.THEME,
        Capability.WALLPAPER,
        Capability.APP_PANELS,
        Capability.DOCK_MAGNIFY,
        Capability.NOTIFICATION_APP_ROW,
        Capability.TINT_NOTIFICATIONS,
        Capability.TINT_MEDIA,
        Capability.PAGE_EFFECTS,
    )

    override fun apply(change: PackageChange): String = when (change) {
        is PackageChange.Theme -> {
            val theme = FolioTheme.fromJson(change.json) ?: error("that theme file isn't one Folio can read")
            val before = FolioTheme.of(launcher.state, PREVIOUS_THEME).toJson().toString()
            launcher.applyTheme(theme)
            before
        }
        is PackageChange.Tweaks -> {
            val before = tweakSnapshot(launcher.state, change.bundle)
            change.bundle.tweaks.forEach(::applyTweak)
            before
        }
        // Refused here as well as when the package was read, because a change can also arrive from a restored
        // backup record, and a record written before wallpapers carried a credit decodes without one (DES-2b).
        is PackageChange.Wallpaper -> {
            if (!change.credited) error("that wallpaper doesn't say who made it and what it's licensed under")
            launcher.applyArtBackground(
                Artwork(
                    id = change.id, title = change.title, artist = change.artist,
                    license = change.license, detail = change.detail, source = change.source,
                ),
                change.bytes,
                change.pictureSha256,
            )
        }
        // Reading a package already refuses kinds this Folio can't apply; this is the belt to that's braces.
        else -> error("Folio can't apply that yet")
    }

    override fun restore(change: PackageChange, snapshot: String) {
        when (change) {
            is PackageChange.Theme -> FolioTheme.fromJson(snapshot)?.let(launcher::applyTheme)
            is PackageChange.Tweaks -> restoreTweaks(snapshot)
            is PackageChange.Wallpaper -> launcher.restoreArtBackground(change.id, snapshot)
            else -> Unit
        }
    }

    private fun applyTweak(setting: TweakSetting) {
        val feature = featureFor(setting.id) ?: return
        if (setting.enabled) launcher.installTweak(feature) else launcher.removeTweak(feature)
        // A bundle that leaves out a screen means "not there": an override, not the tweak's own default.
        launcher.setFeatureScope(feature.id, FolioScreen.COVER, if (setting.cover) ScopeValue.DEFAULT else ScopeValue.OFF)
        launcher.setFeatureScope(feature.id, FolioScreen.INNER, if (setting.inner) ScopeValue.DEFAULT else ScopeValue.OFF)
    }

    private fun restoreTweaks(snapshot: String) {
        val array = runCatching { JSONArray(snapshot) }.getOrNull() ?: return
        for (i in 0 until array.length()) {
            val json = array.optJSONObject(i) ?: continue
            val feature = featureFor(TweakId.from(json.optString("id")) ?: continue) ?: continue
            if (json.optBoolean("installed")) launcher.installTweak(feature) else launcher.removeTweak(feature)
            for (screen in FolioScreen.entries) {
                val value = ScopeValue.entries.firstOrNull { it.name == json.optString(screen.name) } ?: ScopeValue.DEFAULT
                launcher.setFeatureScope(feature.id, screen, value)
            }
        }
    }

    private companion object {
        const val PREVIOUS_THEME = "Before this package"

        fun featureFor(id: TweakId): TweakFeature? = TweakFeatures.firstOrNull { it.id == id.id }

        /** What the tweaks in [bundle] looked like before, so removing the package puts them back exactly. */
        fun tweakSnapshot(state: LauncherState, bundle: TweakBundle): String {
            val array = JSONArray()
            for (setting in bundle.tweaks) {
                val feature = featureFor(setting.id) ?: continue
                val json = JSONObject()
                    .put("id", setting.id.id)
                    .put("installed", feature.id in state.installedTweaks)
                for (screen in FolioScreen.entries) {
                    json.put(screen.name, FeatureScopes.value(state.featureScopes, feature.id, screen).name)
                }
                array.put(json)
            }
            return array.toString()
        }
    }
}
