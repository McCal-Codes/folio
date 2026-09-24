package com.mccal.folio

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

/**
 * Checks the sources the user added, in the background, when they've asked Folio to.
 *
 * Off by default, because Folio is local-first: with it off, Folio only goes online when the store is opened and
 * refreshed by hand. When it's on, it asks once a day at most, only on unmetered networks unless that's turned off,
 * and [com.mccal.folio.market.RepoClient] still won't ask a source more than once every six hours.
 */
class MarketRefreshJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            try {
                val prefs = rememberedMarketPrefs(applicationContext)
                // The gate is asked again here, not only when the job was scheduled: a supporter's code can run out
                // between one day and the next, and a phone that can't open the store shouldn't be going online for
                // it. The setting is asked again for the same reason.
                if (MarketAccess.isOpen(applicationContext) && prefs.backgroundRefresh) {
                    refreshSources(applicationContext)
                } else {
                    schedule(applicationContext)
                }
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    /** Android wants the work to stop. It stops: the refresh is a nicety, and it runs again tomorrow. */
    override fun onStopJob(params: JobParameters): Boolean {
        scope.coroutineContext.cancelChildren()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val REFRESH = 4104

        private fun scheduler(context: Context) = context.getSystemService(JobScheduler::class.java)

        /**
         * Schedules or cancels the daily check to match the setting. Called when the setting changes and when Folio
         * starts, so turning it off really stops it.
         */
        fun schedule(context: Context) {
            val jobs = scheduler(context) ?: return
            val prefs = rememberedMarketPrefs(context)
            if (!MarketAccess.isOpen(context) || !prefs.backgroundRefresh) {
                jobs.cancel(REFRESH)
                return
            }
            val network = if (prefs.refreshOnWifiOnly) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY
            val pending = jobs.getPendingJob(REFRESH)
            if (pending != null && pending.networkType == network) return
            runCatching {
                jobs.schedule(
                    JobInfo.Builder(REFRESH, ComponentName(context, MarketRefreshJob::class.java))
                        .setRequiredNetworkType(network)
                        .setPeriodic(24L * 60 * 60 * 1000, 6L * 60 * 60 * 1000)
                        .build(),
                )
            }
        }

        /**
         * Refreshes every source, keeping the cached list when one fails. Nothing is installed: an update is something
         * the user chooses, so this only makes the store know there is one.
         */
        internal suspend fun refreshSources(context: Context) {
            val session = MarketSession(context, ReadOnlyLauncher)
            session.sources.refreshAll(force = false)
        }
    }
}

/**
 * A launcher that changes nothing, for the background job: refreshing a list must never apply anything, and with this
 * there's no way for it to.
 */
private object ReadOnlyLauncher : MarketLauncher {
    override val state = LauncherState()
    override fun installTweak(feature: TweakFeature) = Unit
    override fun removeTweak(feature: TweakFeature) = Unit
    override fun setFeatureScope(id: String, screen: FolioScreen, value: ScopeValue) = Unit
    override fun applyTheme(theme: FolioTheme) = Unit
    override fun applyArtBackground(art: Artwork, bytes: ByteArray): String =
        error("this launcher applies nothing")

    override fun restoreArtBackground(artId: String, snapshot: String) = Unit
}
