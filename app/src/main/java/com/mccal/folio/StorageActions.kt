package com.mccal.folio

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/** The four doors the Storage page opens. Each one is somebody else's screen; Folio only knocks. */
internal enum class StorageAction { FreeUpSpace, StorageSettings, Files, Downloads }

/** An intent as plain data, so the fallback order can be tested without Android. */
internal data class IntentSpec(val action: String, val category: String? = null)

internal sealed interface StorageLaunch {
    /** [spec] was the first one that started. */
    data class Opened(val spec: IntentSpec) : StorageLaunch
    /** Nothing on this phone answered. */
    data object Unavailable : StorageLaunch
}

internal object StorageActions {
    // Spelled out so they are plain constants; each equals the SDK name in the comment.
    const val MANAGE_STORAGE = "android.os.storage.action.MANAGE_STORAGE" // StorageManager.ACTION_MANAGE_STORAGE
    const val INTERNAL_STORAGE = "android.settings.INTERNAL_STORAGE_SETTINGS" // Settings.ACTION_INTERNAL_STORAGE_SETTINGS
    const val MAIN = "android.intent.action.MAIN" // Intent.ACTION_MAIN
    const val APP_FILES = "android.intent.category.APP_FILES" // Intent.CATEGORY_APP_FILES
    const val VIEW_DOWNLOADS = "android.intent.action.VIEW_DOWNLOADS"

    /** What to try, best first. No Samsung or other maker component is named: only standard actions. */
    fun candidates(action: StorageAction): List<IntentSpec> = when (action) {
        StorageAction.FreeUpSpace -> listOf(IntentSpec(MANAGE_STORAGE), IntentSpec(INTERNAL_STORAGE))
        StorageAction.StorageSettings -> listOf(IntentSpec(INTERNAL_STORAGE))
        StorageAction.Files -> listOf(IntentSpec(MAIN, APP_FILES))
        StorageAction.Downloads -> listOf(IntentSpec(VIEW_DOWNLOADS))
    }

    /** Tries each candidate through [start] (true means it started) and stops at the first that does. Never throws. */
    fun launch(action: StorageAction, start: (IntentSpec) -> Boolean): StorageLaunch {
        for (spec in candidates(action)) {
            val started = try { start(spec) } catch (_: Exception) { false }
            if (started) return StorageLaunch.Opened(spec)
        }
        return StorageLaunch.Unavailable
    }

    /** The real starter: opens the screen from [context]; false when nothing handles it or the phone refuses. */
    fun starter(context: Context): (IntentSpec) -> Boolean = { spec ->
        try {
            val intent = Intent(spec.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            spec.category?.let(intent::addCategory)
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }
    }
}
