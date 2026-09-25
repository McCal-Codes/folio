package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mccal.folio.market.RefreshResult
import com.mccal.folio.market.Source
import com.mccal.folio.market.SourceKey

/**
 * The Sources tab: Folio's own, the ones the user added, and how to add another.
 *
 * A source is a place with packages in it, so its row opens that place - Cydia's and Sileo's shape, where a repo
 * is somewhere you go rather than a line with buttons on it. Refresh and Remove live on the page they belong to,
 * which also stops a mis-tap on a crowded row from removing a source.
 */
@Composable
internal fun MarketSourcesTab(
    builtInName: String,
    builtInCount: Int,
    statuses: List<SourceStatus>,
    localDevAllowed: Boolean,
    openUrl: String?,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onAddLocalDev: () -> Unit,
    /** Whether this supporter gets Folio Beta: a code with beta access and a broker to ask. */
    folioBeta: Boolean = false,
) {
    Column {
        SheetGroupLabel(stringResource(R.string.sources))
        SheetGroup(Modifier.padding(bottom = FolioSpace.COMPACT.dp)) {
            SourceRow(
                name = builtInName,
                // The count is its own column now, so the line under the name doesn't say it twice.
                detail = stringResource(R.string.built_into_the_app_no_network),
                icon = Icons.Rounded.Home,
                tint = FolioColors.Green,
                count = builtInCount,
                selected = openUrl == BUILT_IN_SOURCE_URL,
                onOpen = { onOpen(BUILT_IN_SOURCE_URL) },
            )
            // Right under Folio's own: it's Folio too, and it comes and goes with the code, like Keyd's source.
            if (folioBeta) {
                MenuDivider()
                FolioBetaSourceRow(selected = openUrl == FOLIO_BETA_SOURCE_URL) { onOpen(FOLIO_BETA_SOURCE_URL) }
            }
            statuses.forEach { status ->
                MenuDivider()
                val local = status.source.kind == Source.Kind.LOCAL_DEV
                SourceRow(
                    name = status.source.label,
                    detail = sourceDetail(status),
                    icon = if (local) Icons.Rounded.Warning else Icons.Rounded.Public,
                    tint = if (local) FolioColors.Warning else FolioColors.BlueOnDark,
                    failed = status.failure != null,
                    count = status.packages.size.takeIf { status.snapshot != null },
                    selected = openUrl == status.source.url,
                    onOpen = { onOpen(status.source.url) },
                )
            }
        }
        SheetGroup(Modifier.padding(bottom = FolioSpace.COMPACT.dp)) {
            IosActionRow(stringResource(R.string.add_a_source)) { onAdd() }
            if (localDevAllowed) {
                MenuDivider()
                IosActionRow(stringResource(R.string.add_a_local_source_folio_dev)) { onAddLocalDev() }
            }
        }
        Text(
            stringResource(R.string.folio_shows_a_source_s_key_fingerprint),
            color = Color.White.copy(alpha = .55f), fontSize = FolioType.FOOTNOTE.sp, modifier = Modifier.padding(bottom = FolioSpace.LARGE.dp),
        )
    }
}

/** What a source's row says under its name: what it is, or what went wrong reaching it. */
@Composable
private fun sourceDetail(status: SourceStatus): String = when {
    status.refreshing -> stringResource(R.string.refreshing)
    status.failure != null -> status.failure.message
    status.source.kind == Source.Kind.LOCAL_DEV -> stringResource(R.string.unsigned_served_from_this_phone)
    // A source nobody typed in says where it came from, or it looks like something that appeared on its own.
    status.source.kind == Source.Kind.SUPPORTER && status.snapshot == null ->
        stringResource(R.string.added_with_your_supporter_code)
    status.snapshot != null -> pluralStringResource(R.plurals.n_packages, status.packages.size, status.packages.size)
    else -> stringResource(R.string.not_read_yet)
}

