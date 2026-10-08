package com.mccal.folio

import android.content.Context
import android.graphics.BitmapFactory
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

    /** Puts a tweak's options back exactly (the ones a package restore saved). Read-only launchers ignore it. */
    fun setTweakOptions(id: String, options: Map<String, String>) = Unit
    fun applyTheme(theme: FolioTheme)

    /** Duet's look and the fold intensity, from a package. Read-only launchers ignore it. */
    fun setDuet(options: com.mccal.folio.duet.DuetOptions, intensity: Float) = Unit

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

    /** Adds a packaged page effect to the picker. Nothing is chosen for the user; returns nothing to restore. */
    fun addPageEffect(effect: PackagedPageEffect) = Unit

    /** Takes a packaged page effect out of the picker again (remove, Undo, Safe Mode). */
    fun removePageEffect(id: String) = Unit
}

/** The real launcher behind [MarketLauncher]. */
internal class ModelLauncher(private val model: LauncherModel, private val context: Context) : MarketLauncher {
    override val state get() = model.state.value
    override fun installTweak(feature: TweakFeature) = model.installTweak(feature)
    override fun removeTweak(feature: TweakFeature) = model.removeTweak(feature)
    override fun setTweakOptions(id: String, options: Map<String, String>) = model.setTweakOptions(id, options)
    override fun setFeatureScope(id: String, screen: FolioScreen, value: ScopeValue) = model.setFeatureScope(id, screen, value)
    override fun addPageEffect(effect: PackagedPageEffect) = model.addPackagedEffect(effect)
    override fun removePageEffect(id: String) = model.removePackagedEffect(id)
    override fun applyTheme(theme: FolioTheme) = model.applyTheme(theme)
    override fun setDuet(options: com.mccal.folio.duet.DuetOptions, intensity: Float) = model.setDuet(options, intensity)

    /**
     * [bytes] is empty when this change came from a record rather than from a package file, because a record does
     * not carry the picture. On the phone that installed it the file is still there and is used as it stands, which
     * is what makes Undo, Safe Mode and Try Again work with no network. On a phone restoring someone else's backup
     * it is not there, and there is nothing honest to do but say so: the package lands in the restore's failed list,
     * turned off, and getting it again from its source is what brings the picture with it.
     */
    override fun applyArtBackground(art: Artwork, bytes: ByteArray, sha256: String): String =
        // The installer turns any refusal into one general message, so the reason goes in the trail on its way out.
        runCatching { putArt(art, bytes, sha256) }.onFailure { Diagnostics.artRefused(art.id, it.message) }.getOrThrow()

    private fun putArt(art: Artwork, bytes: ByteArray, sha256: String): String {
        // Every refusal comes before anything on disk changes. A refusal after the write used to leave an updated id
        // with its old picture renamed away and its new one deleted, and a thrown apply is never restored.
        if (BackgroundLibrary.isBuiltIn(art.id)) error("that wallpaper uses a name that belongs to art inside Folio")
        if (!art.credited) error("that wallpaper doesn't say who made it")
        if (bytes.isNotEmpty()) {
            // Read from the header alone. A few megabytes of WebP can decode to hundreds, and Home would then fail to
            // draw on every start, long after Safe Mode stopped watching this install.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            BackgroundLibrary.sizeProblem(bounds.outWidth, bounds.outHeight)?.let { error("$it (${bounds.outWidth}x${bounds.outHeight})") }
        }
        // Asked before the credit is written: an id already in the library is an update or a package coming back on,
        // and those follow what the user chose since rather than choosing for them (ArtSelection).
        val fresh = BackgroundLibrary.installed(context).none { it.id == art.id }
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
        if (!BackgroundLibrary.record(context, art)) {
            // The credit could not be written down, so the picture goes back to how it was before this call.
            if (bytes.isNotEmpty()) {
                file.delete()
                BackgroundLibrary.unkeep(dir, art.id)
            }
            error("that wallpaper's credit couldn't be saved")
        }
        val was = artSelection(context).applied(art.id, fresh).save()
        Diagnostics.artOn(art.id, fresh, shown = backgroundChoice(context) == BackgroundChoice.Art(art.id))
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
        val showing = backgroundChoice(context) == BackgroundChoice.Art(artId)
        artSelection(context).restored(artId, BackgroundChoice.parse(snapshot) { id ->
            id != artId && BackgroundLibrary.exists(context, id)
        })
        Diagnostics.artOff(artId, putBack = showing)
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
        Capability.FOLD_TRANSITION,
    )

