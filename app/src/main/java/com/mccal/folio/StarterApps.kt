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

/**
 * The starter apps topped up with the other apps of the phone, in the order given, until [capacity] places are filled, so a
 * tall screen starts with a full Home instead of a handful of apps and empty rows. Nothing is added when [capacity] is 0.
 */
internal fun fillHomePicks(starters: List<String>, apps: List<String>, capacity: Int): List<String> =
    if (starters.size >= capacity) starters else starters + apps.filter { it !in starters }.take(capacity - starters.size)

/** About how many app rows fit a window of this size on Home (the same fit Home measures once it is open), before Home has measured it. */
internal fun estimatedHomeRows(widthDp: Float, heightDp: Float): Int =
    homeGeometry(widthDp, heightDp, LayoutPreset(), labels = true, statusHeight = 0f, labelHeight = 20f, inLibrary = false,
        homeBottomSpace = 44f, railControls = 0, classScale = 1f, appRows = BASE_APP_ROWS, foldAtCenter = false, fillSpace = true)
        .fitAppRows.coerceIn(BASE_APP_ROWS, MAX_APP_ROWS)

/** How many rows [count] apps fill on Home's grid. */
internal fun homeRowsFor(count: Int): Int = if (count <= 0) 0 else (count + GRID_COLUMNS - 1) / GRID_COLUMNS
