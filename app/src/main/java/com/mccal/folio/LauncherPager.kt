package com.mccal.folio

import androidx.compose.foundation.pager.PagerState

/** Home/drop indices stay zero based; Discover is logical page -1. */
internal class LauncherPager(val state: PagerState, private val firstHome: Int) {
    val currentPage get() = state.currentPage - firstHome
    val settledPage get() = state.settledPage - firstHome
    /** Where the pager is between pages, in Home pages (2.25 is a quarter of the way from page 2 to page 3). Read it when drawing, not in composition. */
    val position get() = state.currentPage + state.currentPageOffsetFraction - firstHome
    fun requestScrollToPage(page: Int) = state.requestScrollToPage(page + firstHome)
    suspend fun scrollToPage(page: Int) = state.scrollToPage(page + firstHome)
    suspend fun animateScrollToPage(page: Int) = state.animateScrollToPage(page + firstHome)
}
