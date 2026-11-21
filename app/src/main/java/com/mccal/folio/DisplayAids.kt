package com.mccal.folio

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
 * Android's own developer switches that make a screenshot or a fold easier to read, and that an app is allowed to flip:
 * [SHOW_TAPS] and [POINTER_LOCATION] live in Settings.System (Modify system settings), [KEEP_ACTIVITIES] in Settings.Global
 * (the WRITE_SECURE_SETTINGS grant). Layout bounds, Force RTL, Force resizable and Smallest width are system properties or
 * need Android's own page, so Folio only links there. None of these flash or strobe.
 */
internal enum class DisplayAid(val heading: String, val note: String, val key: String, val global: Boolean) {
    SHOW_TAPS("Show taps", "A dot where each touch lands, so a screenshot shows what was pressed.", "show_touches", false), // english-only
    POINTER_LOCATION("Pointer location", "A bar of touch numbers along the top edge.", "pointer_location", false), // english-only
    KEEP_ACTIVITIES("Don't keep activities", "Android destroys Folio each time you leave it, to test coming back after a fold.", "always_finish_activities", true), // english-only
}

/** What Folio can do about a switch right now. */
internal enum class AidAccess { WRITABLE, ALLOW_MODIFY_SETTINGS, NEEDS_SECURE_GRANT }

/** A secure grant also covers Settings.System, so it unlocks every switch; Modify system settings covers only the System ones. */
internal fun aidAccess(aid: DisplayAid, canWriteSystem: Boolean, canWriteSecure: Boolean): AidAccess = when {
    canWriteSecure -> AidAccess.WRITABLE
    aid.global -> AidAccess.NEEDS_SECURE_GRANT
    canWriteSystem -> AidAccess.WRITABLE
    else -> AidAccess.ALLOW_MODIFY_SETTINGS
}

/** The one command, run once from a computer, that lets Folio Dev change the protected switch. */
internal fun secureGrantCommand(packageName: String) = "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS" // english-only

internal object DisplayAids {
    fun isOn(context: Context, aid: DisplayAid): Boolean = runCatching {
        if (aid.global) Settings.Global.getInt(context.contentResolver, aid.key, 0) else Settings.System.getInt(context.contentResolver, aid.key, 0)
    }.getOrDefault(0) == 1

    /** False when Android refused: the grant was taken back, or this phone does not let apps write the setting. */
    fun set(context: Context, aid: DisplayAid, on: Boolean): Boolean = runCatching {
        val value = if (on) 1 else 0
        if (aid.global) Settings.Global.putInt(context.contentResolver, aid.key, value) else Settings.System.putInt(context.contentResolver, aid.key, value)
    }.getOrDefault(false)

    fun access(context: Context, aid: DisplayAid): AidAccess = aidAccess(aid, Settings.System.canWrite(context),
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED)
}

private object DisplayAidsText {
    const val GROUP = "Display aids" // english-only
    const val ALLOW = "Allow Modify system settings" // english-only
    const val OPEN_ANDROID = "Open Android Developer options" // english-only
    const val OTHERS = "Layout bounds, Force RTL, Force activities to be resizable and Smallest width stay in Android's own page: an app cannot change them." // english-only
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
                AidAccess.ALLOW_MODIFY_SETTINGS -> IosActionRow(DisplayAidsText.ALLOW, "display-aid-allow-${aid.key}") {
                    runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                AidAccess.NEEDS_SECURE_GRANT -> CardNote(DisplayAidsText.grant(secureGrantCommand(context.packageName)))
            }
        }
        IosActionRow(DisplayAidsText.OPEN_ANDROID, "display-aids-android") {
            runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        CardNote(DisplayAidsText.OTHERS)
    }
}
