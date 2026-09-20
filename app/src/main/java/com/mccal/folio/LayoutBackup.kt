package com.mccal.folio

import android.content.ComponentName
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 3: 36-cell Home pages (More rows); versions 1–2 had 24-cell pages and are moved on import. */
const val LAYOUT_BACKUP_VERSION = 3
const val MAX_LAYOUT_BACKUP_BYTES = 2 * 1024 * 1024
private const val MAX_BACKUP_PAGES = 100

data class BackupWidgetDescriptor(
    val slot: Int,
    val providerComponent: String?,
    val userSerial: Long?,
    val title: String,
    val profileLabel: String,
    val builtinId: Int? = null,
    val isWork: Boolean = false,
)

data class LayoutImportPreview(
    val layout: HomeLayout,
    val missingApps: List<String>,
    val profileIssues: List<String>,
    val appCount: Int,
    val folderCount: Int,
    val widgetCount: Int,
    val compact: LayoutPreset,
    val expanded: LayoutPreset,
    val labels: Boolean,
    val googleSearch: Boolean,
    val verticalStatus: Boolean,
    /** Names people typed themselves. They exist nowhere else on the phone, so a backup that left them out lost them. */
    val appNames: Map<String, String> = emptyMap(),
)

fun layoutBackupScope(context: Context): String {
    val prefs = context.getSharedPreferences("layout_backup_identity", Context.MODE_PRIVATE)
    return prefs.getString("scope", null) ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("scope", it).apply() }
}

fun encodeLayoutBackup(state: LauncherState, widgetDescriptors: List<BackupWidgetDescriptor>, sourceScope: String): String {
    require(sourceScope.isNotBlank())
    require(state.leadingSlots.size == HOME_CELLS) { "Unfolded-only page must contain exactly $HOME_CELLS cells" }
    val descriptorBySlot = widgetDescriptors.associateBy(BackupWidgetDescriptor::slot)
    val apps = JSONArray().also { array -> state.apps.forEach { app -> array.put(JSONObject()
        .put("id", app.id).put("label", app.label).put("component", app.component.flattenToString())
        .put("userSerial", app.userSerial).put("profileLabel", app.profileLabel).put("work", app.isWork)) } }
    val folders = JSONArray().also { array -> state.folders.forEach { folder -> array.put(JSONObject()
        .put("id", folder.id).put("title", folder.title).put("apps", JSONArray(folder.appIds))) } }
    val widgets = JSONArray().also { array -> state.widgetPlacements.forEach { placement ->
        val saved = state.widgetRestores.firstOrNull { it.slot == placement.slot }
        val descriptor = descriptorBySlot[placement.slot]
        val item = JSONObject().put("slot", placement.slot).put("page", placement.page)
            .put("column", placement.column).put("row", placement.row).put("spanX", placement.spanX).put("spanY", placement.spanY)
        when {
            placement.id in setOf(CLOCK_WIDGET, DATE_WIDGET, INFO_WIDGET, UP_NEXT_WIDGET, SUGGESTIONS_WIDGET, BIG_CLOCK_WIDGET) -> item.put("builtinId", placement.id)
            saved != null -> item.put("provider", saved.providerComponent).put("userSerial", saved.userSerial)
                .put("title", saved.title).put("profileLabel", saved.profileLabel).put("work", saved.isWork)
                .put("sourceScope", exportedWidgetScope(saved, sourceScope))
            descriptor?.providerComponent != null && descriptor.userSerial != null -> item.put("provider", descriptor.providerComponent)
                .put("userSerial", descriptor.userSerial).put("title", descriptor.title).put("profileLabel", descriptor.profileLabel)
                .put("work", descriptor.isWork).put("sourceScope", sourceScope)
            else -> error("Widget ${placement.slot} has no portable provider descriptor")
        }
        array.put(item)
    } }
    fun preset(value: LayoutPreset) = JSONObject().put("iconSize", value.iconSize).put("rowGap", value.rowGap)
        .put("dockWidth", value.dockWidth).put("dockPosition", value.dockPosition).put("dockAlignToGrid", value.dockAlignToGrid)
        .put("dockPlacement", value.dockPlacement.name).put("statusAlignToGrid", value.statusAlignToGrid).put("statusPosition", value.statusPosition)
        .put("columnGap", value.columnGap).put("dockSpacing", value.dockSpacing).put("widgetScale", value.widgetScale).put("pageTop", value.pageTop)
    return JSONObject().put("version", LAYOUT_BACKUP_VERSION).put("sourceScope", sourceScope).put("apps", apps)
        .put("homeSlots", JSONArray(state.homeSlots)).put("leadingSlots", JSONArray(state.leadingSlots))
        .put("dock", JSONArray(state.dock)).put("folders", folders).put("widgets", widgets)
        .put("labels", state.labels).put("googleSearch", state.googleSearch).put("verticalStatus", state.verticalStatus)
        .put("compact", preset(state.compact)).put("expanded", preset(state.expanded))
        .put("appNames", JSONObject().apply { state.appNames.forEach { (id, name) -> put(id, name) } })
        .toString(2)
}

