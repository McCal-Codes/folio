package com.mccal.folio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Simple Mode (roadmap 0.7.2, previewed in 0.6.9 beta 4 behind [FeatureGate.SIMPLE_MODE]): a calm Home of a few big,
 * labeled apps that a helper sets up, styled after Apple's Assistive Access. This file is the part with no UI: what
 * Simple Home shows ([SimpleSetup]), which settings Simple changes ([SimpleChanges]) and the copy of Home and those
 * settings taken when it is turned on ([SimpleRestore]), so turning it off puts everything back exactly, as Pixel's
 * Simple View does.
 *
 * Helper powers are layout only: which apps, their order, the Home style and Folio's own text size. Nothing here reads
 * messages, location or activity (research 12 Oct, items 9 to 11).
 */
internal const val SIMPLE_MAX_APPS = 6

/** How Simple Home lays out its apps. List is the default: it reads top to bottom and holds up best at 200% text. */
enum class SimpleStyle { LIST, GRID }

/** Folio's own text size in Simple. It changes Folio only, never Android's font size or other apps (research item 7). */
enum class SimpleTextSize(val scale: Float, @androidx.annotation.StringRes val label: Int) {
    STANDARD(1f, R.string.standard), LARGER(1.15f, R.string.simple_text_larger), LARGEST(1.3f, R.string.simple_text_largest),
}

/** What Simple Home shows: the helper's apps in order (app ids, at most [SIMPLE_MAX_APPS]), the style and the text size. */
data class SimpleSetup(
    val apps: List<String> = emptyList(),
    val style: SimpleStyle = SimpleStyle.LIST,
    val textSize: SimpleTextSize = SimpleTextSize.STANDARD,
) {
    /** Each app once, and no more than Home holds. */
    fun sanitized() = copy(apps = apps.filter { it.isNotBlank() }.distinct().take(SIMPLE_MAX_APPS))

    fun toJson(): JSONObject = JSONObject().put("apps", JSONArray(apps)).put("style", style.name).put("textSize", textSize.name)

    companion object {
        fun fromJson(o: JSONObject) = SimpleSetup(
            apps = o.optJSONArray("apps")?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty(),
            style = SimpleStyle.entries.firstOrNull { it.name == o.optString("style") } ?: SimpleStyle.LIST,
            textSize = SimpleTextSize.entries.firstOrNull { it.name == o.optString("textSize") } ?: SimpleTextSize.STANDARD,
        ).sanitized()
    }
}

/**
 * Every setting Simple changes, and nothing else: the profile's three ([ExperienceProfile.SIMPLE]'s bundle), the Folio
 * extras that draw over Home or other apps (the island and dock everywhere, StandBy, the Lock Cover), motion that
 * Simple leaves out (page effects, the fold animation, wallpaper parallax), and new apps landing on Home. While Simple
 * is on, these are [SIMPLE]; the values from before are kept in [SimpleRestore] and put back when it is turned off.
 */
internal data class SimpleChanges(
    val island: Boolean, val folioPanels: Boolean, val swipeDownHome: String,
    val islandEverywhere: Boolean, val dockEverywhere: Boolean,
    val standBy: Boolean, val standByCharging: Boolean, val standByTent: Boolean, val lockCover: Boolean,
    val pageEffect: PageEffect, val foldEffect: Boolean, val wallpaperMotion: Boolean,
    val addNewAppsToHome: Boolean,
) {
    fun applyTo(s: LauncherState) = s.copy(island = island, folioPanels = folioPanels, swipeDownHome = swipeDownHome,
        islandEverywhere = islandEverywhere, dockEverywhere = dockEverywhere,
        standBy = standBy, standByCharging = standByCharging, standByTent = standByTent, lockCover = lockCover,
        pageEffect = pageEffect, foldEffect = foldEffect, wallpaperMotion = wallpaperMotion, addNewAppsToHome = addNewAppsToHome)

    fun toJson(): JSONObject = JSONObject().put("island", island).put("folioPanels", folioPanels).put("swipeDownHome", swipeDownHome)
        .put("islandEverywhere", islandEverywhere).put("dockEverywhere", dockEverywhere)
        .put("standBy", standBy).put("standByCharging", standByCharging).put("standByTent", standByTent).put("lockCover", lockCover)
        .put("pageEffect", pageEffect.name).put("foldEffect", foldEffect).put("wallpaperMotion", wallpaperMotion)
        .put("addNewAppsToHome", addNewAppsToHome)

    companion object {
        fun of(s: LauncherState) = SimpleChanges(s.island, s.folioPanels, s.swipeDownHome, s.islandEverywhere, s.dockEverywhere,
            s.standBy, s.standByCharging, s.standByTent, s.lockCover, s.pageEffect, s.foldEffect, s.wallpaperMotion, s.addNewAppsToHome)

        /** Simple's values: nothing drawn over Home or other apps, no motion beyond a fade, and Home changed only by a helper. */
        val SIMPLE: SimpleChanges get() {
            val bundle = ExperienceProfile.SIMPLE.bundle!!
            return SimpleChanges(bundle.island, bundle.folioPanels, bundle.swipeDownHome, islandEverywhere = false, dockEverywhere = false,
                standBy = false, standByCharging = false, standByTent = false, lockCover = false,
                pageEffect = PageEffect.NONE, foldEffect = false, wallpaperMotion = false, addNewAppsToHome = false)
        }

        /** Strict: a snapshot missing a value is not used, so a damaged file can never put back a half-guessed setting. */
        fun fromJson(o: JSONObject) = SimpleChanges(o.getBoolean("island"), o.getBoolean("folioPanels"), o.getString("swipeDownHome"),
            o.getBoolean("islandEverywhere"), o.getBoolean("dockEverywhere"),
            o.getBoolean("standBy"), o.getBoolean("standByCharging"), o.getBoolean("standByTent"), o.getBoolean("lockCover"),
            PageEffect.valueOf(o.getString("pageEffect")), o.getBoolean("foldEffect"), o.getBoolean("wallpaperMotion"),
            o.getBoolean("addNewAppsToHome"))
    }
}

