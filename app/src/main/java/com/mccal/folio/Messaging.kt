package com.mccal.folio

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Messaging helpers: quick reply and mark-as-read through a notification's own actions (works for any
 * messaging app), and opening a conversation in OpenBubbles or BlueBubbles (iMessage on Android).
 * Folio never talks to those apps' servers; it only uses what Android already exposes.
 */
internal object Messaging {
    const val OPENBUBBLES = "com.openbubbles.messaging"
    const val BLUEBUBBLES = "com.bluebubbles.messaging"

    /** Messaging apps Folio can open a conversation in, besides the default texting app. */
    val iMessageApps = listOf(OPENBUBBLES to "OpenBubbles", BLUEBUBBLES to "BlueBubbles")

    fun installed(context: Context, pkg: String) = runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    fun isMessage(n: Notification): Boolean =
        n.category == Notification.CATEGORY_MESSAGE || n.extras.getString(Notification.EXTRA_TEMPLATE)?.endsWith("MessagingStyle") == true

    /** The action that accepts typed text, preferring the app's declared Reply action. */
    fun replyAction(n: Notification): Notification.Action? {
        fun Notification.Action.freeForm() = remoteInputs?.any { it.allowFreeFormInput } == true
        val actions = n.actions.orEmpty().filter { it.freeForm() }
        return actions.firstOrNull { it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY } ?: actions.firstOrNull()
            ?: Notification.WearableExtender(n).actions.firstOrNull { it.freeForm() }
    }

    fun markReadAction(n: Notification): Notification.Action? =
        n.actions.orEmpty().firstOrNull { it.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ }

    /** Sends [text] through the notification's reply action, exactly as the system shade's inline reply does. */
    fun sendReply(context: Context, action: Notification.Action, text: String): Boolean = runCatching {
        val input = action.remoteInputs.first { it.allowFreeFormInput }
        val fill = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        RemoteInput.addResultsToIntent(arrayOf(input), fill, Bundle().apply { putCharSequence(input.resultKey, text) })
        RemoteInput.setResultsSource(fill, RemoteInput.SOURCE_FREE_FORM_INPUT)
        action.actionIntent.send(context, 0, fill)
        true
    }.getOrDefault(false)

    fun send(action: Notification.Action): Boolean = runCatching { action.actionIntent.send(); true }.getOrDefault(false)

    /**
     * Opens a conversation with [address] (phone number or email). [appPackage] null uses the default
     * texting app; OpenBubbles/BlueBubbles get an `imessage:` link, or simply open if they don't take it.
     */
    fun conversationIntent(context: Context, appPackage: String?, address: String): Intent? {
        val pm = context.packageManager
        if (appPackage == null) return Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", address, null))
        val link = Intent(Intent.ACTION_VIEW, Uri.parse("imessage:${Uri.encode(address, "+@.")}")).setPackage(appPackage)
        return if (link.resolveActivity(pm) != null) link else pm.getLaunchIntentForPackage(appPackage)
    }
}

/** iOS-style inline reply: a capsule field with a round send button. Returns to [onDone] after sending. */
@Composable
internal fun QuickReplyField(to: String?, modifier: Modifier = Modifier, onSend: (String) -> Boolean, onDone: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) { delay(80); runCatching { focus.requestFocus() }; keyboard?.show() }
    LaunchedEffect(sent) { if (sent) { keyboard?.hide(); delay(700); onDone() } }
    val submit = {
        if (text.isNotBlank() && !sent) {
            if (onSend(text.trim())) { haptic.perform(FolioHaptic.Commit); sent = true }
            else haptic.perform(FolioHaptic.Refuse)
        }
    }
    Row(modifier.fillMaxWidth().heightIn(min = 40.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f))
        .padding(start = FolioSpace.COMFY.dp, end = FolioSpace.TINY.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).padding(vertical = 9.dp)) {
            if (text.isEmpty()) Text(if (sent) "Sent" else to?.let { "Reply to $it" } ?: "Reply", color = Color.White.copy(alpha = .5f),
                fontSize = FolioType.SUBHEAD.sp, maxLines = 1)
            BasicTextField(text, { if (!sent) text = it }, Modifier.fillMaxWidth().focusRequester(focus),
                textStyle = TextStyle(color = Color.White, fontSize = FolioType.SUBHEAD.sp), cursorBrush = SolidColor(Color.White), maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }))
        }
        val ready = text.isNotBlank() || sent
        Box(Modifier.size(32.dp).clip(CircleShape).background(if (sent) FolioColors.Green else if (ready) LocalAccent.current.fill else Color.White.copy(alpha = .18f))
            .clickable(enabled = ready && !sent) { submit() }.semantics { contentDescription = if (sent) "Sent" else "Send" },
            contentAlignment = Alignment.Center) {
            Icon(if (sent) Icons.Rounded.Check else Icons.Rounded.ArrowUpward, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
    if (sent) LaunchedEffect(Unit) { text = "" }
}

/** Small glass action pill used under notifications ("Reply", "Mark as Read"). */
@Composable
internal fun MessageActionPill(label: String, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 32.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f)).clickable(onClick = onClick)
        .padding(horizontal = FolioSpace.COMFY.dp), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}

/**
 * Call notification buttons. Android 12+ CallStyle notifications carry answer/decline/hang-up intents directly;
 * other calling apps are matched by their action labels.
 */
internal object CallControls {
    enum class Kind { ANSWER, DECLINE, HANG_UP, MUTE, SPEAKER }

    fun isCall(n: Notification) = n.category == Notification.CATEGORY_CALL ||
        n.extras.getString(Notification.EXTRA_TEMPLATE)?.endsWith("CallStyle") == true

    fun isIncoming(n: Notification): Boolean {
        val type = n.extras.getInt(Notification.EXTRA_CALL_TYPE, 0)
        if (type != 0) return type == 1 // CALL_TYPE_INCOMING
        return intent(n, Kind.ANSWER) != null && intent(n, Kind.HANG_UP) == null
    }

    fun intent(n: Notification, kind: Kind): android.app.PendingIntent? {
        val direct = when (kind) {
            Kind.ANSWER -> androidx.core.os.BundleCompat.getParcelable(n.extras, Notification.EXTRA_ANSWER_INTENT, android.app.PendingIntent::class.java)
            Kind.DECLINE -> androidx.core.os.BundleCompat.getParcelable(n.extras, Notification.EXTRA_DECLINE_INTENT, android.app.PendingIntent::class.java)
            Kind.HANG_UP -> androidx.core.os.BundleCompat.getParcelable(n.extras, Notification.EXTRA_HANG_UP_INTENT, android.app.PendingIntent::class.java)
            else -> null
        }
        if (direct != null) return direct
        val words = when (kind) {
            Kind.ANSWER -> listOf("answer", "accept")
            Kind.DECLINE -> listOf("decline", "reject", "dismiss")
            Kind.HANG_UP -> listOf("hang up", "end call", "end")
            Kind.MUTE -> listOf("mute")
            Kind.SPEAKER -> listOf("speaker")
        }
        return n.actions.orEmpty().firstOrNull { action ->
            val label = action.title?.toString()?.lowercase()?.trim() ?: return@firstOrNull false
            words.any { label == it || label.startsWith("$it ") }
        }?.actionIntent
    }
}
