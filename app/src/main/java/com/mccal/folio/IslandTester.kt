package com.mccal.folio

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Folio Dev only: puts made-up calls, timers, music and pop-ups in the island so its placement, expanding and motion
 * can be checked without a real call or notification. It feeds the island's own models, so it tests the island's UI,
 * not how Android's notifications are read. Every entry point refuses unless [DevBuild.isDevApp], so a public build
 * can never be driven by it. Each button fires once; nothing repeats.
 */
object IslandTester {
    /** A fake activity never lingers: it clears itself after this long. */
    const val FAKE_MS = 60_000L
    private const val APP = "Example Messages"
    private const val CALLER = "Alex Example"

    private val fakeFlow = MutableStateFlow<IslandActivity?>(null)
    /** The made-up activity, when one is set. */
    val fake: StateFlow<IslandActivity?> = fakeFlow.asStateFlow()

    internal var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timeout: Job? = null
    private var session: MediaSession? = null

    /** The island shows [fake] when one is set, else the [real] activity. */
    fun overlay(real: StateFlow<IslandActivity?>, scope: CoroutineScope): StateFlow<IslandActivity?> =
        combine(real, fakeFlow) { r, f -> f ?: r }.stateIn(scope, SharingStarted.Eagerly, fakeFlow.value ?: real.value)

    /** Shows [activity] for [FAKE_MS], replacing any earlier one. Does nothing outside Folio Dev. */
    fun show(context: Context, activity: IslandActivity): Boolean {
        if (!DevBuild.isDevApp(context)) return false
        releaseSession()
        fakeFlow.value = activity
        timeout?.cancel()
        timeout = scope.launch { delay(FAKE_MS); clearNow() }
        return true
    }

    /** Puts the real activity back. Does nothing outside Folio Dev. */
    fun clear(context: Context) {
        if (!DevBuild.isDevApp(context)) return
        clearNow()
    }

    private fun clearNow() {
        timeout?.cancel(); timeout = null
        fakeFlow.value = null
        releaseSession()
    }

    private fun releaseSession() { session?.let { runCatching { it.isActive = false; it.release() } }; session = null }

    private fun icon(context: Context): Bitmap? =
        runCatching { context.packageManager.getApplicationIcon(context.packageName).toBitmap(96, 96) }.getOrNull()

    fun incomingCall(context: Context) = show(context, IslandActivity.Call(context.packageName, CALLER, icon(context), null, "tester-call",
        incoming = true, canAnswer = true, canDecline = true))

    fun ongoingCall(context: Context) = show(context, IslandActivity.Call(context.packageName, CALLER, icon(context),
        System.currentTimeMillis() - 12_000, "tester-call", canHangUp = true, canMute = true, canSpeaker = true))

    fun countdown(context: Context) = show(context, IslandActivity.Timer(context.packageName, "Example timer", icon(context), // english-only
        System.currentTimeMillis() + 5 * 60_000L, true, "tester-timer"))

    fun navigation(context: Context) = show(context, IslandActivity.Navigation(context.packageName, "Turn left in 300 ft", // english-only
        "Example Street", icon(context), "tester-nav")) // english-only

    fun progress(context: Context) = show(context, IslandActivity.Progress(context.packageName, "Example download", // english-only
        "40%", icon(context), 0.4f, "tester-progress"))

