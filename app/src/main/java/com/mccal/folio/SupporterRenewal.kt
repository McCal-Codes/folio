package com.mccal.folio

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Picks up the next code for a Ko-fi member, so a monthly supporter doesn't have to open the email each month.
 *
 * Every membership payment already earns a fresh code, which the worker emails. This asks the same worker for that
 * code when the one on the phone is in its last week or has ended, and redeems it exactly as if it had been pasted:
 * the signature is checked here, on the phone, so the worker can only hand back a code it was given, never make one.
 * A member who cancels simply stops getting new codes and the current one runs out, as before.
 *
 * It is opt-in: nothing is sent until the person turns on "Renew automatically" on the Supporter page (off by
 * default, and cleared when the code is removed). Then it asks at most once a day, and says nothing when there is
 * nothing to take or the worker can't be reached: the reminder and the Renew on Ko-fi button are still there. Run
 * from the daily check and whenever Folio opens, before the reminder.
 */
internal object SupporterRenewal {
    private const val PREFS = "folio"
    private const val ASKED = "supporter_renewal_asked"

    /** Whether to ask today: the code is in its last week or has ended, and the worker hasn't been asked yet today. */
    internal fun due(state: SupporterState, askedOn: Long, today: LocalDate): Boolean {
        val ending = when (state) {
            is SupporterState.Active -> state.daysLeft != null && state.daysLeft <= SUPPORTER_SOON_DAYS
            is SupporterState.Expired -> true
            SupporterState.None -> false
        }
        return ending && askedOn != today.toEpochDay()
    }

    /** Where to ask: the worker's address plus /renew, or null for anything that isn't https (a code never travels in the clear). */
    internal fun address(broker: String): String? =
        broker.trim().trimEnd('/').takeIf { it.startsWith("https://") }?.let { "$it/renew" }

    /** The code in the worker's answer, or null when there isn't one or it is the code already held. */
    internal fun newerCode(body: String, current: String): String? {
        val sent = runCatching { JSONObject(body).optString("code") }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return sent.takeIf { BetaCodes.group(it) != BetaCodes.group(current) }
    }

    /** Whether [offered] can replace [held] without taking anything away: every scope the held code opens, it opens too. */
    internal fun opensAtLeast(offered: BetaCodes.Code, held: BetaCodes.Code?): Boolean =
        held == null || offered.scopes.containsAll(held.scopes)

    /** Asks the worker and redeems what it sends. Call off the main thread. Returns true when a newer code was taken. */
    fun check(context: Context, today: LocalDate = LocalDate.now()): Boolean {
        if (!SoftwareUpdate.supported(context) || !Supporter.autoRenewOn(context)) return false
        val current = Supporter.storedText(context) ?: return false
        val prefs = context.getSharedPreferences(PREFS, 0)
        if (!due(Supporter.state(context, today), prefs.getLong(ASKED, 0L), today)) return false
        val address = address(SoftwareUpdate.BETA_BROKER) ?: return false
        // Written before asking, so a worker that is down is tried tomorrow rather than on every launch.
        prefs.edit().putLong(ASKED, today.toEpochDay()).apply()
        val code = newerCode(ask(address, current) ?: return false, current) ?: return false

        // Never trade a working code for one that opens less, such as a Builder member's code for a plainer one
        // after changing tier: what is in place keeps working, and the new code is left for the person to paste.
        val keys = Supporter.keys(context)
        val offered = (BetaCodes.verify(code, keys, today, BetaKeys.WITHDRAWN) as? BetaCodes.Result.Valid)?.code ?: return false
        if (!opensAtLeast(offered, Supporter.code(context, today, keys))) return false

        // A code that had already ended counts as a first redemption and would switch early access back on. Someone
        // who turned it off meant it, so it goes back the way they had it.
        val earlyAccess = Supporter.betaOn(context)
        val result = Supporter.redeem(context, code, today, onBetaChannel = {})
        if (!earlyAccess) Supporter.setBetaOn(context, false)
        return result is BetaCodes.Result.Valid
    }

    /** The worker's answer body for a 200, or null for anything else: no newer code, no renewal on file, or no reply. */
    private fun ask(address: String, code: String): String? = runCatching {
        val c = URL(address).openConnection() as HttpURLConnection
        try {
            c.setRequestProperty("User-Agent", "Folio") // HTTP header, never shown // english-only
            c.setRequestProperty("Authorization", "Bearer $code") // HTTP header, never shown // english-only
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally { c.disconnect() }
    }.getOrNull()
}
