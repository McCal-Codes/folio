package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter

/**
 * Settings › Supporter: redeem a Ko-fi code, see what it unlocks, and choose whether to take the beta features that
 * come with it. Everything happens on the phone — the code is checked against a key inside Folio, and nothing about
 * it is ever sent anywhere.
 */
@Composable
internal fun SupporterPage() {
    val context = LocalContext.current
    var code by remember { mutableStateOf(Supporter.code(context)) }
    var stored by remember { mutableStateOf(Supporter.storedText(context)) }
    var beta by remember { mutableStateOf(Supporter.betaOn(context)) }
    var redeeming by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    SettingsCard("CODE") {
        val current = code
        if (current == null) {
            CardAction(stringResource(R.string.redeem_a_code), onClick = { problem = null; redeeming = true })
            CardNote(stringResource(R.string.codes_come_with_a_ko_fi_thank_you_folio))
            // What a code opens today, on the page where someone would go looking for it.
            if (!MarketAccess.isOpen(context)) CardNote(stringResource(MARKET_NOT_YET))
        } else {
            InfoRow(stringResource(R.string.code), shortCode(stored.orEmpty()))
            InfoRow(stringResource(R.string.unlocks), unlocksText(context, current.scopes))
            Supporter.ends(context, current)?.let {
                InfoRow(stringResource(R.string.until), it.format(DateTimeFormatter.ofPattern("d MMM yyyy")))
            }
            CardAction(stringResource(R.string.remove_code), destructive = true, onClick = {
                Supporter.remove(context); code = null; stored = null; beta = false
            })
        }
        problem?.let { CardNote(it) }
    }

    if (code?.scopes?.contains(BetaCodes.SCOPE_BETA) == true) SettingsCard("BETA FEATURES") {
        SettingsSwitch(stringResource(R.string.beta_features), beta, { on -> Supporter.setBetaOn(context, on); beta = on }, "supporter-beta-switch")
        CardNote(stringResource(R.string.beta_features_arrive_a_release_or_two_ea))
        // Redeeming turned the beta channel on as well, so say so here rather than only in Software Update.
        CardNote(stringResource(R.string.your_code_also_switched_on_beta_updates))
    }

    // "keys" is the scope a code carries for Keyd (the scope kept the old name; see BetaCodes). Keyd itself is in the
    // supporter source, which redeeming adds to the Market, so the card says where to find it.
    if (code?.scopes?.contains(BetaCodes.SCOPE_KEYS) == true) SettingsCard("KEYD") {
        InfoRow(stringResource(R.string.status), stringResource(R.string.keyd_in_the_market))
        InfoRow(stringResource(R.string.keyd_what_it_does), stringResource(R.string.keyd_features))
        CardNote(stringResource(R.string.keyd_where_to_get_it))
    }

    SettingsCard("SUPPORT") {
        CardNote(stringResource(R.string.folio_s_core_is_free_and_stays_free_home))
    }

    // Development builds only, and only after a code signed with the development key: see Dev.
    if (Dev.possible(context)) DeveloperCard(onFaceChange = { code = Supporter.code(context) })

    if (redeeming) RedeemAlert(onCancel = { redeeming = false }, onRedeem = { typed ->
        when (val result = Supporter.redeem(context, typed)) {
            is BetaCodes.Result.Valid -> {
                code = result.code; stored = Supporter.storedText(context); problem = null; redeeming = false
            }
            is BetaCodes.Result.Expired -> problem = context.getString(R.string.that_code_has_run_out_ko_fi_codes_have_a)
            BetaCodes.Result.Withdrawn -> problem = context.getString(R.string.that_code_has_been_withdrawn_if_you_thin)
            BetaCodes.Result.NotOurs -> problem = context.getString(R.string.folio_doesn_t_recognize_that_code_check)
            BetaCodes.Result.Unreadable -> problem = context.getString(R.string.that_doesn_t_look_like_a_folio_code_past)
        }
    })
}

/** Enough of the code to recognize it, without four lines of letters in a settings row. */
private fun shortCode(code: String): String =
    code.split('-').let { if (it.size <= 3) code else "${it.first()}…${it.last()}" }

