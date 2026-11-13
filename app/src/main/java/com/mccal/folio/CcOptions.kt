package com.mccal.folio

import org.json.JSONObject

/** Which page Control Center opens on: always the Controls page, or the page it was last left on. */
enum class CcOpenOn { CONTROLS, LAST }

/**
 * The options on Settings > Notifications & Control Center > Control Center that are not size or look
 * (FeatureGate.CC_SETTINGS_V2). The defaults are what Control Center did before they existed, so a phone that never opens the
 * page behaves exactly as it did.
 */
data class CcOptions(
    /** The Media and sound page may be shown. It still hides itself when the first page already holds what it holds. */
    val mediaPage: Boolean = true,
    /** The Connectivity page may be shown, under the same rule. */
    val connectivityPage: Boolean = true,
    val openOn: CcOpenOn = CcOpenOn.CONTROLS,
    /** A light tick as a slider reaches its top or bottom. */
    val sliderTicks: Boolean = true,
    /** Close Control Center after a tap on a one-shot tile (see [ccClosesAfterTap]). */
    val closeAfterToggle: Boolean = false,
    /** On a wide window Control Center is a compact panel (about the width of an iPad's) that the side choice can move; off, it fills the window. */
    val compact: Boolean = false,
    /** The page rail (battery ring, Wi-Fi, page icons) beside the grid on a wide window. Off by default: like an iPhone, pages are swiped and dots lie under the grid. */
    val rail: Boolean = false,
) {
    fun writeTo(json: JSONObject): JSONObject = json.put(MEDIA_PAGE, mediaPage).put(CONNECTIVITY_PAGE, connectivityPage)
        .put(OPEN_ON, openOn.name).put(SLIDER_TICKS, sliderTicks).put(CLOSE_AFTER, closeAfterToggle).put(COMPACT, compact).put(RAIL, rail)

    companion object {
        private const val MEDIA_PAGE = "ccMediaPage"
        private const val CONNECTIVITY_PAGE = "ccConnectivityPage"
        private const val OPEN_ON = "ccOpenOn"
        private const val SLIDER_TICKS = "ccSliderTicks"
        private const val CLOSE_AFTER = "ccCloseAfterToggle"
        private const val COMPACT = "ccCompact"
        private const val RAIL = "ccRail"

        fun read(json: JSONObject): CcOptions = CcOptions(
            mediaPage = json.optBoolean(MEDIA_PAGE, true),
            connectivityPage = json.optBoolean(CONNECTIVITY_PAGE, true),
            openOn = runCatching { CcOpenOn.valueOf(json.optString(OPEN_ON)) }.getOrDefault(CcOpenOn.CONTROLS),
            sliderTicks = json.optBoolean(SLIDER_TICKS, true),
            closeAfterToggle = json.optBoolean(CLOSE_AFTER, false),
            compact = json.optBoolean(COMPACT, false),
            rail = json.optBoolean(RAIL, false),
        )
    }
}

/**
 * The extra pages that are shown: those that are needed (the first page cannot hold what they hold, see [ccExtraPages]) AND whose
 * switch is on. The Controls pages are never switched off.
 */
internal fun ccShownExtraPages(needed: List<CcPage>, options: CcOptions): List<CcPage> = needed.filter {
    when (it) {
        CcPage.MEDIA -> options.mediaPage
        CcPage.CONNECTIVITY -> options.connectivityPage
        CcPage.CONTROLS -> true
    }
}

/**
 * The pages the rail beside the grid shows (the Duo, the iPhone and the iPad keep it): Media and Connectivity are always there as
 * page icons when their switch is on, not only when the first page runs out of room. Without the rail it is [ccShownExtraPages].
 */
internal fun ccRailExtraPages(needed: List<CcPage>, options: CcOptions, rail: Boolean): List<CcPage> =
    if (!rail) ccShownExtraPages(needed, options)
    else listOfNotNull(CcPage.MEDIA.takeIf { options.mediaPage }, CcPage.CONNECTIVITY.takeIf { options.connectivityPage })

/**
 * The position, in the order of [pages] (0 is the first page), Control Center opens on. [CcOpenOn.LAST] returns to the page last used
 * when it is still there (the first Controls page for any Controls page), and otherwise the first page.
 */
internal fun ccStartPage(openOn: CcOpenOn, last: CcPage?, pages: List<CcPageSpec>): Int {
    if (openOn != CcOpenOn.LAST || last == null) return 0
    return pages.indexOfFirst { it.page == last }.coerceAtLeast(0)
}

/** The page Control Center was last left on, kept while Folio runs (Home's panels and the one over other apps share it). */
internal object CcLastPage {
    @Volatile var page: CcPage? = null
}

/** The tiles that do one thing and are done: with "Close after a toggle" on, a tap on one closes Control Center. Switches stay open. */
internal val CC_ONE_SHOT_CONTROLS: Set<CcControl> = setOf(
    CcControl.FLASHLIGHT, CcControl.CAMERA, CcControl.CALCULATOR, CcControl.SCREENSHOT,
    CcControl.SETTINGS, CcControl.LOCK, CcControl.WALLET, CcControl.NOTES,
)

internal fun ccClosesAfterTap(control: CcControl, options: CcOptions): Boolean = options.closeAfterToggle && control in CC_ONE_SHOT_CONTROLS

/** How long Control Center waits after a one-shot tap, so the tile's own press and haptic finish first. */
internal const val CC_CLOSE_AFTER_MS = 220L

/** How long "Reset Control Center" offers Undo. */
internal const val CC_RESET_UNDO_MS = 6000L

/**
 * "Edit controls" in Settings closes Settings and opens Control Center already in edit mode. The request is good for a few seconds, so
 * a panel that never opened (the service was not ready) cannot start editing the next time the person pulls it down.
 */
internal object CcEditRequest {
    private const val WINDOW_MS = 4000L
    @Volatile private var at = Long.MIN_VALUE

    fun request(now: Long = android.os.SystemClock.uptimeMillis()) { at = now }

    /** True once for a request made within the last few seconds. */
    @Synchronized fun take(now: Long = android.os.SystemClock.uptimeMillis()): Boolean {
        val fresh = at != Long.MIN_VALUE && now - at in 0..WINDOW_MS
        at = Long.MIN_VALUE
        return fresh
    }
}

/** The width of Control Center's compact panel on a wide window (about an iPad's, so the grid is four columns). */
internal const val CC_COMPACT_WIDTH_DP = 520
