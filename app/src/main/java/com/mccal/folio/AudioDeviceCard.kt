package com.mccal.folio

import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull

private const val CARD_MS = 6_000L

/**
 * The iPhone-style card that rises on Home when Bluetooth headphones or a speaker connects. Over other apps the
 * Dynamic Island shows the same moment in its compact form.
 */
@Composable
internal fun AudioDeviceCard(enabled: Boolean, blocked: Boolean) {
    var device by remember { mutableStateOf<IslandEvent.Bluetooth?>(null) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (!enabled) { open = false; return@LaunchedEffect }
        // Other island moments (charging, silent) pass by without cutting the card short.
        IslandEvents.latest.filterNotNull().filter { it.first is IslandEvent.Bluetooth }.collectLatest { (event, at) ->
            val remaining = CARD_MS - (System.currentTimeMillis() - at)
            if (remaining > 0) { device = event as IslandEvent.Bluetooth; open = true; delay(remaining); open = false }
        }
    }
    val visible = open && !blocked
    androidx.activity.compose.BackHandler(visible) { open = false }
    // AirPods battery from the system (root, off until the owner turns it on): one read when the card shows, never while hidden.
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(visible, device) { device?.takeIf { visible && !it.speaker }?.let { AirPodsRoot.onCardShown(context, it.name) } }
    // AirPods battery from the AirPods' own broadcast (no root, its switch off until turned on): a short scan only while the card
    // shows for AirPods, stopped the moment it hides (AirPodsBle.kt).
    val bleSurface = visible && device?.let { !it.speaker && AirPodsBle.isAppleName(it.name) } == true
    androidx.compose.runtime.DisposableEffect(bleSurface) {
        AirPodsBle.setVisible(context, BleSurface.HOME_CARD, bleSurface)
        onDispose { AirPodsBle.setVisible(context, BleSurface.HOME_CARD, false) }
    }
    val reduceMotion = LocalReduceMotion.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible,
            enter = if (reduceMotion) fadeIn() else slideInVertically(FolioMotion.spring(FolioMotion.Settle)) { it } + fadeIn(),
            exit = if (reduceMotion) fadeOut() else slideOutVertically { it } + fadeOut()) {
            device?.let { DeviceCard(it) { open = false } }
        }
    }
}

/** How recent a reading must be to show on the card as current. */
private const val AIRPODS_FRESH_MS = 60_000L

/**
 * The AirPods' left, right and case battery under "Connected", from their own broadcast or, failing that, the root read for this
 * device when it came back with numbers and is fresh. Numbers change without animation and charging is a static glyph; nothing here moves (WCAG 2.3.1, Reduce Motion).
 */
@Composable
private fun AirPodsCardLine(device: IslandEvent.Bluetooth, color: Color) {
    val ui by AirPodsRoot.state.collectAsState()
    val bleRead by AirPodsBle.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val now = System.currentTimeMillis()
    // The broadcast reading wins while it is not expired (shown with its age after a few minutes); otherwise the root read for
    // this device, only while it is fresh.
    val ble = (bleRead as? AirPodsBleRead.Found)?.battery?.takeIf { AirPodsBle.isAppleName(device.name) }
    val bleLine = ble?.let { AirPodsBleText.line(context, it, now) }
    val battery: AirPodsBattery
    val spoken: String
    if (ble != null && bleLine != null) { battery = ble; spoken = bleLine } else {
        val done = ui.last as? AirPodsRead.Done ?: return
        if (done.forName != device.name || now - done.atMillis > AIRPODS_FRESH_MS) return
        battery = (done.reading as? AirPodsReading.Found)?.battery ?: return
        spoken = AirPodsText.line(context, battery)
    }
    Spacer(Modifier.height(FolioSpace.SNUG.dp))
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FolioSpace.SNUG.dp)) {
        if (AirPodsText.parts(battery).any { it.charging }) Icon(Icons.Rounded.BatteryChargingFull, null, tint = FolioColors.Green, modifier = Modifier.size(18.dp))
        Text(spoken, color = color, fontSize = FolioType.SUBHEAD.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DeviceCard(device: IslandEvent.Bluetooth, onDone: () -> Unit) {
    val dark = LocalDuoPalette.current.dark
    val background = if (dark) FolioColors.SecondaryBackground else FolioColors.LightBackground
    val primary = if (dark) Color.White else Color.Black
    val secondary = if (dark) FolioColors.SecondaryLabelOnDark else FolioColors.SecondaryLabelOnLight
    val title = device.name ?: stringResource(if (device.speaker) R.string.speaker else R.string.headphones)
    Column(Modifier.navigationBarsPadding().padding(FolioSpace.MEDIUM.dp).widthIn(max = 400.dp).fillMaxWidth()
        .clip(RoundedCornerShape(36.dp)).background(background).padding(horizontal = 22.dp, vertical = 18.dp)
        .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally) {
        val closeLabel = stringResource(R.string.close)
        Box(Modifier.fillMaxWidth()) {
            Text(title, Modifier.align(Alignment.Center).padding(horizontal = 44.dp), color = primary, fontSize = FolioType.TITLE3.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.align(Alignment.CenterEnd).minimumInteractiveComponentSize()
                .clickable(role = Role.Button, onClick = onDone).semantics { contentDescription = closeLabel },
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(secondary.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, null, tint = secondary, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.size(112.dp).clip(CircleShape).background(Brush.linearGradient(listOf(FolioColors.Cyan, LocalAccent.current.fill))),
            contentAlignment = Alignment.Center) {
            Icon(if (device.speaker) Icons.Rounded.Speaker else Icons.Rounded.Headphones, null, tint = Color.White, modifier = Modifier.size(60.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FolioSpace.SNUG.dp)) {
            Icon(Icons.Rounded.CheckCircle, null, tint = FolioColors.Green, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.connected), color = secondary, fontSize = FolioType.SUBHEAD.sp)
        }
        AirPodsCardLine(device, secondary)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(FolioRadius.CARD.dp)).background(LocalAccent.current.fill)
            .clickable(role = Role.Button, onClick = onDone), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.done), color = Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
