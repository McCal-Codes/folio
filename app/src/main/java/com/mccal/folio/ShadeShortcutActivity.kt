package com.mccal.folio

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle

/**
 * "Notification shade" and "Quick settings" as launchable entries (two aliases in the manifest), so a phone's own
 * gesture or button action that opens an app (Samsung's One-hand operation, Routines, a Home shortcut) can pull the
 * shade down over whatever is open. It uses the gestures service Folio already has, shows nothing, and leaves at once.
 * Without the service it takes you to Folio's explanation of how to turn it on.
 */
class ShadeShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val panel = intent.component?.let { component ->
            runCatching { packageManager.getActivityInfo(component, PackageManager.GET_META_DATA).metaData?.getString(META_PANEL) }.getOrNull()
        }.let(ShadePanel::fromShortcut)
        if (panel != null) when (SystemShadeAccessibilityService.open(this, panel)) {
            ShadeOpenResult.SERVICE_DISABLED -> startActivity(Intent(this, MainActivity::class.java)
                .putExtra("duo_destination", "shade_setup").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ShadeOpenResult.SERVICE_STARTING -> IslandEvents.notice(this, getString(R.string.folio_gestures_are_starting_swipe_down_a))
            ShadeOpenResult.OPENED, ShadeOpenResult.ACTION_REJECTED -> Unit
        }
        finish()
    }

    private companion object {
        const val META_PANEL = "folio.shade.panel"
    }
}
