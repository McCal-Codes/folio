package com.mccal.folio

import androidx.compose.ui.res.stringResource
import android.app.AlarmManager
import android.content.Intent
import android.provider.MediaStore
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * iPhone-style Lock Cover shown when unlocking straight to Home (Android doesn't let apps replace the real
 * lock screen): date and large clock, next alarm, recent notifications, flashlight and camera buttons.
 * Swipe up (or Back) to reveal Home.
 */
@Composable
internal fun LockCover(visible: Boolean, onDismiss: () -> Unit) {
    if (!visible) return
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    androidx.activity.compose.BackHandler { onDismiss() }
    val tick by rememberMinuteTick()
    val now = displayNow(tick)
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val screenshot by ScreenshotMode.on.collectAsState()
    val alarm = remember(tick, screenshot) {
        if (screenshot) null else context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it.triggerTime), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern(if (is24) "EEE HH:mm" else "EEE h:mm a"))
        }
    }
    val notifications = IslandListenerService.notifications.collectAsState().value.take(4)

    Box(Modifier.fillMaxSize().graphicsLayer {
        translationY = offset.value
        alpha = (1f + offset.value / (size.height * .6f)).coerceIn(0f, 1f)
    }.background(Color.Black.copy(alpha = .55f))
        .pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = {
                    scope.launch {
                        if (offset.value < -size.height * .22f) {
                            haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            offset.animateTo(-size.height.toFloat(), spring(stiffness = 900f)); onDismiss()
                        } else offset.animateTo(0f, spring(dampingRatio = .8f))
                    }
                },
                onDragCancel = { scope.launch { offset.animateTo(0f) } },
            ) { change, amount -> change.consume(); scope.launch { offset.snapTo((offset.value + amount).coerceAtMost(0f)) } }
        }
        .windowInsetsPadding(WindowInsets.folioSafeTop).navigationBarsPadding()) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        // Wider than tall (the cover turned sideways, as on iPhone Duo's Lock Screen): a short date, the clock sized to
        // the height, fewer notifications, and flashlight and camera stacked on the side edge instead of the bottom corners.
        val wide = maxWidth > maxHeight
        val clockSize = minOf(if (wide) maxHeight.value * .3f else maxWidth.value * .26f, 120f)
        val room = ((maxHeight.value - clockSize * 1.6f - 150f) / 76f).toInt().coerceIn(0, 4)
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = if (wide) 84.dp else 16.dp).padding(top = if (wide) 8.dp else 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(now.format(DateTimeFormatter.ofPattern(if (wide) "EEE MMM d" else "EEEE, MMMM d")), color = Color.White,
                fontSize = if (wide) 17.sp else 20.sp, fontWeight = FontWeight.SemiBold)
            Text(now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm")), color = Color.White,
                fontSize = clockSize.sp, fontWeight = FontWeight.Bold, lineHeight = (clockSize * 1.04f).sp)
            alarm?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Alarm, null, tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(it, color = Color.White.copy(alpha = .8f), fontSize = 15.sp)
                }
            }
            // Up Next, like the Calendar widget iPhone puts under the Lock Screen clock.
            val next by produceState<UpNextEvent?>(null, tick) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { UpNext.events(context, limit = 1).firstOrNull() } }
            next?.let { e ->
                val begin = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.begin), ZoneId.systemDefault())
                Row(Modifier.padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).clickable { onDismiss(); UpNext.openEvent(context, e) }
                    .padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 3.dp, height = 16.dp).clip(RoundedCornerShape(2.dp)).background(e.color?.let { Color(it) } ?: FolioColors.Blue))
                    Spacer(Modifier.width(6.dp))
                    Text("${e.title} · ${if (e.allDay) stringResource(R.string.all_day) else begin.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a"))}",
                        color = Color.White.copy(alpha = .85f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(24.dp))
            notifications.take(room).forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF2A2A2E).copy(alpha = .8f))
                    .clickable { onDismiss(); IslandListenerService.openNotification(context, item) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    item.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title ?: item.appLabel, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        item.text?.let { Text(it, color = Color.White.copy(alpha = .8f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
        val flashlight: @Composable () -> Unit = { CoverButton(Icons.Rounded.FlashlightOn, "Flashlight") { FolioActions.run(context, FolioAction.TORCH) } }
        val camera: @Composable () -> Unit = { CoverButton(Icons.Rounded.PhotoCamera, "Camera") {
            onDismiss(); runCatching { context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } }
        // Flashlight and camera: bottom corners as on iPhone, or stacked on the side edge when wide, as on iPhone Duo.
        if (wide) Column(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            flashlight(); camera()
        } else Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 44.dp, vertical = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween) { flashlight(); camera() }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.swipe_up_to_open), color = Color.White.copy(alpha = .6f), fontSize = 13.sp)
            Box(Modifier.padding(top = 6.dp).size(width = 134.dp, height = 5.dp).clip(CircleShape).background(Color.White))
        }
      }
    }
}

@Composable
private fun CoverButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(50.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f)).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp)) }
}
