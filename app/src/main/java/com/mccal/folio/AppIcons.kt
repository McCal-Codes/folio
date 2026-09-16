package com.mccal.folio

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Alternate app icons, like iOS's setAlternateIconName: each icon is its own launcher entry (an activity-alias of
 * MainActivity), and exactly one is enabled. Only Folio's app entry changes; the Home app itself isn't touched.
 */
/** Folio's Kotlin package (and the release app ID). */
internal const val FOLIO_CLASSES = "com.mccal.folio"

internal enum class AppIconChoice(val label: String, val alias: String, val mipmap: Int) {
    OLIVE("Olive", "FolioSettingsApp", R.mipmap.ic_launcher),
    SOFT("Soft", "FolioSettingsAppSoft", R.mipmap.ic_launcher_soft);

    companion object {
        /** Every alias class name starts with this, so Folio can recognize its own app entry. */
        const val ALIAS_PREFIX = "FolioSettingsApp"

        fun current(context: Context): AppIconChoice = entries.firstOrNull { choice ->
            val state = context.packageManager.getComponentEnabledSetting(ComponentName(context, "${context.packageName}.${choice.alias}"))
            state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && choice == OLIVE)
        } ?: OLIVE

        fun set(context: Context, choice: AppIconChoice) {
            val pm = context.packageManager
            // Enable the new entry before disabling the old one, so there's never a moment with no app icon.
            pm.setComponentEnabledSetting(ComponentName(context, "${context.packageName}.${choice.alias}"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            entries.filter { it != choice }.forEach {
                pm.setComponentEnabledSetting(ComponentName(context, "${context.packageName}.${it.alias}"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
