package com.mccal.folio

import android.content.Context
import android.content.pm.PackageManager
import com.mccal.folio.market.SourceKey
import com.mccal.folio.market.UrlHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicBoolean

/*
 * The Folio Dev debug source (docs/sdk/threat-model.md, T24): development builds and work-in-progress tweaks out of a
 * private repository, behind a signed dev code, for Folio Dev only. This file is the real thing; a release build
 * compiles src/nodevsource instead, which has the same names and does nothing.
 *
 * What protects the phone: the list of builds is signed with the dev publisher key and judged for age and rollback;
 * a download must match the size and SHA-256 in that list, carry this app's package id and signer, and not be an
 * older versionCode than the one installed. The dev code goes only to the worker's /dev/ addresses, never after a
 * redirect, and a release build neither stores nor sends one.
 */

/** One row of the signed builds list. */
internal data class DevBuildRow(
    val name: String, val tag: String, val branch: String, val sha: String,
    val builtAt: String, val builtAtEpoch: Long, val versionCode: Long, val versionName: String,
    val size: Long, val sha256: String, val subject: String, val buildType: String,
) {
    /** Anything not on main is work in progress, and is labeled so. */
    val wip: Boolean get() = branch != "main"
}

/** Reading and judging the signed list. Pure: no Android, no network. */
internal object DevBuilds {
    const val FORMAT = 1
    /** A list may ask to be believed for at most this long; one that asks for more is not a list this app made. */
    const val MAX_AGE_LIMIT_SECONDS = 90L * 24 * 60 * 60
    private const val MAX_BUILDS = 60
    private const val MAX_APK_BYTES = 200L * 1024 * 1024
    private val NAME = Regex("[A-Za-z0-9._-]{1,120}")
    private val HEX = Regex("[0-9a-f]{64}")

    enum class Problem { UNREADABLE, BAD_SIGNATURE, STALE, ROLLBACK }

    sealed interface Verdict {
        data class Good(val builds: List<DevBuildRow>, val timestamp: Long) : Verdict
        data class Bad(val problem: Problem) : Verdict
    }

    /**
     * [bytes] is builds.json exactly as served, [signature] the base64 DER of builds.json.sig. Order matters: nothing
     * in the file is read before the signature has checked, then age, then the rollback floor.
     */
    fun verify(bytes: ByteArray, signature: String, keys: List<String>, nowSeconds: Long, floorSeconds: Long): Verdict {
        val signed = keys.any { key -> SourceKey.parse(key)?.verifies(bytes, signature) == true }
        if (!signed) return Verdict.Bad(Problem.BAD_SIGNATURE)
        val json = runCatching { JSONObject(bytes.decodeToString()) }.getOrNull() ?: return Verdict.Bad(Problem.UNREADABLE)
        if (json.optInt("format") != FORMAT) return Verdict.Bad(Problem.UNREADABLE)
        val timestamp = json.optLong("timestamp")
        val maxAge = json.optLong("maxAge")
        if (timestamp <= 0 || maxAge !in 1..MAX_AGE_LIMIT_SECONDS) return Verdict.Bad(Problem.UNREADABLE)
        val rows = json.optJSONArray("builds") ?: return Verdict.Bad(Problem.UNREADABLE)
        if (rows.length() > MAX_BUILDS) return Verdict.Bad(Problem.UNREADABLE)
        val builds = (0 until rows.length()).map { parseRow(rows.optJSONObject(it)) ?: return Verdict.Bad(Problem.UNREADABLE) }
        if (timestamp < floorSeconds) return Verdict.Bad(Problem.ROLLBACK)
        if (nowSeconds > timestamp + maxAge) return Verdict.Bad(Problem.STALE)
        return Verdict.Good(builds.sortedByDescending { it.builtAtEpoch }, timestamp)
    }

    private fun parseRow(o: JSONObject?): DevBuildRow? {
        o ?: return null
        val name = o.optString("name"); val tag = o.optString("tag"); val sha256 = o.optString("sha256")
        val builtAt = o.optString("builtAt"); val size = o.optLong("size"); val code = o.optLong("versionCode")
        val epoch = runCatching { OffsetDateTime.parse(builtAt).toEpochSecond() }.getOrNull() ?: return null
        if (!NAME.matches(name) || !NAME.matches(tag) || !HEX.matches(sha256)) return null
        if (size !in 1..MAX_APK_BYTES || code <= 0) return null
        return DevBuildRow(name, tag, o.optString("branch").take(120), o.optString("sha").take(40), builtAt, epoch, code,
            o.optString("versionName").take(60), size, sha256, o.optString("subject").take(200), o.optString("buildType").take(20))
    }

