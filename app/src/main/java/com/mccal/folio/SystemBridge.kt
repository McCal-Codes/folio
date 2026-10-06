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
            providers = { listOf(HingeSensorProvider(app), NotificationAccessProvider(app), AccessibilityProvider()) },
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
    @androidx.annotation.StringRes fun wayLabel(off: Boolean, safe: Boolean): Int = when {
        safe -> R.string.bridge_state_safe
        off -> R.string.bridge_state_off
        else -> R.string.bridge_state_not_yet
    }

    @androidx.annotation.StringRes fun capabilityName(c: FolioCapability): Int = when (c) {
        FolioCapability.SHADE_OPEN -> R.string.bridge_cap_shade
        FolioCapability.NOTIFICATIONS_READ -> R.string.bridge_cap_notifications
        FolioCapability.HINGE_ANGLE -> R.string.bridge_cap_hinge
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
