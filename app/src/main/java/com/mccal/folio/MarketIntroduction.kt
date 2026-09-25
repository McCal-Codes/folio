package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mccal.folio.market.FeaturedStyle

/**
 * The welcome, shown the first time the Market opens (and again from its settings).
 *
 * Two steps, the way iOS welcomes you to an app the first time it opens: what the Market is, in three rows, then how
 * Featured should look. Every row says something the Market already does, from the 0.6.6 release notes, so the
 * welcome never promises what isn't there. Skip is on both steps. The style choice is the one McCal asked for: the
 * carousel by default, with Calm offered up front rather than buried.
 */
@Composable
internal fun MarketIntroduction(style: FeaturedStyle, onStyle: (FeaturedStyle) -> Unit, onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing).padding(FolioSpace.XXL.dp)) {
        Text(
            stringResource(R.string.skip),
            color = LocalAccent.current.ink, fontSize = 16.sp,
            modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(FolioRadius.CONTROL.dp))
                .clickable(onClickLabel = stringResource(R.string.skip_the_introduction), onClick = onDone)
                .heightIn(min = FolioTouch.MIN.dp).padding(horizontal = FolioSpace.MEDIUM.dp, vertical = FolioSpace.MEDIUM.dp),
        )
        // Scrolls rather than clipping, for a cover screen held sideways or the largest text (A11Y-12).
        Column(Modifier.align(Alignment.Center).widthIn(max = 520.dp).fillMaxWidth()
            .verticalScroll(rememberScrollState()).padding(top = FolioTouch.MIN.dp, bottom = 96.dp)) {
            when (step) {
                0 -> {
                    Title(stringResource(R.string.welcome_to_the_folio_market))
                    Spacer(Modifier.height(FolioSpace.MEDIUM.dp))
                    FeatureRow(Icons.Rounded.Palette, R.string.market_welcome_packages_title, R.string.market_welcome_packages_body)
                    FeatureRow(Icons.Rounded.Public, R.string.market_welcome_sources_title, R.string.market_welcome_sources_body)
                    FeatureRow(Icons.Rounded.VerifiedUser, R.string.market_welcome_checked_title, R.string.market_welcome_checked_body)
                }
                else -> {
                    Title(stringResource(R.string.choose_how_featured_looks))
                    Body(stringResource(R.string.you_can_change_this_any_time_in_the))
                    Spacer(Modifier.height(FolioSpace.LARGE.dp))
                    for (option in FeaturedStyle.entries) {
                        StyleCard(option, chosen = option == style) { onStyle(option) }
                        Spacer(Modifier.height(FolioSpace.COMPACT.dp))
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).widthIn(max = 520.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally) {
            FolioButton(
                stringResource(if (step == 0) R.string.continue_choice else R.string.open_the_market),
                onClick = { if (step == 0) step++ else onDone() },
                modifier = Modifier.fillMaxWidth(),
                tag = "market-introduction-next",
            )
            Row(Modifier.padding(top = FolioSpace.MEDIUM.dp), horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                repeat(2) { index ->
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White.copy(alpha = if (index == step) 1f else .3f)))
                }
            }
        }
    }
}

/** One of the welcome's three rows: a glyph in the accent, a bold line, and one sentence. */
@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, @androidx.annotation.StringRes title: Int, @androidx.annotation.StringRes body: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = FolioSpace.MEDIUM.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = LocalAccent.current.ink, modifier = Modifier.padding(top = FolioSpace.HAIR.dp).size(34.dp))
        Column(Modifier.padding(start = FolioSpace.LARGE.dp).weight(1f)) {
            Text(stringResource(title), color = Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(body), color = Color.White.copy(alpha = .7f), fontSize = FolioType.SUBHEAD.sp,
                modifier = Modifier.padding(top = FolioSpace.HAIR.dp))
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = FolioSpace.COMPACT.dp))
}

@Composable
private fun Body(text: String) {
    Text(text, color = Color.White.copy(alpha = .75f), fontSize = 16.sp)
}

@Composable
private fun StyleCard(option: FeaturedStyle, chosen: Boolean, onChoose: () -> Unit) {
    val name = stringResource(option.label)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.GROUP.dp)).background(FolioColors.SheetSurface)
            .border(if (chosen) 2.dp else 0.dp, if (chosen) LocalAccent.current.ink else Color.Transparent, RoundedCornerShape(FolioRadius.GROUP.dp))
            .clickable(role = Role.RadioButton, onClickLabel = name, onClick = onChoose)
            .padding(FolioSpace.COMFY.dp),
    ) {
        Text(name, color = Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.Medium)
        Text(stringResource(option.description), color = Color.White.copy(alpha = .7f), fontSize = 14.sp)
    }
}