    /** `0.6.9-dev.<branch>.<n>`: text only, the builds' versionName never changes (D3). n counts this branch's builds, oldest first. */
    fun displayVersion(build: DevBuildRow, all: List<DevBuildRow>): String {
        val n = all.filter { it.branch == build.branch }.sortedBy { it.builtAtEpoch }.indexOfFirst { it.tag == build.tag } + 1
        val slug = build.branch.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "build" }
        return "${build.versionName.substringBefore('-')}-dev.$slug.${n.coerceAtLeast(1)}"
    }
}

/** Why a downloaded or chosen build is refused. */
internal enum class DevRefusal { WRONG_SIZE, WRONG_HASH, WRONG_PACKAGE, WRONG_SIGNER, OLDER_VERSION }

/** The checks between a download and Android's installer. Pure, so each refusal is tested on its own. */
internal object DevInstallCheck {
    /** Before anything is downloaded: Android will not install an older versionCode over a newer one. */
    fun beforeDownload(build: DevBuildRow, installedVersionCode: Long): DevRefusal? =
        if (build.versionCode < installedVersionCode) DevRefusal.OLDER_VERSION else null

    /** On the downloaded file. An empty signer set never matches: an unsigned archive is refused. */
    fun archive(
        build: DevBuildRow, size: Long, sha256: String,
        archivePackage: String?, archiveSigners: Set<String>, archiveVersionCode: Long,
        installedPackage: String, installedSigners: Set<String>, installedVersionCode: Long,
    ): DevRefusal? = when {
        size != build.size -> DevRefusal.WRONG_SIZE
        !sha256.equals(build.sha256, ignoreCase = true) -> DevRefusal.WRONG_HASH
        archivePackage != installedPackage -> DevRefusal.WRONG_PACKAGE
        archiveSigners.isEmpty() || archiveSigners != installedSigners -> DevRefusal.WRONG_SIGNER
        archiveVersionCode < installedVersionCode -> DevRefusal.OLDER_VERSION
        else -> null
    }
}

/** Where the dev code may go. Exact host and port of the worker, and only for paths under /dev/. */
internal object DevRouting {
    fun isDevRoute(url: String, base: String): Boolean = runCatching {
        val u = URI(url).normalize(); val b = URI(base)
        u.userInfo == null && u.scheme.equals(b.scheme, true) && u.host.equals(b.host, true) &&
            port(u) == port(b) && u.path.startsWith("/dev/")
    }.getOrDefault(false)

    /**
     * The one plain-http source address the Market may use: the dev tweaks source on the loopback stub. Exactly host
     * 127.0.0.1 (not localhost, not 127.0.0.2, not a private address), exactly port 8787, no user info, and only the
     * tweaks folder. Signatures are still required and checked with the pinned key.
     */
    fun isLoopbackSource(url: String): Boolean = runCatching {
        val u = URI(url).normalize()
        u.scheme == "http" && u.host == "127.0.0.1" && u.port == 8787 && u.userInfo == null && u.path.startsWith("/dev/file/tweaks/")
    }.getOrDefault(false)

    /**
     * Opens connections, but refuses a plain-http address on the stub's host and port while the stub is switched off, so
     * an https source cannot redirect Folio down to the loopback address behind its back.
     */
    fun stubGuard(stubOn: () -> Boolean, delegate: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }):
        (URL) -> HttpURLConnection = { url ->
        if (url.protocol.equals("http", true) && url.host == "127.0.0.1" && url.port == 8787 && !stubOn()) throw java.io.IOException()
        delegate(url)
    }

    private fun port(u: URI) = if (u.port >= 0) u.port else if (u.scheme.equals("https", true)) 443 else 80

    /**
     * Wraps the connection opener the Market's HTTP client already takes. The header is set per connection, and the
     * client opens a new one for every redirect hop, so a redirect to anywhere else gets a connection without it.
     */
    fun opener(base: () -> String, code: () -> String?, delegate: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }):
        (URL) -> HttpURLConnection = { url ->
        val connection = delegate(url)
        if (isDevRoute(url.toString(), base())) code()?.let { connection.setRequestProperty("Authorization", "Bearer $it") } 
        connection
    }
}

