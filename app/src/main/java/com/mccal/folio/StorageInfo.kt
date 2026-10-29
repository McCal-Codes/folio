package com.mccal.folio

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume

/**
 * One place files live, as Android reports it. A size Folio could not read is null, never a guess: the page says
 * "Unavailable" for it.
 *
 * [mounted] is true only for a volume that is mounted and writable. A card that is read-only, being checked, or
 * shared with a computer is not somewhere Folio can put files, so it counts as not mounted.
 */
internal data class VolumeInfo(
    val id: String,
    val label: String,
    val totalBytes: Long?,
    val freeBytes: Long?,
    val removable: Boolean,
    val primary: Boolean,
    val mounted: Boolean,
) {
    /** Bytes in use, or null when either size is unknown or the total is not a real size. */
    val usedBytes: Long? get() {
        val total = totalBytes ?: return null
        val free = freeBytes ?: return null
        if (total <= 0L) return null
        return (total - free).coerceIn(0L, total)
    }

    /** 0..1 of the volume in use, or null when it cannot be worked out. */
    val usedFraction: Float? get() {
        val used = usedBytes ?: return null
        val total = totalBytes ?: return null
        return (used.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    /** Where the page's calm "nearly full" note starts. */
    val nearlyFull: Boolean get() = (usedFraction ?: 0f) >= NEARLY_FULL_FRACTION

    companion object {
        const val NEARLY_FULL_FRACTION = 0.9f
    }
}

/** Removable volumes that are mounted and writable: the only places a later copy could go. */
internal fun destinationsFrom(volumes: List<VolumeInfo>): List<VolumeInfo> =
    volumes.filter { it.removable && it.mounted }

/** The name Android gives a volume, or [fallback] when it gives none. */
internal fun volumeLabel(description: String?, fallback: String): String =
    description?.trim().takeUnless { it.isNullOrEmpty() } ?: fallback

/** An app and the bytes Android says it takes (code, data and cache). */
internal data class AppSize(val packageName: String, val label: String, val bytes: Long)

/** The [limit] biggest apps, biggest first; apps with no size are left out. */
internal fun topApps(apps: List<AppSize>, limit: Int = 5): List<AppSize> =
    apps.filter { it.bytes > 0L }.sortedByDescending { it.bytes }.take(limit)

/** Everything the Storage page shows, read in one go. [apps] is null while Usage Access is not granted. */
internal data class StorageSnapshot(
    val internal: VolumeInfo,
    val destinations: List<VolumeInfo>,
    val apps: List<AppSize>?,
)

/** Reads real values from the OS. Every read is guarded: a failure becomes an unknown size, never a crash. */
internal object StorageProbe {
    /** Call off the main thread: it touches the disk and asks Android for per-app sizes. */
    fun read(context: Context, internalLabel: String, removableLabel: String): StorageSnapshot {
        val stats = context.getSystemService(StorageStatsManager::class.java)
        val internal = VolumeInfo(
            id = "internal",
            label = internalLabel,
            totalBytes = runCatching { stats.getTotalBytes(StorageManager.UUID_DEFAULT) }.getOrNull(),
            freeBytes = runCatching { stats.getFreeBytes(StorageManager.UUID_DEFAULT) }.getOrNull(),
            removable = false, primary = true, mounted = true,
        )
        val others = runCatching {
            context.getSystemService(StorageManager::class.java).storageVolumes.filter { !it.isPrimary }.map { volume(context, it, removableLabel) }
        }.getOrDefault(emptyList())
        val apps = if (runCatching { Suggestions.hasUsageAccess(context) }.getOrDefault(false)) appSizes(context, stats) else null
        return StorageSnapshot(internal, destinationsFrom(others), apps)
    }

    private fun volume(context: Context, v: StorageVolume, fallback: String): VolumeInfo {
        val mounted = v.state == Environment.MEDIA_MOUNTED
        val dir = if (mounted) runCatching { v.directory }.getOrNull() else null
        val statFs = dir?.let { runCatching { StatFs(it.path) }.getOrNull() }
        val description = runCatching { v.getDescription(context) }.getOrNull()
        return VolumeInfo(
            id = v.uuid ?: dir?.path ?: description.orEmpty(),
            label = volumeLabel(description, fallback),
            totalBytes = statFs?.let { it.blockCountLong * it.blockSizeLong },
            freeBytes = statFs?.let { it.availableBlocksLong * it.blockSizeLong },
            removable = v.isRemovable, primary = v.isPrimary, mounted = mounted,
        )
    }

    private fun appSizes(context: Context, stats: StorageStatsManager): List<AppSize> {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val user = android.os.Process.myUserHandle()
        val sizes = runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
            .distinctBy { it.applicationInfo.packageName }
            .mapNotNull { info ->
                val s = runCatching { stats.queryStatsForPackage(StorageManager.UUID_DEFAULT, info.applicationInfo.packageName, user) }.getOrNull()
                    ?: return@mapNotNull null
                // dataBytes already includes the cache, so adding cacheBytes would count it twice.
                AppSize(info.applicationInfo.packageName, info.label.toString(), s.appBytes + s.dataBytes)
            }
        return topApps(sizes)
    }
}
