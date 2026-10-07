package com.mccal.folio

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager

/**
 * What Settings › Advanced › System Bridge shows (ADR 0009). The broker is wired to the three tiers Folio really uses
 * today (A0 to A2); Shizuku, root and system integration have no provider yet, so they read "Not available yet" and
 * nothing is detected or probed. The switch is stored apart from the launcher state, so a backup or a layout restore
 * never turns system access back on.
 */
internal object SystemBridge {
    private const val PREFS = "system_bridge"
    private const val OFF = "off"
    private const val RECENT = 20

    private val recent = ArrayDeque<AuditEvent>()

    fun isOff(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(OFF, false)
    fun setOff(context: Context, off: Boolean) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(OFF, off).apply()

    /** Calls that ran or fell back since Folio started. Held in memory only: no content, cleared when Folio closes. */
    @Synchronized fun recentCalls(): List<AuditEvent> = recent.toList().asReversed()
    @Synchronized private fun record(e: AuditEvent) { recent.addLast(e); while (recent.size > RECENT) recent.removeFirst() }

    fun broker(context: Context): CapabilityBroker {
        val app = context.applicationContext
        return CapabilityBroker(
            providers = { listOf(HingeSensorProvider(app), NotificationAccessProvider(app), AccessibilityProvider(), RootHingeProvider { RootHingeStore.state(app) }, SecureSettingsGrantProvider { SecureSettingsGrant.isGranted(app) }) },
            integrationOff = { isOff(app) },
            safeMode = { SafeMode.active },
            audit = ::record,
        )
    }

    /** The words for a capability's state; [off] and [safe] only matter for ways above A2 that have no provider. */
    @androidx.annotation.StringRes fun stateLabel(s: CapabilityState): Int = when {
        s.available -> R.string.bridge_state_available
        s.reason == UnavailableReason.NOT_GRANTED -> R.string.bridge_state_needs_permission
        s.reason == UnavailableReason.LOST -> R.string.bridge_state_lost
        s.reason == UnavailableReason.UNSUPPORTED -> R.string.bridge_state_unsupported
        s.reason == UnavailableReason.DISABLED_BY_USER -> R.string.bridge_state_off
        s.reason == UnavailableReason.SAFE_MODE -> R.string.bridge_state_safe
        else -> R.string.bridge_state_not_yet
    }

    /** What a way above Standard reads: no provider exists, so the only things to say are off, Safe Mode or not yet. */
    /** Rows that read "Not available yet" are folded into one row, so the page lists only what is real or can be set up. Order is kept. */
    fun <T> splitNotYet(items: List<T>, label: (T) -> Int): Pair<List<T>, List<T>> = items.partition { label(it) != R.string.bridge_state_not_yet }

    /** The line an advanced card shows first while system access is paused, or null when it is not. */
    @androidx.annotation.StringRes fun pausedNote(off: Boolean, safe: Boolean): Int? = when {
        safe -> R.string.bridge_paused_safe
        off -> R.string.bridge_paused_off
        else -> null
    }

    @androidx.annotation.StringRes fun wayLabel(off: Boolean, safe: Boolean): Int = when {
        safe -> R.string.bridge_state_safe
        off -> R.string.bridge_state_off
        else -> R.string.bridge_state_not_yet
    }

    /** What the Root row reads: the owner's last root test, unless the switch or Safe Mode has turned the tier off. */
    @androidx.annotation.StringRes fun rootWayLabel(state: RootState, off: Boolean, safe: Boolean): Int = when {
        safe -> R.string.bridge_state_safe
        off -> R.string.bridge_state_off
        else -> when (state) {
            RootState.READY -> R.string.bridge_state_available
            RootState.NO_ROOT -> R.string.bridge_state_no_root
            RootState.NO_SENSOR -> R.string.bridge_state_unsupported
            RootState.DENIED -> R.string.bridge_state_not_allowed
            RootState.LOST -> R.string.bridge_state_lost
            RootState.UNKNOWN -> R.string.bridge_state_not_tested
        }
    }

    /** The sentence for what a root test found. Moving shows four numbers; every other result is a plain sentence. */
    fun rootResult(context: Context, r: RootTestReport): String = when (r.outcome) {
        RootTestReport.Outcome.MOVING -> context.getString(R.string.bridge_root_result_moving, r.readings, r.distinctAngles, r.minDegrees.toInt(), r.maxDegrees.toInt())
        RootTestReport.Outcome.STILL -> context.getString(R.string.bridge_root_result_still)
        RootTestReport.Outcome.NO_ROOT -> context.getString(R.string.bridge_root_result_no_root)
        RootTestReport.Outcome.DENIED -> context.getString(R.string.bridge_root_result_denied)
        RootTestReport.Outcome.NO_ANSWER -> context.getString(R.string.bridge_root_result_no_answer)
        RootTestReport.Outcome.NO_SENSOR -> context.getString(R.string.bridge_root_result_no_sensor)
        RootTestReport.Outcome.FAILED -> context.getString(R.string.bridge_root_result_failed)
    }

    @androidx.annotation.StringRes fun capabilityName(c: FolioCapability): Int = when (c) {
        FolioCapability.SHADE_OPEN -> R.string.bridge_cap_shade
        FolioCapability.NOTIFICATIONS_READ -> R.string.bridge_cap_notifications
        FolioCapability.HINGE_ANGLE -> R.string.bridge_cap_hinge
        FolioCapability.HINGE_ANGLE_CONTINUOUS -> R.string.bridge_cap_hinge_continuous
        FolioCapability.SETTINGS_SECURE_WRITE -> R.string.bridge_cap_secure_settings
        FolioCapability.SYSTEM_ACTIONS -> R.string.bridge_cap_actions
        FolioCapability.SYSTEM_STATUS_MODULES -> R.string.bridge_cap_status
        FolioCapability.SYSTEM_QUICK_SETTINGS_LAYOUT -> R.string.bridge_cap_quick_settings
    }

    @androidx.annotation.StringRes fun outcomeLabel(o: AuditEvent.Outcome): Int = when (o) {
        AuditEvent.Outcome.RAN -> R.string.bridge_outcome_ran
        AuditEvent.Outcome.FELL_BACK -> R.string.bridge_outcome_fell_back
        AuditEvent.Outcome.FAILED_THEN_FELL_BACK -> R.string.bridge_outcome_failed
    }
}

/** A0: the public hinge sensor. It says the sensor exists; what it reports (the Fold8 gives 0, 90 and 180) is [HingeCapability]'s job. */
internal class HingeSensorProvider(private val context: Context) : CapabilityProvider {
    override val tier = PrivilegeTier.STANDARD
    override val capabilities = setOf(FolioCapability.HINGE_ANGLE)
    override fun status() =
        if ((context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null) BackendStatus.AVAILABLE
        else BackendStatus.UNSUPPORTED
}

/** A1: notification access, which the person grants in Android's settings. */
internal class NotificationAccessProvider(private val context: Context) : CapabilityProvider {
    override val tier = PrivilegeTier.NOTIFICATIONS
    override val capabilities = setOf(FolioCapability.NOTIFICATIONS_READ)
    override fun status() = BackendStatus.of(IslandListenerService.hasAccess(context))
}

/** A2: Folio's accessibility service, which opens the shade over other apps. */
internal class AccessibilityProvider : CapabilityProvider {
    override val tier = PrivilegeTier.ACCESSIBILITY
    override val capabilities = setOf(FolioCapability.SHADE_OPEN)
    override fun status() = BackendStatus.of(SystemShadeAccessibilityService.isConnected())
}