internal object DevSource {
    /** Stands in for an address, so the Sources tab can open the Folio Dev page like any other source. */
    const val PAGE_URL = "folio://dev/"
    const val LOOPBACK_BASE = "http://127.0.0.1:8787"
    private const val PREFS = "dev_source"
    private const val CODE = "code"
    private const val LOOPBACK = "loopback"
    private const val FLOOR = "list_floor"
    private const val LIST_MAX_BYTES = 256 * 1024

    /** Second lock: the real code is only compiled into the Folio Dev build types, and only runs under a `.dev` id. */
    fun enabled(context: Context): Boolean = context.packageName.endsWith(".dev")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Debug-only: aim the dev set at the loopback stub (tools/dev-source-stub.mjs) over `adb reverse`. */
    fun loopback(context: Context) = enabled(context) && prefs(context).getBoolean(LOOPBACK, false)
    fun setLoopback(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(LOOPBACK, on).remove(FLOOR).apply()
        list.value = ListState.Idle
    }

    fun base(context: Context): String = if (loopback(context)) LOOPBACK_BASE else SoftwareUpdate.BETA_BROKER.trimEnd('/')

    /** The stored dev code, only when it still verifies against the dev code key and carries the dev scope. */
    fun code(context: Context): String? {
        if (!enabled(context)) return null
        val text = prefs(context).getString(CODE, null) ?: return null
        return text.takeIf { valid(it) }
    }

    private fun valid(text: String): Boolean =
        (BetaCodes.verify(text, DevSourceKeys.codeKeys, withdrawn = BetaKeys.WITHDRAWN) as? BetaCodes.Result.Valid)
            ?.code?.scopes?.contains(BetaCodes.SCOPE_DEV) == true

    /** Keeps [text] when it is a good dev code. Anything else, including a supporter code, is not stored. */
    fun redeem(context: Context, text: String): Boolean {
        if (!enabled(context) || !valid(text)) return false
        prefs(context).edit().putString(CODE, text.trim()).apply()
        list.value = ListState.Idle
        return true
    }

    fun forgetCode(context: Context) {
        prefs(context).edit().remove(CODE).apply()
        list.value = ListState.Idle
    }

    /** The Market's HTTP client with the dev code added for the worker's /dev/ addresses and nowhere else. */
    fun httpClient(context: Context, allowLocalhost: Boolean): UrlHttpClient {
        val app = context.applicationContext
        if (!enabled(app)) return UrlHttpClient(allowLocalhost = allowLocalhost)
        return UrlHttpClient(allowLocalhost = allowLocalhost,
            open = DevRouting.opener({ base(app) }, { code(app) }, DevRouting.stubGuard({ loopback(app) })))
    }

    /** Handed to the Market's source list and client: plain http only for the stub's tweaks address, in a `.dev` build with the stub switched on. */
    fun insecureSourceOk(context: Context): (String) -> Boolean {
        val app = context.applicationContext
        return { url -> enabled(app) && loopback(app) && DevRouting.isLoopbackSource(url) }
    }

    /**
     * The dev source's addresses, on the real worker and on the stub, for background runs to leave alone. A background
     * refresh or auto-update would otherwise send the dev code from a job nobody opened.
     */
    fun backgroundSkip(@Suppress("UNUSED_PARAMETER") context: Context): (String) -> Boolean = { url -> isDevAddress(url) }

    internal fun isDevAddress(url: String) =
        DevRouting.isDevRoute(url, SoftwareUpdate.BETA_BROKER.trimEnd('/')) || DevRouting.isDevRoute(url, LOOPBACK_BASE)

    fun tweaksUrl(context: Context) = base(context) + "/dev/file/tweaks/"

    // ---- the list ---------------------------------------------------------------------------------------------

    enum class Failure { NO_CODE, NETWORK, WORKER_REFUSED, BAD_SIGNATURE, UNREADABLE, STALE, ROLLBACK }

    sealed interface ListState {
        data object Idle : ListState
        data object Loading : ListState
        data class Loaded(val builds: List<DevBuildRow>, val checkedAt: Long) : ListState
        data class Failed(val failure: Failure) : ListState
    }

    sealed interface Work {
        data object Idle : Work
        data class Downloading(val build: DevBuildRow, val fraction: Float?) : Work
        data object Installing : Work
        data class Refused(val reason: DevRefusal) : Work
        data object Failed : Work
    }

    val list = MutableStateFlow<ListState>(ListState.Idle)
    val work = MutableStateFlow<Work>(Work.Idle)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val busy = AtomicBoolean(false)

    private fun launchExclusive(block: suspend () -> Unit) {
        if (!busy.compareAndSet(false, true)) return
        scope.launch { try { block() } finally { busy.set(false) } }
    }

