package com.mccal.folio

/**
 * The dock pulled out over another app (Dock handle on the rail edge): how big its apps are and how many fit whole.
 * It follows Home's Dock › Size, and like Home's Side Bar it shows only the places that fit, never half an icon, so it
 * never runs off a short screen. The apps it leaves out stay in the dock.
 */
internal data class OverlayDockPlan(val icon: Float, val shown: Int, val width: Float)

private const val OVERLAY_DOCK_ICON = 50f
private const val OVERLAY_DOCK_MARGIN = 16f
private const val OVERLAY_DOCK_DIVIDER = 1f

/** [space] is the gap between apps and the panel's padding (FolioSpace.MEDIUM). */
internal fun overlayDockPlan(screenHeightDp: Float, appCount: Int, dockSize: Float, space: Float = 12f): OverlayDockPlan {
    val size = dockSize.takeIf { it.isFinite() }?.coerceIn(MIN_DOCK_SIZE, MAX_DOCK_SIZE) ?: 1f
    val icon = OVERLAY_DOCK_ICON * size
    val room = screenHeightDp - 2f * OVERLAY_DOCK_MARGIN - 2f * space
    // Under the apps: the divider (with the gaps either side of it) when there are apps, and the Home button.
    val fixed = icon + if (appCount > 0) space + OVERLAY_DOCK_DIVIDER + space else 0f
    val fits = ((room - fixed + space) / (icon + space)).toInt().coerceAtLeast(0)
    return OverlayDockPlan(icon, minOf(appCount, fits), icon + 2f * space - 2f)
}
