package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mccal.folio.market.ExternalSource
import com.mccal.folio.market.PackageManifest

/**
 * Where to get an app of its own.
 *
 * The manifest's `via` list is the author's, so the sheet shows every option rather than choosing one, and each of
 * them leaves Folio: Android takes it from there, asks its own questions, and Folio finds out the app arrived by
 * looking for it afterwards. [onInstallHere] adds the one that doesn't leave, when the setting allows it.
 */
@Composable
internal fun MarketExternalSheet(
    manifest: PackageManifest,
    /** Folio's own install, when the setting allows it and the listing carries an APK. */
    onInstallHere: (() -> Unit)? = null,
    /**
     * Whether the listing came from a signed source. A Local Dev source is unsigned, so "the checksum its source
     * signed" would be untrue there: the checksum is still checked, but nothing vouches for the list it came in.
     */
    signed: Boolean = true,
    onPick: (ExternalSource) -> Unit,
    onCancel: () -> Unit,
) {
    val name = manifest.name.english
    // Both lines of this sheet's own copy say what Folio does, so both change when Folio is one of the answers.
    // Leaving "Folio doesn't install apps itself" above a row that says "Install with Folio" was a lie by layout.
    val here = onInstallHere != null
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 18.dp).testTag("market-external-sheet")) {
        Text(
            stringResource(R.string.get_1_s, name),
            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(
                if (here) R.string.install_it_with_folio_or_get_it_from
                else R.string.folio_doesn_t_install_apps_itself_choose,
            ),
            color = Color.White.copy(alpha = .7f), fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(14.dp))
        onInstallHere?.let { install ->
            // First, because it is the one that doesn't leave. Sileo's shape: the store fetches and Android asks.
            val label = stringResource(R.string.install_with_folio)
            SheetGroup(Modifier.padding(bottom = 10.dp)) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp)
                        .clickable(onClickLabel = label, onClick = install)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("market-external-here"),
                ) {
                    Text(label, color = LocalAccent.current.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.folio_downloads_it_and_android_asks),
                        color = Color.White.copy(alpha = .55f), fontSize = 13.sp,
                    )
                }
            }
        }
        SheetGroup {
            manifest.via.forEachIndexed { index, source ->
                if (index > 0) MenuDivider()
                val label = stringResource(MarketExternalApp.label(source.store))
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp)
                        .clickable(onClickLabel = label) { onPick(source) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("market-external-${source.store.id}"),
                ) {
                    Text(label, color = Color.White, fontSize = 16.sp)
                    Text(
                        stringResource(MarketExternalApp.detail(source.store)),
                        color = Color.White.copy(alpha = .55f), fontSize = 13.sp,
                    )
                }
            }
        }
        // Said before anything is tapped, not after: leaving Folio for an app store is the part worth knowing -
        // or, when Folio can install it, what Folio checks and what it can't.
        Text(
            stringResource(
                when {
                    !here -> R.string.folio_never_downloads_or_installs_an_app
                    signed -> R.string.folio_checks_the_download_against_the
                    else -> R.string.folio_checks_the_download_unsigned
                },
            ),
            color = Color.White.copy(alpha = .55f), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp),
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
            Text(
                stringResource(R.string.cancel),
                color = LocalAccent.current.ink, fontSize = 16.sp,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onCancel)
                    .heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
