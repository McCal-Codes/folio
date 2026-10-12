package com.mccal.folio

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * What an app held over another app on Home asks for, with folders on drop and the 0.6.9 motion pass on (the Mockup Lab's
 * "Folder or make room", chosen 9 Oct 2026). Held over the middle of an app for [FOLDER_WAIT_MS], it asks for a folder: a plate
 * grows behind that app and nothing else moves. Held over its edge or a gap for [ROOM_WAIT_MS], the others make room. Passing
 * over an app asks for nothing, so moving across Home never makes a folder by accident. Without the pass, any app under the
 * finger means a folder, as before. The numbers are starting points to tune on the phone.
 */
internal object FolderOrRoom {
    /** The middle: within this share of the icon's width from its center (a circle 64% as wide as the icon). */
    const val MIDDLE = .32f
    const val FOLDER_WAIT_MS = 250L
    const val ROOM_WAIT_MS = 120L
    /** How much bigger than the icon the folder plate grows, and how small the app gets inside it. */
    const val PLATE_GROWTH = .18f
    const val ICON_INSIDE = .86f

    /** True when [held] (the carried icon's center) is over the middle of [icon]. */
    fun inMiddle(held: Offset?, icon: Rect?): Boolean =
        held != null && icon != null && !icon.isEmpty && (held - icon.center).getDistance() < icon.width * MIDDLE
}
