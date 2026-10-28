package com.mccal.folio

/**
 * The apps first setup ticks for Home: one per everyday job, the first one found on this phone, in this order. They are
 * matched by package name against the apps Folio already lists, so nothing needs permission to see other apps and nothing
 * is guessed from names. A phone with none of them gets an empty suggestion and the person ticks their own.
 */
internal val STARTER_APP_GROUPS: List<List<String>> = listOf(
    listOf("com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer", "com.android.contacts"),
    listOf("com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms"),
    listOf("com.sec.android.app.camera", "com.google.android.GoogleCamera", "com.android.camera", "com.android.camera2"),
    listOf("com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox", "com.brave.browser"),
    listOf("com.google.android.apps.photos", "com.sec.android.gallery3d"),
    listOf("com.google.android.apps.maps"),
    listOf("com.google.android.calendar", "com.samsung.android.calendar"),
    listOf("com.android.settings"),
    listOf("com.google.android.youtube"),
    listOf("com.google.android.gm", "com.samsung.android.email.provider"),
)

/**
 * The ids of the apps to tick, from [installed] pairs of (app id, package name): at most [max], one per group, groups in
 * order, the first package of a group that is installed.
 */
internal fun starterAppIds(installed: List<Pair<String, String>>, max: Int = 8): List<String> {
    val byPackage = LinkedHashMap<String, String>()
    installed.forEach { (id, pkg) -> byPackage.putIfAbsent(pkg, id) }
    return STARTER_APP_GROUPS.mapNotNull { group -> group.firstNotNullOfOrNull { byPackage[it] } }.take(max)
}

/** How many rows [count] apps fill on Home's grid. */
internal fun homeRowsFor(count: Int): Int = if (count <= 0) 0 else (count + GRID_COLUMNS - 1) / GRID_COLUMNS
