package com.mccal.folio

/**
 * The Capability Broker (ADR 0008, ADR 0009): the one place that knows which way, if any, Folio can do something on
 * this phone. Features ask "is this capability available" and never "is Shizuku running". Pure Kotlin with no Android
 * calls, so every rule below is a unit test. Nothing here asks Android for anything; providers do, behind this seam.
 *
 * Phase A: the broker and its vocabulary exist, and no provider above A2 does. Home never waits on any of it.
 */

/** What a feature can ask for. A capability is a thing Folio can do, not a technology; several providers may offer one. */
internal enum class FolioCapability(val id: String, val minTier: PrivilegeTier, val risk: OperationRisk) {
    SHADE_OPEN("system.shade.open", PrivilegeTier.ACCESSIBILITY, OperationRisk.REVERSIBLE),
    NOTIFICATIONS_READ("notification.metadata.read", PrivilegeTier.NOTIFICATIONS, OperationRisk.OBSERVE),
    HINGE_ANGLE("device.hinge.angle", PrivilegeTier.STANDARD, OperationRisk.OBSERVE),
    SYSTEM_ACTIONS("system.actions", PrivilegeTier.SHIZUKU, OperationRisk.REVERSIBLE),
    SYSTEM_STATUS_MODULES("system.status.modules", PrivilegeTier.HOOKS, OperationRisk.EXPERIMENTAL),
    SYSTEM_QUICK_SETTINGS_LAYOUT("system.quicksettings.layout", PrivilegeTier.HOOKS, OperationRisk.EXPERIMENTAL);

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id }
    }
}

/** Why a capability is not available now, in the order the Bridge page reports it. Always a state, never an error. */
internal enum class UnavailableReason {
    /** Not unavailable. */
    NONE,
    /** This build has no provider for it at all. */
    NO_PROVIDER,
    /** A provider exists and the person has not granted what it needs. */
    NOT_GRANTED,
    /** It worked and then stopped: Shizuku stopped after a reboot, access revoked, the bridge is gone. */
    LOST,
    /** This Android version or device cannot do it. */
    UNSUPPORTED,
    /** The person turned privileged integration off. */
    DISABLED_BY_USER,
    /** Safe Mode is on, so integration above standard access stays off. */
    SAFE_MODE,
}

/** One way of reaching capabilities. A provider is the only code that touches Shizuku, `su`, a bridge or a vendor API. */
internal interface CapabilityProvider {
    val tier: PrivilegeTier
    val capabilities: Set<FolioCapability>
    /** Current access. May throw; the broker treats a throw as [BackendStatus.LOST]. */
    fun status(): BackendStatus
}

/** What a feature sees for one capability. */
internal data class CapabilityState(
    val capability: FolioCapability,
    val available: Boolean,
    /** The tier of the provider that would run it, or null when none can. */
    val via: PrivilegeTier?,
    val reason: UnavailableReason,
) {
    /** The person can do something about it: grant access, start Shizuku, install the bridge. */
    val setupRequired get() = reason == UnavailableReason.NOT_GRANTED || reason == UnavailableReason.LOST
}

/** A local record of a privileged call (the Inspector's store). No content, no network. */
internal data class AuditEvent(
    val capability: FolioCapability,
    val via: PrivilegeTier?,
    val outcome: Outcome,
) {
    enum class Outcome { RAN, FELL_BACK, FAILED_THEN_FELL_BACK }
}

