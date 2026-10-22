package com.mccal.folio

import android.content.pm.PackageInstaller

/** What Android's installer told Folio about an install it was handed, boiled down to what Folio does next. */
internal enum class InstallOutcome { NEEDS_CONFIRM, SUCCESS, CANCELLED, FAILED }

/** Tapping Cancel on Android's install prompt is an answer, not a failure, so it gets its own outcome. */
internal fun installOutcome(status: Int): InstallOutcome = when (status) {
    PackageInstaller.STATUS_PENDING_USER_ACTION -> InstallOutcome.NEEDS_CONFIRM
    PackageInstaller.STATUS_SUCCESS -> InstallOutcome.SUCCESS
    PackageInstaller.STATUS_FAILURE_ABORTED -> InstallOutcome.CANCELLED
    else -> InstallOutcome.FAILED
}

/**
 * A link may add a supporter code but never replace one you hold, so a page offering a smaller code can't quietly take the
 * place of the one you paid for. A code that has run out holds nothing, though, so a fresh one is welcome.
 */
internal fun canRedeemFromLink(heldGroup: String?, heldStillValid: Boolean, codeGroup: String): Boolean =
    heldGroup == null || !heldStillValid || heldGroup == codeGroup
