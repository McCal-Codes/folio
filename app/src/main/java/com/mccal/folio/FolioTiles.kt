package com.mccal.folio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.json.JSONObject
import java.lang.ref.WeakReference

/**
 * Quick Settings tiles for Samsung's pull-down: Spotlight, and switches for the island and dock over other
 * apps and StandBy. Toggles go through the running [LauncherModel] when there is one (so Home never writes
 * an older value back), otherwise straight into the saved launcher state that Folio reads on start.
 */
internal object FolioSettingsBridge {
    @Volatile var liveModel: WeakReference<LauncherModel>? = null

    private fun model() = liveModel?.get()

    fun value(context: Context, key: String, default: Boolean, fromState: (LauncherState) -> Boolean): Boolean =
        model()?.let { fromState(it.state.value) } ?: runCatching {
            JSONObject(context.getSharedPreferences(SettingKeys.PREFS, 0).getString(SettingKeys.STATE, "{}") ?: "{}").optBoolean(key, default)
        }.getOrDefault(default)

    fun set(context: Context, key: String, value: Boolean, viaModel: (LauncherModel, Boolean) -> Unit) {
        val live = model()
        if (live != null) { viaModel(live, value); return }
        val prefs = context.getSharedPreferences(SettingKeys.PREFS, 0)
        val raw = prefs.getString(SettingKeys.STATE, null) ?: return // nothing saved yet: Folio hasn't run, keep defaults
        runCatching { prefs.edit().putString(SettingKeys.STATE, JSONObject(raw).put(key, value).toString()).apply() }
    }
}

/** Opens an activity from a tile and closes the pull-down (Android 14+ requires a PendingIntent). */
@Suppress("DEPRECATION")
@android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // the Intent form only runs below Android 14
private fun TileService.openAndCollapse(intent: Intent) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (Build.VERSION.SDK_INT >= 34) {
        startActivityAndCollapse(PendingIntent.getActivity(this, intent.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    } else startActivityAndCollapse(intent)
}

class SpotlightTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply { state = Tile.STATE_INACTIVE; updateTile() }
    }

    override fun onClick() {
        val open = {
            SpotlightRequest.request()
            openAndCollapse(Intent(this, MainActivity::class.java))
        }
        if (isLocked) unlockAndRun(open) else open()
    }
}

/** A tile that switches one Folio setting. */
abstract class FolioToggleTile(private val key: String, private val default: Boolean) : TileService() {
    abstract fun read(state: LauncherState): Boolean
    abstract fun write(model: LauncherModel, value: Boolean)
    /** Called after turning on, e.g. to finish setup. */
    open fun afterEnable() = Unit

    private fun current() = FolioSettingsBridge.value(this, key, default, ::read)

    override fun onStartListening() = refresh()

    override fun onClick() {
        val next = !current()
        FolioSettingsBridge.set(this, key, next, ::write)
        refresh(next)
        if (next) afterEnable()
    }

    private fun refresh(value: Boolean = current()) {
        qsTile?.apply {
            state = if (value) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            stateDescription = getString(if (value) R.string.state_on else R.string.state_off)
            updateTile()
        }
    }

    /** The island and dock over other apps need Folio's accessibility service. */
    protected fun openAccessibilityIfNeeded() {
        if (!SystemShadeAccessibilityService.isConnected()) openAndCollapse(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

class IslandTileService : FolioToggleTile(SettingKeys.ISLAND_EVERYWHERE, false) {
    override fun read(state: LauncherState) = state.islandEverywhere
    override fun write(model: LauncherModel, value: Boolean) = model.setIslandEverywhere(value)
    override fun afterEnable() = openAccessibilityIfNeeded()
}

class DockTileService : FolioToggleTile(SettingKeys.DOCK_EVERYWHERE, false) {
    override fun read(state: LauncherState) = state.dockEverywhere
    override fun write(model: LauncherModel, value: Boolean) = model.setDockEverywhere(value)
    override fun afterEnable() = openAccessibilityIfNeeded()
}

class StandByTileService : FolioToggleTile("standBy", true) {
    override fun read(state: LauncherState) = state.standBy
    override fun write(model: LauncherModel, value: Boolean) = model.setStandBy(value)
}
