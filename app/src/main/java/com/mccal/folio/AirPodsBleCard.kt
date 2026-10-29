package com.mccal.folio

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

/** Marks [surface] as showing an AirPods battery reading while this screen is resumed, so a scan may run and stops when it is not. */
@Composable
internal fun AirPodsBleSurface(surface: BleSurface, onResume: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, surface) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { onResume(); AirPodsBle.setVisible(context, surface, true) }
                Lifecycle.Event.ON_PAUSE -> AirPodsBle.setVisible(context, surface, false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); AirPodsBle.setVisible(context, surface, false) }
    }
}

/** Wall-clock time for the age of a reading, moved on twice a minute while the card shows. Text only, nothing animates. */
@Composable
internal fun rememberAirPodsNow(): Long = produceState(System.currentTimeMillis()) {
    while (true) { delay(30_000L); value = System.currentTimeMillis() }
}.value

/** A label and a value on one line, read as one item by TalkBack. */
@Composable
private fun AirPodsRow(label: String, value: String, tag: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {}.testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.padding(end = FolioSpace.SMALL.dp), style = MaterialTheme.typography.bodyLarge)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
    }
}

/**
 * Settings › Dynamic Island › AirPods battery: the switch for the Bluetooth advertisement route (off until turned on), the Nearby
 * devices request in context, the reading or the reason there is none, where it came from, when, Refresh and the report.
 */
@Composable
internal fun AirPodsBleSettingsCard() {
    val context = LocalContext.current
    val read by AirPodsBle.state.collectAsState()
    var on by remember { mutableStateOf(AirPodsBle.switchOn(context)) }
    var permitted by remember { mutableStateOf(AirPodsBle.hasPermission(context)) }
    var asked by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val now = rememberAirPodsNow()
    AirPodsBleSurface(BleSurface.SETTINGS) { permitted = AirPodsBle.hasPermission(context) }
    val activity = androidx.activity.compose.LocalActivity.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted; asked = true
        AirPodsBle.recheck(context)
    }
    // After "Don't allow" twice Android stops asking, so the button opens Folio's page in Android Settings instead.
    val viaSettings = asked && !permitted && activity?.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_SCAN) == false
    fun request() {
        if (viaSettings)
            runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) }
        else ask.launch(Manifest.permission.BLUETOOTH_SCAN)
    }
    SettingsCard(stringResource(R.string.airpods_root_title)) {
        CardNote(stringResource(R.string.airpods_ble_note))
        SettingsSwitch(stringResource(R.string.airpods_ble_switch), on, { v ->
            on = v; copied = false
            AirPodsBle.setSwitchOn(context, v)
            if (v && !AirPodsBle.hasPermission(context)) request()
        }, "island-airpods-ble")
        if (!on) return@SettingsCard
        if (!permitted) {
            CardNote(stringResource(R.string.airpods_ble_permission_note), Modifier.testTag("island-airpods-ble-permission"))
            CardAction(stringResource(if (viaSettings) R.string.airpods_ble_open_settings else R.string.airpods_ble_allow), Modifier.fillMaxWidth().testTag("island-airpods-ble-allow"), onClick = ::request)
        }
        val battery = AirPodsBle.battery(now)
        val numbers = battery?.let { AirPodsBleText.line(context, it, now) }
        AirPodsRow(stringResource(R.string.airpods_now), numbers ?: stringResource(AirPodsBleText.summary(read, now) ?: R.string.airpods_ble_expired), "island-airpods-ble-now")
        if (battery != null) AirPodsRow(stringResource(R.string.airpods_source), stringResource(AirPodsBleText.sourceLabel(battery.source)), "island-airpods-ble-source")
        val at = AirPodsBleText.atMillis(read)
        AirPodsRow(stringResource(R.string.airpods_last_read),
            at?.let { android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(it)) } ?: stringResource(R.string.airpods_never), "island-airpods-ble-last")
        CardAction(stringResource(R.string.airpods_refresh), Modifier.fillMaxWidth().testTag("island-airpods-ble-refresh"),
            enabled = permitted && read != AirPodsBleRead.Scanning, onClick = { copied = false; AirPodsBle.refresh(context) })
        CardAction(stringResource(R.string.airpods_report), Modifier.fillMaxWidth().testTag("island-airpods-ble-report"),
            enabled = at != null, onClick = { AirPodsBle.copyReport(context); copied = true })
        if (copied) Text(stringResource(R.string.airpods_copied), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = FolioSpace.SMALL.dp).testTag("island-airpods-ble-copied"))
        CardNote(stringResource(R.string.airpods_ble_report_note))
    }
}
