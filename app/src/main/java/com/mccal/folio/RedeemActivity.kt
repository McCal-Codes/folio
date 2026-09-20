package com.mccal.folio

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * A folio://redeem link from a supporter email, so a code is one tap instead of a copy and paste. The link is
 * untrusted, which costs nothing here: a code only works if it carries McCal's signature, so the worst a bad link can
 * do is be refused. Nothing is claimed silently — Settings opens on the Supporter page to show what happened.
 */
class RedeemActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val code = redeemCode(intent?.data?.toString())
        // A link is something anyone can put in front of you, so it may add a code but never replace one: a page
        // offering a smaller code could otherwise quietly take the place of the one you paid for. Swapping codes
        // is done in Settings › Supporter, where you can see what you already have.
        val held = Supporter.storedText(this)
        val replacing = code != null && held != null && BetaCodes.group(held) != BetaCodes.group(code)
        // The same wording Settings › Supporter uses, so a link and a typed code answer alike, in any language.
        val said = when {
            code == null -> R.string.that_link_doesn_t_carry_a_folio_code
            replacing -> R.string.you_already_have_a_code_paste_this_one
            else -> when (Supporter.redeem(this, code)) {
                is BetaCodes.Result.Valid -> R.string.code_added_thank_you
                is BetaCodes.Result.Expired -> R.string.that_code_has_run_out_ko_fi_codes_have_a
                BetaCodes.Result.Withdrawn -> R.string.that_code_has_been_withdrawn_if_you_thin
                BetaCodes.Result.NotOurs -> R.string.folio_doesn_t_recognize_that_code_check
                BetaCodes.Result.Unreadable -> R.string.that_doesn_t_look_like_a_folio_code_past
            }
        }
        Toast.makeText(this, getString(said), Toast.LENGTH_LONG).show()
        // Settings opens on Supporter, as this says it does: with a link there is nothing else on screen to show
        // what happened.
        SettingsLink.page = CustomizationPage.SUPPORTER
        runCatching {
            startActivity(android.content.Intent(this, MainActivity::class.java)
                .setAction(android.content.Intent.ACTION_APPLICATION_PREFERENCES)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
        finish()
    }
}

/** The code in a folio://redeem?c=… link, or null for anything else. Plain string work, so it can be tested. */
internal fun redeemCode(link: String?): String? {
    val text = link?.trim() ?: return null
    val prefix = "folio://redeem"
    if (!text.startsWith(prefix, ignoreCase = true)) return null
    val rest = text.substring(prefix.length)
    val query = rest.substringAfter('?', "")
    val code = query.split('&').firstOrNull { it.startsWith("c=", ignoreCase = true) }?.substringAfter('=')
        ?: rest.substringBefore('?').trim('/').takeIf { it.isNotEmpty() }
    val decoded = runCatching { java.net.URLDecoder.decode(code ?: return null, "UTF-8") }.getOrNull() ?: return null
    // Long enough to be a code, and only characters a code can hold: anything else isn't worth passing on.
    return decoded.trim().takeIf { it.length in 20..200 && it.all { c -> c.isLetterOrDigit() || c == '-' } }
}
