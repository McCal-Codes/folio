package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionCatalogTest {
    private val registry = ActionRegistry.standard
    private fun env(accessibility: Boolean = false, sdk: Int = 36) =
        ActionEnv(accessibilityConnected = { accessibility }, policyAccess = { true }, sdk = sdk, safeMode = { false })
    private fun verdict(env: ActionEnv): (ActionRef) -> ActionVerdict = { registry.verdict(it, env, ActionSource.TRY) }

    @Test fun `every registered action is in exactly one category, so none is left out of the picker`() {
        val placed = ActionCategory.entries.flatMap { it.ids }
        assertEquals("an action listed twice", placed.size, placed.toSet().size)
        assertEquals(registry.all.map { it.id }.toSet(), placed.toSet())
    }

    @Test fun `suggested actions are all registered and need no permission, so the first pick always works`() {
        ActionCatalog.suggested.forEach { id ->
            val spec = registry.spec(id)!!
            assertEquals(id, ActionNeeds.NONE, spec.needs)
        }
    }

    @Test fun `accessibility actions say so until the service is on`() {
        val off = ActionCatalog.category(ActionCategory.ACCESSIBILITY, registry, verdict(env(accessibility = false)))
        assertTrue(off.all { it.verdict == ActionVerdict.NeedsAccessibility })
        val on = ActionCatalog.category(ActionCategory.ACCESSIBILITY, registry, verdict(env(accessibility = true)))
        assertTrue(on.filter { it.spec.minSdk <= 36 }.all { it.verdict.allowed })
    }

    @Test fun `an action for a newer Android is dimmed with its reason, not hidden`() {
        val rows = ActionCatalog.category(ActionCategory.ACCESSIBILITY, registry, verdict(env(accessibility = true, sdk = 34)))
        val system = rows.single { it.spec.id == "a11y.mediaplaypause" }
        assertTrue(system.dimmed)
        assertEquals(ActionVerdict.NeedsAndroid(36), system.verdict)
        assertFalse(rows.single { it.spec.id == "a11y.back" }.dimmed)
    }

    @Test fun `lock, power menu and screenshot are marked disruptive, and nothing else is`() {
        val disruptive = ActionCategory.entries.flatMap { ActionCatalog.category(it, registry, verdict(env())) }.filter { it.disruptive }.map { it.spec.id }
        assertEquals(setOf("LOCK", "a11y.power", "SCREENSHOT"), disruptive.toSet())
    }

    @Test fun `Folio's panel and Android's shade have names that tell them apart`() {
        val folio = ActionCatalog.category(ActionCategory.FOLIO, registry, verdict(env())).single { it.spec.id == "NOTIFICATIONS" }
        assertEquals(R.string.action_folio_notification_center, folio.label)
        val android = ActionCatalog.category(ActionCategory.ACCESSIBILITY, registry, verdict(env())).single { it.spec.id == "a11y.shade" }
        assertEquals(R.string.action_a11y_shade, android.label)
    }

    @Test fun `opening an app or a shortcut asks which one first`() {
        val apps = ActionCatalog.category(ActionCategory.APPS, registry, verdict(env()))
        assertTrue(apps.all { it.needsChoice })
        assertFalse(ActionCatalog.category(ActionCategory.PHONE, registry, verdict(env())).any { it.needsChoice })
    }

    @Test fun `search matches the shown name in category order, and an empty query shows nothing`() {
        val names = mapOf(R.string.action_volume_up to "Volume up", R.string.action_volume_down to "Volume down", R.string.action_volume_mute to "Mute or unmute")
        val found = ActionCatalog.search("vol", registry, verdict(env())) { names[it] ?: "" }
        assertEquals(listOf("volume.up", "volume.down"), found.map { it.spec.id })
        assertEquals(emptyList<ActionRow>(), ActionCatalog.search("  ", registry, verdict(env())) { "x" })
    }
}
