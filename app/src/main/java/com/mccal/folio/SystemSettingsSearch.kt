package com.mccal.folio

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.text.Normalizer
import java.util.Locale

/** Public Android settings pages, with localized names and on-device search aliases. */
internal data class SystemSettingShortcut(val title: String, val action: String, val keywords: List<String>) {
    internal val searchText = normalizeSettingText(title + " " + keywords.joinToString(" "))
    internal val normalizedTitle = normalizeSettingText(title)
}

private val settingMarks = Regex("\\p{M}+")
private val settingSeparators = Regex("[^\\p{L}\\p{N}]+")
private fun normalizeSettingText(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKD)
    .replace(settingMarks, "").lowercase(Locale.ROOT).replace(settingSeparators, " ").trim()

private val settingNouns = setOf("settings", "setting", "preferences", "preference")
private val settingQueryWords = settingNouns + setOf("phone", "system", "android")
private val settingQueryFillers = setOf("show", "me", "open", "change", "the", "for", "and", "of", "my", "to", "turn", "on", "off")

/** Match all meaningful words; "settings for display" and "notification settings" stay entirely on the phone. */
internal fun searchSystemSettings(
    shortcuts: List<SystemSettingShortcut>, query: String, settingsLabel: String, limit: Int = 4,
): List<SystemSettingShortcut> {
    val normalized = normalizeSettingText(query)
    if (normalized.count { it.isLetterOrDigit() } < 2 || limit <= 0) return emptyList()
    val label = normalizeSettingText(settingsLabel)
    // This also handles languages that join the category and "Settings" without a space.
    val words = (if (label.isEmpty()) normalized else normalized.replace(label, " "))
        .split(' ').filter { it.isNotEmpty() && it !in settingQueryWords && it !in settingQueryFillers }
    if (words.isEmpty()) {
        val generic = normalized.split(' ').any { it in settingQueryWords || it == label } || normalized == label
        return if (generic) shortcuts.filter { it.action == Settings.ACTION_SETTINGS && it.normalizedTitle == label }.take(limit) else emptyList()
    }
    val phrase = words.joinToString(" ")
    val hits = shortcuts.filter { shortcut -> shortcut.action != Settings.ACTION_APP_SEARCH_SETTINGS && words.all { it in shortcut.searchText } }
        .sortedByDescending {
            when {
                it.normalizedTitle == phrase -> 2
                it.normalizedTitle.startsWith(phrase) -> 1
                else -> 0
            }
        }.take(limit)
    val asksForSettings = normalized.split(' ').any { it in settingNouns } || (label.isNotEmpty() && label in normalized)
    return if (hits.isEmpty() && asksForSettings) shortcuts.filter { it.action == Settings.ACTION_APP_SEARCH_SETTINGS }.take(limit) else hits
}

