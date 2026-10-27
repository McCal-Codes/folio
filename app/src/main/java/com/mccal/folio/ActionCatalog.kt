package com.mccal.folio

import androidx.annotation.StringRes

/**
 * How the Icon Actions picker lays out the registry, as in the Lab scene `icon-actions`: a short Suggested list, then a
 * few categories that drill in, plus search, never one long scroll. Every registered action sits in exactly one
 * category (a test holds that), so a new action can't be left out of the picker by accident.
 */
internal enum class ActionCategory(@StringRes val title: Int, val ids: List<String>) {
    FOLIO(R.string.action_category_folio, listOf("SPOTLIGHT", "NOTIFICATIONS", "CONTROL_CENTER", "FOCUS_SLEEP", "FOCUS_WORK", "FOCUS_PERSONAL", "FOCUS_OFF")),
    APPS(R.string.action_category_apps, listOf("app.open", "shortcut.open")),
    PHONE(R.string.action_category_phone, listOf("TORCH", "DND_ON", "DND_OFF", "volume.up", "volume.down", "volume.mute",
        "media.playpause", "media.next", "media.prev")),
    /** Android lets no app switch Wi-Fi, Bluetooth, mobile data or airplane mode, so these open Android's own panel. */
    PANELS(R.string.action_category_panels, listOf("panel.wifi", "panel.bluetooth")),
    ACCESSIBILITY(R.string.action_category_accessibility, listOf("a11y.back", "a11y.home", "a11y.recents", "a11y.quicksettings",
        "a11y.allapps", "a11y.shade", "a11y.dismissshade", "a11y.splitscreen", "LOCK", "a11y.power", "SCREENSHOT", "a11y.mediaplaypause")),
}

/** One row of the picker: the action, the name it shows, and why it can't be picked yet, if it can't. */
internal data class ActionRow(
    val spec: ActionSpec,
    @StringRes val label: Int,
    val verdict: ActionVerdict,
    /** Locks the phone, opens the power menu or takes a screenshot: said on the row, and a caution on Double tap. */
    val disruptive: Boolean,
    /** Picking it asks one more question first (which app, which shortcut). */
    val needsChoice: Boolean,
) {
    /** Dimmed with its reason, not hidden: only a too-old Android can't be fixed from the picker. */
    val dimmed: Boolean get() = verdict is ActionVerdict.NeedsAndroid
}

internal object ActionCatalog {
    /** Actions people reach for first, none needing a permission, so the first pick always works. */
    val suggested = listOf("TORCH", "CONTROL_CENTER", "shortcut.open", "media.playpause", "SPOTLIGHT")

    /** Names that say whose panel it is, since Folio's Notification Center and Android's shade are both offered. */
    private val pickerLabels = mapOf("NOTIFICATIONS" to R.string.action_folio_notification_center)

    fun row(spec: ActionSpec, verdict: ActionVerdict) = ActionRow(
        spec = spec,
        label = pickerLabels[spec.id] ?: spec.label,
        verdict = verdict,
        disruptive = spec.risk == OperationRisk.DISRUPTIVE,
        needsChoice = spec.id == "app.open" || spec.id == "shortcut.open",
    )

    fun rows(ids: List<String>, registry: ActionRegistry, verdict: (ActionRef) -> ActionVerdict): List<ActionRow> =
        ids.mapNotNull { id -> registry.spec(id)?.let { row(it, verdict(ActionRef(id))) } }

    fun category(category: ActionCategory, registry: ActionRegistry, verdict: (ActionRef) -> ActionVerdict) =
        rows(category.ids, registry, verdict)

    /** Every action whose shown name contains [query], in category order. [name] turns a string id into its text. */
    fun search(query: String, registry: ActionRegistry, verdict: (ActionRef) -> ActionVerdict, name: (Int) -> String): List<ActionRow> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return ActionCategory.entries.flatMap { category(it, registry, verdict) }.filter { name(it.label).contains(q, ignoreCase = true) }
    }
}
