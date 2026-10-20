@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mccal.folio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicInteger

/**
 * The box a reply is typed into when Folio's panels are drawn over another app. The overlay window can't take the keyboard (Android only
 * lets a window be its target if it is focusable, and the overlay is not), so Reply starts this small translucent activity instead: it
 * is an ordinary window, so the keyboard works on every phone, and it sends through the same quick reply as Home. It has its own task and
 * leaves the recents list, so when it finishes you are back in the app you were using. Closes on a tap outside, after sending, and with
 * Back. Never opens while the phone is locked: it does not ask to show over the lock screen.
 */
class ShadeReplyActivity : ComponentActivity() {
    /** The notification's key and who it is from; follows the latest intent, since this activity is singleTask. */
    private val target = mutableStateOf<Pair<String, String?>?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        read(intent)
    }

    private fun read(intent: Intent) {
        target.value = intent.getStringExtra(EXTRA_KEY)?.takeIf { it.isNotBlank() }?.let { it to intent.getStringExtra(EXTRA_TO) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        created.incrementAndGet()
        read(intent)
        setContent {
            val current = target.value
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { shown = true }
            // Nothing to reply to (an intent without a key): leave at once.
            if (current == null) LaunchedEffect(Unit) { finish() }
            else Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f))
                .clickable(remember { MutableInteractionSource() }, null) { finish() }, contentAlignment = Alignment.BottomCenter) {
                AnimatedVisibility(shown, enter = fadeIn() + slideInVertically(FolioMotion.spring(FolioMotion.Appear)) { it / 3 }) {
                    Column(Modifier.navigationBarsPadding().windowInsetsPadding(WindowInsets.imeAnimationTarget).padding(FolioSpace.LARGE.dp)
                        .widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp))
                        .background(FolioColors.SecondaryBackground.copy(alpha = .94f))
                        // A tap inside the card must not fall through to the scrim and close it.
                        .clickable(remember { MutableInteractionSource() }, null) {}.padding(18.dp)) {
                        QuickReplyField(current.second, Modifier, onSend = { IslandListenerService.reply(this@ShadeReplyActivity, current.first, it) },
                            onDone = { finish() })
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_KEY = "reply_key"
        private const val EXTRA_TO = "reply_to"

        /** How many times it has been created: the overlay compares it before and after a start to see whether Android allowed it. */
        internal val created = AtomicInteger(0)

        /** Starts the reply box for [item], from a service or any other context. */
        internal fun start(context: Context, item: NotificationItem) {
            context.startActivity(Intent(context, ShadeReplyActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_KEY, item.key).putExtra(EXTRA_TO, item.title ?: item.appLabel))
        }
    }
}
