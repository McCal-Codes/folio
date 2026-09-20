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

    /** No Folio code exists before this, so a phone whose clock says earlier than this doesn't know the date yet. */
    private val CLOCK_FLOOR: LocalDate = LocalDate.of(2026, 1, 1)

    /**
     * The latest date this phone has ever believed it was. A window is judged against this rather than against
     * whatever the clock says now, so winding the date back can neither hand back a month that has run out nor
     * take one that hasn't. One date for every code, and it only ever moves forward.
     */
    private const val SEEN = "supporter_seen"

    /** Public keys a code may be signed with: McCal's, plus a test key the dev build (com.mccal.folio.dev) accepts. */
    private fun keys(context: Context): List<String> = listOfNotNull(
        BetaKeys.SUPPORTER.takeIf { it.isNotBlank() },
        BetaKeys.TEST.takeIf { it.isNotBlank() && context.packageName.endsWith(".dev") })

    /** Whether this build can check codes at all: without a key, Settings doesn't offer the row. */
    fun available(context: Context): Boolean = keys(context).isNotEmpty()

    fun code(context: Context, today: LocalDate = LocalDate.now()): BetaCodes.Code? {
        val text = stored(context) ?: return null
        val clock = clock(context, today)
        val judged = clock ?: today
        val code = (BetaCodes.verify(text, keys(context), judged, BetaKeys.WITHDRAWN) as? BetaCodes.Result.Valid)?.code
            ?: return null
        return code.takeIf { !it.expired(judged, window(context, it, clock)) }
    }

    /** When this code's time runs out here, counting a months code from the day its window began. */
    fun ends(context: Context, code: BetaCodes.Code, today: LocalDate = LocalDate.now()): LocalDate? =
        code.ends(supporterWindowStart(started(context, code), readClock(context, today), code.months))

    /**
     * The day to judge a window by, noted down so a later clock can't fall behind it. Null means this phone has
     * never seen a real date: nothing to count from, and nothing to count against.
     */
    private fun clock(context: Context, today: LocalDate): LocalDate? {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val clock = readClock(context, today) ?: return null
        if (clock.toEpochDay() != prefs.getLong(SEEN, 0L)) {
            prefs.edit().putLong(SEEN, clock.toEpochDay()).apply()
        }
        return clock
    }

    /** The same day, without writing anything down: for the rows Settings draws. */
    private fun readClock(context: Context, today: LocalDate): LocalDate? =
        supporterClock(context.getSharedPreferences(PREFS, 0).getLong(SEEN, 0L).takeIf { it > 0L }
            ?.let(LocalDate::ofEpochDay), today, CLOCK_FLOOR)

    /**
     * The day this code's window began here, written down the first time there's a day worth writing down. A phone
     * that doesn't know the date yet doesn't start anyone's month, and a day later than any this phone has seen —
     * a clock that was wrong when the code was redeemed — is replaced rather than kept, so a bad clock can't burn
     * a code for good.
     */
    private fun window(context: Context, code: BetaCodes.Code, clock: LocalDate?): LocalDate? {
        val stored = started(context, code)
        val start = supporterWindowStart(stored, clock, code.months)
        if (start != null && start != stored) {
            context.getSharedPreferences(PREFS, 0).edit().putLong(startedKey(code.serial), start.toEpochDay()).apply()
        }
        return start
    }

    private fun started(context: Context, code: BetaCodes.Code): LocalDate? =
        context.getSharedPreferences(PREFS, 0).getLong(startedKey(code.serial), 0L)
            .takeIf { it > 0L }?.let { LocalDate.ofEpochDay(it) }

    private fun stored(context: Context): String? =
        context.getSharedPreferences(PREFS, 0).getString(CODE, null)?.takeIf { it.isNotBlank() }

    /** Checks a code and keeps it when it's good. The result is what Settings shows the person. */
    fun redeem(context: Context, text: String, today: LocalDate = LocalDate.now()): BetaCodes.Result {
        val clock = clock(context, today)
        val judged = clock ?: today
        val result = BetaCodes.verify(text, keys(context), judged, BetaKeys.WITHDRAWN)
        if (result !is BetaCodes.Result.Valid) return result
        val code = result.code
        // A months code starts its window the first time it is redeemed here; a code coming back after its window
        // has run out is expired, not a fresh month.
        if (code.expired(judged, window(context, code, clock))) return BetaCodes.Result.Expired(code)
        context.getSharedPreferences(PREFS, 0).edit().putString(CODE, BetaCodes.group(text)).apply()
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
 * The day a supporter window is judged by, apart from Android so it can be checked. [seen] is the latest date this
 * phone has ever noted, [today] what its clock says now, and [floor] the earliest date a Folio code could have been
 * redeemed on.
 *
 * The later of the two wins, because a clock can be wound back — by hand, or by a battery that died and left the
 * phone on its build date — and neither a month that has run out nor one that hasn't should turn on that. Null is
 * a phone that has never had a real date at all: a fresh one that hasn't reached the network yet.
 */
internal fun supporterClock(seen: LocalDate?, today: LocalDate, floor: LocalDate): LocalDate? =
    listOfNotNull(seen, today.takeIf { !it.isBefore(floor) }).maxOrNull()

/**
 * When a months code's window begins. [stored] is the day already written down for this code and [clock] the day
 * to count from, as [supporterClock] worked it out.
 *
 * A day already written down is kept, because the window must not restart when someone re-pastes their code — and
 * must not slide earlier either, or a clock that lost a month would take a month of access with it. It is replaced
 * only when it sits after every date this phone has ever seen, which means the clock was wrong when the code was
 * redeemed; otherwise that code would be refused for good. Without a real date, nobody's month starts.
 */
internal fun supporterWindowStart(stored: LocalDate?, clock: LocalDate?, months: Int): LocalDate? {
    if (months <= 0) return null
    if (clock == null) return stored
    return if (stored == null || stored.isAfter(clock)) clock else stored
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
