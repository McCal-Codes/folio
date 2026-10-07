package com.mccal.folio

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.launch

/**
 * Giving Folio the settings permission with root (ADR 0010), when the owner allowed it in Settings. The command is `pm grant` or
 * `pm revoke`, built only from Folio's own package name (a plain one, or there is no command) and the one permission.
 */
internal object RootSettingsGrantRunner {
    enum class Outcome { DONE, DENIED, FAILED }

    /** What `su` runs, or null when the package name is not a plain one. */
    fun command(packageName: String, grant: Boolean): String? =
        (if (grant) SecureSettingsGrant.grantCommand(packageName) else SecureSettingsGrant.revokeCommand(packageName))?.removePrefix("adb shell ")

    fun run(launcher: SuLauncher, su: String, packageName: String, grant: Boolean, now: () -> Long, waitMs: Long = 15_000L): Outcome {
        val command = command(packageName, grant) ?: return Outcome.FAILED
        val process = try { launcher.start(listOf(su, "-c", command)) } catch (_: IOException) { return Outcome.FAILED }
        try {
            val deadline = now() + waitMs
            while (true) {
                val left = deadline - now()
                if (left <= 0) return Outcome.FAILED
                when (process.next(minOf(left, 1_000L))) {
                    SuLine.Timeout, is SuLine.Text -> Unit // `pm` prints nothing on success; anything it does print is dropped
                    SuLine.Eof -> return when {
                        process.exitCode() == 0 -> Outcome.DONE
                        RootHingeRunner.refusedRoot(process.errorText()) -> Outcome.DENIED
                        else -> Outcome.FAILED
                    }
                }
            }
        } finally {
            process.close()
        }
    }
}

/** Runs it where a screen change cannot cancel it; the page only watches [status]. */
internal object RootSettingsGrantTester {
    sealed interface Status {
        data object Idle : Status
        data object Running : Status
        data class Done(val outcome: RootSettingsGrantRunner.Outcome, val granted: Boolean) : Status
    }

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val _status = kotlinx.coroutines.flow.MutableStateFlow<Status>(Status.Idle)
    val status: kotlinx.coroutines.flow.StateFlow<Status> = _status

    /** Only with advanced options on, root tested and working, and the owner's approval switch on; anything else is a refusal. */
    fun allowed(context: Context) = RootHingeStore.advanced(context) && RootHingeStore.allowRootGrant(context) && RootHingeStore.state(context) == RootState.READY

    fun start(context: Context, grant: Boolean) {
        if (!_status.compareAndSet(_status.value.takeIf { it !is Status.Running } ?: return, Status.Running)) return
        val app = context.applicationContext
        scope.launch {
            val su = RootHingeStore.suPath(app)
            _status.value = Status.Done(
                if (!allowed(app) || su == null) RootSettingsGrantRunner.Outcome.DENIED
                else RootSettingsGrantRunner.run(ProcessSuLauncher, su, app.packageName, grant, now = { android.os.SystemClock.elapsedRealtime() }),
                grant)
        }
    }

    fun dismiss() { if (_status.value is Status.Done) _status.value = Status.Idle }
}