/** Try the requested page, then Android's Settings home. Some vendors do not export every settings action. */
internal fun openSystemSetting(context: Context, action: String): Boolean {
    for (candidate in listOf(action, Settings.ACTION_SETTINGS).distinct()) {
        try {
            context.startActivity(Intent(candidate).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (_: ActivityNotFoundException) {
            // The next public action is the fallback.
        } catch (_: SecurityException) {
            // A vendor may restrict a page to its own apps.
        }
    }
    return false
}

internal fun systemSettingShortcuts(context: Context) = listOf(
    SystemSettingShortcut(context.getString(R.string.wi_fi), Settings.ACTION_WIFI_SETTINGS, listOf("wifi", "wi fi", "wireless", "internet", "network")),
    SystemSettingShortcut(context.getString(R.string.bluetooth), Settings.ACTION_BLUETOOTH_SETTINGS, listOf("bluetooth", "pair", "headphones")),
    SystemSettingShortcut(context.getString(R.string.mobile_network), Settings.ACTION_NETWORK_OPERATOR_SETTINGS, listOf("cellular", "mobile", "data", "sim", "network", "roaming", "5g", "lte")),
    SystemSettingShortcut(context.getString(R.string.airplane_mode), Settings.ACTION_AIRPLANE_MODE_SETTINGS, listOf("airplane", "flight")),
    SystemSettingShortcut(context.getString(R.string.display_brightness), Settings.ACTION_DISPLAY_SETTINGS, listOf("display", "brightness", "screen", "dark", "dark mode", "night mode", "font", "text size", "screen timeout", "refresh rate", "adaptive brightness", "motion smoothness", "resolution")),
    SystemSettingShortcut(context.getString(R.string.sounds_vibration), Settings.ACTION_SOUND_SETTINGS, listOf("sound", "volume", "ringtone", "vibration", "vibrate", "silent", "mute", "ringer", "media volume")),
    SystemSettingShortcut(context.getString(R.string.notifications_title), if (android.os.Build.VERSION.SDK_INT >= 33) Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS else Settings.ACTION_SETTINGS, listOf("notification", "alerts", "badges", "app notifications")),
    SystemSettingShortcut(context.getString(R.string.battery), Intent.ACTION_POWER_USAGE_SUMMARY, listOf("battery", "power", "charging")),
    SystemSettingShortcut(context.getString(R.string.apps), Settings.ACTION_APPLICATION_SETTINGS, listOf("apps", "applications", "uninstall")),
    SystemSettingShortcut(context.getString(R.string.default_apps), Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, listOf("default", "home app", "browser", "assistant")),
    SystemSettingShortcut(context.getString(R.string.storage), Settings.ACTION_INTERNAL_STORAGE_SETTINGS, listOf("storage", "space")),
    SystemSettingShortcut(context.getString(R.string.location), Settings.ACTION_LOCATION_SOURCE_SETTINGS, listOf("location", "gps")),
    SystemSettingShortcut(context.getString(R.string.security_privacy), Settings.ACTION_SECURITY_SETTINGS, listOf("security", "privacy", "lock", "password", "fingerprint", "pin", "pattern", "face unlock", "biometrics", "find my device", "unknown apps")),
    SystemSettingShortcut(context.getString(R.string.accessibility), Settings.ACTION_ACCESSIBILITY_SETTINGS, listOf("accessibility", "talkback", "magnification", "color inversion", "color correction", "captions", "bold text", "high contrast", "remove animations", "hearing", "vision")),
    SystemSettingShortcut(context.getString(R.string.date_time), Settings.ACTION_DATE_SETTINGS, listOf("date", "time", "clock")),
    SystemSettingShortcut(context.getString(R.string.language_keyboard), Settings.ACTION_LOCALE_SETTINGS, listOf("language", "keyboard")),
    SystemSettingShortcut(context.getString(R.string.do_not_disturb), Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS, listOf("dnd", "do not disturb", "focus")),
    SystemSettingShortcut(context.getString(R.string.nfc_payments), Settings.ACTION_NFC_SETTINGS, listOf("nfc", "pay", "wallet", "contactless")),
    SystemSettingShortcut(context.getString(R.string.developer_options), Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, listOf("developer", "usb debugging")),
    SystemSettingShortcut(context.getString(R.string.about_phone), Settings.ACTION_DEVICE_INFO_SETTINGS, listOf("about", "phone", "software", "version", "model number", "android version", "build number", "imei", "serial")),
    SystemSettingShortcut(context.getString(R.string.hotspot), Settings.ACTION_WIRELESS_SETTINGS, listOf("hotspot", "tethering", "share internet", "mobile hotspot", "connections")),
    SystemSettingShortcut(context.getString(R.string.vpn), Settings.ACTION_VPN_SETTINGS, listOf("vpn", "virtual private network")),
    SystemSettingShortcut(context.getString(R.string.screen_mirroring), Settings.ACTION_CAST_SETTINGS, listOf("cast", "screen mirroring", "smart view", "mirror", "tv")),
    SystemSettingShortcut(context.getString(R.string.night_light), Settings.ACTION_NIGHT_DISPLAY_SETTINGS, listOf("night light", "blue light", "eye comfort", "warm", "night mode")),
    SystemSettingShortcut(context.getString(R.string.battery_saver), Settings.ACTION_BATTERY_SAVER_SETTINGS, listOf("battery saver", "power saving", "low power")),
    SystemSettingShortcut(context.getString(R.string.privacy_permissions), Settings.ACTION_PRIVACY_SETTINGS, listOf("privacy", "permissions", "backup", "reset")),
    SystemSettingShortcut(context.getString(R.string.lock_screen), Settings.ACTION_SECURITY_SETTINGS, listOf("lock screen", "screen lock", "pin", "pattern", "password")),
    SystemSettingShortcut(context.getString(R.string.settings_accounts), Settings.ACTION_SYNC_SETTINGS, listOf("accounts", "account", "sync", "google account", "sign in")),
    SystemSettingShortcut(context.getString(R.string.software_update), Settings.ACTION_SYSTEM_UPDATE_SETTINGS, listOf("update", "software update", "system update", "security patch")),
    SystemSettingShortcut(context.getString(R.string.screen_saver), Settings.ACTION_DREAM_SETTINGS, listOf("screen saver", "daydream", "standby")),
    SystemSettingShortcut(context.getString(R.string.wallpaper), Intent.ACTION_SET_WALLPAPER, listOf("wallpaper", "background")),
    SystemSettingShortcut(context.getString(R.string.settings), Settings.ACTION_SETTINGS, listOf("settings", "preferences")),
    SystemSettingShortcut(context.getString(R.string.search_phone_settings), Settings.ACTION_APP_SEARCH_SETTINGS, emptyList()),
)
