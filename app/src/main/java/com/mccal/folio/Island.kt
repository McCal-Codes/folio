package com.mccal.folio

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A notification as shown in Folio's Notification Center. */
data class NotificationItem(val key: String, val packageName: String, val appLabel: String, val icon: Bitmap?,
    val title: String?, val text: String?, val postTime: Long, val clearable: Boolean,
    val contentIntent: android.app.PendingIntent?,
    /** Messaging notifications: whether quick reply / mark-as-read are offered by the app. */
    val canReply: Boolean = false, val canMarkRead: Boolean = false, val channelId: String? = null)

/** A messaging app's notification channel and whether Android pops it up itself (importance HIGH or above). */
data class MessageChannel(val packageName: String, val appLabel: String, val channelId: String, val channelName: String?, val importance: Int,
    /** False for other apps' notification channels (Brief pop-ups › Other Notifications). */
    val isMessage: Boolean = true) {
    val popsUp get() = importance >= android.app.NotificationManager.IMPORTANCE_HIGH
    fun settingsIntent(): Intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName).putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** What the side-rail island shows: now playing wins over ongoing progress. */
sealed interface IslandActivity {
    val packageName: String
    val title: String
    val icon: Bitmap?

    data class Media(override val packageName: String, override val title: String, val subtitle: String?,
        override val icon: Bitmap?, val playing: Boolean, val token: android.media.session.MediaSession.Token,
        /** Album art (scaled down), when the app provides it. */
        val art: Bitmap? = null) : IslandActivity {
        /** Excluded from equality: a fresh controller object per query must not look like a change. */
        lateinit var controller: MediaController; internal set
    }

    data class Progress(override val packageName: String, override val title: String, val subtitle: String?,
        override val icon: Bitmap?, val fraction: Float?, val key: String) : IslandActivity

    /** Ongoing phone or VoIP call; [since] is when it started (for the running timer). */
    data class Call(override val packageName: String, override val title: String, override val icon: Bitmap?,
        val since: Long?, val key: String,
        /** Ringing (not yet answered). */
        val incoming: Boolean = false,
        /** Caller photo from the call notification, when the app provides one. */
        val avatar: Bitmap? = null,
        val canAnswer: Boolean = false, val canDecline: Boolean = false, val canHangUp: Boolean = false,
        val canMute: Boolean = false, val canSpeaker: Boolean = false) : IslandActivity

    /** Countdown timer or stopwatch from a chronometer notification. [base] is wall-clock millis. */
    data class Timer(override val packageName: String, override val title: String, override val icon: Bitmap?,
        val base: Long, val countDown: Boolean, val key: String) : IslandActivity

    /** Turn-by-turn navigation. */
    data class Navigation(override val packageName: String, override val title: String, val subtitle: String?,
        override val icon: Bitmap?, val key: String) : IslandActivity
}

/** Short-lived system moments the island briefly shows, like iPhone's Dynamic Island. */
sealed interface IslandEvent {
    data class Charging(val level: Int?) : IslandEvent
    data class Silent(val on: Boolean) : IslandEvent
    data class Focus(val on: Boolean) : IslandEvent
    /** Bluetooth headphones or a speaker connected; [name] is the device's own name. */
    data class Bluetooth(val name: String?, val speaker: Boolean = false) : IslandEvent
    /**
     * A new message from any messaging app (OpenBubbles, WhatsApp, Signal, Messages...). With [alert], any other app's
     * notification shown the same way: [sender] is its title (or the app's name) and [avatar] its large icon.
     */
    data class Message(val key: String, val packageName: String, val appLabel: String, val sender: String, val text: String?,
        val avatar: Bitmap?, val appIcon: Bitmap?, val canReply: Boolean, val alert: Boolean = false) : IslandEvent
    /** Brief feedback from Folio itself ("Calendar is unavailable"), with the app's icon when it's about an app. */
    data class Notice(val text: String, val appIcon: Bitmap? = null) : IslandEvent
}

