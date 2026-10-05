package com.mccal.folio

/**
 * What Folio can reach on this phone, by the tiers in `docs/capability-matrix.md` (ADR 0008): A0 needs nothing, A1 is
 * notification access, A2 is Folio's accessibility service. Higher tiers (Shizuku, root) are not used by Folio yet, so
 * they are not listed: a row here only ever says what is true now.
 */
internal enum class CapabilityTier(val code: String, val privilege: PrivilegeTier, val risk: OperationRisk) {
    /** Home, folders, Focus and the Market: every change has an Undo. */
    STANDARD("A0", PrivilegeTier.STANDARD, OperationRisk.REVERSIBLE),
    /** Reads notifications; replying and dismissing one are changes that stay. */
    NOTIFICATIONS("A1", PrivilegeTier.NOTIFICATIONS, OperationRisk.STATEFUL),
    /** Global gestures and the shade over other apps: they end when you let go. */
    ACCESSIBILITY("A2", PrivilegeTier.ACCESSIBILITY, OperationRisk.REVERSIBLE),
}

internal data class CapabilityRow(val tier: CapabilityTier, val on: Boolean) {
    val status get() = BackendStatus.of(granted = on)
    /** The most that Folio does with this tier. */
    val risk get() = tier.risk
}

internal object Capabilities {
    /** The rows for this phone: Standard is always on; the other two follow what the person has allowed. */
    fun rows(notificationAccess: Boolean, accessibilityConnected: Boolean): List<CapabilityRow> = listOf(
        CapabilityRow(CapabilityTier.STANDARD, true),
        CapabilityRow(CapabilityTier.NOTIFICATIONS, notificationAccess),
        CapabilityRow(CapabilityTier.ACCESSIBILITY, accessibilityConnected),
    )

    /** Which tiers still need the person's permission, in the order to offer them. */
    fun needsPermission(rows: List<CapabilityRow>) = rows.filter { !it.on }.map { it.tier }
}

/** One line of Folio's own activity trail, read back for the Inspector: when, what, and whether it was a failure Folio carried on from. */
internal data class InspectorEntry(val time: String, val text: String, val failed: Boolean)

internal object Inspector {
    private val line = Regex("""^(\d{2}:\d{2}:\d{2}\.\d{3})\s{2}(.*)$""")

    /** The trail (see [Diagnostics.trailText]) newest first. A line that doesn't look like a trail line is kept as it is, with no time. */
    fun entries(trail: String): List<InspectorEntry> = trail.lineSequence().filter { it.isNotBlank() }.map { raw ->
        line.matchEntire(raw)?.let { m -> InspectorEntry(m.groupValues[1], m.groupValues[2], isFailure(m.groupValues[2])) }
            ?: InspectorEntry("", raw.trim(), isFailure(raw))
    }.toList().asReversed()

    /** [Diagnostics.caught] writes "<where> failed: <Type>"; that is what marks a failure. */
    fun isFailure(text: String) = " failed: " in text || text.startsWith("failed")

    fun filter(entries: List<InspectorEntry>, failedOnly: Boolean) = if (failedOnly) entries.filter { it.failed } else entries
}
