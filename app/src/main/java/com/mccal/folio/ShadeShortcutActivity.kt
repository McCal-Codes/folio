package com.mccal.folio

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * "Notification shade" and "Quick settings" as launchable entries, so a phone's own gesture or button action that opens an app
 * (Samsung's One-hand operation, Routines, a gesture app, a Home shortcut) can pull the shade down over whatever is open. Each entry
 * is its own activity (below), not an alias of a shared one: some pickers list an app's entries by the class behind them and showed
 * only one of the two (the DroidLab report on issue 294). It uses the gestures service Folio already has, shows nothing, and leaves at once.
 * With "Folio's panels over other apps" on, what it pulls down is Folio's own Notification Center or Control Center.
 * Without the service it takes you to Folio's explanation of how to turn it on.
 */
abstract class ShadeShortcutActivity(private val panelName: String) : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val panel = ShadePanel.fromShortcut(panelName)
        if (panel == null) { finish(); return }
        // With "Folio's panels over other apps" on, the shortcut draws Folio's own panel; otherwise (or locked, or in Safe Mode) the phone's.
        if (!SystemShadeAccessibilityService.openFolio(panel)) when (SystemShadeAccessibilityService.open(this, panel)) {
            ShadeOpenResult.SERVICE_DISABLED -> startActivity(Intent(this, MainActivity::class.java)
                .putExtra("duo_destination", "shade_setup").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ShadeOpenResult.SERVICE_STARTING -> IslandEvents.notice(this, getString(R.string.folio_gestures_are_starting_swipe_down_a))
            ShadeOpenResult.OPENED, ShadeOpenResult.ACTION_REJECTED -> Unit
        }
        finish()
    }
}

/** The entry named "Folio Notification Shade". The class name is the old alias's, so a Routine or gesture that already points at it keeps working. */
class ShadeShortcutNotifications : ShadeShortcutActivity("NOTIFICATIONS")

/** The entry named "Folio Quick Settings". */
class ShadeShortcutQuickSettings : ShadeShortcutActivity("QUICK_SETTINGS")
