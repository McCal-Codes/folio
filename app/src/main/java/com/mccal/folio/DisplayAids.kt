package com.mccal.folio

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Android's own developer switches that make a screenshot or a fold easier to read. [STAY_AWAKE] and [FORCE_RESIZABLE] live in
 * Settings.Global, so the WRITE_SECURE_SETTINGS grant lets Folio flip them. Stay awake is a bitmask: 7 is AC, USB and wireless.
 * [SHOW_TAPS] and [POINTER_LOCATION] are private System settings that Android refuses to let an app write, and [KEEP_ACTIVITIES]
 * also needs the activity manager told (its setting alone does nothing), so Folio shows where those stand and sends you to
 * Android's Developer options to change them, as it does for Layout bounds, Force RTL and Smallest width. None of these flash or strobe.
 */
internal enum class DisplayAid(val heading: String, val note: String, val key: String, val global: Boolean, val onValue: Int = 1, val viaAndroid: Boolean = false) {
    SHOW_TAPS("Show taps", "A dot where each touch lands, so a screenshot shows what was pressed.", "show_touches", false, viaAndroid = true), // english-only
    POINTER_LOCATION("Pointer location", "A bar of touch numbers along the top edge.", "pointer_location", false, viaAndroid = true), // english-only
    KEEP_ACTIVITIES("Don't keep activities", "Android destroys Folio each time you leave it, to test coming back after a fold.", "always_finish_activities", true, viaAndroid = true), // english-only
    STAY_AWAKE("Stay awake while charging", "The screen stays lit while the phone is plugged in or on a charger. Turn it off when you finish.", "stay_on_while_plugged_in", true, onValue = 7), // english-only
    FORCE_RESIZABLE("Force activities to be resizable", "Apps that refuse to resize follow a fold or flip window. Android may need a restart before it takes effect.", "force_resizable_activities", true), // english-only
}

/** What Folio can do about a switch right now. */
internal enum class AidAccess { WRITABLE, NEEDS_SECURE_GRANT, ANDROID_ONLY }

/** The switches Android keeps to itself are read-only here; the others need the secure grant. */
internal fun aidAccess(aid: DisplayAid, canWriteSecure: Boolean): AidAccess = when {
    aid.viaAndroid -> AidAccess.ANDROID_ONLY
    canWriteSecure -> AidAccess.WRITABLE
    else -> AidAccess.NEEDS_SECURE_GRANT
}

/** The one command, run once from a computer, that lets Folio Dev change the protected switch. */
internal fun secureGrantCommand(packageName: String) = "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS" // english-only

internal object DisplayAids {
    fun isOn(context: Context, aid: DisplayAid): Boolean = runCatching {
        if (aid.global) Settings.Global.getInt(context.contentResolver, aid.key, 0) else Settings.System.getInt(context.contentResolver, aid.key, 0)
    }.getOrDefault(0) != 0

    /** False when Android refused: the grant was taken back, or this phone does not let apps write the setting. */
    fun set(context: Context, aid: DisplayAid, on: Boolean): Boolean = runCatching {
        val value = if (on) aid.onValue else 0
        if (aid.global) Settings.Global.putInt(context.contentResolver, aid.key, value) else Settings.System.putInt(context.contentResolver, aid.key, value)
    }.getOrDefault(false)

    fun access(context: Context, aid: DisplayAid): AidAccess = aidAccess(aid,
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED)
}

private object DisplayAidsText {
    const val GROUP = "Display aids" // english-only
    const val OPEN_ANDROID = "Open Android Developer options" // english-only
    const val OTHERS = "Show taps, Pointer location, Don't keep activities, Layout bounds, Force RTL and Smallest width are changed in Android's own page: an app cannot change them." // english-only
    const val IN_ANDROID = "Change it in Android Developer options." // english-only
    fun grant(command: String) = "Needs one grant, run once from a computer: $command" // english-only
}

/** Settings › Advanced › Developer, unlocked (Folio Dev only): the Android switches above, read live and flipped in place. */
@Composable
internal fun DisplayAidsCard() {
    val context = LocalContext.current
    // Re-read after a write and when coming back from Android's own pages, where the same switches can change.
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    SettingsCard(DisplayAidsText.GROUP) {
        DisplayAid.entries.forEach { aid ->
            val access = remember(tick) { DisplayAids.access(context, aid) }
            val on = remember(tick) { DisplayAids.isOn(context, aid) }
            SettingsSwitch(aid.heading, on, { DisplayAids.set(context, aid, it); tick++ }, "display-aid-${aid.key}", enabled = access == AidAccess.WRITABLE)
            when (access) {
                AidAccess.WRITABLE -> CardNote(aid.note)
                AidAccess.ANDROID_ONLY -> CardNote("${aid.note} ${DisplayAidsText.IN_ANDROID}")
                AidAccess.NEEDS_SECURE_GRANT -> CardNote(DisplayAidsText.grant(secureGrantCommand(context.packageName)))
            }
        }
        IosActionRow(DisplayAidsText.OPEN_ANDROID, "display-aids-android") {
            runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        CardNote(DisplayAidsText.OTHERS)
    }
}
