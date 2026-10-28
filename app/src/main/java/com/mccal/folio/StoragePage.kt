package com.mccal.folio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The result of a read: [snapshot] is null when Android would not say. */
private class StorageLoad(val snapshot: StorageSnapshot?)

/**
 * Settings > General > Storage. Real numbers from Android, read off the main thread, and a plain "Unavailable" for
 * anything Android would not tell us. Nothing here deletes or moves a file: the actions open Android's own screens.
 */
@Composable
internal fun StoragePage() {
    val context = LocalContext.current
    val internalLabel = stringResource(R.string.storage_this_phone)
    val removableLabel = stringResource(R.string.storage_removable)
    var refresh by remember { mutableIntStateOf(0) }

    // Read again when the person comes back (from Usage Access, say) and when a drive is attached or removed.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(observer)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) { refresh++ }
        }
        val filter = IntentFilter().apply {
            listOf(Intent.ACTION_MEDIA_MOUNTED, Intent.ACTION_MEDIA_UNMOUNTED, Intent.ACTION_MEDIA_EJECT,
                Intent.ACTION_MEDIA_REMOVED, Intent.ACTION_MEDIA_BAD_REMOVAL).forEach(::addAction)
            addDataScheme("file")
        }
        val registered = runCatching { context.registerReceiver(receiver, filter) }.isSuccess
        onDispose {
            lifecycle.removeObserver(observer)
            if (registered) runCatching { context.unregisterReceiver(receiver) }
        }
    }

    val load by produceState<StorageLoad?>(null, refresh) {
        value = StorageLoad(withContext(Dispatchers.IO) {
            runCatching { StorageProbe.read(context, internalLabel, removableLabel) }.getOrNull()
        })
    }

    val current = load
    val data = current?.snapshot
    when {
        current == null -> SettingsCard(null) { CardNote(stringResource(R.string.storage_reading)) }
        data == null -> SettingsCard(null) {
            StorageValueRow(stringResource(R.string.storage_this_phone), stringResource(R.string.unavailable))
            CardNote(stringResource(R.string.storage_unavailable_note))
        }
        else -> {
            SettingsCard(null) {
                VolumeSummary(data.internal)
                if (data.internal.nearlyFull) CardNote(stringResource(R.string.storage_nearly_full))
            }
            if (data.destinations.isNotEmpty()) SettingsCard(stringResource(R.string.storage_removable)) {
                data.destinations.forEach { VolumeSummary(it) }
            }
            if (data.apps == null) SettingsCard(stringResource(R.string.apps)) {
                IosNavRow(stringResource(R.string.storage_show_app_sizes), null,
                    { runCatching { context.startActivity(Suggestions.usageAccessIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                    "storage-app-sizes-row")
                CardNote(stringResource(R.string.storage_app_sizes_note))
            } else if (data.apps.isNotEmpty()) SettingsCard(stringResource(R.string.apps)) {
                data.apps.forEach { app ->
                    val size = Formatter.formatFileSize(context, app.bytes)
                    StorageValueRow(app.label, size)
                }
                CardNote(stringResource(R.string.storage_measured_by_android))
            }
        }
    }

    SettingsCard(stringResource(R.string.storage_manage)) {
        StorageActionRow(StorageAction.FreeUpSpace, R.string.storage_free_up_space, "storage-free-up")
        StorageActionRow(StorageAction.StorageSettings, R.string.storage_settings, "storage-settings")
        StorageActionRow(StorageAction.Files, R.string.storage_files, "storage-files")
        StorageActionRow(StorageAction.Downloads, R.string.storage_downloads, "storage-downloads")
    }
}

/** A volume's name, "X of Y used", a bar and "Z available", read by TalkBack as one sentence. */
@Composable
private fun VolumeSummary(volume: VolumeInfo) {
    val context = LocalContext.current
    val total = volume.totalBytes
    val used = volume.usedBytes
    val free = volume.freeBytes
    val unavailable = stringResource(R.string.unavailable)
    val usedText = if (used != null && total != null) stringResource(R.string.storage_used_of,
        Formatter.formatFileSize(context, used), Formatter.formatFileSize(context, total)) else unavailable
    val freeText = if (free != null && used != null) stringResource(R.string.storage_available, Formatter.formatFileSize(context, free)) else unavailable
    val fraction = volume.usedFraction
    val shown by animateFloatAsState(fraction ?: 0f,
        if (LocalReduceMotion.current) snap() else FolioMotion.spring(FolioMotion.Settle), label = "storage-bar")
    val sentence = stringResource(R.string.storage_row_description, volume.label, usedText, freeText)
    Column(Modifier.fillMaxWidth().padding(vertical = FolioSpace.SMALL.dp).clearAndSetSemantics { contentDescription = sentence }) {
        Text(volume.label, color = Color.White, fontSize = FolioType.BODY.sp)
        Text(usedText, color = Color.White.copy(alpha = .55f), fontSize = FolioType.BODY.sp)
        Box(Modifier.padding(vertical = FolioSpace.SNUG.dp).fillMaxWidth().height(8.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = .15f)).testTag("storage-bar-${volume.id}")) {
            if (fraction != null) Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(LocalAccent.current.fill))
        }
        Text(freeText, color = Color.White.copy(alpha = .55f), fontSize = FolioType.BODY.sp)
    }
}

/** A label with its value on the right, read as one sentence. */
@Composable
private fun StorageValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = FolioRow.NAV.dp).semantics(mergeDescendants = true) { contentDescription = "$label, $value" },
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = FolioType.BODY.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White.copy(alpha = .55f), fontSize = FolioType.BODY.sp)
    }
}

/** A row that opens one of Android's screens; if the phone has none, a calm notice instead of a crash. */
@Composable
private fun StorageActionRow(action: StorageAction, @androidx.annotation.StringRes title: Int, tag: String) {
    val context = LocalContext.current
    val name = stringResource(title)
    val missing = stringResource(R.string.storage_action_unavailable, name)
    IosNavRow(name, null, {
        if (StorageActions.launch(action, StorageActions.starter(context)) is StorageLaunch.Unavailable) IslandEvents.notice(context, missing)
    }, tag)
}