internal fun exportedWidgetScope(restore: WidgetRestore, currentScope: String) = restore.sourceScope ?: currentScope

fun decodeLayoutBackup(raw: String, currentApps: List<AppEntry>, currentProfiles: List<AppProfile>, currentScope: String): LayoutImportPreview {
    require(raw.toByteArray(Charsets.UTF_8).size <= MAX_LAYOUT_BACKUP_BYTES) { "Layout backup is larger than 2 MB" }
    val root = JSONObject(raw)
    val version = root.strictInt("version")
    require(version in 1..LAYOUT_BACKUP_VERSION) { "Unsupported layout backup version" }
    val sourceScope = root.getString("sourceScope").also { require(it.isNotBlank()) }
    val sameScope = sourceScope == currentScope
    val appMetadata = root.getJSONArray("apps").let { array -> List(array.length()) { index ->
        val item = array.getJSONObject(index)
        val id = item.getString("id")
        val identity = parseProfileAppId(id) ?: error("Invalid app identity")
        require(item.getString("component") == identity.component)
        require(ComponentName.unflattenFromString(identity.component) != null)
        val serial = item.strictLong("userSerial")
        require(serial >= 0 && item.getString("label").isNotBlank() && item.getString("profileLabel").isNotBlank())
        if (item.strictBoolean("work")) require(identity.userSerial == serial) else require(identity.userSerial == null)
        id to item.getString("label")
    } }.also { entries -> require(entries.map { it.first }.distinct().size == entries.size) }.toMap()
    val available = currentApps.filter { !it.isWork || sameScope }.mapTo(mutableSetOf(), AppEntry::id)
    val missing = linkedSetOf<String>()
    fun importedApp(id: String?): String? {
        if (id == null) return null
        require(id in appMetadata) { "Layout references an app without metadata" }
        return id.takeIf { it in available } ?: run { missing += "$id (${appMetadata.getValue(id)})"; null }
    }
    // Versions 1–2 have 24-cell pages: the same cells keep their place on today's pages.
    val legacyGrid = version < 3
    val pageCells = if (legacyGrid) LEGACY_HOME_CELLS else HOME_CELLS
    val slotsArray = root.getJSONArray("homeSlots")
    require(slotsArray.length() <= pageCells * MAX_BACKUP_PAGES)
    val rawSlots = List(slotsArray.length()) { index -> if (slotsArray.isNull(index)) null else slotsArray.getString(index) }
        .let { if (legacyGrid) migrateLegacyHomeSlots(it) else it }
    val rawLeadingSlots = if (version == 1) List(HOME_CELLS) { null } else {
        val array = root.getJSONArray("leadingSlots")
        require(array.length() == pageCells) { "Unfolded-only page must contain exactly $pageCells cells" }
        migrateLegacyLeadingSlots(List(pageCells) { index -> if (array.isNull(index)) null else array.getString(index) })
    }
    val folderArray = root.getJSONArray("folders")
    val importedFolders = List(folderArray.length()) { index ->
        val item = folderArray.getJSONObject(index)
        val children = item.getJSONArray("apps")
        FolderEntry(item.getString("id"), item.getString("title"), List(children.length()) { children.getString(it) })
    }
    require(importedFolders.map(FolderEntry::id).distinct().size == importedFolders.size)
    require(importedFolders.flatMap(FolderEntry::appIds).distinct().size == importedFolders.sumOf { it.appIds.size })
    importedFolders.forEach { folder ->
        require(isFolderId(folder.id) && folder.title.isNotBlank() && folder.appIds.size >= 2)
        require(folder.appIds.none(::isReservedFolderId))
        require((rawSlots + rawLeadingSlots).count(folder.id::equals) == 1)
    }
    val dockArray = root.getJSONArray("dock")
    require(dockArray.length() == 4)
    val rawDock = List(4) { index -> if (dockArray.isNull(index)) null else dockArray.getString(index) }
    val surfaceApps = (rawSlots + rawLeadingSlots).filterNotNull().filterNot(::isReservedFolderId) + rawDock.filterNotNull() +
        importedFolders.flatMap(FolderEntry::appIds)
    require(surfaceApps.distinct().size == surfaceApps.size) { "An app shortcut appears more than once" }
    val folderResults = importedFolders.associate { folder -> folder.id to folder.copy(appIds = folder.appIds.mapNotNull(::importedApp)) }
    val folders = folderResults.values.filter { it.appIds.size >= 2 }
    val slots = rawSlots.map { value -> when {
        value == null -> null
        isReservedFolderId(value) -> folderResults[value]?.let { folder -> when (folder.appIds.size) { 0 -> null; 1 -> folder.appIds.single(); else -> folder.id } }
            ?: error("Orphan folder reference")
        else -> importedApp(value)
    } }
    val leadingSlots = rawLeadingSlots.map { value -> when {
        value == null -> null
        isReservedFolderId(value) -> folderResults[value]?.let { folder -> when (folder.appIds.size) { 0 -> null; 1 -> folder.appIds.single(); else -> folder.id } }
            ?: error("Orphan folder reference")
        else -> importedApp(value)
    } }
    val dock = rawDock.map { value -> value?.also { require(!isReservedFolderId(it)) }?.let(::importedApp) }
    var layout = HomeLayout(slots.dropLastWhile { it == null }, dock, folders = folders, leadingSlots = leadingSlots)
    val profileSerials = currentProfiles.mapTo(mutableSetOf(), AppProfile::userSerial)
    val profileIssues = linkedSetOf<String>()
    val widgetArray = root.getJSONArray("widgets")
    require(widgetArray.length() <= 500)
    val widgetSlots = mutableSetOf<Int>()
    repeat(widgetArray.length()) { index ->
        val item = widgetArray.getJSONObject(index)
        val slot = item.strictInt("slot")
        require(widgetSlots.add(slot)) { "Widget slots must be unique" }
        val builtin = if (item.has("builtinId")) item.strictInt("builtinId") else null
        val provider = item.optString("provider").takeIf(String::isNotBlank)
        val id = if (builtin != null) {
            require(builtin in setOf(CLOCK_WIDGET, DATE_WIDGET, INFO_WIDGET, UP_NEXT_WIDGET, SUGGESTIONS_WIDGET, BIG_CLOCK_WIDGET)); builtin
        } else NEEDS_BINDING_WIDGET
        val placement = WidgetPlacement(slot, id, item.strictInt("page"), item.strictInt("column"), item.strictInt("row"),
            item.strictInt("spanX"), item.strictInt("spanY")).let { if (legacyGrid) migrateLegacyWidgetPlacement(it) else it }
        require(validBackupPlacement(placement) && layout.widgetPlacements.none { backupOverlaps(it, placement) })
        require(placement.coveredIndices().none { layout.slotAt(it) != null })
        val restore = if (id == NEEDS_BINDING_WIDGET) {
            require(provider != null && ComponentName.unflattenFromString(provider) != null)
            val savedSerial = item.strictLong("userSerial"); require(savedSerial >= 0)
            val title = item.getString("title"); val profileLabel = item.getString("profileLabel")
            require(title.isNotBlank() && profileLabel.isNotBlank())
            val work = item.strictBoolean("work")
            val widgetScope = item.optString("sourceScope").takeIf { it.isNotBlank() } ?: sourceScope
            val serial = if (work) savedSerial else currentProfiles.firstOrNull { it.isPersonal }?.userSerial ?: savedSerial
            if ((work && widgetScope != currentScope) || serial !in profileSerials) profileIssues += "$title ($profileLabel profile requires explicit mapping)"
            WidgetRestore(slot, provider, serial, title, profileLabel, work, widgetScope)
        } else null
        layout = layout.copy(widgetPlacements = (layout.widgetPlacements + placement).sortedBy { it.slot },
            widgetRestores = layout.widgetRestores + listOfNotNull(restore))
    }
    fun preset(key: String): LayoutPreset {
        val item = root.getJSONObject(key)
        val loaded = LayoutPreset(item.strictFloat("iconSize"), item.strictFloat("rowGap"),
            item.strictFloat("dockWidth"), item.strictFloat("dockPosition"), item.strictBoolean("dockAlignToGrid"),
            // Added in 0.6.5: older backups don't have them.
            DockPlacement.parse(item.optString("dockPlacement")), item.optBoolean("statusAlignToGrid", true),
            item.optDouble("statusPosition", 0.0).toFloat(),
            // Added with More rows.
            item.optDouble("columnGap", DEFAULT_COLUMN_GAP.toDouble()).toFloat(), item.optDouble("dockSpacing", 0.0).toFloat(),
            item.optDouble("widgetScale", 1.0).toFloat(), item.optBoolean("pageTop", false))
        require(loaded == loaded.sanitized()) { "Invalid layout preset" }
        return loaded
    }
    // Validate settings eagerly even though HomeLayout contains placement data only.
    val compact = preset("compact"); val expanded = preset("expanded")
    val labels = root.strictBoolean("labels"); val googleSearch = root.strictBoolean("googleSearch")
    val verticalStatus = root.strictBoolean("verticalStatus")
    // Written since 0.6.5; a backup made before that simply has none, and the names already on the phone stay.
    val appNames = root.optJSONObject("appNames")?.let { o ->
        o.keys().asSequence().mapNotNull { id -> o.optString(id).takeIf { it.isNotBlank() }?.let { id to it.take(60) } }.toMap()
    }.orEmpty()
    return LayoutImportPreview(layout, missing.toList(), profileIssues.toList(),
        appCount = (slots + leadingSlots).count { it != null && !isReservedFolderId(it) } +
            dock.count { it != null } + folders.sumOf { it.appIds.size },
        folderCount = folders.size, widgetCount = layout.widgetPlacements.size,
        compact = compact, expanded = expanded, labels = labels, googleSearch = googleSearch, verticalStatus = verticalStatus,
        appNames = appNames)
}

