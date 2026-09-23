package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Folio Beta: supporters update Folio itself from the Market (0.6.7, row M6; mockup market-folio-beta-source.html).
 *
 * It isn't a package source. Folio can't install over itself the way it installs a tweak, and a beta has to come
 * through the supporter worker, since the beta repository is private. So the page is Software Update's beta channel
 * shown where supporters look for new things: the same check, the same download checked against Folio's signing key,
 * the same Update Now and Tonight. Settings › Software Update and this page always agree, because they are one thing.
 */

/** Stands in for an address, so the Sources tab can open Folio Beta like any other source. */
internal const val FOLIO_BETA_SOURCE_URL = "folio://beta/"

private val BetaOrange = FolioColors.Orange

/** The Sources tab's Folio Beta row, marked as the supporter's, with "Update available" when there's a newer beta. */
@Composable
internal fun FolioBetaSourceRow(selected: Boolean, onOpen: () -> Unit) {
    val status by SoftwareUpdate.status.collectAsState()
    val name = stringResource(R.string.folio_beta)
    val waiting = status is SoftwareUpdate.Status.Available || status is SoftwareUpdate.Status.Ready
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .background(if (selected) Color.White.copy(alpha = .06f) else Color.Transparent)
            .clickable(onClickLabel = name, onClick = onOpen)
            .padding(14.dp)
            .testTag("market-folio-beta-row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Science, contentDescription = null, tint = BetaOrange, modifier = Modifier.size(20.dp))
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(name, color = Color.White, fontSize = 16.sp)
            Text(
                stringResource(if (waiting) R.string.update_available else R.string.supporter),
                color = if (waiting) Color(0xFF6CB4FF) else Color.White.copy(alpha = .55f), fontSize = 13.sp,
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = .3f), modifier = Modifier.size(18.dp))
    }
}

/** The Folio Beta page: what you're on, the newest beta, and Folio itself with Update. */
@Composable
internal fun MarketFolioBetaPage(showBack: Boolean, backTitle: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val status by SoftwareUpdate.status.collectAsState()
    val installed = remember { SoftwareUpdate.installedVersion(context) }
    val supported = SoftwareUpdate.supported(context)
    var betaOn by remember { mutableStateOf(SoftwareUpdate.betaChannel(context)) }
    // Like Software Update: opening the page is asking, unless a check already ran.
    LaunchedEffect(betaOn) { if (supported && betaOn && status == SoftwareUpdate.Status.Idle) SoftwareUpdate.startCheck(context) }
    val release = when (val s = status) {
        is SoftwareUpdate.Status.Available -> s.release
        is SoftwareUpdate.Status.Downloading -> s.release
        is SoftwareUpdate.Status.Ready -> s.release
        else -> null
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag("market-folio-beta-page"),
    ) {
        if (showBack) {
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.back), onClick = onBack).padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = null, tint = FolioColors.Blue, modifier = Modifier.size(18.dp))
                Text(backTitle, color = FolioColors.Blue, fontSize = 16.sp)
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Science, contentDescription = null, tint = BetaOrange, modifier = Modifier.size(34.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(stringResource(R.string.folio_beta), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.folio_beta_for_supporters), color = Color.White.copy(alpha = .55f), fontSize = 14.sp)
            }
        }
        SheetGroupLabel(stringResource(R.string.information))
        SheetGroup(Modifier.padding(bottom = 12.dp)) {
            BetaInfoRow(stringResource(R.string.you_re_on), installed)
            MenuDivider()
            BetaInfoRow(stringResource(R.string.newest_beta), when {
                release != null -> release.version
                status == SoftwareUpdate.Status.Checking -> stringResource(R.string.checking_for_updates)
                status == SoftwareUpdate.Status.UpToDate -> installed
                else -> "–"
            })
            MenuDivider()
            BetaInfoRow(stringResource(R.string.checked), SoftwareUpdate.lastChecked(context).takeIf { it > 0 }?.let {
                if (System.currentTimeMillis() - it < 60_000) stringResource(R.string.just_now)
                else android.text.format.DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(),
                    android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()
            } ?: stringResource(R.string.never))
        }
        SheetGroupLabel(stringResource(R.string.from_this_source))
        when {
            // Folio Dev and other test builds never update themselves; say so rather than offer a button that can't.
            !supported -> CardNote(stringResource(R.string.folio_dev_a_test_build_it_updates_from_n))
            !betaOn -> {
                CardNote(stringResource(R.string.beta_updates_are_off_here))
                SheetGroup(Modifier.padding(top = 8.dp)) {
                    IosActionRow(stringResource(R.string.turn_on_beta_updates), "market-folio-beta-on") {
                        SoftwareUpdate.setBeta(context, true); betaOn = true
                    }
                }
            }
            release != null -> UpdateCard(release, status)
            else -> {
                if (status is SoftwareUpdate.Status.Failed) CardNote((status as SoftwareUpdate.Status.Failed).message)
                else if (status == SoftwareUpdate.Status.UpToDate) CardNote(stringResource(R.string.folio_is_up_to_date))
                SheetGroup(Modifier.padding(top = 8.dp)) {
                    IosActionRow(stringResource(R.string.check_for_updates), "market-folio-beta-check",
                        enabled = status != SoftwareUpdate.Status.Checking && status != SoftwareUpdate.Status.Installing) {
                        SoftwareUpdate.startCheck(context)
                    }
                }
            }
        }
        CardNote(stringResource(R.string.folio_beta_page_note), Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BetaInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 16.sp)
        Text(value, color = Color.White.copy(alpha = .55f), fontSize = 15.sp, textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 12.dp))
    }
}
