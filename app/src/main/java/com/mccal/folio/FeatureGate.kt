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
    MARKET("market", closedSince = "2026-09-19", opensIn = "0.7.0", { MarketFeature.RELEASED });

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
        /** Every gate still shut, for the check that says how long each has been waiting. */
        fun closed(): List<FeatureGate> = entries.filterNot { it.open }
    }
}
