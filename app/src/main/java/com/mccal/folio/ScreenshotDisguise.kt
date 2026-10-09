package com.mccal.folio

import java.text.Collator

/**
 * Which apps Screenshot Mode shows as a stock app instead: the ones the person picked, and with [allThirdParty] every
 * app that did not ship with the phone, including ones installed later. Keyed by app id like icon looks.
 */
data class DisguiseRules(
    val chosen: Set<String> = emptySet(),
    val allThirdParty: Boolean = false,
) {
    val isEmpty: Boolean get() = chosen.isEmpty() && !allThirdParty
}

/**
 * Screenshot Mode's app swap, visual only: a swapped app keeps its id, component and user, so layout, drag and drop
 * and launching all still work and open the real app, but it shows a stock app's icon and name. The stand-ins are
 * the phone's own apps (Phone, Camera, Clock...), so nothing is drawn or bundled.
 *
 * Apps that shipped with the phone are never swapped and are the only stand-ins. A work-profile app is never a
 * stand-in, since its badge is part of the icon. The stand-in is picked from the app's id, so the same app shows as
 * the same stock app every time; two apps may share one. With no stand-in on the phone nothing is swapped.
 */
object ScreenshotDisguise {
    /**
     * [apps] with the swapped ones disguised and re-sorted by the name shown, or [apps] itself when nothing is
     * swapped. [systemPackages] are the packages that shipped with the phone.
     */
    fun apply(apps: List<AppEntry>, rules: DisguiseRules, systemPackages: Set<String>): List<AppEntry> {
        if (rules.isEmpty) return apps
        val pool = standIns(apps, systemPackages)
        if (pool.isEmpty()) return apps
        var changed = false
        val disguised = apps.map { app ->
            if (!isSwapped(app, rules, systemPackages)) app
            else {
                changed = true
                val standIn = pool[Math.floorMod(app.id.hashCode(), pool.size)]
                // systemLabel too, so search finds the app only by the name on screen.
                app.copy(label = standIn.label, systemLabel = standIn.label, icon = standIn.icon, iconFrom = standIn)
            }
        }
        if (!changed) return apps
        val collator = Collator.getInstance()
        return disguised.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    fun isSwapped(app: AppEntry, rules: DisguiseRules, systemPackages: Set<String>): Boolean =
        app.packageName !in systemPackages && (app.id in rules.chosen || rules.allThirdParty)

    /** The phone's own apps that can stand in, in a fixed order so a pick does not move when apps are added. */
    internal fun standIns(apps: List<AppEntry>, systemPackages: Set<String>): List<AppEntry> =
        apps.filter { it.packageName in systemPackages && !it.isWork && !it.isShortcut && it.available }
            .distinctBy { it.packageName }
            .sortedBy { it.id }
}
