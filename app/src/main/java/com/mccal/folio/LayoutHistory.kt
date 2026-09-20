package com.mccal.folio

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** One saved Home layout: when, why, and the layout itself (apps, folders, widgets, dock, pages). */
data class LayoutSnapshot(val time: Long, val reason: String, val layout: HomeLayout)

/**
 * Layout History (Beta, after Icon Restore): Folio saves Home automatically before big changes (a restored backup,
 * Arrange Like iPhone, restoring an older layout) and on request, so any of those can be undone later. Stored on
 * the phone only; the newest [MAX] are kept.
 */
internal object LayoutHistory {
    const val MAX = 10
    private const val PREFS = "layout_history"
    private const val KEY = "snapshots"
    val snapshots = MutableStateFlow<List<LayoutSnapshot>>(emptyList())
    private var loaded = false

    fun load(context: Context): List<LayoutSnapshot> {
        if (!loaded) {
            loaded = true
            snapshots.value = decode(context.getSharedPreferences(PREFS, 0).getString(KEY, null))
        }
        return snapshots.value
    }

    fun add(context: Context, reason: String, layout: HomeLayout, now: Long = System.currentTimeMillis()) {
        val list = load(context)
        // Nothing new to save when the latest snapshot already has this layout.
        if (list.firstOrNull()?.layout == layout) return
        save(context, (listOf(LayoutSnapshot(now, reason, layout)) + list).take(MAX))
    }

    fun remove(context: Context, snapshot: LayoutSnapshot) = save(context, load(context) - snapshot)

    fun clear(context: Context) = save(context, emptyList())

    private fun save(context: Context, list: List<LayoutSnapshot>) {
        snapshots.value = list
        context.getSharedPreferences(PREFS, 0).edit().putString(KEY, encode(list)).apply()
    }

    fun encode(list: List<LayoutSnapshot>): String = JSONArray().also { array ->
        list.forEach { s -> array.put(JSONObject().put("time", s.time).put("reason", s.reason).put("layout", encodeLayout(s.layout))) }
    }.toString()

    fun decode(raw: String?): List<LayoutSnapshot> = runCatching {
        val array = JSONArray(raw ?: return emptyList())
        // Each snapshot is read on its own: one entry a later build can't parse used to take the other nine with
        // it, and the next automatic snapshot wrote that loss back to disk.
        (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                LayoutSnapshot(o.getLong("time"), o.optString("reason"), decodeLayout(o.getJSONObject("layout")))
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    private fun ids(list: List<String?>) = JSONArray().also { a -> list.forEach { a.put(it ?: JSONObject.NULL) } }
    private fun idList(a: JSONArray?) = if (a == null) emptyList() else List(a.length()) { a.optString(it).takeIf { s -> !a.isNull(it) && s.isNotBlank() } }

    fun encodeLayout(l: HomeLayout): JSONObject = JSONObject().put("pageCells", HOME_CELLS)
        .put("slots", ids(l.slots)).put("leadingSlots", ids(l.leadingSlots)).put("dock", ids(l.dock)).put("minPages", l.minPages)
        .put("widgets", JSONArray().also { a -> l.widgetPlacements.forEach { w ->
            a.put(JSONObject().put("slot", w.slot).put("id", w.id).put("page", w.page).put("column", w.column).put("row", w.row)
                .put("spanX", w.spanX).put("spanY", w.spanY)) } })
        .put("folders", JSONArray().also { a -> l.folders.forEach { f ->
            a.put(JSONObject().put("id", f.id).put("title", f.title).put("apps", JSONArray(f.appIds))) } })
        .put("restores", JSONArray().also { a -> l.widgetRestores.forEach { r ->
            a.put(JSONObject().put("slot", r.slot).put("provider", r.providerComponent).put("userSerial", r.userSerial).put("title", r.title)
                .put("profileLabel", r.profileLabel).put("work", r.isWork).put("scope", r.sourceScope ?: JSONObject.NULL)) } })

    fun decodeLayout(o: JSONObject): HomeLayout {
        fun <T> objects(key: String, read: (JSONObject) -> T): List<T> = o.optJSONArray(key)?.let { a -> List(a.length()) { read(a.getJSONObject(it)) } }.orEmpty()
        // Snapshots from before More rows have no page size: their pages had 24 cells.
        val legacy = o.optInt("pageCells", LEGACY_HOME_CELLS) == LEGACY_HOME_CELLS
        return HomeLayout(
            slots = idList(o.optJSONArray("slots")).let { if (legacy) migrateLegacyHomeSlots(it) else it },
            dock = idList(o.optJSONArray("dock")).let { d -> List(4) { d.getOrNull(it) } },
            widgetPlacements = objects("widgets") { w -> WidgetPlacement(w.getInt("slot"), w.getInt("id"), w.getInt("page"), w.getInt("column"),
                w.getInt("row"), w.getInt("spanX"), w.getInt("spanY")).let { if (legacy) migrateLegacyWidgetPlacement(it) else it } },
            folders = objects("folders") { f -> FolderEntry(f.getString("id"), f.optString("title"),
                f.optJSONArray("apps")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()) },
            widgetRestores = objects("restores") { r -> WidgetRestore(r.getInt("slot"), r.getString("provider"), r.getLong("userSerial"),
                r.optString("title"), r.optString("profileLabel"), r.optBoolean("work"), r.optString("scope").takeIf { !r.isNull("scope") && it.isNotBlank() }) },
            leadingSlots = idList(o.optJSONArray("leadingSlots")).let { s -> List(HOME_CELLS) { s.getOrNull(it) } },
            minPages = o.optInt("minPages", 1),
        )
    }
}

/**
 * Recent-app dots under dock icons (Beta, after Lynx 2's indicators). Android doesn't tell launchers which apps are
 * running, so this uses Usage Access: apps opened in the last [WINDOW_MS].
 */
internal object RecentUse {
    const val WINDOW_MS = 60L * 60 * 1000

    fun packages(context: Context, now: Long = System.currentTimeMillis()): Set<String> = runCatching {
        if (!Suggestions.hasUsageAccess(context)) return emptySet()
        val usage = context.getSystemService(android.app.usage.UsageStatsManager::class.java)
        usage.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_BEST, now - WINDOW_MS, now)
            .filter { it.lastTimeUsed >= now - WINDOW_MS && it.totalTimeInForeground > 0 && it.packageName != context.packageName }
            .map { it.packageName }.toSet()
    }.getOrDefault(emptySet())
}

/** Packages to mark with a dot in the dock (empty when the Beta is off). */
internal val LocalRecentPackages = androidx.compose.runtime.staticCompositionLocalOf { emptySet<String>() }