internal fun validBackupPlacement(value: WidgetPlacement): Boolean {
    val base = value.slot in 0..10_000 && value.page in -1..99 && value.column >= 0 && value.row >= 0 &&
        value.spanX in 1..GRID_COLUMNS && value.spanY in 1..GRID_ROWS && value.column + value.spanX <= GRID_COLUMNS
    val inside = value.row + value.spanY <= GRID_ROWS
    val overflow = value.page > 0 && value.slot / 3 == value.page && value.slot % 3 == 2 && value.column == 0 &&
        value.row == GRID_ROWS && value.spanX == GRID_COLUMNS && value.spanY == 4
    return base && (inside || overflow)
}

private fun backupOverlaps(a: WidgetPlacement, b: WidgetPlacement) = a.page == b.page &&
    a.column < b.column + b.spanX && b.column < a.column + a.spanX &&
    a.row < b.row + b.spanY && b.row < a.row + a.spanY

private fun JSONObject.strictInt(key: String): Int {
    val number = get(key) as? Number ?: error("$key must be an integer")
    val value = number.toDouble()
    require(value.isFinite() && value % 1.0 == 0.0 && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble())
    return value.toInt()
}

private fun JSONObject.strictLong(key: String): Long {
    val number = get(key) as? Number ?: error("$key must be an integer")
    val text = number.toString()
    return text.toLongOrNull()?.takeIf { it >= 0 } ?: error("$key must be a non-negative integer")
}

private fun JSONObject.strictFloat(key: String): Float {
    val number = get(key) as? Number ?: error("$key must be a number")
    return number.toFloat().takeIf(Float::isFinite) ?: error("$key must be finite")
}

private fun JSONObject.strictBoolean(key: String) = get(key) as? Boolean ?: error("$key must be a boolean")
