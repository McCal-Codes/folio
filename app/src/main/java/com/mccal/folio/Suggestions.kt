package com.mccal.folio

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow

/**
 * Siri-style app suggestions: apps you tend to open around this time of day, favoring recent habits.
 * Built on Folio's own launch history (no permission). With Usage Access, apps opened from anywhere count too.
 * Everything is computed on the phone.
 */
internal object Suggestions {
    private const val HALF_LIFE_MS = 14 * 24 * 60 * 60 * 1000.0
    private const val HOUR_MS = 60 * 60 * 1000L
    private const val DAY_MS = 24 * HOUR_MS

    /**
     * Score per key for [now]: each use counts by how close its time of day is to now (a bell curve about 90 minutes
     * wide, wrapping past midnight), faded by age (halving every two weeks), plus a little plain recency.
     */
    fun scores(uses: List<Pair<String, Long>>, now: Long, zoneOffsetMs: Long = java.util.TimeZone.getDefault().getOffset(now).toLong()): Map<String, Double> {
        val nowOfDay = Math.floorMod(now + zoneOffsetMs, DAY_MS).toDouble()
        val result = HashMap<String, Double>()
        uses.forEach { (key, at) ->
            if (at > now) return@forEach
            val ofDay = Math.floorMod(at + zoneOffsetMs, DAY_MS).toDouble()
            val gap = abs(ofDay - nowOfDay).let { min(it, DAY_MS - it) } / HOUR_MS
            val timeOfDay = exp(-(gap * gap) / (2 * 1.5 * 1.5))
            val age = 0.5.pow((now - at) / HALF_LIFE_MS)
            result.merge(key, age * (timeOfDay + .15), Double::plus)
        }
        return result
    }

    /** Folio's launch log as (app id, time). */
    private fun launches(context: Context): List<Pair<String, Long>> =
        (context.getSharedPreferences("folio", 0).getString("launch_log", "") ?: "").split('\n').mapNotNull { line ->
            val cut = line.lastIndexOf('|')
            if (cut <= 0) null else line.substring(cut + 1).toLongOrNull()?.let { line.substring(0, cut) to it }
        }

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))

    /** App opens from Usage Access over the last four weeks, as (package, time). */
    private fun usage(context: Context, now: Long): List<Pair<String, Long>> {
        if (!hasUsageAccess(context)) return emptyList()
        val events = runCatching { context.getSystemService(UsageStatsManager::class.java).queryEvents(now - 28 * DAY_MS, now) }.getOrNull() ?: return emptyList()
        val out = ArrayList<Pair<String, Long>>()
        val event = UsageEvents.Event()
        var lastPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // One use per time an app comes to the front, not per activity inside it.
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && event.packageName != lastPackage && event.packageName != context.packageName) {
                out += event.packageName to event.timeStamp
                lastPackage = event.packageName
            }
        }
        return out
    }

    /**
     * Smart Rotate: the card to show now, or null to stay. A card takes over only when it's clearly more relevant
     * than the one showing (half again as relevant and above a floor), so a stack doesn't flip back and forth.
     */
    fun smartStackPick(relevance: List<Double>, current: Int): Int? {
        val best = relevance.indices.maxByOrNull { relevance[it] } ?: return null
        val now = relevance.getOrElse(current) { 0.0 }
        return best.takeIf { it != current && relevance[it] >= .3 && relevance[it] > now * 1.5 }
    }

    /** How often Smart Rotate looks for a more relevant card. */
    const val SMART_ROTATE_INTERVAL_MS = 15 * 60_000L

    /**
     * Smart Rotate: how long to wait before the next look, given when the last one was (both on a clock that keeps
     * running while Home is in the background). Time away from Home counts, so coming back after the interval
     * looks at once instead of starting a fresh wait on every resume.
     */
    fun smartRotateWait(now: Long, lastCheck: Long, interval: Long = SMART_ROTATE_INTERVAL_MS): Long =
        (interval - (now - lastCheck)).coerceIn(0L, interval)

    /** Relevance by package for right now (same scoring as [forNow]). Call off the main thread. */
    fun packageRelevance(context: Context, apps: List<AppEntry>, now: Long = System.currentTimeMillis()): Map<String, Double> {
        val byId = apps.associateBy { it.id }
        val out = HashMap<String, Double>()
        scores(launches(context).filter { it.first in byId }, now).forEach { (id, s) -> byId[id]?.let { out.merge(it.packageName, s, Double::plus) } }
        scores(usage(context, now), now).forEach { (pkg, s) -> out.merge(pkg, s * .6, Double::plus) }
        return out
    }

    /** Suggested apps for right now, best first. Call off the main thread. */
    fun forNow(context: Context, apps: List<AppEntry>, limit: Int = 8, now: Long = System.currentTimeMillis()): List<AppEntry> {
        val byId = apps.associateBy { it.id }
        val byPackage = apps.filter { !it.isWork }.groupBy { it.packageName }
        val launchScores = scores(launches(context).filter { it.first in byId }, now)
        val usageScores = scores(usage(context, now), now)
        val total = HashMap<String, Double>(launchScores)
        // Usage events name a package; credit its main app (a package with several launchers counts once).
        usageScores.forEach { (pkg, score) -> byPackage[pkg]?.firstOrNull()?.let { total.merge(it.id, score * .6, Double::plus) } }
        return total.entries.sortedByDescending { it.value }.mapNotNull { byId[it.key] }.take(limit)
    }
}
