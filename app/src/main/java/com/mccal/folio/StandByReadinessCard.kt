package com.mccal.folio

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private fun StandByRoute.labelRes(): Int = when (this) {
    StandByRoute.HALF_OPEN -> R.string.standby_half_open_like_a_laptop
    StandByRoute.CHARGING -> R.string.standby_while_charging_on_its_side
    StandByRoute.OFF_CHARGER -> R.string.standby2_off_charger
    StandByRoute.TENT -> R.string.standby_standing_as_a_tent
}

private fun StandByRequirement.labelRes(): Int = when (this) {
    StandByRequirement.PLUGGED -> R.string.standby_check_plugged
    StandByRequirement.UNPLUGGED -> R.string.standby_check_unplugged
    StandByRequirement.SIDEWAYS -> R.string.standby_check_sideways
    StandByRequirement.STILL -> R.string.standby_check_still
    StandByRequirement.HALF_OPEN -> R.string.standby_check_half_open
    StandByRequirement.ON_COVER -> R.string.standby_check_on_cover
    StandByRequirement.TENT_POSE -> R.string.standby_check_tent
    StandByRequirement.BATTERY -> R.string.standby_check_battery
}

/** Plugged in and the battery percent, read while the card is on screen. Null level until Android says. */
@Composable
private fun rememberPowerNow(context: Context): Pair<Boolean, Int?> {
    var power by remember { mutableStateOf(false to null as Int?) }
    DisposableEffect(context) {
        fun read(intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            power = isCharging(intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1), intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) to
                (if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null)
        }
        val receiver = object : BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) = read(intent) }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::read)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return power
}

/**
 * Settings › Fold & Displays › StandBy: live, for each way that is switched on, what StandBy is waiting for. It exists
 * because StandBy asks several things at once (plugged in, on its side, still, or an eligible fold pose) and a phone
 * that is missing one just looks like StandBy never works. The numbers come from the same signals Home uses
 * ([rememberStandBySignals]); motion is read while this card is up so "on its side" and "still" show before the charger
 * goes in. Nothing is stored or sent.
 */
@Composable
internal fun StandByReadinessCard(activity: Activity, ways: StandByWays) {
    val context = LocalContext.current
    val (plugged, battery) = rememberPowerNow(context)
    val pose = rememberHalfOpenPose(activity)
    // offCharger forces motion on in rememberStandBySignals; the real switch only decides which rows are listed.
    val signals = rememberStandBySignals(activity, ways.copy(offCharger = true), plugged, pose != null, battery)
    val readiness = standByReadiness(ways, signals)
    if (readiness.isEmpty()) return
    val next = standByNextStep(readiness)
    SettingsCard(stringResource(R.string.standby_check_title)) {
        Text(
            if (next == null) stringResource(R.string.standby_check_ready)
            else stringResource(R.string.standby_check_waiting, stringResource(next.labelRes())),
            Modifier.fillMaxWidth().padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.COMPACT.dp),
            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        for (route in readiness) {
            Text(stringResource(route.route.labelRes()), Modifier.fillMaxWidth().padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.COMPACT.dp),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for ((requirement, met) in route.checks) {
                val label = stringResource(requirement.labelRes())
                val value = stringResource(if (met) R.string.standby_check_yes else R.string.standby_check_not_yet)
                Row(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.COMPACT.dp)
                    .semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(value, style = MaterialTheme.typography.bodyLarge,
                        color = if (met) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        CardNote(stringResource(R.string.standby_check_note))
    }
}