    // Added from the Market or from Settings' Tweak Library: either way it's in installedTweaks.
    override fun hasTweak(id: String): Boolean = id in launcher.state.installedTweaks

    // Read ahead of the change, so a kill between the change and the installer noting its snapshot can still put it back.
    // A wallpaper's snapshot comes from the picture store as it is applied, so that one is not recorded ahead of time.
    override fun snapshotBefore(change: PackageChange): String? = when (change) {
        is PackageChange.Theme -> FolioTheme.of(launcher.state, PREVIOUS_THEME).toJson().toString()
        is PackageChange.Tweaks -> tweakSnapshot(launcher.state, change.bundle)
        is PackageChange.PageEffect -> ""
        else -> null
    }

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
        is PackageChange.PageEffect -> {
            launcher.addPageEffect(PackagedPageEffect.of(change.id, change.name, change.maxRotation, change.pivot,
                change.shrink, change.cameraWidths))
            ""
        }
        // Reading a package already refuses kinds this Folio can't apply; this is the belt to that's braces.
        else -> error("Folio can't apply that yet")
    }

    override fun restore(change: PackageChange, snapshot: String) {
        when (change) {
            is PackageChange.Theme -> FolioTheme.fromJson(snapshot)?.let(launcher::applyTheme)
            is PackageChange.Tweaks -> restoreTweaks(snapshot)
            is PackageChange.Wallpaper -> launcher.restoreArtBackground(change.id, snapshot)
            is PackageChange.PageEffect -> launcher.removePageEffect(change.id)
            else -> Unit
        }
    }

    private fun applyTweak(setting: TweakSetting) {
        val feature = featureFor(setting.id) ?: return
        if (setting.enabled) launcher.installTweak(feature) else launcher.removeTweak(feature)
        // A bundle that leaves out a screen means "not there": an override, not the tweak's own default.
        launcher.setFeatureScope(feature.id, FolioScreen.COVER, if (setting.cover) ScopeValue.DEFAULT else ScopeValue.OFF)
        launcher.setFeatureScope(feature.id, FolioScreen.INNER, if (setting.inner) ScopeValue.DEFAULT else ScopeValue.OFF)
        // A package that turns Duet off sets no options for it.
        if (setting.id == TweakId.DUET && setting.enabled && setting.options.isNotEmpty()) applyDuet(setting.options)
    }

    /** Only the keys the package set change; the rest keep what the person has. [DuetOptions.fromJson] clamps. */
    private fun applyDuet(options: Map<String, Any>) {
        val state = launcher.state
        val merged = state.duet.toJson()
        for (key in listOf("style", "frost", "darkening", "perspective", "direction")) options[key]?.let { merged.put(key, it) }
        val intensity = (options["intensity"] as? Double)?.toFloat() ?: state.foldIntensity
        launcher.setDuet(com.mccal.folio.duet.DuetOptions.fromJson(merged), intensity)
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
            // Turning a tweak off forgets its options, so removing the package has to put them back with it.
            launcher.setTweakOptions(feature.id, json.optJSONObject("options")?.let { o -> o.keys().asSequence().associateWith { o.optString(it) } }.orEmpty())
            // Removing the package puts back the look it replaced, not the default.
            json.optJSONObject("duet")?.let { launcher.setDuet(com.mccal.folio.duet.DuetOptions.fromJson(it), json.optDouble("foldIntensity", 1.0).toFloat()) }
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
                json.put("options", JSONObject(state.tweakOptions[feature.id].orEmpty()))
                if (setting.id == TweakId.DUET && setting.options.isNotEmpty()) {
                    json.put("duet", state.duet.toJson()).put("foldIntensity", state.foldIntensity.toDouble())
                }
                array.put(json)
            }
            return array.toString()
        }
    }
}
