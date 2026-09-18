package com.mccal.folio

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Alternate app icons, like iOS's setAlternateIconName: each icon is its own launcher entry (an activity-alias of
 * MainActivity), and exactly one is enabled. Only Folio's app entry changes; the Home app itself isn't touched.
 */
/** Folio's Kotlin package: class names stay the same in the Folio Dev build, whose app ID ends in ".dev". */
internal const val FOLIO_CLASSES = "com.mccal.folio"

internal enum class AppIconChoice(@androidx.annotation.StringRes val label: Int, val alias: String, private val background: Int, private val foreground: Int) {
    TEAL(R.string.teal, "FolioSettingsApp", R.drawable.folio_icon_teal_background, R.drawable.ic_launcher_foreground),
    SOFT(R.string.soft, "FolioSettingsAppSoft", R.drawable.folio_icon_soft_background, R.drawable.ic_launcher_soft_foreground),
    OLIVE(R.string.olive, "FolioSettingsAppOlive", R.drawable.ic_launcher_olive_background, R.drawable.ic_launcher_olive_foreground);

    /**
     * The icon as Folio draws it in its own screens: the real artwork (never Folio Dev's launcher tint) as an iOS
     * rounded square, drawn from the adaptive layers rather than through the device's icon mask.
     */
    fun artwork(context: Context, size: Int): androidx.compose.ui.graphics.ImageBitmap? = runCatching<androidx.compose.ui.graphics.ImageBitmap> {
        android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = android.graphics.Canvas(bitmap)
            listOf(background, foreground).mapNotNull(context::getDrawable).forEach { layer ->
                // Adaptive layers are 108dp with the visible icon in the middle 72dp.
                layer.setBounds(-size / 4, -size / 4, size * 5 / 4, size * 5 / 4); layer.draw(canvas)
            }
        }.asImageBitmap()
    }.getOrNull()

    companion object {
        /** Every alias class name starts with this, so Folio can recognize its own app entry. */
        const val ALIAS_PREFIX = "FolioSettingsApp"

        fun current(context: Context): AppIconChoice = entries.firstOrNull { choice ->
            val state = context.packageManager.getComponentEnabledSetting(ComponentName(context, "$FOLIO_CLASSES.${choice.alias}"))
            state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && choice == TEAL)
        } ?: TEAL

        fun set(context: Context, choice: AppIconChoice) {
            val pm = context.packageManager
            // Enable the new entry before disabling the old one, so there's never a moment with no app icon.
            pm.setComponentEnabledSetting(ComponentName(context, "$FOLIO_CLASSES.${choice.alias}"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            entries.filter { it != choice }.forEach {
                pm.setComponentEnabledSetting(ComponentName(context, "$FOLIO_CLASSES.${it.alias}"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
