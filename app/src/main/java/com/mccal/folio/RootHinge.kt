package com.mccal.folio

/**
 * A sketch of the root hinge provider (ADR 0012): the parts that need no root and no Android, so they can be tested.
 * Nothing here runs `su`, starts a process or touches the phone. The helper process, the Settings switch and the wiring into
 * [FoldTimeline] come later, and only if McCal wants them.
 */

/** What Folio knows about root, from the one test the owner starts. Never found out by asking for root in the background. */
internal enum class RootState {
    /** The owner has not run the root test yet. */
    UNKNOWN,
    /** The test ran and there is no `su` at all. */
    NO_ROOT,
    /** `su` exists but this app has not been enabled in the root manager (KernelSU, Magisk, APatch). */
    DENIED,
    /** The test ran and root works, but this phone has no Samsung hinge angle sensor, or the sensor refused even root. */
    NO_SENSOR,
    /** The test ran and the helper's readings arrived. */
    READY,
    /** It worked before and then did not: the helper died, or the grant was taken away. */
    LOST,
}

/** Offers [FolioCapability.HINGE_ANGLE_CONTINUOUS] when root is ready; every other state says why not, in the broker's words. */
internal class RootHingeProvider(private val rootState: () -> RootState) : CapabilityProvider {
    override val tier = PrivilegeTier.ROOT
    override val capabilities = setOf(FolioCapability.HINGE_ANGLE_CONTINUOUS)
    override fun status() = when (rootState()) {
        RootState.READY -> BackendStatus.AVAILABLE
        RootState.LOST -> BackendStatus.LOST
        RootState.NO_ROOT, RootState.NO_SENSOR -> BackendStatus.UNSUPPORTED
        // Not asked yet, or not enabled: the person can fix both, so it reads as "needs your permission".
        RootState.UNKNOWN, RootState.DENIED -> BackendStatus.NOT_GRANTED
    }
}

/** One reading from the root helper. */
internal data class HingeSample(val angleDegrees: Float, val timestampNanos: Long)

/**
 * What the root helper prints, one line per message, so the app reads a pipe it owns and no socket is open to anyone else:
 *
 *     R                     ready: the sensor registered
 *     H <nanos> <degrees>   a reading
 *     E <word>              why it stopped (denied, no-sensor, died)
 */
internal object RootHingeProtocol {
    const val MAX_DEGREES = 180f
    /** Readings arrive at the sensor's own pace; more than this many a second are dropped, so a bad helper cannot flood the app. */
    const val MAX_PER_SECOND = 100

    sealed interface Message {
        data object Ready : Message
        data class Reading(val sample: HingeSample) : Message
        data class Stopped(val reason: String) : Message
    }

    /** The message on [line], or null for anything else (blank, unknown, damaged, or an angle that cannot be real). */
    fun parse(line: String): Message? {
        val parts = line.trim().split(' ')
        return when (parts.firstOrNull()) {
            "R" -> if (parts.size == 1) Message.Ready else null
            "H" -> {
                if (parts.size != 3) return null
                val nanos = parts[1].toLongOrNull()?.takeIf { it >= 0 } ?: return null
                val degrees = parts[2].toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..MAX_DEGREES } ?: return null
                Message.Reading(HingeSample(degrees, nanos))
            }
            "E" -> parts.getOrNull(1)?.takeIf { parts.size == 2 && it.length in 1..24 && it.all { c -> c.isLetter() || c == '-' } }?.let(Message::Stopped)
            else -> null
        }
    }

    /** Keeps readings in time order and no faster than [MAX_PER_SECOND]. Feed it each reading; it says whether to pass it on. */
    class Gate {
        private var lastNanos = Long.MIN_VALUE
        fun accept(sample: HingeSample): Boolean {
            if (sample.timestampNanos <= lastNanos) return false
            if (lastNanos != Long.MIN_VALUE && sample.timestampNanos - lastNanos < 1_000_000_000L / MAX_PER_SECOND) return false
            lastNanos = sample.timestampNanos
            return true
        }
    }
}

/** Which source feeds the fold animation. The public sensor is always the way back. */
internal enum class HingeSource { PUBLIC_SENSOR, ROOT_HELPER }

internal fun hingeSource(broker: CapabilityBroker): HingeSource =
    if (broker.state(FolioCapability.HINGE_ANGLE_CONTINUOUS).available) HingeSource.ROOT_HELPER else HingeSource.PUBLIC_SENSOR

/**
 * The key the learned hinge capability is remembered under, per source. A root feed that was seen to be continuous must not
 * be remembered as the public sensor's, or the fold animation would expect smooth readings after the root helper is gone.
 */
internal fun hingeCapabilityKey(source: HingeSource) = when (source) {
    HingeSource.PUBLIC_SENSOR -> "fold_hinge_capability"
    HingeSource.ROOT_HELPER -> "fold_hinge_capability_root"
}

/**
 * The one-time permission that lets Folio change Android's protected settings. The owner grants it from a computer with adb
 * (`pm grant`); Folio never grants it to itself, and nothing in Folio uses it yet. It sits at the shell tier, so the system-access switch and
 * Safe Mode turn it off like every other provider above accessibility.
 */
internal object SecureSettingsGrant {
    const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"

    /** Only a plain package name is put in a command the owner will run; anything else gets no command. */
    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    fun grantCommand(packageName: String): String? = packageName.takeIf { PACKAGE.matches(it) }?.let { "adb shell pm grant $it $PERMISSION" }
    fun revokeCommand(packageName: String): String? = packageName.takeIf { PACKAGE.matches(it) }?.let { "adb shell pm revoke $it $PERMISSION" }

    fun isGranted(context: android.content.Context): Boolean =
        context.checkSelfPermission(PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

internal class SecureSettingsGrantProvider(private val granted: () -> Boolean) : CapabilityProvider {
    override val tier = PrivilegeTier.SHIZUKU
    override val capabilities = setOf(FolioCapability.SETTINGS_SECURE_WRITE)
    override fun status() = BackendStatus.of(granted())
}