    /** Only when the page is opened, Check is tapped, or the Market is refreshed: never on Home start, never in the background. */
    fun refresh(context: Context) = launchExclusive {
        val app = context.applicationContext
        list.value = ListState.Loading
        list.value = load(app)
    }

    internal fun load(context: Context, nowSeconds: Long = System.currentTimeMillis() / 1000): ListState {
        if (code(context) == null) return ListState.Failed(Failure.NO_CODE)
        val client = httpClient(context, allowLocalhost = loopback(context))
        val root = base(context) + "/dev/file/"
        val body = client.get(root + "builds.json", LIST_MAX_BYTES, null)
        val sig = client.get(root + "builds.json.sig", 1024, null)
        for (result in listOf(body, sig)) if (result is com.mccal.folio.market.HttpResult.Failed) {
            return ListState.Failed(if (result.status == 400 || result.status == 403) Failure.WORKER_REFUSED else Failure.NETWORK)
        }
        val bytes = (body as? com.mccal.folio.market.HttpResult.Body)?.bytes ?: return ListState.Failed(Failure.NETWORK)
        val signature = (sig as? com.mccal.folio.market.HttpResult.Body)?.bytes?.decodeToString() ?: return ListState.Failed(Failure.NETWORK)
        val floor = prefs(context).getLong(FLOOR, 0L)
        return when (val verdict = DevBuilds.verify(bytes, signature, DevSourceKeys.publisherKeys, nowSeconds, floor)) {
            is DevBuilds.Verdict.Good -> {
                prefs(context).edit().putLong(FLOOR, maxOf(floor, verdict.timestamp)).apply()
                ListState.Loaded(verdict.builds, nowSeconds * 1000)
            }
            is DevBuilds.Verdict.Bad -> ListState.Failed(when (verdict.problem) {
                DevBuilds.Problem.BAD_SIGNATURE -> Failure.BAD_SIGNATURE
                DevBuilds.Problem.UNREADABLE -> Failure.UNREADABLE
                DevBuilds.Problem.STALE -> Failure.STALE
                DevBuilds.Problem.ROLLBACK -> Failure.ROLLBACK
            })
        }
    }

    // ---- installing -------------------------------------------------------------------------------------------

    /** The build this Folio Dev was made from, to mark it in the list. */
    fun running(context: Context): DevBuildInfo? = DevBuild.load(context)

    private fun installedCode(context: Context): Long =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode }.getOrDefault(0L)

    fun install(context: Context, build: DevBuildRow) = launchExclusive {
        val app = context.applicationContext
        work.value = runCatching { downloadAndInstall(app, build) }.getOrElse { Work.Failed }
    }

    private fun downloadAndInstall(context: Context, build: DevBuildRow): Work {
        val installed = installedCode(context)
        DevInstallCheck.beforeDownload(build, installed)?.let { return Work.Refused(it) }
        val code = code(context) ?: return Work.Failed
        val base = base(context)
        val dir = File(context.filesDir, "updates-dev").apply { deleteRecursively(); mkdirs() }
        val part = File(dir, "${build.name}.apk.part")
        work.value = Work.Downloading(build, null)
        try {
            // The first hop is the worker's own /dev/apk/ address and carries the code; the redirect to GitHub is
            // followed by hand without it (SoftwareUpdate.open), and only to https (or the loopback stub's own address).
            SoftwareUpdate.download("$base/dev/apk/${build.tag}", part, build.size, code,
                redirectOk = { it.startsWith("https://") || (loopback(context) && it.startsWith("$LOOPBACK_BASE/")) },
                maxBytes = build.size) { work.value = Work.Downloading(build, it) }
            val refusal = checkArchive(context, build, part)
            if (refusal != null) { dir.deleteRecursively(); return Work.Refused(refusal) }
            val apk = File(dir, "${build.name}.apk")
            check(part.renameTo(apk))
            work.value = Work.Installing
            SoftwareUpdate.install(context, apk)
            return Work.Installing
        } catch (error: Exception) {
            dir.deleteRecursively()
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun checkArchive(context: Context, build: DevBuildRow, file: File): DevRefusal? {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        fun signers(info: android.content.pm.PackageInfo?) =
            info?.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        return DevInstallCheck.archive(build, file.length(), SoftwareUpdate.sha256(file),
            archive?.packageName, signers(archive), archive?.longVersionCode ?: 0L,
            context.packageName, signers(installed), installed.longVersionCode)
    }
}
