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

    /**
     * The month this phone first redeemed a code carrying [BetaCodes.SCOPE_THANKS], as YYYY-MM. It is a thank-you
     * for $15 or more, so it outlives the code: it survives the months running out and [remove], and it never
     * moves once written. Nothing is sent anywhere; Folio only knows it because the code said so, offline.
     */
    private const val BADGE = "supporter_badge_since"

    /** Public keys a code may be signed with: McCal's, plus a test key the dev build (com.mccal.folio.dev) accepts. */
    internal fun keys(context: Context): List<String> = listOfNotNull(
        BetaKeys.SUPPORTER.takeIf { it.isNotBlank() },
        BetaKeys.TEST.takeIf { it.isNotBlank() && context.packageName.endsWith(".dev") })

    /** Whether this build can check codes at all: without a key, Settings doesn't offer the row. */
    fun available(context: Context): Boolean = keys(context).isNotEmpty()

    /**
     * The stored code, once it has been checked.
     *
     * Checking means an ECDSA verification, which is about a millisecond, and this is asked from inside
     * composables: Settings asks on every redraw whether to show the Market row, twice over in the split view. A
     * millisecond of signature maths per frame is a sixteenth of the frame, for an answer that only changes when
     * somebody redeems or removes a code. So the signature's answer is kept, keyed on the exact text it was worked
     * out from, and a new or removed code recomputes it.
     *
     * The whole answer is kept for the day it was worked out on, and a new day works it out again. That is safe
     * because a code can only run out at a day's boundary: its own last day, or the end of the months it bought
     * counted from the day it was first redeemed here. So a code doesn't stay valid past midnight because the answer
     * was cached before it, and the clock is written at most once a day rather than on every redraw.
     */
    fun code(context: Context, today: LocalDate = LocalDate.now(), keys: List<String> = keys(context)): BetaCodes.Code? {
        val text = stored(context) ?: return null
        val remembered = checked?.takeIf { it.text == text && it.keys == keys }
        // Asked again the same day: the whole answer stands, window and clock included, so a redraw costs one
        // preference read. A new day works it out again, which is also the only time the clock is written down.
        if (remembered != null && remembered.day == today) return remembered.answer
        val clock = clock(context, today)
        val judged = clock ?: today
        val code = remembered?.code
            ?: (BetaCodes.verify(text, keys, judged, BetaKeys.WITHDRAWN) as? BetaCodes.Result.Valid)?.code
        val answer = code?.takeIf { !it.expired(judged, window(context, it, clock)) }
        checked = Checked(text, keys, code, today, answer)
        return answer
    }

    /**
     * The last code checked: what its signature came to, and what the whole answer was on [day]. The signature's
     * part holds until the code changes; the answer only for the day it was worked out on.
     */
    private data class Checked(val text: String, val keys: List<String>, val code: BetaCodes.Code?,
        val day: LocalDate, val answer: BetaCodes.Code?)

    @Volatile private var checked: Checked? = null

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

    /**
     * Checks a code and keeps it when it's good. The result is what Settings shows the person.
     *
     * A code carrying the beta scope turns two things on as it lands, both of which the person can turn off again:
     *
     * - **Beta features**, because otherwise a supporter redeems a code and nothing happens. The switch is what
     *   lets them step back off early access, and it defaults off, which was fine while Beta Updates opened the
     *   Market as well and is a trapdoor now that a code is the only way in.
     * - **Beta Updates**, so the builds come too. Supporting Folio buys the store *and* the releases it is in;
     *   asking someone to find a second switch in another page to get what they paid for is a way to lose them.
     *
     * It also adds the supporter source, so nobody has to be handed an address to paste - see [SUPPORTER_SOURCE]
     * for why that is safe to do without asking.
     *
     * Only on the way in, and only the first time: turning either off afterwards sticks, because redeeming again
     * with the same code is refused before it reaches here.
     *
     * A months code starts its window the first time it is redeemed here; a code coming back after its window has
     * run out is expired, not a fresh month.
     */
    fun redeem(context: Context, text: String, today: LocalDate = LocalDate.now(),
        keys: List<String> = keys(context), onBetaChannel: (Boolean) -> Unit = { SoftwareUpdate.setBeta(context, it) },
        onSupporterSource: (Boolean) -> Unit = { if (it) MarketAccess.addSupporterSource(context) else MarketAccess.forgetSupporterSource(context) },
    ): BetaCodes.Result {
        val clock = clock(context, today)
        val judged = clock ?: today
        val result = BetaCodes.verify(text, keys, judged, BetaKeys.WITHDRAWN)
        if (result !is BetaCodes.Result.Valid) return result
        val code = result.code
        if (code.expired(judged, window(context, code, clock))) return BetaCodes.Result.Expired(code)
        // "First" means no working code, not no stored text: a supporter whose months ran out and who redeems a new
        // code gets Beta and the supporter source like anyone new, rather than keeping a Market that stays shut.
        val first = code(context, today, keys) == null
        checked = null
        context.getSharedPreferences(PREFS, 0).edit().putString(CODE, BetaCodes.group(text)).apply()
        if (first && BetaCodes.SCOPE_BETA in code.scopes) {
            setBetaOn(context, true)
            runCatching { onBetaChannel(true) }
        }
        if (first) runCatching { onSupporterSource(true) }
        // Written once, and kept for good: a later code without the scope never takes the badge away.
        if (BetaCodes.SCOPE_THANKS in code.scopes) {
            val prefs = context.getSharedPreferences(PREFS, 0)
            if (prefs.getString(BADGE, null) == null) {
                prefs.edit().putString(BADGE, "%04d-%02d".format(judged.year, judged.monthValue)).apply()
            }
        }
        return result
    }

    fun remove(context: Context, onSupporterSource: (Boolean) -> Unit = { if (!it) MarketAccess.forgetSupporterSource(context) }) {
        checked = null
        context.getSharedPreferences(PREFS, 0).edit().remove(CODE).remove(BETA).apply()
        // The list goes; what it listed and anything installed from it stays. Removing a code says "not a
        // supporter on this phone any more", not "undo everything that ever came of it".
        runCatching { onSupporterSource(false) }
    }

    fun storedText(context: Context): String? = stored(context)

    /** The month the lasting Supporter badge was earned (YYYY-MM), or null on a phone that has not earned one. */
    fun badgeSince(context: Context): String? =
        context.getSharedPreferences(PREFS, 0).getString(BADGE, null)
            ?.takeIf { it.length == 7 && it[4] == '-' && it.removeRange(4, 5).all(Char::isDigit) }

    /** A code unlocks a feature; beta features also need the switch, so early access can be left at any time. */
    fun has(context: Context, scope: String, keys: List<String> = keys(context)): Boolean {
        // A development build can be told to behave as a supporter, so the gated features can be seen without a
        // code. The developer's own scope is never handed out this way: it has its own lock.
        if (scope != BetaCodes.SCOPE_DEV && Dev.face(context) != Dev.Face.FREE) {
            return scope != BetaCodes.SCOPE_BETA || betaOn(context)
        }
        val code = code(context, keys = keys) ?: return false
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
/**
 * A phone's date can be wrong in both directions. Winding it back must not hand back time that has been used, which
 * is why the latest day ever seen is kept — but a dead battery can bring the clock up as 2030, and latching that
 * would expire every code for good, with nothing in the app able to undo it. So the day only moves forward by days
 * that could plausibly have passed: a leap further than [MAX_CLOCK_LEAP] is not believed, and the phone keeps the
 * day it had until the clock agrees again.
 */
internal fun supporterClock(seen: LocalDate?, today: LocalDate, floor: LocalDate): LocalDate? {
    val real = today.takeIf { !it.isBefore(floor) }
    if (seen == null) return real
    if (real == null || !real.isAfter(seen)) return seen
    return if (real.toEpochDay() - seen.toEpochDay() > MAX_CLOCK_LEAP) seen else real
}

/** A year and a half: longer than any phone is left in a drawer, shorter than the jumps a broken clock makes. */
internal const val MAX_CLOCK_LEAP = 550L

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
    /**
     * The development key. Its private half lives in ~/.folio/folio-dev-key.pem and is never committed; codes signed
     * with it are accepted only by a build whose package id ends in ".dev", so a release build ignores them entirely.
     */
    const val TEST = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1ThekiCv9nsyGnrXe7tB/r/p2hQTy/J50m2vdz56roUKiD37LRV6JRyssSx2eB18n3k8CVlCOZt52CCDMr3dLg=="

    /**
     * Serial numbers of codes that no longer work: one that was posted publicly, or one a refund took back. A code is
     * checked on the phone, so a withdrawal only takes effect when someone updates Folio — keep the list short and
     * only for codes that were really passed around.
     */
    val WITHDRAWN: Set<Long> = emptySet()
}
