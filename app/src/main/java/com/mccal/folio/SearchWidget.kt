package com.mccal.folio

import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

internal tailrec fun android.content.Context.findMainActivity(): MainActivity? = when (this) {
    is MainActivity -> this
    is android.content.ContextWrapper -> baseContext.findMainActivity()
    else -> null
}

/** A Search widget is the strip below this height; at or above it the card also shows the shortcuts to a few phone settings. */
internal const val SEARCH_WIDGET_CARD_MIN_HEIGHT_DP = 120

/** The phone settings the card offers before the person has searched for anything: the three pages people ask for most. */
internal fun searchWidgetShortcuts(): List<Pair<Int, String>> = listOf(
    R.string.wi_fi to Settings.ACTION_WIFI_SETTINGS,
    R.string.bluetooth to Settings.ACTION_BLUETOOTH_SETTINGS,
    R.string.display_brightness to Settings.ACTION_DISPLAY_SETTINGS,
)

/**
 * Home's Search widget, for people who want the search box on Home all the time (One UI's Finder). Tapping it opens
 * Spotlight, which finds apps, contacts and the phone's own settings. The card size adds one-tap chips for three
 * settings pages. While Home is being edited a tap does [onOpen], which then edits instead of searching.
 */
@Composable
internal fun SearchWidgetCard(onOpen: () -> Unit) {
    val context = LocalContext.current
    val edit = LocalHomeEdit.current
    val ink = LocalHomeInk.current
    val hint = stringResource(R.string.search_widget_hint)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val card = maxHeight >= SEARCH_WIDGET_CARD_MIN_HEIGHT_DP.dp
        GlassCard(onClick = onOpen) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = ink.secondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(FolioSpace.MEDIUM.dp))
                Text(hint, color = ink.secondary, fontSize = FolioType.SUBHEAD.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            if (card) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                    for ((title, action) in searchWidgetShortcuts()) {
                        val label = stringResource(title)
                        Row(Modifier.weight(1f).heightIn(min = 48.dp).background(Color.White.copy(alpha = .16f), CircleShape)
                            .clickable(role = Role.Button, onClickLabel = label) { if (!edit.active) openSystemSetting(context, action) else onOpen() }
                            .padding(horizontal = FolioSpace.MEDIUM.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center) {
                            Text(label, color = ink.primary, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.Medium, maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
