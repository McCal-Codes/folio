package com.mccal.folio

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Base64

/** The passphrase record on the phone: a salted hash and a failure count, nothing else (see [DevLock]). Folio Dev only. */
internal object DevLockStore {
    private const val PREFS = "dev_lock"

    fun load(context: Context): DevLockRecord? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).let { p ->
        val salt = p.getString("salt", null) ?: return null
        val hash = p.getString("hash", null) ?: return null
        runCatching {
            DevLockRecord(Base64.getDecoder().decode(salt), Base64.getDecoder().decode(hash), p.getInt("iterations", DevLock.ITERATIONS),
                p.getInt("failures", 0), p.getLong("lockedUntil", 0L))
        }.getOrNull()
    }

    fun save(context: Context, record: DevLockRecord) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("salt", Base64.getEncoder().encodeToString(record.salt)).putString("hash", Base64.getEncoder().encodeToString(record.hash))
            .putInt("iterations", record.iterations).putInt("failures", record.failures).putLong("lockedUntil", record.lockedUntilMs).apply()
    }

    fun clear(context: Context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply() }
}

/** Whether Developer is unlocked right now. In memory only: a restart locks it, and leaving the page does too. */
internal object DevLockSession {
    private val until = mutableLongStateOf(0L)
    fun unlocked(): Boolean = SystemClock.elapsedRealtime() < until.longValue
    fun unlock() { until.longValue = SystemClock.elapsedRealtime() + DevLock.UNLOCK_MS }
    fun lock() { until.longValue = 0L }
}

/** Developer's words stay English, one per line, like the rest of Folio Dev's page (DevBuild.kt). */
private object DevLoginText {
    const val LOCKED_LEAD = "Settings for testing Folio Dev. Locked until you set a passphrase." // english-only
    const val ENTER_LEAD = "Enter your passphrase." // english-only
    const val SET_LEAD = "At least 8 characters. It cannot be recovered: if you forget it, reinstall Folio Dev to clear it." // english-only
    const val NEW_FIELD = "New passphrase" // english-only
    const val AGAIN_FIELD = "Again" // english-only
    const val PASS_FIELD = "Passphrase" // english-only
    const val SET_BUTTON = "Set passphrase" // english-only
    const val SET_START = "Set a passphrase" // english-only
    const val UNLOCK = "Unlock" // english-only
    const val CANCEL = "Cancel" // english-only
    const val SHORT = "Use at least 8 characters." // english-only
    const val BLANK = "A passphrase cannot be only spaces." // english-only
    const val MISMATCH = "The two do not match." // english-only
    const val STORED = "Stored as a salted hash on this phone only. Never in the repository, never sent anywhere." // english-only
    const val UNLOCKED_LEAD = "Unlocked until you leave this page or 15 minutes pass." // english-only
    const val BUILD_GROUP = "This build" // english-only
    const val SHOW_BUILD = "Show the build page again" // english-only
    const val PASS_GROUP = "Passphrase" // english-only
    const val CHANGE = "Change passphrase" // english-only
    const val REMOVE = "Remove passphrase" // english-only
    const val LOCK_NOW = "Lock now" // english-only
    const val GOOGLE_GROUP = "Google account" // english-only
    const val GOOGLE_ROW = "Link a Google account: not built. It needs a sign-in credential made in Google Cloud." // english-only
    fun wrong(left: Int) = if (left > 0) "That is not the passphrase. $left tries left before a short wait." else "That is not the passphrase." // english-only
    fun waiting(seconds: Long) = "Try again in ${if (seconds >= 60) "${(seconds + 59) / 60} min" else "$seconds s"}. Each wrong try after five doubles the wait, up to 15 minutes." // english-only
}

private enum class DevMode { AUTO, SET }

