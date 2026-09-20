package com.mccal.folio

import android.content.Context
import androidx.annotation.StringRes

/**
 * The developer's own switches: what Folio looks like to somebody who hasn't paid, to a supporter, and to whoever is
 * building it — without needing three phones or three codes.
 *
 * Two locks, and both have to be open. The build has to be a development one (`com.mccal.folio.dev`) carrying
 * [BetaKeys.TEST], which a release build does not, and a code signed with that development key has to have been
 * pasted in Settings › Supporter. So a release build has no key to check an unlock against and no switch to find,
 * and the private half of the development key never leaves McCal's machine.
 *
 * Nothing here changes what Folio does for anyone else: the flags live in this phone's own settings, and turning the
 * face back to [Face.FREE] leaves the app exactly as it was.
 */
internal object Dev {
    private const val PREFS = "folio"
    private const val UNLOCKED = "dev_unlocked"
    private const val FACE = "dev_face"
    private const val CHANNEL = "dev_channel"
    private const val FLAG = "dev_flag_"

    /** Which audience Folio should behave as, so all three can be seen from one build. */
    internal enum class Face(val key: String, @StringRes val title: Int, @StringRes val detail: Int) {
        FREE("free", R.string.dev_face_free, R.string.dev_face_free_detail),
        SUPPORTER("supporter", R.string.dev_face_supporter, R.string.dev_face_supporter_detail),
        DEV("dev", R.string.dev_face_dev, R.string.dev_face_dev_detail),
    }

    /**
     * Where Software Update looks, whatever the Beta Updates switch says. [DEFAULT] leaves that switch in charge, so
     * the update path can be tested as a stranger sees it too.
     */
    internal enum class Channel(val key: String, @StringRes val title: Int) {
        DEFAULT("default", R.string.dev_channel_default),
        STABLE("stable", R.string.dev_channel_stable),
        BETA("beta", R.string.dev_channel_beta),
    }

    /**
     * A feature that can be turned off to see the release before it. Each one names the line it arrived on, so a bug
     * reported against an older Folio can be reproduced here rather than by installing that older Folio.
     */
    internal enum class Line(val key: String, @StringRes val title: Int, @StringRes val detail: Int) {
        CLEAR_ICONS("clear_icons", R.string.dev_line_clear_icons, R.string.dev_line_since_065),
        GAUGE("gauge", R.string.dev_line_gauge, R.string.dev_line_since_065),
        BIG_CLOCK("big_clock", R.string.dev_line_big_clock, R.string.dev_line_since_065),
        SOFTWARE_UPDATE("software_update", R.string.dev_line_software_update, R.string.dev_line_since_065),
    }

    /** Whether this build could ever unlock: a development build with a development key compiled into it. */
    fun possible(context: Context): Boolean = devPossible(context.packageName, BetaKeys.TEST)

    fun unlocked(context: Context): Boolean =
        possible(context) && prefs(context).getBoolean(UNLOCKED, false)

    /**
     * Opens the switches when [text] is a code signed with the development key and carrying the `dev` scope. The
     * supporter key is deliberately not accepted: a code handed to a supporter must never open this.
     */
    fun unlock(context: Context, text: String): BetaCodes.Result {
        if (!possible(context)) return BetaCodes.Result.NotOurs
        val result = BetaCodes.verify(text, listOf(BetaKeys.TEST), withdrawn = BetaKeys.WITHDRAWN)
        if (result !is BetaCodes.Result.Valid) return result
        if (BetaCodes.SCOPE_DEV !in result.code.scopes) return BetaCodes.Result.NotOurs
        prefs(context).edit().putBoolean(UNLOCKED, true).apply()
        return result
    }

    /** Closes them again and forgets every switch, so the build behaves like anyone else's. */
    fun lock(context: Context) {
        val editor = prefs(context).edit().remove(UNLOCKED).remove(FACE).remove(CHANNEL)
        for (line in Line.entries) editor.remove(FLAG + line.key)
        editor.apply()
    }

    fun face(context: Context): Face =
        devFace(unlocked(context), prefs(context).getString(FACE, null))

    fun setFace(context: Context, face: Face) {
        prefs(context).edit().putString(FACE, face.key).apply()
    }

    fun channel(context: Context): Channel =
        if (!unlocked(context)) Channel.DEFAULT
        else Channel.entries.firstOrNull { it.key == prefs(context).getString(CHANNEL, null) } ?: Channel.DEFAULT

    fun setChannel(context: Context, channel: Channel) {
        prefs(context).edit().putString(CHANNEL, channel.key).apply()
    }

    /** Whether a feature is on. Off only ever means "hidden here, to see the release before it". */
    fun on(context: Context, line: Line): Boolean =
        !unlocked(context) || prefs(context).getBoolean(FLAG + line.key, true)

    fun setOn(context: Context, line: Line, on: Boolean) {
        prefs(context).edit().putBoolean(FLAG + line.key, on).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, 0)
}

/**
 * Whether a build could ever open the developer switches, apart from Android so it can be checked: a development
 * package id and a development key compiled in. A release build fails both halves.
 */
internal fun devPossible(packageName: String, testKey: String): Boolean =
    packageName.endsWith(".dev") && testKey.isNotBlank()

/**
 * Which audience the app should behave as. Locked is always [Dev.Face.FREE], whatever was stored before the lock —
 * a face left behind by an earlier unlock can't outlive it.
 */
internal fun devFace(unlocked: Boolean, stored: String?): Dev.Face =
    if (!unlocked) Dev.Face.FREE
    else Dev.Face.entries.firstOrNull { it.key == stored } ?: Dev.Face.FREE
