package com.mccal.folio

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mccal.folio.market.FeaturedItem
import com.mccal.folio.market.IndexPackage
import kotlinx.coroutines.delay

/**
 * Featured, in whichever style the user picked ([com.mccal.folio.market.FeaturedStyle]).
 *
 * The carousel shows whole banners only, snaps between them, and advances itself every few seconds until you touch it
 * — and never when Reduce Motion is on. The calm style is the same packages as plain rows.
 */
@Composable
internal fun MarketFeatured(
    featured: List<FeaturedItem>,
    packages: List<IndexPackage>,
    calm: Boolean,
    onOpen: (String) -> Unit,
    /** Draws one of the index's pictures (a path on the source) to fill the modifier. Folio's own index, so its own files. */
    picture: @Composable (String, Modifier) -> Unit = { _, _ -> },
) {
    val banners = featured.mapNotNull { item -> packages.firstOrNull { it.id == item.packageId }?.let { item to it } }
    if (banners.isEmpty()) return
    if (calm) {
        SheetGroup(Modifier.padding(bottom = FolioSpace.COMPACT.dp)) {
            banners.forEachIndexed { index, (item, entry) ->
                if (index > 0) MenuDivider()
                val name = entry.manifest?.name?.english ?: entry.id
                val openLabel = stringResource(R.string.open_1_s, name)
                Column(
                    Modifier.fillMaxWidth()
                        .clickable(onClickLabel = openLabel) { onOpen(entry.id) }
                        .padding(horizontal = FolioSpace.COMFY.dp, vertical = FolioSpace.MEDIUM.dp),
                ) {
                    item.label?.english?.let { Text(it, color = Color.White.copy(alpha = .55f), fontSize = FolioType.GROUP_LABEL.sp) }
                    Text(name, color = Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.Medium)
                    entry.manifest?.description?.english?.let {
                        Text(it, color = Color.White.copy(alpha = .7f), fontSize = FolioType.FOOTNOTE.sp, maxLines = 2)
                    }
                }
            }
        }
        return
    }

    val pager = rememberPagerState(pageCount = { banners.size })
    var touched by remember { mutableStateOf(false) }
    val still = LocalReduceMotion.current
    // A drag by a finger, not any scroll at all: `isScrollInProgress` is true while the carousel advances itself,
    // so watching that made the first advance count as a touch and the carousel stopped after one banner.
    LaunchedEffect(pager) {
        pager.interactionSource.interactions.collect {
            if (it is androidx.compose.foundation.interaction.DragInteraction.Start ||
                it is androidx.compose.foundation.interaction.PressInteraction.Press
            ) {
                touched = true
            }
        }
    }
    if (!still && banners.size > 1) {
        LaunchedEffect(touched) {
            while (!touched) {
                delay(4500)
                if (touched) break
                pager.animateScrollToPage((pager.currentPage + 1) % banners.size)
            }
        }
    }
    // A picture is about twice as wide as tall, so the card follows the width, between the old 148 dp and a cap that keeps
    // it from filling an unfolded screen. Every page takes the same height so the carousel does not jump between them.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
    val bannerHeight = (maxWidth / 2.05f).coerceIn(148.dp, 200.dp)
    HorizontalPager(
        state = pager,
        contentPadding = PaddingValues(end = 0.dp),
        pageSpacing = 10.dp,
        // At least the banner's height; a text banner grows with the text, which at 200% font scale was cut off below the title.
        modifier = Modifier.fillMaxWidth().heightIn(min = bannerHeight),
    ) { page ->
        val (item, entry) = banners[page]
        val name = entry.manifest?.name?.english ?: entry.id
        val openLabel = stringResource(R.string.open_1_s, name)
        val image = item.image
        if (image != null) {
            // The picture is the banner and carries its own words, so nothing is written over it; TalkBack gets them here.
            val said = listOfNotNull(item.label?.english, name).joinToString(", ")
            Box(
                Modifier.fillMaxWidth().height(bannerHeight).clip(RoundedCornerShape(FolioRadius.GROUP.dp))
                    .background(bannerColor(page))
                    .clickable(onClickLabel = openLabel) { onOpen(entry.id) }
                    .semantics { contentDescription = said },
            ) { picture(image, Modifier.fillMaxSize()) }
            return@HorizontalPager
        }
        Column(
            Modifier.fillMaxWidth().heightIn(min = bannerHeight).clip(RoundedCornerShape(FolioRadius.GROUP.dp))
                .background(bannerColor(page))
                .clickable(onClickLabel = openLabel) { onOpen(entry.id) }
                .padding(FolioSpace.LARGE.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            item.label?.english?.let { Text(it, color = Color.White.copy(alpha = .95f), fontSize = FolioType.GROUP_LABEL.sp) }
            Text(name, color = Color.White, fontSize = FolioType.TITLE3.sp, fontWeight = FontWeight.Medium)
            entry.manifest?.description?.english?.let {
                Text(it, color = Color.White.copy(alpha = .9f), fontSize = FolioType.FOOTNOTE.sp, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
    }
    if (banners.size > 1) {
        // One control, not one per dot: you swipe the carousel, and the dots say where you are. The words are read
        // before the semantics block, which isn't a composable scope.
        val dots = stringResource(R.string.featured_page_1_d_of_2_d, pager.currentPage + 1, banners.size)
        Row(
            Modifier.fillMaxWidth().padding(vertical = FolioSpace.SMALL.dp)
                .semantics { contentDescription = dots },
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(banners.size) { page ->
                val on by animateFloatAsState(if (page == pager.currentPage) 1f else .3f, label = "dot")
                Box(
                    Modifier.padding(horizontal = 3.dp).size(7.dp).clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = on)),
                )
            }
        }
    }
}

/** Banner colors, from Folio's own palette, so a package without an image still looks like Folio. */
private fun bannerColor(page: Int): Color =
    listOf(Color(0xFF0A6CCC), Color(0xFF0F6E56), FolioColors.Indigo, Color(0xFFB0194A))[page % 4]
