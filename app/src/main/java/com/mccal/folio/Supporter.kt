package com.mccal.folio

import android.content.Context
import java.time.LocalDate

/**
 * What a redeemed supporter code unlocks, kept next to Folio's other small local settings. Nothing here leaves the
 * phone: the code is verified on the device and stored as typed, so it can be shown, checked again after an update,
 * or removed.
 *
 * Beta features are opt-in even with a code. Early access means more bugs than usual, so turning the switch off is
 * always one tap away and puts every beta feature back to how the last stable release behaved.
 */
internal object Supporter {
    private const val PREFS = "folio"
    private const val CODE = "supporter_code"
    private const val BETA = "supporter_beta_on"

    /**
     * The day a code was first redeemed on this phone, one key per serial. A code that buys months rather than a
     * fixed date counts from here, so a code minted long before it was handed out still gives its full time.
     *
     * It outlives [remove] on purpose: pasting the same code again resumes the window it started, rather than
     * handing out another month for free. It's a serial number and a date, kept on the phone and sent nowhere.
     */
    private fun startedKey(serial: Long) = "supporter_started_$serial"

    /** Public keys a code may be signed with: McCal's, plus a test key the dev build (com.mccal.folio.dev) accepts. */
    private fun keys(context: Context): List<String> = listOfNotNull(
        BetaKeys.SUPPORTER.takeIf { it.isNotBlank() },
        BetaKeys.TEST.takeIf { it.isNotBlank() && context.packageName.endsWith(".dev") })

    /** Whether this build can check codes at all: without a key, Settings doesn't offer the row. */
    fun available(context: Context): Boolean = keys(context).isNotEmpty()

    fun code(context: Context, today: LocalDate = LocalDate.now()): BetaCodes.Code? {
        val text = stored(context) ?: return null
        val code = (BetaCodes.verify(text, keys(context), today, BetaKeys.WITHDRAWN) as? BetaCodes.Result.Valid)?.code
            ?: return null
        return code.takeIf { !it.expired(today, started(context, it)) }
    }

    /** When this code's time runs out here, counting a months code from the day it was redeemed. */
    fun ends(context: Context, code: BetaCodes.Code): LocalDate? = code.ends(started(context, code))

    private fun started(context: Context, code: BetaCodes.Code): LocalDate? =
        context.getSharedPreferences(PREFS, 0).getLong(startedKey(code.serial), 0L)
            .takeIf { it > 0L }?.let { LocalDate.ofEpochDay(it) }

    private fun stored(context: Context): String? =
        context.getSharedPreferences(PREFS, 0).getString(CODE, null)?.takeIf { it.isNotBlank() }

    /** Checks a code and keeps it when it's good. The result is what Settings shows the person. */
    fun redeem(context: Context, text: String, today: LocalDate = LocalDate.now()): BetaCodes.Result {
        val result = BetaCodes.verify(text, keys(context), today, BetaKeys.WITHDRAWN)
        if (result !is BetaCodes.Result.Valid) return result
        val code = result.code
        val prefs = context.getSharedPreferences(PREFS, 0)
        // A months code starts its window the first time it is redeemed here; a code coming back after its window
        // has run out is expired, not a fresh month.
        val started = started(context, code) ?: today.takeIf { code.months > 0 }
        if (code.expired(today, started)) return BetaCodes.Result.Expired(code)
        val editor = prefs.edit().putString(CODE, BetaCodes.group(text))
        started?.let { editor.putLong(startedKey(code.serial), it.toEpochDay()) }
        editor.apply()
        return result
    }

    fun remove(context: Context) {
        context.getSharedPreferences(PREFS, 0).edit().remove(CODE).remove(BETA).apply()
    }

    fun storedText(context: Context): String? = stored(context)

    /** A code unlocks a feature; beta features also need the switch, so early access can be left at any time. */
    fun has(context: Context, scope: String): Boolean {
        val code = code(context) ?: return false
        if (scope !in code.scopes) return false
        return scope != BetaCodes.SCOPE_BETA || betaOn(context)
    }

    fun betaOn(context: Context): Boolean = context.getSharedPreferences(PREFS, 0).getBoolean(BETA, false)

    fun setBetaOn(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, 0).edit().putBoolean(BETA, on).apply()
    }
}

/**
 * Public keys for supporter codes. [SUPPORTER] is filled in from McCal's own key pair (the private half never leaves
 * his Mac; `scripts/beta-code.py` makes both the key and the codes). [TEST] signs codes for development builds only.
 */
internal object BetaKeys {
    const val SUPPORTER = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEM4vVq0D/ZzqkVWlyQYMTFN3TTbdqRgKdt2a30T88NkeQhDEWnujFjfyXyKtPIVBKMSiRVXeY/OcC0+TaB6eypg=="
    const val TEST = ""

    /**
     * Serial numbers of codes that no longer work: one that was posted publicly, or one a refund took back. A code is
     * checked on the phone, so a withdrawal only takes effect when someone updates Folio — keep the list short and
     * only for codes that were really passed around.
     */
    val WITHDRAWN: Set<Long> = emptySet()
}