    /** A real [MediaSession] held here, with a title, an artist and a playing state; it plays no audio. */
    fun music(context: Context): Boolean {
        if (!DevBuild.isDevApp(context)) return false
        val s = MediaSession(context, "FolioIslandTester") // english-only
        s.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Example Song")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "Example Artist").putLong(MediaMetadata.METADATA_KEY_DURATION, 180_000L).build())
        s.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 30_000L, 0f)
            .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS).build())
        s.isActive = true
        val media = IslandActivity.Media(context.packageName, "Example Song", "Example Artist", icon(context), true, s.sessionToken) // english-only
        media.controller = MediaController(context, s.sessionToken)
        show(context, media) // releases any earlier session first
        session = s
        return true
    }

    private fun post(context: Context, event: IslandEvent): Boolean {
        if (!DevBuild.isDevApp(context)) return false
        IslandEvents.post(event)
        return true
    }

    private fun message(context: Context, sender: String, text: String, canReply: Boolean, alert: Boolean) =
        IslandEvent.Message("tester-message", context.packageName, APP, sender, text, null, icon(context), canReply, alert)

    fun messageWithReply(context: Context) = post(context, message(context, CALLER, "Are we still on for lunch?", canReply = true, alert = false)) // english-only

    fun otherAppNotification(context: Context) =
        post(context, message(context, "Example Calendar", "Meeting starts in 10 minutes.", canReply = false, alert = true)) // english-only

    fun longMessage(context: Context) = post(context, message(context, CALLER, "This is an example of a long message that goes on for a while " +
        "so the island has to cut it short. It keeps going with more words about nothing in particular, only to fill up the " +
        "space and show where the text stops and the ellipsis begins on a narrow screen.", canReply = true, alert = false))

    fun notice(context: Context) = post(context, IslandEvent.Notice("Example notice", icon(context))) // english-only

    fun noticeWithUndo(context: Context) = post(context, IslandEvent.Notice("Example item removed", icon(context), // english-only
        NoticeAction("Undo") { post(context, IslandEvent.Notice("Example item restored", icon(context))) })) // english-only

    fun charging(context: Context) = post(context, IslandEvent.Charging(82))
    fun silent(context: Context, on: Boolean) = post(context, IslandEvent.Silent(on))
    fun focusOn(context: Context) = post(context, IslandEvent.Focus(true))
    fun bluetooth(context: Context) = post(context, IslandEvent.Bluetooth("Example Headphones")) // english-only
}

/** Settings > Developer > Island tester (Folio Dev only). Words stay English, like the rest of the Developer page. */
@Composable
internal fun IslandTesterCard() {
    val context = LocalContext.current
    if (!DevBuild.isDevApp(context)) return
    SettingsCard("Island tester") { // english-only
        CardNote("Shows made-up Example calls, timers, music and messages in the island so you can check where it sits, how it expands and how it moves. It tests the island's own screen, not how Android reads notifications. Each button fires once, and an activity clears itself after 1 minute.") // english-only
    }
    SettingsCard("Activities") { // english-only
        IosActionRow("Incoming call") { IslandTester.incomingCall(context) } // english-only
        IosActionRow("Ongoing call") { IslandTester.ongoingCall(context) } // english-only
        IosActionRow("Timer (countdown)") { IslandTester.countdown(context) } // english-only
        IosActionRow("Navigation") { IslandTester.navigation(context) } // english-only
        IosActionRow("Progress (40%)") { IslandTester.progress(context) } // english-only
        IosActionRow("Music") { IslandTester.music(context) } // english-only
        IosActionRow("Clear") { IslandTester.clear(context) } // english-only
    }
    SettingsCard("Events") { // english-only
        IosActionRow("Message with reply") { IslandTester.messageWithReply(context) } // english-only
        IosActionRow("Notification from another app") { IslandTester.otherAppNotification(context) } // english-only
        IosActionRow("Long message") { IslandTester.longMessage(context) } // english-only
        IosActionRow("Notice") { IslandTester.notice(context) } // english-only
        IosActionRow("Notice with Undo") { IslandTester.noticeWithUndo(context) } // english-only
        IosActionRow("Charging 82%") { IslandTester.charging(context) } // english-only
        IosActionRow("Silent on") { IslandTester.silent(context, true) } // english-only
        IosActionRow("Silent off") { IslandTester.silent(context, false) } // english-only
        IosActionRow("Focus on") { IslandTester.focusOn(context) } // english-only
        IosActionRow("Bluetooth headphones") { IslandTester.bluetooth(context) } // english-only
    }
}