/** Home and the settings Simple changes, as they were just before Simple was turned on. */
internal data class SimpleRestore(val layout: HomeLayout, val settings: SimpleChanges) {
    fun toJson(): JSONObject = JSONObject().put("version", VERSION).put("layout", LayoutHistory.encodeLayout(layout)).put("settings", settings.toJson())

    companion object {
        private const val VERSION = 1
        fun of(s: LauncherState) = SimpleRestore(s.layout, SimpleChanges.of(s))
        fun fromJson(o: JSONObject): SimpleRestore {
            require(o.optInt("version") == VERSION) { "Unknown Simple Mode snapshot" }
            return SimpleRestore(LayoutHistory.decodeLayout(o.getJSONObject("layout")), SimpleChanges.fromJson(o.getJSONObject("settings")))
        }
    }
}

/**
 * Where [SimpleRestore] lives: its own small file, written whole before Simple turns on, so a phone that restarts or a
 * Folio that is killed mid-way still finds it. Kept out of Folio's main saved state so Home's start doesn't read it.
 */
internal class SimpleRestoreStore(private val file: File) {
    /** Writes [restore] and waits until it is on disk. False when it couldn't be, and then Simple must not turn on. */
    fun save(restore: SimpleRestore): Boolean = runCatching {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(temporary).use { out -> out.write(restore.toJson().toString().toByteArray()); out.fd.sync() }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        true
    }.getOrDefault(false)

    /** The saved copy, or null when there is none or it can't be read. */
    fun load(): SimpleRestore? = runCatching { SimpleRestore.fromJson(JSONObject(file.readText())) }.getOrNull()

    fun clear() { file.delete() }

    /** Whether a copy is saved, which is while Simple is on. */
    fun exists(): Boolean = file.isFile

    companion object {
        fun of(context: Context) = SimpleRestoreStore(File(context.filesDir, "simple_restore.json"))
    }
}

/**
 * Simple turned on with [apps] on its Home in [style]: Simple's settings, and the setup. The copy of what it replaces
 * is taken first, by the caller ([LauncherModel.turnOnSimple], the one way in for Settings and for setup).
 */
internal fun LauncherState.turnedSimple(apps: List<String>, style: SimpleStyle = SimpleStyle.LIST): LauncherState =
    withProfile(ExperienceProfile.SIMPLE).copy(simple = (simple ?: SimpleSetup()).copy(apps = apps, style = style).sanitized())

/**
 * Simple turned off: [restore]'s settings back exactly, and Simple's Home gone. Folio's own Home is the live one.
 * Simple never edits it (its Home is locked and separate), so it changes only when an app leaves the phone or a helper
 * changes it on purpose from All Settings, a restored backup for one; either way the live Home is the newer truth.
 * The Home from before Simple is kept too, in Layout History, whenever it differs ([homeToKeep]), so nothing is lost.
 * Without a copy (it couldn't be read), the Folio profile is put back.
 */
internal fun LauncherState.leftSimple(restore: SimpleRestore?): LauncherState =
    (restore?.settings?.applyTo(this) ?: withProfile(ExperienceProfile.FOLIO)).copy(simple = null)

/** Layout History's name for the Home from before Simple, like its other reasons ("Before restoring"). */
internal const val SIMPLE_HISTORY_REASON = "Before Simple Mode"

/** The Home from before Simple, when it isn't the one [live] shows, to keep in Layout History on leaving. */
internal fun SimpleRestore.homeToKeep(live: LauncherState): HomeLayout? = layout.takeIf { it != live.layout }

/**
 * Simple is on: nothing that Folio would show by itself may appear over Simple Home (What's New, the Folio Dev page,
 * hints, island notices and toasts, setup and supporter prompts). They are only held back, not marked as seen, so each
 * still shows once Simple is off if it is still due. [allSettings] is set while a helper has Folio's full Settings open
 * from Helper Settings.
 */
internal object SimpleQuiet {
    @Volatile var active = false
    val allSettings = androidx.compose.runtime.mutableStateOf(false)

    /**
     * Read once as Folio's process starts, before any notice or toast can show. The saved copy is on disk exactly while
     * Simple is on (written before it turns on, removed when it turns off), so its being there is the answer, without
     * reading the whole saved state. The Home's state keeps it right from then on.
     */
    fun start(store: SimpleRestoreStore) { active = store.exists() }
}
