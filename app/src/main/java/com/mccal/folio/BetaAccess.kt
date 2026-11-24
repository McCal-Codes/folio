package com.mccal.folio

import android.content.Context

/**
 * Where a phone stands on getting betas, in one place so the Supporter page and Software Update always say the same thing.
 * The order is the order of the fixes: a code first, then what the code includes, then the two switches.
 */
internal enum class BetaAccess(val short: Int, val long: Int) {
    NO_CODE(R.string.beta_access_no_code, R.string.beta_access_no_code_long),
    CODE_INVALID(R.string.beta_access_invalid, R.string.beta_access_invalid_long),
    CODE_ENDED(R.string.beta_access_ended, R.string.beta_access_ended_long),
    NO_BETA_SCOPE(R.string.beta_access_no_scope, R.string.beta_access_no_scope_long),
    EARLY_ACCESS_OFF(R.string.beta_access_early_off, R.string.beta_access_early_off_long),
    UPDATES_OFF(R.string.beta_access_updates_off, R.string.beta_access_updates_off_long),
    READY(R.string.beta_access_ready, R.string.beta_access_ready_long);

    /** Whether renewing or getting a code on Ko-fi is the way forward. */
    val needsKofi get() = this == NO_CODE || this == CODE_ENDED
}

internal fun betaAccess(standing: SoftwareUpdate.CodeStanding, hasBetaScope: Boolean, earlyAccess: Boolean, betaUpdates: Boolean): BetaAccess = when {
    standing == SoftwareUpdate.CodeStanding.NONE -> BetaAccess.NO_CODE
    standing == SoftwareUpdate.CodeStanding.INVALID -> BetaAccess.CODE_INVALID
    standing == SoftwareUpdate.CodeStanding.ENDED -> BetaAccess.CODE_ENDED
    !hasBetaScope -> BetaAccess.NO_BETA_SCOPE
    !earlyAccess -> BetaAccess.EARLY_ACCESS_OFF
    !betaUpdates -> BetaAccess.UPDATES_OFF
    else -> BetaAccess.READY
}

internal fun betaAccessNow(context: Context): BetaAccess = betaAccess(
    SoftwareUpdate.codeStanding(context), Supporter.has(context, BetaCodes.SCOPE_BETA), Supporter.betaOn(context), SoftwareUpdate.beta(context))
