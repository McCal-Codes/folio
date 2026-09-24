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
 * The introduction, shown the first time the Market opens after updating (and again from its settings).
 *
 * Three steps: what's in here, how Featured should look, and where packages come from. Skip is on every step, and the
 * style choice is the one McCal asked for: the carousel by default, with Calm offered up front rather than buried.
 */
@Composable
internal fun MarketIntroduction(style: FeaturedStyle, onStyle: (FeaturedStyle) -> Unit, onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing).padding(FolioSpace.XXL.dp)) {
        Text(
            stringResource(R.string.skip),
            color = LocalAccent.current.ink, fontSize = 16.sp,
            modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(12.dp))
                .clickable(onClickLabel = stringResource(R.string.skip_the_introduction), onClick = onDone).padding(horizontal = FolioSpace.MEDIUM.dp, vertical = FolioSpace.SMALL.dp),
        )
        Column(Modifier.align(Alignment.Center).fillMaxWidth()) {
            when (step) {
                0 -> {
                    Title(stringResource(R.string.welcome_to_the_folio_market))
                    Body(
                        stringResource(R.string.folio_s_own_themes_and_tweaks_live_here),
                    )
                }
                1 -> {
                    Title(stringResource(R.string.choose_how_featured_looks))
                    Body(stringResource(R.string.you_can_change_this_any_time_in_the))
                    Spacer(Modifier.height(16.dp))
                    for (option in FeaturedStyle.entries) {
                        StyleCard(option, chosen = option == style) { onStyle(option) }
                        Spacer(Modifier.height(10.dp))
                    }
                }
                else -> {
                    Title(stringResource(R.string.where_packages_come_from))
                    Body(
                        stringResource(R.string.folio_s_own_packages_come_with_the_app_a),
                    )
                }
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Start) {
                repeat(3) { index ->
                    Box(
                        Modifier.padding(end = FolioSpace.SNUG.dp).width(7.dp).height(7.dp).clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = if (index == step) 1f else .3f)),
                    )
                }
            }
            // A Modifier's click label isn't a composable scope, so both labels are read before the chain.
            val nextStep = stringResource(R.string.next_step)
            val openMarket = stringResource(R.string.open_the_market)
            Text(
                stringResource(if (step < 2) R.string.next else R.string.start),
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(FolioRadius.CARD.dp)).background(LocalAccent.current.fill)
                    .clickable(onClickLabel = if (step < 2) nextStep else openMarket) {
                        if (step < 2) step++ else onDone()
                    }
                    .padding(horizontal = FolioSpace.XL.dp, vertical = 11.dp),
            )
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.GROUP.dp)).background(Color(0xFF2C2C2E))
            .border(if (chosen) 2.dp else 0.dp, if (chosen) LocalAccent.current.ink else Color.Transparent, RoundedCornerShape(FolioRadius.GROUP.dp))
            .clickable(role = Role.RadioButton, onClickLabel = name, onClick = onChoose)
            .padding(FolioSpace.COMFY.dp),
    ) {
        Text(name, color = Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.Medium)
        Text(stringResource(option.description), color = Color.White.copy(alpha = .7f), fontSize = 14.sp)
    }
}
