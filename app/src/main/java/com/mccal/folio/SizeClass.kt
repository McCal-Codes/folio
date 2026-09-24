package com.mccal.folio

import android.content.res.Configuration
import android.util.DisplayMetrics
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How much to scale dp by to judge a size class at the phone's own screen density. Developer options' "Smallest
 * width" and Display size change how many dp a screen reports, but a phone-sized screen should still get the phone
 * layout: a Galaxy Z Fold8 cover set to 600dp was getting the unfolded one (bottom dock, wide margins).
 */
internal val Configuration.classScale: Float
    get() = classScale(densityDpi, DisplayMetrics.DENSITY_DEVICE_STABLE)

/** Regular size (the unfolded screen, tablets), judged at the phone's own density. */
internal fun Configuration.fitsRegularHomeLayout(): Boolean = fitsRegularHomeLayout(screenWidthDp.toFloat(), screenHeightDp.toFloat(), classScale)

/**
 * How many columns Settings shows at once.
 *
 * 1 is the phone: one page at a time, and above 600 dp the list opens over it from the sidebar button. 2 is iPad
 * Settings: the list beside the page, in either orientation, once there's room for two readable columns. A page
 * opened *from* a page (a tweak from the Tweaks list) then pushes inside the page column with a back label, as iPad
 * Settings does. 3 keeps the list you came from beside it, and only on a window of [THREE_PANES_DP] or more (DeX,
 * an external display, a large tablet): on the Fold8's inner screen (932 dp) three columns left each one too narrow.
 *
 * [nested] is true when the open page came from a list on another page. [onFold] is true when the phone is half
 * folded with the fold down the screen: the divider belongs on the crease then, and a third column would put one of
 * them across it.
 */
internal fun settingsColumns(
    widthDp: Float,
    heightDp: Float,
    classScale: Float = 1f,
    nested: Boolean = false,
    onFold: Boolean = false,
    keyboardDp: Float = 0f,
): Int = when {
    !fitsRegularHomeLayout(widthDp, sizeClassHeightDp(heightDp, keyboardDp), classScale) || widthDp < 700f -> 1
    nested && !onFold && widthDp >= THREE_PANES_DP -> 3
    else -> 2
}

/**
 * The height a size class is judged by while the keyboard is up. A keyboard covers a window; it doesn't make it a
 * smaller one, so [heightDp] (what's left to draw in) plus [keyboardDp] (what the keyboard covers) is the window
 * the layout is owed. Judged on what's left instead, the unfolded screen looks like a phone's the moment you tap a
 * text field, and the pane holding that field is taken away with the layout it belonged to (#117).
 */
internal fun sizeClassHeightDp(heightDp: Float, keyboardDp: Float): Float = heightDp + keyboardDp

/**
 * Whether Settings keeps its list beside the page rather than pushing pages over it, given the columns the host
 * allows it ([maxColumns], one pane inside the Market). The keyboard is measured out, as in [settingsColumns].
 */
internal fun settingsSplits(widthDp: Float, heightDp: Float, classScale: Float = 1f, keyboardDp: Float = 0f, maxColumns: Int = 3): Boolean =
    maxColumns >= 2 && fitsRegularHomeLayout(widthDp, sizeClassHeightDp(heightDp, keyboardDp), classScale)

/**
 * The narrowest window that shows three panes at once: a sidebar, a list and the page opened from it. Material's
 * "large" width class. Below it, what you open replaces the list, with Back to return, so the page gets the room.
 */
internal const val THREE_PANES_DP = 1200f

/**
 * Whether the Market keeps its list beside an open package or source, in a window wide enough to split at all.
 * With the tabs in a sidebar the sidebar is already one pane, so the list and the page only fit side by side from
 * [THREE_PANES_DP]; with the tabs along the bottom (unfolded portrait) they're the two panes.
 */
internal fun marketListBeside(widthDp: Float, sidebar: Boolean): Boolean = !sidebar || widthDp >= THREE_PANES_DP

/** Where the Market's tabs sit: a bar under the content, a rail along the long edge, or a sidebar beside it. */
internal enum class TabPlacement { BOTTOM, RAIL, SIDEBAR }

/**
 * Whether the Market splits its window into panes at all: a regular window with room for two readable columns. The
 * keyboard is measured out, as in [settingsSplits], so typing a source's address, or searching the Settings tab
 * inside the Market, can't turn the unfolded screen into a phone-sized one for as long as the keyboard is up (#117).
 */
internal fun marketSplits(widthDp: Float, heightDp: Float, classScale: Float = 1f, keyboardDp: Float = 0f): Boolean =
    fitsRegularHomeLayout(widthDp, sizeClassHeightDp(heightDp, keyboardDp), classScale) && widthDp >= 700f

/**
 * Where the Market puts its tabs, the same rule the Mockup Lab draws: a sidebar once the window is as wide as the
 * Fold8 inner screen (iPad), a rail on the long edge when the window is too short for a bar under it (the cover
 * screen rotated), and the bar itself everywhere else. The keyboard is measured out of both questions: judged on
 * what's left to draw in, a portrait window would read as a landscape one the moment a field took focus, and the
 * tabs would leave the bottom for the rail until the keyboard went away.
 */
internal fun marketTabs(widthDp: Float, heightDp: Float, classScale: Float = 1f, keyboardDp: Float = 0f): TabPlacement {
    val height = sizeClassHeightDp(heightDp, keyboardDp)
    val regular = fitsRegularHomeLayout(widthDp, height, classScale)
    return when {
        !regular && widthDp > height -> TabPlacement.RAIL
        regular && widthDp >= 920f -> TabPlacement.SIDEBAR
        else -> TabPlacement.BOTTOM
    }
}

/**
 * How many columns Settings may use where something else already takes part of the window: the Market's sidebar,
 * when Settings is its Settings tab. Settings measures only its own box, so without this it would add its list and
 * page beside the Market's sidebar and make three panes on a window that has room for two.
 */
internal val LocalSettingsMaxColumns = staticCompositionLocalOf { 3 }

/** Settings' share of a window whose sidebar is already one pane: one below [THREE_PANES_DP], two from it. */
internal fun settingsColumnsBesideSidebar(windowWidthDp: Float): Int = if (windowWidthDp >= THREE_PANES_DP) 2 else 1

/** How many columns the Market's rows use when the list has the pane to itself: two once each gets about 320 dp. */
internal fun marketListColumns(paneWidthDp: Float): Int = if (paneWidthDp >= 640f) 2 else 1
