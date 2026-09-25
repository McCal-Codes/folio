package com.mccal.folio

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * iOS Today View, the page left of Home: search, the date, app suggestions and a two-column grid of
 * widgets (small = one column, medium = full width, large = full width and double height).
 */
@Composable
internal fun TodayView(state: LauncherState, widgets: WidgetController, modifier: Modifier = Modifier,
    onSearch: () -> Unit, onLaunch: (AppEntry) -> Unit, onAddWidget: () -> Unit,
    onRemove: (Int) -> Unit, onMove: (Int, Int) -> Unit) {
    val context = LocalContext.current
    val edit = remember { HomeEditMode() }
    androidx.activity.compose.BackHandler(edit.active) { edit.stop() }
    val tick by rememberMinuteTick()
    val today = remember(tick) { LocalDate.now() }
    val apps = remember(state.apps, state.hiddenApps) { state.apps.filter { it.id !in state.hiddenApps } }
    val suggestions by produceState(emptyList<AppEntry>(), apps) {
        value = withContext(Dispatchers.IO) {
            Suggestions.forNow(context, apps)
        }
    }

    ProvideJiggle(edit) {
        BoxWithConstraints(modifier.testTag("today-view")) {
            val wide = maxWidth > TODAY_TWO_COLUMN_MIN_WIDTH_DP.dp
            // iPad-like column: small widgets stay about 180dp wide even on the unfolded screen.
            val columnsWidth = minOf(maxWidth - 32.dp, 390.dp)
            val gap = 14.dp
            val cell = (columnsWidth - gap) / 2
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = FolioSpace.LARGE.dp).padding(top = FolioSpace.MEDIUM.dp, bottom = 96.dp), // clear of Home's page dots
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
                Column(Modifier.width(columnsWidth), verticalArrangement = Arrangement.spacedBy(gap)) {
                    // Search capsule
                    val ink = LocalHomeInk.current
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.CARD.dp)).background(Color.White.copy(alpha = if (ink.dark) .5f else .16f))
                        .clickable(onClickLabel = stringResource(R.string.search), onClick = onSearch).padding(horizontal = FolioSpace.COMFY.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Search, null, tint = ink.secondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.search), color = ink.secondary, fontSize = FolioType.BODY.sp)
                    }
                    Column(Modifier.padding(start = FolioSpace.TINY.dp, top = FolioSpace.SNUG.dp)) {
                        Text(today.format(DateTimeFormatter.ofPattern("EEEE")).uppercase(), color = FolioColors.Red, fontSize = FolioType.FOOTNOTE.sp,
                            fontWeight = FontWeight.SemiBold, letterSpacing = .6.sp)
                        Text(today.format(DateTimeFormatter.ofPattern("MMMM d")), color = LocalHomeInk.current.primary, fontSize = if (wide) 40.sp else 34.sp,
                            fontWeight = FontWeight.Bold)
                    }
                    if (suggestions.isNotEmpty() && !edit.active) TodaySuggestions(suggestions, 4, onLaunch)

                    // Widget grid, packed two columns at a time.
                    todayRows(state.todayWidgets).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            row.forEach { widget ->
                                val width = if (widget.size.columns == 2) columnsWidth else cell
                                val height = if (widget.size.rows == 2) cell * 2 + gap else cell
                                TodayWidgetTile(widget, widgets, width, height, edit,
                                    canMoveUp = state.todayWidgets.first() != widget, canMoveDown = state.todayWidgets.last() != widget,
                                    onRemove = { onRemove(widget.id) }, onMove = { onMove(widget.id, it) })
                            }
                        }
                    }
                    if (state.todayWidgets.isEmpty()) Text(stringResource(R.string.add_widgets_for_the_things_you_check_mos), color = LocalHomeInk.current.secondary,
                        fontSize = FolioType.SUBHEAD.sp, modifier = Modifier.padding(FolioSpace.TINY.dp))

                    // Edit / Add / Done, like the bottom of iOS's Today View
                    Row(Modifier.fillMaxWidth().padding(top = FolioSpace.SNUG.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        if (edit.active) {
                            JigglePill(stringResource(R.string.add_widget_2), Icons.Rounded.Add, description = stringResource(R.string.add_widget)) { onAddWidget() }
                            Spacer(Modifier.width(12.dp))
                            JigglePill(stringResource(R.string.done), emphasized = true) { edit.stop() }
                        } else JigglePill(stringResource(R.string.edit)) { edit.start() }
                    }
                }
            }
        }
    }
}