private fun unlocksText(context: android.content.Context, scopes: Set<String>): String = listOf(
    BetaCodes.SCOPE_BETA to context.getString(R.string.beta_features_2), BetaCodes.SCOPE_LOOK to context.getString(R.string.personalization),
    BetaCodes.SCOPE_POWER to context.getString(R.string.automation), BetaCodes.SCOPE_KEYS to context.getString(R.string.keyboard_extras))
    .filter { it.first in scopes }.joinToString(", ") { it.second }.ifEmpty { context.getString(R.string.nothing_yet) }

@Composable private fun InfoRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 17.sp)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 6.dp))
        Text(value, color = FolioColors.SecondaryLabel, fontSize = 15.sp, textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth())
    }
}

/** iOS-style alert with one field, like Save Backup: paste the code, tap Redeem. */
@Composable private fun RedeemAlert(onCancel: () -> Unit, onRedeem: (String) -> Unit) {
    var typed by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.redeem_a_code)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.paste_the_code_from_your_ko_fi_thank_you), fontSize = 13.sp)
                BasicTextField(typed, { typed = it.take(160) },
                    Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = .1f)).padding(horizontal = 10.dp, vertical = 8.dp)
                        .focusRequester(focus).testTag("redeem-code"),
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onRedeem(typed) }))
            }
        },
        confirmButton = { TextButton(onClick = { onRedeem(typed) }) { Text(stringResource(R.string.redeem)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } })
}

/**
 * Settings › Supporter › Developer, in a development build. Locked, it offers one action; unlocked, it decides which
 * audience Folio behaves as, where Software Update looks, and which of this release's features are hidden.
 */
@Composable private fun DeveloperCard(onFaceChange: () -> Unit) {
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf(Dev.unlocked(context)) }
    var face by remember { mutableStateOf(Dev.face(context)) }
    var channel by remember { mutableStateOf(Dev.channel(context)) }
    var lines by remember { mutableStateOf(Dev.Line.entries.associateWith { Dev.on(context, it) }) }
    var unlocking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    SettingsCard("DEVELOPER") {
        if (!unlocked) {
            CardAction(stringResource(R.string.unlock_developer_switches), onClick = { problem = null; unlocking = true })
            CardNote(stringResource(R.string.paste_a_code_signed_with_the_development_key))
            problem?.let { CardNote(it) }
        } else {
            InfoRow(stringResource(R.string.behave_as), stringResource(face.title))
            for (option in Dev.Face.entries) {
                SettingsSwitch(stringResource(option.title), face == option, { on ->
                    if (on) { Dev.setFace(context, option); face = option; onFaceChange() }
                }, "dev-face-${option.key}")
            }
            CardNote(stringResource(face.detail))
            CardAction(stringResource(R.string.lock_developer_switches), destructive = true, onClick = {
                Dev.lock(context); unlocked = false; face = Dev.Face.FREE; channel = Dev.Channel.DEFAULT
                lines = Dev.Line.entries.associateWith { true }; onFaceChange()
            })
        }
    }

    if (unlocked) SettingsCard("UPDATE CHANNEL") {
        for (option in Dev.Channel.entries) {
            SettingsSwitch(stringResource(option.title), channel == option, { on ->
                if (on) { Dev.setChannel(context, option); channel = option }
            }, "dev-channel-${option.key}")
        }
    }

    if (unlocked) SettingsCard("FEATURES") {
        for (line in Dev.Line.entries) {
            SettingsSwitch(stringResource(line.title), lines[line] != false, { on ->
                Dev.setOn(context, line, on); lines = lines + (line to on)
            }, "dev-line-${line.key}")
        }
        CardNote(stringResource(R.string.turn_one_off_to_see_the_release_before_it))
    }

    if (unlocking) RedeemAlert(onCancel = { unlocking = false }, onRedeem = { typed ->
        when (Dev.unlock(context, typed)) {
            is BetaCodes.Result.Valid -> { unlocked = true; face = Dev.face(context); problem = null; unlocking = false }
            BetaCodes.Result.NotOurs -> problem = context.getString(R.string.that_code_doesn_t_carry_the_developer_scope)
            is BetaCodes.Result.Expired -> problem = context.getString(R.string.that_code_has_run_out_ko_fi_codes_have_a)
            BetaCodes.Result.Withdrawn -> problem = context.getString(R.string.that_code_has_been_withdrawn_if_you_thin)
            BetaCodes.Result.Unreadable -> problem = context.getString(R.string.that_doesn_t_look_like_a_folio_code_past)
        }
    })
}
