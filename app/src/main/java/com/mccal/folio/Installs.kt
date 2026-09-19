package com.mccal.folio

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageInstaller
import android.graphics.Bitmap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** An app being downloaded or updated, as the installer reports it. */
data class InstallProgress(val packageName: String, val label: String?, val icon: Bitmap?, val progress: Float, val newApp: Boolean)

/**
 * iOS-style download progress: Android tells the Home app about every install session (Play Store, Galaxy Store,
 * sideloads) through LauncherApps, so Folio can show a ring on the icon while an app updates and a waiting tile while
 * a new app downloads. Nothing is guessed: progress is the installer's own number.
 */
internal object Installs {
    private val sessions = MutableStateFlow<Map<Int, InstallProgress>>(emptyMap())
    /** Active install sessions by session id. */
    val active: StateFlow<Map<Int, InstallProgress>> = sessions.asStateFlow()
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        val launcherApps = app.getSystemService(LauncherApps::class.java)
        val installer = app.packageManager.packageInstaller
        fun installed(pkg: String) = runCatching { app.packageManager.getPackageInfo(pkg, 0) }.isSuccess
        fun put(info: PackageInstaller.SessionInfo?) {
            val pkg = info?.appPackageName ?: return
            sessions.update { it + (info.sessionId to InstallProgress(pkg, info.appLabel?.toString(), info.appIcon, info.progress, !installed(pkg))) }
        }
        runCatching { launcherApps.allPackageInstallerSessions.filter { it.isActive }.forEach(::put) }
        runCatching {
            launcherApps.registerPackageInstallerSessionCallback(app.mainExecutor, object : PackageInstaller.SessionCallback() {
                override fun onCreated(sessionId: Int) = put(installer.getSessionInfo(sessionId))
                override fun onBadgingChanged(sessionId: Int) = put(installer.getSessionInfo(sessionId))
                override fun onActiveChanged(sessionId: Int, active: Boolean) {
                    if (active) put(installer.getSessionInfo(sessionId)) else sessions.update { it - sessionId }
                }
                override fun onProgressChanged(sessionId: Int, progress: Float) {
                    sessions.update { map -> map[sessionId]?.let { map + (sessionId to it.copy(progress = progress)) } ?: map }
                }
                override fun onFinished(sessionId: Int, success: Boolean) {
                    val finished = sessions.value[sessionId]
                    sessions.update { it - sessionId }
                    if (success && finished?.newApp == true) NewApps.mark(app, finished.packageName)
                }
            })
        }
    }
}

/** Install progress by package for icon rings (provided at the root of Folio's UI). */
internal val LocalInstallProgress = staticCompositionLocalOf { emptyMap<String, Float>() }
/** Packages installed recently and not opened yet: they get iOS's blue dot beside their name. */
internal val LocalNewApps = staticCompositionLocalOf { emptySet<String>() }

/** Recently downloaded apps that haven't been opened, like the blue dot on iPhone. Stored locally; cleared on first launch. */
internal object NewApps {
    private const val PREFS = "new_apps"
    private const val KEEP_MS = 7L * 24 * 60 * 60 * 1000
    val packages = MutableStateFlow<Set<String>>(emptySet())

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val now = System.currentTimeMillis()
        val fresh = prefs.all.mapNotNull { (pkg, at) -> pkg.takeIf { (at as? Long)?.let { now - it < KEEP_MS } == true } }.toSet()
        prefs.edit().apply { prefs.all.keys.filter { it !in fresh }.forEach(::remove) }.apply()
        packages.value = fresh
    }

    fun mark(context: Context, pkg: String) {
        context.getSharedPreferences(PREFS, 0).edit().putLong(pkg, System.currentTimeMillis()).apply()
        packages.update { it + pkg }
    }

    fun opened(context: Context, pkg: String) {
        if (pkg !in packages.value) return
        context.getSharedPreferences(PREFS, 0).edit().remove(pkg).apply()
        packages.update { it - pkg }
    }
}

/** iOS's blue dot before the name of an app that was just downloaded and hasn't been opened. */
@androidx.compose.runtime.Composable
internal fun NewAppDot(packageName: String, size: androidx.compose.ui.unit.Dp = 6.dp) {
    if (packageName !in LocalNewApps.current) return
    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(end = 3.dp).size(size)
        .background(FolioColors.Blue, androidx.compose.foundation.shape.CircleShape)
        .semantics { contentDescription = "New" })
}

/** New apps still downloading, as waiting icons with their progress, at the top of the App Library. */
@androidx.compose.runtime.Composable
internal fun DownloadingApps(labelColor: androidx.compose.ui.graphics.Color) {
    val downloads = Installs.active.collectAsState().value.values.filter { it.newApp }.distinctBy { it.packageName }
    if (downloads.isEmpty()) return
    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxWidth().padding(bottom = 14.dp).testTag("library-downloading")) {
        androidx.compose.material3.Text(stringResource(R.string.downloading), color = labelColor.copy(alpha = .7f), fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = androidx.compose.ui.Modifier.padding(start = 4.dp, bottom = 8.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
            items(downloads, key = { it.packageName }) { d ->
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.width(72.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(56.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = .16f))) {
                        d.icon?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null, androidx.compose.ui.Modifier.fillMaxSize()) }
                        InstallRing(d.progress, androidx.compose.ui.Modifier.fillMaxSize())
                    }
                    androidx.compose.material3.Text(d.label ?: "Waiting…", color = labelColor, fontSize = 11.sp, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = androidx.compose.ui.Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