/** Packs Today widgets into rows of up to two columns, in order. */
internal fun todayRows(list: List<TodayWidget>): List<List<TodayWidget>> {
    val rows = mutableListOf<MutableList<TodayWidget>>()
    list.forEach { widget ->
        val last = rows.lastOrNull()
        val used = last?.sumOf { it.size.columns } ?: 2
        if (last != null && used + widget.size.columns <= 2 && last.all { it.size.rows == widget.size.rows }) last += widget
        else rows += mutableListOf(widget)
    }
    return rows
}

@Composable
private fun TodaySuggestions(apps: List<AppEntry>, columns: Int, onLaunch: (AppEntry) -> Unit) {
    val ink = LocalHomeInk.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = if (ink.dark) .45f else .14f))
        .border(FolioGlass.edge, RoundedCornerShape(22.dp)).padding(horizontal = FolioSpace.COMPACT.dp, vertical = FolioSpace.MEDIUM.dp)) {
        Text(stringResource(R.string.suggestions), color = ink.secondary, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = FolioSpace.SNUG.dp, bottom = FolioSpace.SMALL.dp))
        Row(Modifier.fillMaxWidth()) {
            apps.take(columns).forEach { app ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(FolioRadius.CARD.dp)).clickable { onLaunch(app) }.padding(vertical = FolioSpace.TINY.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcon(app, app.label, Modifier.size(52.dp), shape = RoundedCornerShape(13.dp), badge = false)
                    Text(app.label, color = ink.primary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = FolioSpace.TINY.dp, start = FolioSpace.HAIR.dp, end = FolioSpace.HAIR.dp))
                }
            }
            repeat((columns - apps.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun TodayWidgetTile(widget: TodayWidget, widgets: WidgetController, width: Dp, height: Dp, edit: HomeEditMode,
    canMoveUp: Boolean, canMoveDown: Boolean, onRemove: () -> Unit, onMove: (Int) -> Unit) {
    Box(Modifier.size(width, height).then(if (widget.id < 0) Modifier.jiggle("today-${widget.id}", .5f) else Modifier)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(FolioRadius.PANEL.dp))) {
            if (widget.id < 0) BuiltinWidgetCard(widget.id, -1) { if (!edit.active) edit.start() }
            else {
                val info = remember(widget.id) { runCatching { widgets.manager.getAppWidgetInfo(widget.id) }.getOrNull() }
                if (info == null) Box(Modifier.fillMaxSize().background(Glass.copy(alpha = .2f)), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.widget_unavailable), color = Color.White.copy(alpha = .8f), fontSize = FolioType.FOOTNOTE.sp)
                } else key(widget.id) {
                    AndroidView(factory = { widgets.host.createView(it, widget.id, info) }, modifier = Modifier.fillMaxSize())
                }
            }
        }
        if (edit.active) {
            JiggleRemoveButton("Remove widget", onRemove = onRemove)
            Row(Modifier.align(Alignment.BottomEnd).padding(FolioSpace.SMALL.dp).clip(CircleShape).background(Color.Black.copy(alpha = .45f))) {
                if (canMoveUp) TodayArrow(Icons.Rounded.KeyboardArrowUp, "Move up") { onMove(-1) }
                if (canMoveDown) TodayArrow(Icons.Rounded.KeyboardArrowDown, "Move down") { onMove(1) }
            }
        }
    }
}

@Composable
private fun TodayArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}
