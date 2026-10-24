package com.mccal.folio

import android.content.Context
import androidx.compose.runtime.Composable
import com.mccal.folio.market.UrlHttpClient

/**
 * The release build's twin of src/devsource: the same names, and nothing behind them. The Folio Dev builds source
 * (T24 in docs/sdk/threat-model.md) is not part of a release, so there is no dev code to store, no address to
 * send one to and no page to show. R8 removes these calls; tools/check-no-dev-source.sh proves the real code is absent.
 */
internal object DevSource {
    const val PAGE_URL = "folio://dev/"

    fun enabled(@Suppress("UNUSED_PARAMETER") context: Context): Boolean = false

    /** No plain-http source is ever acceptable in a release build. */
    fun insecureSourceOk(@Suppress("UNUSED_PARAMETER") context: Context): (String) -> Boolean = { false }

    /** The stock client, exactly as the Market built it before the dev source existed. */
    fun httpClient(@Suppress("UNUSED_PARAMETER") context: Context, allowLocalhost: Boolean): UrlHttpClient =
        UrlHttpClient(allowLocalhost = allowLocalhost)
}

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun DevSourceRow(selected: Boolean, onOpen: () -> Unit) = Unit

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun DevSourcePage(showBack: Boolean, backTitle: String, onBack: () -> Unit) = Unit
