package com.mccal.folio

import android.content.Context
import com.mccal.folio.market.MarketFeature

/**
 * A feature that is built, shipped in every APK, and not open to everyone yet.
 *
 * Folio has one trunk and one build ([docs/adr/0007-release-trains.md]): a feature that is not ready for everyone is
 * held back by a gate rather than by a branch, so a supporter with the `beta` scope sees it the day it merges, and
 * opening the gate is what shipping it means. That is the promise `BetaCodes.SCOPE_BETA` has always made: "new
 * features a release or two early".
 *
 * A gate is not a secret. Folio is open source, so anyone can build the app and see everything; this only decides
 * what the official build shows.
 *
 * **Gate at the entry point, not only in the UI.** Hidden work that still polls a sensor or refreshes a source costs
 * every phone battery to do nothing. Ask [isOpen] where the work starts.
 *
 * Adding one: a key, the day it was gated, the release it should open in, and where "open to everyone" is decided.
 * `FeatureGateTest` fails once that release arrives and the gate is still closed, so a finished feature cannot sit
 * hidden and forgotten, which is the one real cost of working this way.
 */
internal enum class FeatureGate(
    val key: String,
    /** When this feature was first held back, `YYYY-MM-DD`, so a gate's age is visible rather than guessed. */
    val closedSince: String,
    /** The release it should open in. [FeatureGateTest] fails when that release arrives and it is still closed. */
    val opensIn: String,
    private val openToEveryone: () -> Boolean,
) {
    /**
     * The Market. Supporters and Folio Dev have had it since 0.6.6; everyone gets it in 0.7.0, when
     * [MarketFeature.RELEASED] becomes true. Every theme and tweak it hands out is already in Settings, so nobody on
     * the stable release is missing a feature while this is shut.
     */
    MARKET("market", closedSince = "2026-09-19", opensIn = "0.7.0", { MarketFeature.RELEASED }),

    /**
     * Clearing an app's badge when you open it. Built for 0.6.7 so supporters can say whether it reads as helpful or
     * as Folio hiding something; everyone gets it in 0.6.8. Flip [OPEN_IN_0_6_8] to true to open it.
     */
    BADGES_WHEN_OPENED("badgesWhenOpened", closedSince = "2026-09-23", opensIn = "0.6.8", { OPEN_IN_0_6_8 }),

    /**
     * StandBy while the phone charges, rather than only when it is stood up half folded. Charging behaviour is the
     * kind of thing that needs real phones overnight, which is what the beta is for.
     */
    STANDBY_CHARGING("standbyCharging", closedSince = "2026-09-23", opensIn = "0.6.8", { OPEN_IN_0_6_8 }),

    /**
     * Page Effects: 3D turns on the Home page swipe ([PageEffect]). Off by default and gated on top of that, because
     * Home's swipe is the one journey Folio already loses on at 120 Hz, and no amount of care in a layer block
     * changes the fact that this asks the GPU for more on exactly that frame. The beta is what decides whether both
     * effects are smooth enough to keep, and on which screens.
     */
    PAGE_EFFECTS("pageEffects", closedSince = "2026-09-24", opensIn = "0.6.8", { OPEN_IN_0_6_8 }),

    /**
     * Focus triggers: a Focus that turns on by itself when the phone is unfolded, charging or has headphones
     * connected. It listens for the phone's folding, power and audio devices, which is the kind of thing that needs
     * real phones for a few days before everyone gets it, so supporters have it in the 0.6.9 betas. Flip
     * [OPEN_IN_0_6_9] to true in the 0.6.9 release.
     */
    FOCUS_TRIGGERS("focusTriggers", closedSince = "2026-10-04", opensIn = "0.6.9", { OPEN_IN_0_6_9 }),

    /**
     * A grabber under the Side Bar dock in edit mode, to drag it up or down instead of using the Dock Height slider
     * (#21). It sits in the middle of Home's edit gestures, so supporters try it on real phones first.
     */
    DOCK_GRABBERS("dockGrabbers", closedSince = "2026-10-04", opensIn = "0.6.9", { OPEN_IN_0_6_9 }),

    /**
     * Packages from a source update by themselves in the background (M1): the daily refresh stages a checked update
     * and it goes in through the running app. Supporters and Folio Dev first, because it changes what is on Home
     * without the person tapping anything.
     */
    MARKET_AUTO_UPDATE("marketAutoUpdate", closedSince = "2026-10-04", opensIn = "0.6.9", { OPEN_IN_0_6_9 }),

    /**
     * Nothing as a choice for the strip above the dock, beside the Search button and the page dots. It changes what
     * Home looks like at rest, so supporters try it first; Stronger rings is not gated, because it is off until asked.
     */
    HOME_STRIP_NOTHING("homeStripNothing", closedSince = "2026-10-05", opensIn = "0.6.9", { OPEN_IN_0_6_9 }),

    /**
     * Folders you can shape: resize by the corner, drop an app on another to make a folder, drag to reorder inside
     * an open folder or out past its edge, and Sort A to Z. Gesture work like this wants real hands on real Folds
     * (the open folder is its own window, which is where the last three bugs were), so the beta gets it first.
     */
    FOLDER_EDITING("folderEditing", closedSince = "2026-10-06", opensIn = "0.6.9", { OPEN_IN_0_6_9 }),

    /**
     * Making the Big Clock your own: Customize (Looks, color, Fine tune) and Place Freely. A clock nobody customizes
     * and nobody places is drawn exactly as before, so the only thing this holds back is the two menu rows.
     */
    CLOCK_CUSTOMIZE("clockCustomize", closedSince = "2026-10-06", opensIn = "0.6.9", { OPEN_IN_0_6_9 }),

    /**
     * A widget at the top of Home fills exactly two app rows, so every row has one pitch and the dock lines up with the
     * rows on a page with a widget and on one without (issue #13). It changes how every stacked layout looks and
     * retires Widget Size, so supporters try it first; opening the gate is what shipping it means.
     */
    WIDGETS_FILL_ROWS("widgetsFillRows", closedSince = "2026-10-06", opensIn = "0.6.9", { OPEN_IN_0_6_9 });

    /** True once the feature ships to everyone and the gate stops mattering. */
    val open: Boolean get() = openToEveryone()

    /**
     * Whether this phone sees the feature: everyone once it has shipped, Folio Dev always so it can be tested beside
     * the signed release, and a supporter whose code carries the `beta` scope with early access still switched on.
     */
    fun isOpen(context: Context): Boolean =
        open ||
            MarketFeature.isDevBuild(context.packageName) ||
            runCatching { Supporter.has(context, BetaCodes.SCOPE_BETA) }.getOrDefault(false)

    companion object {
        /**
         * The features built during 0.6.7 for supporters to try, which open to everyone in 0.6.8. One flag rather than
         * one per feature, because they open together: `FeatureGateTest` fails at 0.6.8 while this is still false.
         */
        private const val OPEN_IN_0_6_8 = true

        /** The features built during 0.6.9 for supporters to try first, which open together in the 0.6.9 release. */
        private const val OPEN_IN_0_6_9 = false

        /** Every gate still shut, for the check that says how long each has been waiting. */
        fun closed(): List<FeatureGate> = entries.filterNot { it.open }
    }
}
