package com.mccal.folio

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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    val reduceMotion = LocalReduceMotion.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible,
            enter = if (reduceMotion) fadeIn() else slideInVertically(FolioMotion.spring(FolioMotion.Settle)) { it } + fadeIn(),
            exit = if (reduceMotion) fadeOut() else slideOutVertically { it } + fadeOut()) {
            device?.let { DeviceCard(it) { open = false } }
        }
    }
}

@Composable
private fun DeviceCard(device: IslandEvent.Bluetooth, onDone: () -> Unit) {
    val dark = LocalDuoPalette.current.dark
    val background = if (dark) FolioColors.SecondaryBackground else Color(0xFFF2F2F7)
    val primary = if (dark) Color.White else Color.Black
    val secondary = if (dark) Color(0xFF98989F) else Color(0xFF6C6C70)
    val title = device.name ?: if (device.speaker) "Speaker" else "Headphones"
    Column(Modifier.navigationBarsPadding().padding(12.dp).widthIn(max = 400.dp).fillMaxWidth()
        .clip(RoundedCornerShape(36.dp)).background(background).padding(horizontal = 22.dp, vertical = 18.dp)
        .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth()) {
            Text(title, Modifier.align(Alignment.Center).padding(horizontal = 44.dp), color = primary, fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.align(Alignment.CenterEnd).minimumInteractiveComponentSize()
                .clickable(role = Role.Button, onClick = onDone).semantics { contentDescription = "Close" },
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(secondary.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, null, tint = secondary, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.size(112.dp).clip(CircleShape).background(Brush.linearGradient(listOf(FolioColors.Cyan, FolioColors.Blue))),
            contentAlignment = Alignment.Center) {
            Icon(if (device.speaker) Icons.Rounded.Speaker else Icons.Rounded.Headphones, null, tint = Color.White, modifier = Modifier.size(60.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.CheckCircle, null, tint = FolioColors.Green, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.connected), color = secondary, fontSize = 15.sp)
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(FolioColors.Blue)
            .clickable(role = Role.Button, onClick = onDone), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.done), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