/**
 * Reads ongoing activities (calls, timers, navigation, progress), the active media session, new messages and, when
 * turned on, other apps' new notifications. Nothing is stored or sent anywhere; the island simply mirrors what the
 * system already shows in the shade.
 */
class IslandListenerService : NotificationListenerService() {
    private var sessions: MediaSessionManager? = null
    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { publish() }
    // All reading happens off the main thread, coalesced so bursts of posts cost one pass.
    private val worker = android.os.HandlerThread("folio-island").apply { start() }
    private val workerHandler = android.os.Handler(worker.looper)
    private val publishPass = Runnable { publishNow() }
    private val controllerCallbacks = mutableMapOf<android.media.session.MediaSession.Token, Pair<MediaController, MediaController.Callback>>()

    override fun onListenerConnected() {
        connected.value = true
        sessions = getSystemService(MediaSessionManager::class.java)
        runCatching { sessions?.addOnActiveSessionsChangedListener(sessionListener, component(this), workerHandler) }
        instance = this
        publish()
    }

    override fun onListenerDisconnected() {
        connected.value = false
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionListener) }
        clearControllerCallbacks()
        if (instance === this) instance = null
        mutable.value = null
        notificationsMutable.value = emptyList() // don't keep other apps' content after access is gone
    }

    override fun onDestroy() {
        workerHandler.removeCallbacksAndMessages(null)
        worker.quitSafely()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = publish()
    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        publish()
        if (Messaging.isMessage(sbn.notification)) workerHandler.post { runCatching { announceMessage(sbn, rankingMap) } }
        else if (isAlert(sbn)) workerHandler.post { runCatching { announceAlert(sbn, rankingMap) } }
    }

    /** Last alerting post time per notification key, so updates that don't alert don't pop up again. */
    private val announced = object : LinkedHashMap<String, Long>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 64
    }

    /** What each notification last popped up with, so an app re-posting the same warning doesn't pop up again. */
    private val shownContent = object : LinkedHashMap<String, Pair<Int, Long>>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Int, Long>>?) = size > 64
    }
    private fun isRepeat(key: String, title: String?, text: String?, posted: Long): Boolean {
        // The time the app itself posted goes into the signature: an app re-posting one warning keeps its own
        // `when`, while a person sending "ok" twice in a conversation makes a new notification with a new one —
        // and messaging apps reuse a single key per conversation, so without this the second "ok" never appeared.
        val signature = (title to text).hashCode() * 31 + posted.hashCode()
        val now = System.currentTimeMillis()
        val last = shownContent[key]
        if (last != null && last.first == signature && now - last.second < REPEAT_QUIET_MS) return true
        shownContent[key] = signature to now
        return false
    }

    /**
     * The rules every island pop-up follows: the app's own alert settings, Do Not Disturb, nothing old or repeated,
     * and (when [avoidDouble]) no second banner on top of Android's own pop-up.
     */
    private fun shouldPopUp(sbn: StatusBarNotification, rankingMap: RankingMap?, avoidDouble: Boolean): Boolean {
        val n = sbn.notification
        if (sbn.packageName == packageName || sbn.isOngoing || n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (System.currentTimeMillis() - sbn.postTime > 10_000) return false
        val ranking = Ranking().takeIf { rankingMap?.getRanking(sbn.key, it) == true }
        if (ranking != null && (!ranking.matchesInterruptionFilter() || ranking.isSuspended ||
                ranking.importance < android.app.NotificationManager.IMPORTANCE_DEFAULT)) return false
        // Android shows its own pop-up for high-importance channels; don't stack a second banner on it.
        if (ranking != null && ranking.importance >= android.app.NotificationManager.IMPORTANCE_HIGH && avoidDouble) return false
        val previous = announced[sbn.key]
        if (previous == sbn.postTime || (previous != null && n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)) return false
        announced[sbn.key] = sbn.postTime
        return true
    }

    /** Pops a new message into the island, following the app's own alert settings and Do Not Disturb. */
    private fun announceMessage(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        val n = sbn.notification
        if (!shouldPopUp(sbn, rankingMap, popUpSettings().avoidDouble)) return
        val style = androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val last = style?.messages?.lastOrNull()
        // Our own reply echoed back into the conversation isn't news.
        if (style != null && last != null && (last.person == null || last.person?.name == style.user.name)) return
        val extras = n.extras
        val group = style?.conversationTitle?.toString()?.takeIf { style.isGroupConversation }
        val person = last?.person?.name?.toString() ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val sender = if (group != null && group != person) "$person · $group" else person
        val text = (last?.text ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        val avatar = runCatching { last?.person?.icon?.loadDrawable(this)?.toBitmap(96, 96) }.getOrNull()
            ?: runCatching { n.getLargeIcon()?.loadDrawable(this)?.toBitmap(96, 96) }.getOrNull()
        IslandEvents.post(IslandEvent.Message(sbn.key, sbn.packageName, appLabel(sbn.packageName), sender, text, avatar,
            appIcon(sbn.packageName), Messaging.replyAction(n) != null))
    }
    /** Pops any other app's new notification into the island, when Other Notifications is on for that app. */
    private fun announceAlert(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        val settings = popUpSettings()
        if (!settings.alerts || sbn.packageName in settings.alertAppsOff) return
        if (!shouldPopUp(sbn, rankingMap, settings.avoidDouble)) return
        val n = sbn.notification
        val extras = n.extras
        val label = appLabel(sbn.packageName)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString()?.takeIf { it.isNotBlank() }
        if (title == null && text == null) return
        // `when` is the app's own idea of when this was posted; postTime is Android's, and stands in when an app leaves it unset.
        if (isRepeat(sbn.key, title, text, n.`when`.takeIf { it > 0L } ?: sbn.postTime)) return
        val picture = runCatching { n.getLargeIcon()?.loadDrawable(this)?.toBitmap(96, 96) }.getOrNull()
        IslandEvents.post(IslandEvent.Message(sbn.key, sbn.packageName, label, title ?: label, text, picture,
            appIcon(sbn.packageName), Messaging.replyAction(n) != null, alert = true))
    }

    /** A notification someone would want to see pop up: not media, a call, progress or a background service. */
    private fun isAlert(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (sbn.packageName == packageName || sbn.isOngoing || !sbn.isClearable) return false
        if (n.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_FOREGROUND_SERVICE) != 0) return false
        if (n.category in QUIET_CATEGORIES || CallControls.isCall(n) || hasProgress(n)) return false
        if (n.extras.getString(Notification.EXTRA_TEMPLATE)?.endsWith("MediaStyle") == true) return false
        return n.extras.getCharSequence(Notification.EXTRA_TITLE) != null || n.extras.getCharSequence(Notification.EXTRA_TEXT) != null
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Swiping one away ends its quiet window: what comes back after that is new, not a re-post.
        shownContent.remove(sbn.key)
        publish()
    }
    // Fires when a channel's importance changes (e.g. its pop-up was turned off), so the settings list updates.
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = publish()

    private fun publish() {
        workerHandler.removeCallbacks(publishPass)
        workerHandler.postDelayed(publishPass, PUBLISH_COALESCE_MS)
    }

    private fun publishNow() {
        val controllers = runCatching { sessions?.getActiveSessions(component(this)) }.getOrNull().orEmpty()
        watch(controllers) // every pass, so ended sessions are unregistered even during a call
        mutable.value = runCatching { currentOngoing() ?: currentMedia(controllers) ?: currentProgress() }.getOrNull()
        notificationsMutable.value = runCatching { currentNotifications() }.getOrDefault(emptyList())
        runCatching { rememberMessageChannels() }
    }

    private data class PopUpSettings(val avoidDouble: Boolean, val alerts: Boolean, val alertAppsOff: Set<String>)

    private fun popUpSettings(): PopUpSettings = runCatching {
        val j = org.json.JSONObject(getSharedPreferences(SettingKeys.PREFS, 0).getString(SettingKeys.STATE, "{}") ?: "{}")
        PopUpSettings(j.optBoolean(SettingKeys.MESSAGES_AVOID_DOUBLE, true), j.optBoolean(SettingKeys.ISLAND_ALERTS, false),
            j.optJSONArray(SettingKeys.ISLAND_ALERT_APPS_OFF)?.let { a -> (0 until a.length()).map(a::getString).toSet() }.orEmpty())
    }.getOrDefault(PopUpSettings(avoidDouble = true, alerts = false, alertAppsOff = emptySet()))

    /**
     * Which apps' channels Folio has seen, and whether Android pops each up on its own: for the "turn off Android
     * pop-ups" helper and the Other Notifications app list in Settings.
     */
    private fun rememberMessageChannels() {
        val ranking = Ranking()
        val map = currentRanking ?: return
        val seen = activeNotifications.orEmpty().filter { it.packageName != packageName }
            .mapNotNull { sbn ->
                val message = Messaging.isMessage(sbn.notification)
                if (!message && !isAlert(sbn)) return@mapNotNull null
                if (!map.getRanking(sbn.key, ranking)) return@mapNotNull null
                val channel = ranking.channel ?: return@mapNotNull null
                MessageChannel(sbn.packageName, appLabel(sbn.packageName), channel.id, channel.name?.toString(), ranking.importance, message)
            }
        if (seen.isEmpty()) return
        messageChannelsMutable.value = messageChannelsMutable.value + seen.associateBy { "${it.packageName}|${it.channelId}" }
    }

    private fun currentNotifications(): List<NotificationItem> = activeNotifications.orEmpty()
        .filter { sbn ->
            val n = sbn.notification
            sbn.packageName != packageName && n.flags and Notification.FLAG_GROUP_SUMMARY == 0 && !isOverflowPlaceholder(sbn) &&
                (n.extras.getCharSequence(Notification.EXTRA_TITLE) != null || n.extras.getCharSequence(Notification.EXTRA_TEXT) != null)
        }
        .sortedByDescending { it.postTime }
        .map { sbn ->
            val extras = sbn.notification.extras
            NotificationItem(sbn.key, sbn.packageName, appLabel(sbn.packageName), appIcon(sbn.packageName),
                extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString(),
                sbn.postTime, sbn.isClearable, sbn.notification.contentIntent,
                canReply = Messaging.replyAction(sbn.notification) != null, canMarkRead = Messaging.markReadAction(sbn.notification) != null,
                channelId = sbn.notification.channelId)
        }

    /** Samsung's System UI posts a "1 more notification" stand-in for its own overflow; it isn't a real notification. */
    private fun isOverflowPlaceholder(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName != "com.android.systemui") return false
        val title = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: return false
        return OVERFLOW_TITLE.matches(title)
    }

    /** Calls, navigation and timers outrank media, as on iPhone. */
    private fun currentOngoing(): IslandActivity? {
        val ongoing = runCatching { activeNotifications }.getOrNull().orEmpty()
            .filter { it.isOngoing && it.packageName != packageName }.sortedByDescending { it.postTime }
        fun title(sbn: StatusBarNotification) = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        // Calls first (ringing ones may not be marked ongoing), like iPhone.
        (runCatching { activeNotifications }.getOrNull().orEmpty().filter { it.packageName != packageName && CallControls.isCall(it.notification) }
            .sortedWith(compareByDescending<StatusBarNotification> { CallControls.isIncoming(it.notification) }.thenByDescending { it.postTime })
            .firstOrNull())?.let { sbn ->
            val n = sbn.notification
            val incoming = CallControls.isIncoming(n)
            val since = n.`when`.takeIf { !incoming && (n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) || it > 0) }
            val person = runCatching { androidx.core.os.BundleCompat.getParcelable(n.extras, Notification.EXTRA_CALL_PERSON, android.app.Person::class.java) }.getOrNull()
            val avatar = runCatching { person?.icon?.loadDrawable(this)?.toBitmap(96, 96) }.getOrNull()
                ?: runCatching { n.getLargeIcon()?.loadDrawable(this)?.toBitmap(96, 96) }.getOrNull()
            return IslandActivity.Call(sbn.packageName, person?.name?.toString() ?: title(sbn) ?: "Call", appIcon(sbn.packageName), since, sbn.key,
                incoming = incoming, avatar = avatar,
                canAnswer = CallControls.intent(n, CallControls.Kind.ANSWER) != null,
                canDecline = CallControls.intent(n, CallControls.Kind.DECLINE) != null,
                canHangUp = CallControls.intent(n, CallControls.Kind.HANG_UP) != null,
                canMute = CallControls.intent(n, CallControls.Kind.MUTE) != null,
                canSpeaker = CallControls.intent(n, CallControls.Kind.SPEAKER) != null)
        }
        ongoing.firstOrNull { it.notification.category == Notification.CATEGORY_NAVIGATION }?.let { sbn ->
            val extras = sbn.notification.extras
            return IslandActivity.Navigation(sbn.packageName, title(sbn) ?: "Navigating",
                extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(), appIcon(sbn.packageName), sbn.key)
        }
        ongoing.firstOrNull { it.notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) }?.let { sbn ->
            val n = sbn.notification
            return IslandActivity.Timer(sbn.packageName, title(sbn) ?: appLabel(sbn.packageName), appIcon(sbn.packageName),
                n.`when`, n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN), sbn.key)
        }
        return null
    }

    /**
     * A paused session that nobody comes back to: some players (Spotify among them) keep their session alive long
     * after the app is gone, and the island was left showing a track whose buttons did nothing. Playing music is
     * always shown; a pause is held for a while, then let go. Reported on r/GalaxyFold, 18 Sep 2026.
     */
    private fun stillWorthShowing(controller: MediaController): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        val since = pausedSince.getOrPut(controller.packageName) { now }
        return now - since < PAUSED_KEEP_MS
    }

    private fun currentMedia(controllers: List<MediaController>): IslandActivity.Media? {
        // A session that has gone away takes its pause clock with it, so a player that comes back paused gets its
        // fifteen minutes again instead of being judged on a pause from an hour ago — and the map can't grow forever.
        pausedSince.keys.retainAll(controllers.map { it.packageName }.toSet())
        // Anything playing is not forgotten, so its pause clock starts again from zero next time it stops.
        controllers.filter { playbackIsLive(it.playbackState?.state) }
            .forEach { pausedSince.remove(it.packageName) }
        val order = controllers.filter { playbackIsLive(it.playbackState?.state) } +
            controllers.filter { it.playbackState?.state == PlaybackState.STATE_PAUSED && stillWorthShowing(it) }
        // One session without a title used to hide a perfectly good one behind it, so each is tried in turn.
        for (active in order) {
            val meta = active.metadata ?: continue
            val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: continue
            val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            return IslandActivity.Media(active.packageName, title, artist,
                appIcon(active.packageName), playbackIsLive(active.playbackState?.state), active.sessionToken,
                art = albumArt(active.packageName, title, artist, meta))
                .also { it.controller = active }
        }
        return null
    }

    /** Cached per track so each playback update reuses one small bitmap (stable equality, no re-scaling). */
    private fun albumArt(pkg: String, title: String, artist: String?, meta: MediaMetadata): Bitmap? {
        val key = "$pkg|$title|$artist"
        artCache.get(key)?.let { return it }
        val source = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON) ?: return null
        val scaled = runCatching {
            val max = 256
            if (source.width <= max && source.height <= max) source
            else Bitmap.createScaledBitmap(source, max, (max * source.height / source.width.coerceAtLeast(1)).coerceAtLeast(1), true)
        }.getOrNull() ?: return null
        artCache.put(key, scaled)
        return scaled
    }

    private fun currentProgress(): IslandActivity.Progress? {
        val sbn = runCatching { activeNotifications }.getOrNull().orEmpty()
            .filter { it.isOngoing && it.packageName != packageName && hasProgress(it.notification) }
            .maxByOrNull { it.postTime } ?: return null
        val extras = sbn.notification.extras
        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val progress = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: appLabel(sbn.packageName)
        return IslandActivity.Progress(sbn.packageName, title, extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            appIcon(sbn.packageName), if (!indeterminate && max > 0) (progress.toFloat() / max).coerceIn(0f, 1f) else null, sbn.key)
    }

    private fun hasProgress(n: Notification): Boolean {
        val extras = n.extras
        return extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 ||
            extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) ||
            extras.getString(Notification.EXTRA_TEMPLATE)?.endsWith("ProgressStyle") == true
    }

    private fun watch(controllers: List<MediaController>) {
        val tokens = controllers.map { it.sessionToken }.toSet()
        controllerCallbacks.keys.filter { it !in tokens }.forEach { token ->
            controllerCallbacks.remove(token)?.let { (controller, callback) -> controller.unregisterCallback(callback) }
        }
        controllers.filter { it.sessionToken !in controllerCallbacks }.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
            }
            controller.registerCallback(callback, workerHandler)
            controllerCallbacks[controller.sessionToken] = controller to callback
        }
    }

    private fun clearControllerCallbacks() {
        controllerCallbacks.values.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        controllerCallbacks.clear()
    }

    private fun appIcon(pkg: String): Bitmap? = iconCache.get(pkg) ?: loadIcon(pkg)?.also { iconCache.put(pkg, it) }
    private fun loadIcon(pkg: String): Bitmap? = run {
        runCatching { packageManager.getApplicationIcon(pkg).toBitmap(96, 96) }.getOrNull()
    }

    private fun appLabel(pkg: String): String = labelCache.get(pkg) ?: runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg).also { labelCache.put(pkg, it) }

    companion object {
        /** An unchanged notification re-posted within this time stays in Notification Center without popping up again. */
        private const val REPEAT_QUIET_MS = 30 * 60_000L
        private val messageChannelsMutable = MutableStateFlow<Map<String, MessageChannel>>(emptyMap())
        /** Messaging channels seen since Folio started (kept in memory only). */
        val messageChannels: StateFlow<Map<String, MessageChannel>> = messageChannelsMutable.asStateFlow()
        private val notificationsMutable = MutableStateFlow<List<NotificationItem>>(emptyList())
        val notifications: StateFlow<List<NotificationItem>> = ScreenshotMode.hide(notificationsMutable.asStateFlow(), emptyList())

        fun dismiss(key: String) { runCatching { instance?.cancelNotification(key) } }
        private fun find(key: String) = runCatching { instance?.activeNotifications?.firstOrNull { it.key == key } }.getOrNull()
        /** Answer, decline, hang up, mute or speaker through the call notification's own buttons. */
        internal fun callAction(context: Context, key: String, kind: CallControls.Kind): Boolean {
            val intent = find(key)?.notification?.let { CallControls.intent(it, kind) } ?: return false
            // Answering usually opens the in-call screen, so allow it to start from here.
            return sendAllowingLaunch(context, intent)
        }
        /** Quick reply through the app's own reply action; false if the notification or action is gone. */
        fun reply(context: Context, key: String, text: String): Boolean =
            find(key)?.notification?.let(Messaging::replyAction)?.let { Messaging.sendReply(context, it, text) } == true
        fun markRead(key: String): Boolean = find(key)?.notification?.let(Messaging::markReadAction)?.let(Messaging::send) == true
        /** Opens a notification by key (its own tap action, or the app). */
        fun openKey(context: Context, key: String, packageName: String) {
            if (!sendAllowingLaunch(context, find(key)?.notification?.contentIntent))
                context.packageManager.getLaunchIntentForPackage(packageName)
                    ?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
        fun dismissAll() { runCatching { instance?.cancelAllNotifications() } }
        /** Hides a notification for a while; Android brings it back afterwards. */
        fun snooze(key: String, millis: Long) { runCatching { instance?.snoozeNotification(key, millis) } }
        fun openNotification(context: Context, item: NotificationItem) {
            val sent = sendAllowingLaunch(context, item.contentIntent)
            if (!sent) context.packageManager.getLaunchIntentForPackage(item.packageName)
                ?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
        private val mutable = MutableStateFlow<IslandActivity?>(null)
        val activity: StateFlow<IslandActivity?> = ScreenshotMode.hide(mutable.asStateFlow(), null)
        val connected = MutableStateFlow(false)
        private const val PUBLISH_COALESCE_MS = 120L
        private val iconCache = android.util.LruCache<String, Bitmap>(64)
        private val artCache = android.util.LruCache<String, Bitmap>(8)
        /** When each app's playback was first seen paused, so a forgotten session doesn't sit in the island forever. */
        private val pausedSince = mutableMapOf<String, Long>()
        /** How long a paused track stays in the island: long enough to come back to, short enough not to be clutter. */
        private const val PAUSED_KEEP_MS = 15 * 60 * 1000L

        /**
         * Playback that is running, not stopped: buffering and connecting count, because a track that pauses to load
         * would otherwise drop out of the island and reappear a second later, once on every skip.
         */
        internal fun playbackIsLive(state: Int?) = state == PlaybackState.STATE_PLAYING ||
            state == PlaybackState.STATE_BUFFERING || state == PlaybackState.STATE_CONNECTING
        private val QUIET_CATEGORIES = setOf(Notification.CATEGORY_CALL, Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_SERVICE, Notification.CATEGORY_NAVIGATION, Notification.CATEGORY_STATUS, "stopwatch", "location_sharing", "workout")
        private val OVERFLOW_TITLE = Regex("^\\d+ more notifications?$", RegexOption.IGNORE_CASE)
        private val labelCache = android.util.LruCache<String, String>(128)

        /** Sends another app's PendingIntent so it may open even though Folio's window isn't in front (Android 14+ rule). */
        internal fun sendAllowingLaunch(context: Context, intent: android.app.PendingIntent?): Boolean = intent != null && runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                val options = android.app.ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle()
                intent.send(context, 0, null, null, null, null, options)
            } else intent.send()
            true
        }.getOrDefault(false)
        private var instance: IslandListenerService? = null

        fun component(context: Context) = ComponentName(context, IslandListenerService::class.java)

        fun hasAccess(context: Context): Boolean =
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?.contains(component(context).flattenToString()) == true

        fun accessSettingsIntent(context: Context): Intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Opens whatever the island is showing (the notification's own tap action, or the media app). */
        fun open(context: Context, activity: IslandActivity) {
            val service = instance
            val key = when (activity) {
                is IslandActivity.Progress -> activity.key
                is IslandActivity.Call -> activity.key
                is IslandActivity.Timer -> activity.key
                is IslandActivity.Navigation -> activity.key
                is IslandActivity.Media -> null
            }
            val pending = key?.let { k ->
                runCatching { service?.activeNotifications?.firstOrNull { it.key == k }?.notification?.contentIntent }.getOrNull()
            } ?: (activity as? IslandActivity.Media)?.controller?.sessionActivity
            val sent = sendAllowingLaunch(context, pending)
            if (!sent) context.packageManager.getLaunchIntentForPackage(activity.packageName)
                ?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
    }
}

private val IslandAccent = Color(0xFF6EE39A)