internal class CapabilityBroker(
    private val providers: () -> List<CapabilityProvider>,
    /** The kill switch: Settings > System Bridge > Disable all privileged integration. */
    private val integrationOff: () -> Boolean = { false },
    private val safeMode: () -> Boolean = { false },
    private val audit: (AuditEvent) -> Unit = {},
) {
    /**
     * The state of [capability]: the lowest tier that can do it wins (least privilege), tiers above A2 are skipped when
     * the kill switch or Safe Mode says so, and a provider that throws counts as lost.
     */
    fun state(capability: FolioCapability): CapabilityState {
        val offered = providers().filter { capability in it.capabilities }.sortedBy { it.tier.ordinal }
        if (offered.isEmpty()) return CapabilityState(capability, false, null, UnavailableReason.NO_PROVIDER)
        var best: UnavailableReason? = null
        for (p in offered) {
            if (p.tier > PrivilegeTier.ACCESSIBILITY) {
                when {
                    safeMode() -> { best = pick(best, UnavailableReason.SAFE_MODE); continue }
                    integrationOff() -> { best = pick(best, UnavailableReason.DISABLED_BY_USER); continue }
                }
            }
            val status = runCatching { p.status() }.getOrDefault(BackendStatus.LOST)
            if (status == BackendStatus.AVAILABLE) return CapabilityState(capability, true, p.tier, UnavailableReason.NONE)
            best = pick(best, when (status) {
                BackendStatus.NOT_GRANTED -> UnavailableReason.NOT_GRANTED
                BackendStatus.LOST -> UnavailableReason.LOST
                else -> UnavailableReason.UNSUPPORTED
            })
        }
        return CapabilityState(capability, false, null, best ?: UnavailableReason.NO_PROVIDER)
    }

    /** Every capability, for the Bridge page. */
    fun states() = FolioCapability.entries.map(::state)

    /**
     * Runs [op] on the provider [state] chose, or [fallback] when none is available or [op] fails. A privileged call
     * that breaks never reaches the caller; Home always has the standard behavior to fall back to.
     */
    fun <T> perform(capability: FolioCapability, fallback: () -> T, op: (PrivilegeTier) -> T): T {
        val s = state(capability)
        val tier = s.via
        if (!s.available || tier == null) {
            audit(AuditEvent(capability, null, AuditEvent.Outcome.FELL_BACK))
            return fallback()
        }
        return try {
            op(tier).also { audit(AuditEvent(capability, tier, AuditEvent.Outcome.RAN)) }
        } catch (e: Exception) {
            audit(AuditEvent(capability, tier, AuditEvent.Outcome.FAILED_THEN_FELL_BACK))
            fallback()
        }
    }

    /** The first reason wins unless a later one is more useful to the person (something they can fix beats "not supported"). */
    private fun pick(current: UnavailableReason?, next: UnavailableReason): UnavailableReason {
        val order = listOf(
            UnavailableReason.SAFE_MODE, UnavailableReason.DISABLED_BY_USER, UnavailableReason.NOT_GRANTED,
            UnavailableReason.LOST, UnavailableReason.UNSUPPORTED,
        )
        return if (current == null || order.indexOf(next) < order.indexOf(current)) next else current
    }
}

/**
 * What a bridge says about itself when Folio connects (ADR 0009). Folio reads it, decides what it may use, and carries
 * on if the answer is "nothing". Newer or older bridges are states, never crashes.
 */
internal data class BridgeHello(
    val protocolVersion: Int,
    /** The oldest Folio protocol this bridge still speaks. */
    val minFolioProtocol: Int,
    val bridgeVersion: String,
    val capabilityIds: Set<String>,
    val androidApi: Int,
    val systemUiAdapter: String? = null,
    val oemAdapter: String? = null,
)

internal sealed interface HandshakeResult {
    /** [usable] is the capabilities both sides know; ids this Folio has never heard of are ignored. */
    data class Compatible(val usable: Set<FolioCapability>) : HandshakeResult
    data object BridgeTooOld : HandshakeResult
    /** The bridge needs a newer Folio than this one. */
    data object FolioTooOld : HandshakeResult
    data object NothingUsable : HandshakeResult
}

internal object BridgeProtocol {
    /** The protocol this Folio speaks, and the oldest bridge protocol it accepts. */
    const val CURRENT = 1
    const val OLDEST_ACCEPTED = 1

    fun negotiate(hello: BridgeHello): HandshakeResult = when {
        hello.protocolVersion < OLDEST_ACCEPTED -> HandshakeResult.BridgeTooOld
        hello.minFolioProtocol > CURRENT -> HandshakeResult.FolioTooOld
        else -> hello.capabilityIds.mapNotNull(FolioCapability::fromId).toSet().let {
            if (it.isEmpty()) HandshakeResult.NothingUsable else HandshakeResult.Compatible(it)
        }
    }
}
