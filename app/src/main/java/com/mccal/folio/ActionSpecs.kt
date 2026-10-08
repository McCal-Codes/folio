package com.mccal.folio

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.media.AudioManager
import android.os.Process
import android.provider.Settings
import android.view.KeyEvent

/**
 * The actions added with Icon Actions. Nothing here asks for a permission except the accessibility ones, and none of
 * them toggles Wi-Fi, Bluetooth or data: Android does not let an app do that, so those open Android's own panel.
 * A spec returns false when it could not do the thing (an app that is gone, a service that is off); the runner turns
 * that into a trail line and, for accessibility, a notice.
 */
internal object ActionSpecs {
    /** Android's media play/pause global action (API 36). Used by value so the call compiles against any SDK. */
    private const val GLOBAL_ACTION_MEDIA_PLAY_PAUSE = 20

    val all: List<ActionSpec> by lazy {
        listOf(
            spec("app.open", R.string.action_app_open, OperationRisk.OBSERVE, "open") { c, a -> openApp(c, a["pkg"]) },
            spec("shortcut.open", R.string.action_shortcut_open, OperationRisk.OBSERVE, "open") { c, a -> openShortcut(c, a["pkg"], a["id"]) },
            spec("media.playpause", R.string.action_media_playpause, OperationRisk.REVERSIBLE, "media") { c, _ -> mediaKey(c, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) },
            spec("media.next", R.string.action_media_next, OperationRisk.REVERSIBLE, "media") { c, _ -> mediaKey(c, KeyEvent.KEYCODE_MEDIA_NEXT) },
            spec("media.prev", R.string.action_media_prev, OperationRisk.REVERSIBLE, "media") { c, _ -> mediaKey(c, KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
            spec("volume.up", R.string.action_volume_up, OperationRisk.REVERSIBLE, "volume") { c, _ -> volume(c, AudioManager.ADJUST_RAISE) },
            spec("volume.down", R.string.action_volume_down, OperationRisk.REVERSIBLE, "volume") { c, _ -> volume(c, AudioManager.ADJUST_LOWER) },
            spec("volume.mute", R.string.action_volume_mute, OperationRisk.REVERSIBLE, "volume") { c, _ -> volume(c, AudioManager.ADJUST_TOGGLE_MUTE) },
            spec("panel.wifi", R.string.action_panel_wifi, OperationRisk.OBSERVE, "panel") { c, _ -> startSettings(c, Settings.Panel.ACTION_INTERNET_CONNECTIVITY) },
            spec("panel.bluetooth", R.string.action_panel_bluetooth, OperationRisk.OBSERVE, "panel") { c, _ -> startSettings(c, Settings.ACTION_BLUETOOTH_SETTINGS) },
            global("a11y.back", R.string.action_a11y_back, OperationRisk.REVERSIBLE, AccessibilityService.GLOBAL_ACTION_BACK),
            global("a11y.home", R.string.action_a11y_home, OperationRisk.REVERSIBLE, AccessibilityService.GLOBAL_ACTION_HOME),
            global("a11y.recents", R.string.action_a11y_recents, OperationRisk.REVERSIBLE, AccessibilityService.GLOBAL_ACTION_RECENTS),
            global("a11y.quicksettings", R.string.action_a11y_quicksettings, OperationRisk.OBSERVE, AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS),
            global("a11y.allapps", R.string.action_a11y_allapps, OperationRisk.OBSERVE, AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS),
            global("a11y.dismissshade", R.string.action_a11y_dismissshade, OperationRisk.OBSERVE, AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE),
            global("a11y.power", R.string.action_a11y_power, OperationRisk.DISRUPTIVE, AccessibilityService.GLOBAL_ACTION_POWER_DIALOG),
            global("a11y.shade", R.string.action_a11y_shade, OperationRisk.OBSERVE, AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS),
            global("a11y.splitscreen", R.string.action_a11y_splitscreen, OperationRisk.REVERSIBLE, AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN),
            global("a11y.mediaplaypause", R.string.action_a11y_mediaplaypause, OperationRisk.REVERSIBLE, GLOBAL_ACTION_MEDIA_PLAY_PAUSE, minSdk = 36),
        )
    }

    private fun spec(id: String, label: Int, risk: OperationRisk, group: String, run: (Context, Map<String, String>) -> Boolean) =
        ActionSpec(id = id, label = label, risk = risk, run = run, group = group)

    private fun global(id: String, label: Int, risk: OperationRisk, action: Int, minSdk: Int = 0) = ActionSpec(
        id = id, label = label, risk = risk, needs = ActionNeeds.ACCESSIBILITY, minSdk = minSdk,
        run = { _, _ -> SystemShadeAccessibilityService.global(action) }, group = "system",
    )

    /** Launches an installed app by package name; false for a missing, blank or unknown package. */
    internal fun openApp(context: Context, pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /** Starts one of an app's shortcuts; false when the shortcut is gone or Folio is not the default Home. */
    internal fun openShortcut(context: Context, pkg: String?, id: String?): Boolean {
        if (pkg.isNullOrBlank() || id.isNullOrBlank()) return false
        val apps = context.getSystemService(LauncherApps::class.java) ?: return false
        val query = LauncherApps.ShortcutQuery()
            .setPackage(pkg).setShortcutIds(listOf(id))
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        if (apps.getShortcuts(query, Process.myUserHandle()).isNullOrEmpty()) return false
        apps.startShortcut(pkg, id, null, null, Process.myUserHandle())
        return true
    }

    private fun mediaKey(context: Context, code: Int): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return true
    }

    private fun volume(context: Context, direction: Int): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return true
    }

    private fun startSettings(context: Context, action: String): Boolean {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