/** Settings › Advanced › Developer (Folio Dev only): a passphrase, then the developer actions that exist today. */
@Composable
internal fun DeveloperPage(onShowBuild: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var record by remember { mutableStateOf(DevLockStore.load(context)) }
    var unlocked by remember { mutableStateOf(DevLockSession.unlocked() && record != null) }
    var mode by remember { mutableStateOf(DevMode.AUTO) }
    var first by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var entry by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    // Leaving the page locks it again, so the passphrase is asked for every time Developer is opened.
    DisposableEffect(Unit) { onDispose { DevLockSession.lock() } }
    val now by produceState(System.currentTimeMillis(), record) { while (true) { value = System.currentTimeMillis(); delay(1_000) } }
    val waitLeft = ((record?.lockedUntilMs ?: 0L) - now).coerceAtLeast(0L)

    when {
        record != null && unlocked && mode == DevMode.AUTO -> {
            Text(DevLoginText.UNLOCKED_LEAD, style = MaterialTheme.typography.bodyMedium)
            SettingsCard(DevLoginText.BUILD_GROUP) {
                IosActionRow(DevLoginText.SHOW_BUILD, onClick = onShowBuild)
            }
            SettingsCard(DevLoginText.PASS_GROUP) {
                IosActionRow(DevLoginText.CHANGE, onClick = { first = ""; again = ""; message = null; mode = DevMode.SET })
                IosActionRow(DevLoginText.REMOVE, onClick = { DevLockStore.clear(context); DevLockSession.lock(); record = null; unlocked = false })
            }
            SettingsCard(DevLoginText.GOOGLE_GROUP) { CardNote(DevLoginText.GOOGLE_ROW) }
            CardAction(DevLoginText.LOCK_NOW, Modifier.fillMaxWidth().testTag("developer-lock"), onClick = { DevLockSession.lock(); unlocked = false })
        }
        record == null && mode == DevMode.AUTO -> {
            Text(DevLoginText.LOCKED_LEAD, style = MaterialTheme.typography.bodyMedium)
            CardAction(DevLoginText.SET_START, Modifier.fillMaxWidth().testTag("developer-set-start"), onClick = { first = ""; again = ""; message = null; mode = DevMode.SET })
        }
        mode == DevMode.SET -> {
            Text(DevLoginText.SET_LEAD, style = MaterialTheme.typography.bodyMedium)
            PassField(DevLoginText.NEW_FIELD, first, "developer-new") { first = it; message = null }
            PassField(DevLoginText.AGAIN_FIELD, again, "developer-again") { again = it; message = null }
            message?.let { Text(it, color = FolioColors.Red, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("developer-message")) }
            CardNote(DevLoginText.STORED)
            CardAction(DevLoginText.SET_BUTTON, Modifier.fillMaxWidth().testTag("developer-set"), onClick = {
                val chars = first.toCharArray()
                message = when {
                    DevLock.problem(chars) == DevLock.Problem.TOO_SHORT -> DevLoginText.SHORT
                    DevLock.problem(chars) == DevLock.Problem.BLANK -> DevLoginText.BLANK
                    first != again -> DevLoginText.MISMATCH
                    else -> null
                }
                if (message == null) {
                    val created = DevLock.create(chars)
                    DevLockStore.save(context, created); record = created
                    DevLockSession.unlock(); unlocked = true; mode = DevMode.AUTO
                }
                chars.fill('\u0000'); if (message == null) { first = ""; again = "" }
            })
            CardAction(DevLoginText.CANCEL, Modifier.fillMaxWidth(), onClick = { first = ""; again = ""; message = null; mode = DevMode.AUTO })
        }
        else -> {
            Text(DevLoginText.ENTER_LEAD, style = MaterialTheme.typography.bodyMedium)
            if (waitLeft > 0) Text(DevLoginText.waiting((waitLeft + 999) / 1000), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("developer-waiting"))
            else {
                PassField(DevLoginText.PASS_FIELD, entry, "developer-pass") { entry = it; message = null }
                message?.let { Text(it, color = FolioColors.Red, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("developer-message")) }
            }
            CardAction(DevLoginText.UNLOCK, Modifier.fillMaxWidth().testTag("developer-unlock"), enabled = waitLeft == 0L, onClick = {
                val chars = entry.toCharArray()
                when (val result = DevLock.check(record, chars, System.currentTimeMillis())) {
                    is DevLock.Result.Unlocked -> { record = result.record; DevLockStore.save(context, result.record); DevLockSession.unlock(); unlocked = true; entry = "" }
                    is DevLock.Result.Wrong -> { record = result.record; DevLockStore.save(context, result.record); message = DevLoginText.wrong(result.triesBeforeWait); entry = "" }
                    is DevLock.Result.Waiting -> message = null
                    DevLock.Result.NotSet -> record = null
                }
                chars.fill('\u0000')
            })
        }
    }
}

@Composable private fun PassField(label: String, value: String, tag: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag))
}
