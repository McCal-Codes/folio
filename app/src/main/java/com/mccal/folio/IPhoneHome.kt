package com.mccal.folio

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings

/** The apps on iPhone's first Home Screen, as roles an Android app can fill. */
internal enum class IPhoneApp(val label: String) {
    FACETIME("FaceTime"), CALENDAR("Calendar"), PHOTOS("Photos"), CAMERA("Camera"),
    MAIL("Mail"), NOTES("Notes"), CLOCK("Clock"), MAPS("Maps"),
    SIRI("Siri"), TV("TV"), HEALTH("Health"), REMINDERS("Reminders"),
    SHORTCUTS("Shortcuts"), APP_STORE("App Store"), WALLET("Wallet"), SETTINGS("Settings"),
    PHONE("Phone"), SAFARI("Safari"), MESSAGES("Messages"), MUSIC("Music"),
}

/** iPhone Duo's first Home Screen app rows (under its two widgets), left to right, as shown in Apple's hands-on footage. */
internal val IPHONE_HOME_ROWS: List<List<IPhoneApp>> = listOf(
    listOf(IPhoneApp.FACETIME, IPhoneApp.CALENDAR, IPhoneApp.PHOTOS, IPhoneApp.CAMERA),
    listOf(IPhoneApp.MAIL, IPhoneApp.NOTES, IPhoneApp.CLOCK, IPhoneApp.MAPS),
    listOf(IPhoneApp.SIRI, IPhoneApp.TV, IPhoneApp.HEALTH, IPhoneApp.REMINDERS),
    listOf(IPhoneApp.SHORTCUTS, IPhoneApp.APP_STORE, IPhoneApp.WALLET, IPhoneApp.SETTINGS),
)
internal val IPHONE_DOCK = listOf(IPhoneApp.PHONE, IPhoneApp.SAFARI, IPhoneApp.MESSAGES, IPhoneApp.MUSIC)

/**
 * The installed app for each iPhone role: the phone's default handler where Android has one (camera, browser,
 * dialer, texting, calendar…), otherwise a known equivalent (Play Store, Wallet, Samsung/Google apps).
 * Roles with no match stay empty, so nothing is made up.
 */
internal fun resolveIPhoneApps(context: Context, apps: List<AppEntry>, messagesApp: String?): Map<IPhoneApp, String> {
    val pm = context.packageManager
    val personal = apps.filter { !it.isWork && it.available }
    fun entryFor(pkg: String?) = pkg?.let { p -> personal.firstOrNull { it.component.packageName == p }?.id }
    fun defaultFor(intent: Intent): String? {
        val info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        // "android" is the chooser: no default set, so take the first app that can handle it.
        val pkg = info?.packageName?.takeUnless { it == "android" }
            ?: pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.firstOrNull { entryFor(it) != null }
        return entryFor(pkg)
    }
    fun category(name: String) = defaultFor(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, name))
    fun firstOf(vararg packages: String) = packages.firstNotNullOfOrNull { entryFor(it) }
    fun labelled(vararg labels: String) = labels.firstNotNullOfOrNull { l -> personal.firstOrNull { it.label.equals(l, ignoreCase = true) }?.id }
    // Each role gets the app that fills it, and an app fills one role only (see [assignRoles]).
    return assignRoles(listOf(
        IPhoneApp.FACETIME to (firstOf("com.google.android.apps.tachyon")),
        IPhoneApp.CALENDAR to (category(Intent.CATEGORY_APP_CALENDAR)),
        IPhoneApp.PHOTOS to (category(Intent.CATEGORY_APP_GALLERY)),
        IPhoneApp.CAMERA to (defaultFor(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))),
        IPhoneApp.MAIL to (category(Intent.CATEGORY_APP_EMAIL)),
        IPhoneApp.NOTES to (firstOf(*CcControl.NOTE_APPS.toTypedArray())),
        IPhoneApp.CLOCK to (defaultFor(Intent(AlarmClock.ACTION_SHOW_ALARMS))),
        IPhoneApp.MAPS to (category(Intent.CATEGORY_APP_MAPS)),
        IPhoneApp.SIRI to (defaultFor(Intent(Intent.ACTION_ASSIST)) ?: firstOf("com.google.android.apps.bard")),
        IPhoneApp.TV to (labelled("TV", "Apple TV") ?: firstOf("com.google.android.videos")),
        IPhoneApp.HEALTH to (firstOf("com.sec.android.app.shealth", "com.google.android.apps.fitness")),
        IPhoneApp.REMINDERS to (firstOf("com.samsung.android.app.reminder", "com.google.android.apps.tasks")),
        IPhoneApp.SHORTCUTS to (firstOf("com.samsung.android.app.routines")),
        IPhoneApp.APP_STORE to (firstOf("com.android.vending")),
        IPhoneApp.WALLET to (firstOf(*CcControl.WALLETS.toTypedArray())),
        IPhoneApp.SETTINGS to (defaultFor(Intent(Settings.ACTION_SETTINGS))),
        IPhoneApp.PHONE to (defaultFor(Intent(Intent.ACTION_DIAL))),
        IPhoneApp.SAFARI to (defaultFor(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE))),
        IPhoneApp.MESSAGES to (entryFor(messagesApp) ?: entryFor(android.provider.Telephony.Sms.getDefaultSmsPackage(context))),
        IPhoneApp.MUSIC to (category(Intent.CATEGORY_APP_MUSIC) ?: firstOf("com.spotify.music", "com.google.android.apps.youtube.music")),
    ))
}