@Composable
private fun SourceRow(
    name: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    count: Int?,
    selected: Boolean,
    onOpen: () -> Unit,
    failed: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .background(if (selected) Color.White.copy(alpha = .06f) else Color.Transparent)
            .clickable(onClickLabel = name, onClick = onOpen)
            .padding(FolioSpace.COMFY.dp)
            .testTag("market-source-row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Column(Modifier.padding(start = FolioSpace.COMPACT.dp).weight(1f)) {
            Text(name, color = Color.White, fontSize = 16.sp)
            Text(
                detail,
                color = if (failed) FolioColors.RedSoft else Color.White.copy(alpha = .55f),
                fontSize = FolioType.FOOTNOTE.sp,
            )
        }
        count?.let {
            Text("$it", color = Color.White.copy(alpha = .55f), fontSize = FolioType.SUBHEAD.sp, modifier = Modifier.padding(start = FolioSpace.SMALL.dp))
        }
        Icon(
            androidx.compose.material.icons.Icons.Rounded.ChevronRight,
            contentDescription = null, tint = Color.White.copy(alpha = .3f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * One source, and what is in it.
 *
 * The packages come in as [rows] rather than being drawn here, so a package listed by a source looks and behaves
 * exactly as it does in the Packages tab - the same row, the same Get, the same long press.
 */
@Composable
internal fun MarketSourcePage(
    name: String,
    source: Source,
    status: SourceStatus?,
    packageCount: Int,
    showBack: Boolean,
    backTitle: String? = null,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onForget: () -> Unit,
    rows: @Composable () -> Unit,
) {
    val builtIn = source.kind == Source.Kind.BUILT_IN
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = FolioSpace.LARGE.dp)
            .testTag("market-source-page"),
    ) {
        if (showBack) {
            val back = stringResource(R.string.back)
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = back, onClick = onBack).padding(vertical = FolioSpace.COMPACT.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = null, tint = LocalAccent.current.ink, modifier = Modifier.size(18.dp))
                Text(backTitle ?: back, color = LocalAccent.current.ink, fontSize = 16.sp)
            }
        }
        Row(Modifier.padding(top = FolioSpace.COMPACT.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when {
                    builtIn -> Icons.Rounded.Home
                    source.kind == Source.Kind.LOCAL_DEV -> Icons.Rounded.Warning
                    else -> Icons.Rounded.Public
                },
                contentDescription = null,
                tint = when {
                    builtIn -> FolioColors.Green
                    source.kind == Source.Kind.LOCAL_DEV -> FolioColors.Warning
                    else -> FolioColors.BlueOnDark
                },
                modifier = Modifier.size(34.dp),
            )
            Column(Modifier.padding(start = FolioSpace.MEDIUM.dp)) {
                Text(name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                // The address, not a tidied version of it: this is the thing to compare with what a publisher says.
                Text(
                    if (builtIn) stringResource(R.string.inside_the_app) else source.url,
                    color = Color.White.copy(alpha = .55f), fontSize = 14.sp,
                )
            }
        }
        status?.failure?.let { failure ->
            SheetGroup(Modifier.padding(top = FolioSpace.COMFY.dp)) {
                Column(Modifier.padding(FolioSpace.COMFY.dp)) {
                    Text(stringResource(R.string.folio_couldn_t_reach_this_source), color = Color.White, fontSize = FolioType.SUBHEAD.sp)
                    Text(failure.message, color = FolioColors.RedSoft, fontSize = FolioType.FOOTNOTE.sp)
                    if (status.snapshot != null) {
                        Text(stringResource(R.string.showing_the_list_it_had_before), color = Color.White.copy(alpha = .55f), fontSize = FolioType.FOOTNOTE.sp)
                    }
                }
            }
        }
        SheetGroupLabel(stringResource(R.string.information))
        SheetGroup(Modifier.padding(bottom = FolioSpace.MEDIUM.dp)) {
            Column(Modifier.padding(FolioSpace.COMFY.dp)) {
                Text(pluralStringResource(R.plurals.n_packages, packageCount, packageCount), color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
                Text(
                    when {
                        builtIn -> stringResource(R.string.it_updates_with_folio_and_uses_no)
                        source.kind == Source.Kind.LOCAL_DEV -> stringResource(R.string.unsigned_and_read_only_over_localhost)
                        status?.snapshot != null -> stringResource(
                            R.string.signed_1_s,
                            android.text.format.DateUtils.getRelativeTimeSpanString(
                                status.snapshot.entry.timestamp * 1000L,
                                System.currentTimeMillis(),
                                android.text.format.DateUtils.MINUTE_IN_MILLIS,
                            ).toString(),
                        )
                        else -> stringResource(R.string.not_read_yet)
                    },
                    color = Color.White.copy(alpha = .55f), fontSize = FolioType.FOOTNOTE.sp,
                )
            }
        }
        if (!builtIn) {
            Row(Modifier.padding(bottom = FolioSpace.MEDIUM.dp), horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                Pill(
                    stringResource(if (status?.refreshing == true) R.string.refreshing else R.string.refresh),
                    onRefresh,
                )
                Pill(stringResource(R.string.remove), onForget, destructive = true)
            }
        }
        SheetGroupLabel(stringResource(R.string.from_this_source))
        if (packageCount == 0) {
            Text(
                stringResource(R.string.nothing_from_this_source_yet),
                color = Color.White.copy(alpha = .55f), fontSize = 14.sp,
                modifier = Modifier.padding(vertical = FolioSpace.LARGE.dp),
            )
        } else {
            rows()
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Folio's own source has no address; this stands in for one so a page can be opened on it like any other. */
internal const val BUILT_IN_SOURCE_URL = "folio://built-in/"

@Composable
private fun Pill(label: String, onClick: () -> Unit, destructive: Boolean = false) {
    Text(
        label,
        color = if (destructive) FolioColors.RedSoft else LocalAccent.current.ink,
        fontSize = FolioType.SUBHEAD.sp,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .08f))
            .clickable(onClick = onClick).heightIn(min = 44.dp).padding(horizontal = FolioSpace.COMFY.dp, vertical = FolioSpace.MEDIUM.dp),
    )
}

/**
 * Trust on first use, out loud: the fingerprint of the key the source signs with, so it can be compared with what the
 * publisher says it should be. Folio pins it, and a later key change comes back here rather than being followed.
 */
@Composable
internal fun MarketTrustSheet(
    url: String,
    key: SourceKey,
    previous: SourceKey?,
    onTrust: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(FolioSpace.XL.dp).testTag("market-trust-sheet")) {
        Text(
            stringResource(if (previous == null) R.string.add_this_source else R.string.this_source_changed_its_key),
            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
        )
        Text(url, color = Color.White.copy(alpha = .55f), fontSize = 14.sp, modifier = Modifier.padding(bottom = FolioSpace.MEDIUM.dp))
        if (previous != null) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.CARD.dp)).background(Color(0xFF3A2A16)).padding(FolioSpace.COMFY.dp)) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = FolioColors.Warning, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.a_source_s_key_normally_never_changes_if),
                    color = Color.White.copy(alpha = .9f), fontSize = FolioType.FOOTNOTE.sp, modifier = Modifier.padding(start = FolioSpace.COMPACT.dp),
                )
            }
            SheetGroupLabel(stringResource(R.string.key_folio_has))
            Fingerprint(previous)
        }
        SheetGroupLabel(stringResource(if (previous == null) R.string.its_key_fingerprint else R.string.new_key))
        Fingerprint(key)
        Text(
            stringResource(R.string.compare_this_with_the_fingerprint_the),
            color = Color.White.copy(alpha = .55f), fontSize = FolioType.FOOTNOTE.sp, modifier = Modifier.padding(top = FolioSpace.SMALL.dp),
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.cancel), color = LocalAccent.current.ink, fontSize = 16.sp,
                modifier = Modifier.clip(RoundedCornerShape(FolioRadius.CARD.dp)).clickable(onClick = onCancel)
                    .heightIn(min = 44.dp).padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.MEDIUM.dp),
            )
            Text(
                stringResource(if (previous == null) R.string.trust_and_add else R.string.trust_the_new_key),
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(FolioRadius.CARD.dp)).background(LocalAccent.current.fill)
                    .clickable(onClick = onTrust).heightIn(min = 44.dp).padding(horizontal = FolioSpace.XL.dp, vertical = FolioSpace.MEDIUM.dp)
                    .testTag("market-trust-confirm"),
            )
        }
    }
}

