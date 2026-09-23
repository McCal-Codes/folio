package com.mccal.folio

/** JSON keys in the saved launcher state that the everywhere overlay (another component) also reads. */
internal object SettingKeys {
    const val PREFS = "launcher"
    const val STATE = "state"
    /** Whether Home's window shows Android's wallpaper, saved on its own and at once (see applyWallpaperWindow). */
    const val SYSTEM_WALLPAPER = "systemWallpaperWindow"
    const val DOCK = "dock"
    const val LEFT_HANDED = "leftHanded"
    const val DOCK_EVERYWHERE = "dockEverywhere"
    const val ISLAND_EVERYWHERE = "islandEverywhere"
    const val ISLAND_EVENTS_OFF = "islandEventsOff"
    /** Keep the island out of full-screen video and games, and optionally out of landscape. */
    const val ISLAND_HIDE_FULL_SCREEN = "islandHideFullScreen"
    const val ISLAND_HIDE_LANDSCAPE = "islandHideLandscape"
    /** Island message cards skip notifications Android will already show as a pop-up. */
    const val MESSAGES_AVOID_DOUBLE = "messagesAvoidDouble"
    /** Brief pop-ups › Other Notifications: any app's new notifications in the island (off by default). */
    const val ISLAND_ALERTS = "islandAlerts"
    /** Apps turned off in that list (package names). */
    const val ISLAND_ALERT_APPS_OFF = "islandAlertAppsOff"
    /** Buttons in Every App: a large Back / Home / Recents bar drawn over other apps. */
    const val BUTTON_BAR = "buttonBar"
    const val BUTTON_BAR_HEIGHT = "buttonBarHeight"
    const val BUTTON_BAR_WIDTH = "buttonBarWidth"
    const val BUTTON_BAR_ANDROID_ORDER = "buttonBarAndroidOrder"
    const val BUTTON_BAR_LIGHT = "buttonBarLight"
    const val BUTTON_BAR_FADE = "buttonBarFade"
}
