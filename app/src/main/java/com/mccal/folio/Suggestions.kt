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

    /** Folio's launch log as it is stored: one "id|time" line per launch. */
    private fun launchLog(context: Context) = context.getSharedPreferences("folio", 0).getString("launch_log", "") ?: ""

    /** Folio's launch log as (app id, time). */
    private fun launches(context: Context): List<Pair<String, Long>> =
        launchLog(context).split('\n').mapNotNull { line ->
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

    /** Relevance by package for right now (same scoring as [forNow]). Call off the main thread. */
    fun packageRelevance(context: Context, apps: List<AppEntry>, now: Long = System.currentTimeMillis()): Map<String, Double> {
        val byId = apps.associateBy { it.id }
        val out = HashMap<String, Double>()
        scores(launches(context).filter { it.first in byId }, now).forEach { (id, s) -> byId[id]?.let { out.merge(it.packageName, s, Double::plus) } }
        scores(usage(context, now), now).forEach { (pkg, s) -> out.merge(pkg, s * .6, Double::plus) }
        return out
    }

    /**
     * Suggested apps for right now, best first. Call off the main thread. Spotlight, Today View and the Suggestions widget
     * share one ranking for [RankingCache.FRESH_MS], so opening Spotlight does not read four weeks of Usage Access events
     * each time; a new launch, a changed app list, Usage Access turned on or off, or a new time zone ranks again.
     */
    fun forNow(context: Context, apps: List<AppEntry>, limit: Int = 8, now: Long = System.currentTimeMillis()): List<AppEntry> {
        val byId = apps.associateBy { it.id }
        return ranking.get(rankingKey(context, apps, now), now) { rank(context, apps, byId, now) }.mapNotNull { byId[it] }.take(limit)
    }

    /**
     * The last ranking for [apps] if it is still fresh, without reading anything slow, so Spotlight can show its
     * suggestions on its first frame instead of laying them out in the middle of its opening spring. Null if there is none.
     */
    fun cachedForNow(context: Context, apps: List<AppEntry>, limit: Int = 8, now: Long = System.currentTimeMillis()): List<AppEntry>? {
        val byId = apps.associateBy { it.id }
        return ranking.peek(rankingKey(context, apps, now), now)?.mapNotNull { byId[it] }?.take(limit)
    }

    private val ranking = RankingCache()

    // The time-of-day scores use the zone's offset, so a new time zone (or a daylight saving change) ranks again.
    private fun rankingKey(context: Context, apps: List<AppEntry>, now: Long) = RankingCache.Key(apps.map { it.id },
        launchLog(context).hashCode(), hasUsageAccess(context), java.util.TimeZone.getDefault().getOffset(now))

    private fun rank(context: Context, apps: List<AppEntry>, byId: Map<String, AppEntry>, now: Long): List<String> {
        val byPackage = apps.filter { !it.isWork }.groupBy { it.packageName }
        val launchScores = scores(launches(context).filter { it.first in byId }, now)
        val usageScores = scores(usage(context, now), now)
        val total = HashMap<String, Double>(launchScores)
        // Usage events name a package; credit its main app (a package with several launchers counts once).
        usageScores.forEach { (pkg, score) -> byPackage[pkg]?.firstOrNull()?.let { total.merge(it.id, score * .6, Double::plus) } }
        return total.entries.sortedByDescending { it.value }.map { it.key }
    }
}

/**
 * The shared suggestion rankings (app ids, best first) and when each was made. It keeps a few, one per key, because the
 * Suggestions widget ranks every app while Spotlight and Today View leave hidden ones out; with one slot they would keep
 * replacing each other's ranking.
 */
internal class RankingCache(private val size: Int = 4) {
    /** What a ranking depends on besides the time: the apps, Folio's launch log, Usage Access and the zone's offset. */
    data class Key(val appIds: List<String>, val launchStamp: Int, val usageAccess: Boolean, val zoneOffset: Int = 0)

    private class Entry(val key: Key, val at: Long, val ids: List<String>)

    // Replaced whole and read without a lock, so Spotlight's first frame never waits for a ranking in progress.
    @Volatile private var entries: List<Entry> = emptyList()

    /** The ranking for [key] at [now]: a fresh one made for the same key, otherwise [compute]'s. */
    fun get(key: Key, now: Long, compute: () -> List<String>): List<String> {
        peek(key, now)?.let { return it }
        // Ranked outside the lock: reading four weeks of Usage Access events can take a while.
        val made = compute()
        synchronized(this) {
            // When two rankings for one key finish out of order, the one made for the later time stays.
            val current = entries.firstOrNull { it.key == key }
            if (current == null || current.at <= now) entries = (listOf(Entry(key, now, made)) + entries.filter { it.key != key }).take(size)
        }
        return made
    }

    /** The ranking for [key] if it is fresh at [now]; null otherwise. Never waits. */
    fun peek(key: Key, now: Long): List<String>? = entries.firstOrNull { it.key == key && fresh(it.at, now) }?.ids

    companion object {
        /** A quarter hour, the same step the Suggestions widget re-ranks on. */
        const val FRESH_MS = 15 * 60 * 1000L
        fun fresh(madeAt: Long, now: Long) = now - madeAt in 0 until FRESH_MS
    }
}