/**
 * Gives each role its app, in order, skipping a role with no app and an app already given to an earlier role. A plain
 * function, not a local one inside the builder: a local `put` of the same name called itself and overflowed the stack the
 * first time any role found an app, which is what made Arrange Like iPhone crash.
 */
internal fun assignRoles(candidates: List<Pair<IPhoneApp, String?>>): Map<IPhoneApp, String> {
    val roles = LinkedHashMap<IPhoneApp, String>()
    for ((role, id) in candidates) if (id != null && id !in roles.values) roles[role] = id
    return roles
}

/**
 * Puts [resolved] apps in iPhone's spots: the four app rows of page 1 (under its widget row) and the dock.
 * Chosen apps move from wherever they were; apps already in those spots move to the first free cells after
 * page 1, so nothing leaves Home (skipping rows pages don't show with [appRows] app rows). Apps inside folders, widgets
 * and spots without a match are left alone.
 */
internal fun arrangeLikeIPhone(layout: HomeLayout, resolved: Map<IPhoneApp, String>, appRows: Int = MAX_APP_ROWS): HomeLayout {
    val inFolders = layout.folders.flatMap { it.appIds }.toSet()
    val wanted = resolved.filterValues { it !in inFolders }
    val wantedIds = wanted.values.toSet()
    val covered = layout.widgetPlacements.filter { it.page >= 0 }.flatMap { it.coveredIndices() }.toSet()
    val slots = layout.slots.map { it?.takeUnless(wantedIds::contains) }.toMutableList()
    while (slots.size < HOME_CELLS) slots += null
    val dock = layout.dock.map { it?.takeUnless(wantedIds::contains) }.toMutableList()
    val displaced = mutableListOf<String>()
    val unplaced = mutableListOf<String>()
    IPHONE_HOME_ROWS.forEachIndexed { r, row ->
        row.forEachIndexed { c, app ->
            val id = wanted[app] ?: return@forEachIndexed
            val index = homeCellIndex(0, (r + 2) * GRID_COLUMNS + c)
            if (index in covered) { unplaced += id; return@forEachIndexed }
            slots[index]?.let(displaced::add)
            slots[index] = id
        }
    }
    IPHONE_DOCK.forEachIndexed { i, app ->
        val id = wanted[app] ?: return@forEachIndexed
        if (i >= dock.size) { unplaced += id; return@forEachIndexed }
        dock[i]?.let(displaced::add)
        dock[i] = id
    }
    (displaced + unplaced).forEach { id ->
        var index = HOME_CELLS
        while (true) {
            while (slots.size <= index) slots += null
            if (slots[index] == null && index !in covered && homeCellShown(index, appRows)) { slots[index] = id; break }
            index++
        }
    }
    return layout.copy(slots = slots, dock = dock, leadingSlots = layout.leadingSlots.map { it?.takeUnless(wantedIds::contains) })
}
