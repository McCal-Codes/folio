package com.mccal.folio

/**
 * Which page a Home button press lands on. Pressed while Folio is already in front with nothing open on top, it goes to
 * the first Home page, like other launchers (a Focus with its own Home page, [focusPage], wins). Pressed from an app, or
 * with a sheet, menu, Spotlight or edit mode open, it keeps today's behavior: the Focus page, else the page you were on,
 * else [lastHomePage]. Closing what is open is the whole job of that press.
 */
internal fun homePressPage(
    alreadyInFront: Boolean, somethingOpen: Boolean, focusPage: Int?, currentPage: Int, homePages: Int, lastHomePage: Int,
): Int {
    if (homePages <= 0) return 0
    if (focusPage != null) return focusPage
    if (alreadyInFront && !somethingOpen) return 0
    return currentPage.takeIf { it in 0 until homePages } ?: lastHomePage.coerceIn(0, homePages - 1)
}