@Composable
private fun Fingerprint(key: SourceKey) {
    Text(
        key.fingerprintGroups,
        color = Color.White, fontSize = FolioType.SUBHEAD.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FolioColors.SheetSurface).padding(FolioSpace.MEDIUM.dp),
    )
}

/** What a refresh said, in a line the store can show. */
internal fun refreshMessage(context: android.content.Context, source: Source, result: RefreshResult): String = when (result) {
    is RefreshResult.Updated -> context.getString(R.string.source_updated, result.snapshot.index.name.english)
    is RefreshResult.Unchanged -> context.getString(R.string.source_is_up_to_date, source.label)
    is RefreshResult.NeedsTrust -> context.getString(R.string.source_needs_its_key_checked, source.label)
    is RefreshResult.Failed -> result.message
}

/** Typing in a source's address. Folio checks it's https and reads its key before anything else happens. */
@Composable
internal fun MarketAddSourceSheet(url: String, onUrl: (String) -> Unit, onNext: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(FolioSpace.XL.dp).testTag("market-add-source")) {
        Text(stringResource(R.string.add_a_source), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.paste_the_address_the_publisher_gave_you),
            color = Color.White.copy(alpha = .55f), fontSize = FolioType.FOOTNOTE.sp, modifier = Modifier.padding(vertical = FolioSpace.SMALL.dp),
        )
        IosSearchField(
            query = url,
            onQuery = onUrl,
            placeholder = "https://…",
            modifier = Modifier.padding(vertical = FolioSpace.TINY.dp),
            onSearch = onNext,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.cancel), color = LocalAccent.current.ink, fontSize = 16.sp,
                modifier = Modifier.clip(RoundedCornerShape(FolioRadius.CARD.dp)).clickable(onClick = onCancel)
                    .heightIn(min = 44.dp).padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.MEDIUM.dp),
            )
            Text(
                stringResource(R.string.next), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(FolioRadius.CARD.dp))
                    .background(if (url.isBlank()) LocalAccent.current.fill.copy(alpha = .4f) else LocalAccent.current.fill)
                    .clickable(enabled = url.isNotBlank(), onClick = onNext)
                    .heightIn(min = 44.dp).padding(horizontal = FolioSpace.XL.dp, vertical = FolioSpace.MEDIUM.dp)
                    .testTag("market-add-source-next"),
            )
        }
    }
}
