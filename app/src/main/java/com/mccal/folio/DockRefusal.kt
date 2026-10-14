package com.mccal.folio

/**
 * Why the dock won't take an app, so the dock's app picker can say so instead of only greying the app out. Null from
 * [dockRefusal] means it will: the same answer as [canPlaceInDock], one reason per way that can say no.
 */
sealed interface DockRefusal {
    /** Not something the dock holds at all: a blank id or a folder. */
    data object NotAnApp : DockRefusal
    /** The app already lives in a folder, and an app sits in one place on Home at a time. */
    data class InFolder(val folderId: String) : DockRefusal
    /** Every dock position is taken. */
    data object Full : DockRefusal
}

fun dockRefusal(layout: HomeLayout, id: String): DockRefusal? {
    if (id.isBlank() || isReservedFolderId(id)) return DockRefusal.NotAnApp
    layout.folders.firstOrNull { id in it.appIds }?.let { return DockRefusal.InFolder(it.id) }
    val room = id in layout.dock || layout.dock.any { it == null } || layout.dock.size < MAX_DOCK_SLOTS
    return if (room) null else DockRefusal.Full
}
