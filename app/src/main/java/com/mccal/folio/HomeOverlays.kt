package com.mccal.folio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * What is open over Home: the context menu on an icon, the rename alert, an App Panel, an Icon Stack's fan or its
 * editor, a folder, the "which folder?" alert a new folder starts with, and the menu an empty cell opens.
 *
 * Each is the id of the thing it belongs to, or null when that overlay is closed — eight separate pieces of state
 * in [LauncherScreen] before this. They are saved, so a long press survives a fold or a rotation the way it always
 * did; nothing here decides which of them may be open at once, because nothing did before.
 */
@Stable
internal class HomeOverlays(
    menu: String? = null,
    rename: String? = null,
    panel: String? = null,
    stackFan: String? = null,
    stackEditor: String? = null,
    folder: String? = null,
    newFolder: String? = null,
    emptyCell: Int? = null,
) {
    /** The icon whose context menu is showing. */
    var menu by mutableStateOf(menu)

    /** The app being given a name of your own. */
    var rename by mutableStateOf(rename)

    /** The app whose App Panel is open. */
    var panel by mutableStateOf(panel)

    /** The Icon Stack anchor whose apps are fanned out, and the one whose contents are being chosen. */
    var stackFan by mutableStateOf(stackFan)
    var stackEditor by mutableStateOf(stackEditor)

    /** The open folder, and the app waiting to be put in one. */
    var folder by mutableStateOf(folder)
    var newFolder by mutableStateOf(newFolder)

    /** The empty Home cell whose menu is open. */
    var emptyCell by mutableStateOf(emptyCell)

    companion object {
        val Saver = listSaver<HomeOverlays, Any?>(
            save = { listOf(it.menu, it.rename, it.panel, it.stackFan, it.stackEditor, it.folder, it.newFolder, it.emptyCell) },
            restore = {
                HomeOverlays(it[0] as String?, it[1] as String?, it[2] as String?, it[3] as String?,
                    it[4] as String?, it[5] as String?, it[6] as String?, it[7] as Int?)
            },
        )
    }
}

@Composable
internal fun rememberHomeOverlays(): HomeOverlays = rememberSaveable(saver = HomeOverlays.Saver) { HomeOverlays() }
