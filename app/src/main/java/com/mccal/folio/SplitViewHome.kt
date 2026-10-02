package com.mccal.folio

/**
 * When Folio is given one half of a split screen, which Home page it should show: the one it was on if that is a Home
 * page, else the last Home page you used. Folio is then one cover-sized page next to your app, instead of whatever
 * page it was left on (the App Library or the Today page). Null means leave it where it is: not in split screen, or
 * already on a Home page, so nothing you were looking at moves.
 */
internal fun splitViewHomePage(inSplitScreen: Boolean, currentPage: Int, homePages: Int, lastHomePage: Int): Int? {
    if (!inSplitScreen || homePages <= 0) return null
    if (currentPage in 0 until homePages) return null
    return lastHomePage.coerceIn(0, homePages - 1)
}
