package com.mccal.folio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The registry wraps the 13 original actions unchanged; the runner gives every run a verdict and a trail line. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActionRegistryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun env(accessibility: Boolean = true, policy: Boolean = true, sdk: Int = 34, safe: Boolean = false) =
        ActionEnv({ accessibility }, { policy }, sdk, { safe })

    private fun ref(action: FolioAction) = ActionRef.of(action)

    @Test fun `every original action except Nothing is registered under its enum name`() {
        val registry = ActionRegistry.standard
        FolioAction.entries.filter { it != FolioAction.NONE }.forEach { action ->
            val spec = registry.spec(action.name)
            assertTrue("${action.name} has no spec", spec != null)
            assertEquals(action.label, spec!!.label)
        }
        assertNull("Nothing is not an action", registry.spec(FolioAction.NONE.name))
    }

    @Test fun `the original ids never change, because they are saved by name`() {
        val saved = listOf("SPOTLIGHT", "NOTIFICATIONS", "CONTROL_CENTER", "LOCK", "SCREENSHOT", "TORCH", "DND_ON", "DND_OFF",
            "FOCUS_SLEEP", "FOCUS_WORK", "FOCUS_PERSONAL", "FOCUS_OFF")
        saved.forEach { assertTrue("$it is gone from the registry", ActionRegistry.standard.spec(it) != null) }
    }

    @Test fun `two actions cannot share an id`() {
        val spec = ActionSpec("x", R.string.nothing, OperationRisk.OBSERVE) { _, _ -> true }
        val failed = runCatching { ActionRegistry(listOf(spec, spec)) }.isFailure
        assertTrue(failed)
    }

    @Test fun `lock and screenshot need accessibility and say so`() {
        val registry = ActionRegistry.standard
        assertEquals(ActionVerdict.NeedsAccessibility, registry.verdict(ref(FolioAction.LOCK), env(accessibility = false)))
        assertEquals(ActionVerdict.NeedsAccessibility, registry.verdict(ref(FolioAction.SCREENSHOT), env(accessibility = false)))
        assertEquals(ActionVerdict.Allowed, registry.verdict(ref(FolioAction.LOCK), env(accessibility = true)))
    }

    @Test fun `Do Not Disturb needs its access`() {
        val registry = ActionRegistry.standard
        assertEquals(ActionVerdict.NeedsPolicyAccess, registry.verdict(ref(FolioAction.DND_ON), env(policy = false)))
        assertEquals(ActionVerdict.Allowed, registry.verdict(ref(FolioAction.DND_OFF), env(policy = true)))
    }

    @Test fun `the flashlight and the panels need nothing`() {
        val registry = ActionRegistry.standard
        listOf(FolioAction.TORCH, FolioAction.SPOTLIGHT, FolioAction.CONTROL_CENTER, FolioAction.FOCUS_WORK).forEach {
            assertEquals(ActionVerdict.Allowed, registry.verdict(ref(it), env(accessibility = false, policy = false)))
        }
    }

    @Test fun `an id this build does not know is unavailable, not an error`() {
        assertEquals(ActionVerdict.Unknown, ActionRegistry.standard.verdict(ActionRef("from.the.future"), env()))
    }

    @Test fun `Safe Mode stops the new paths and leaves the old ones as they were`() {
        val registry = ActionRegistry.standard
        val torch = ref(FolioAction.TORCH)
        assertEquals(ActionVerdict.SafeMode, registry.verdict(torch, env(safe = true), ActionSource.ICON))
        assertEquals(ActionVerdict.SafeMode, registry.verdict(torch, env(safe = true), ActionSource.TRY))
        assertEquals(ActionVerdict.Allowed, registry.verdict(torch, env(safe = true), ActionSource.DIRECT))
        assertEquals(ActionVerdict.Allowed, registry.verdict(torch, env(safe = true), ActionSource.TRIGGER))
    }

    @Test fun `an action that needs a newer Android says which`() {
        val registry = ActionRegistry(listOf(ActionSpec("new", R.string.nothing, OperationRisk.OBSERVE, minSdk = 36) { _, _ -> true }))
        assertEquals(ActionVerdict.NeedsAndroid(36), registry.verdict(ActionRef("new"), env(sdk = 34)))
        assertEquals(ActionVerdict.Allowed, registry.verdict(ActionRef("new"), env(sdk = 36)))
    }

    @Test fun `lock and screenshot are marked disruptive and the panels are not`() {
        val registry = ActionRegistry.standard
        assertEquals(OperationRisk.DISRUPTIVE, registry.spec("LOCK")!!.risk)
        assertEquals(OperationRisk.DISRUPTIVE, registry.spec("SCREENSHOT")!!.risk)
        assertEquals(OperationRisk.OBSERVE, registry.spec("SPOTLIGHT")!!.risk)
    }

    // The runner.

    private class Seen { val trail = mutableListOf<String>(); val notices = mutableListOf<Int>(); var ran = 0 }

    private fun runner(registry: ActionRegistry, ref: ActionRef, env: ActionEnv, seen: Seen, source: ActionSource = ActionSource.ICON) =
        ActionRunner.run(context, ref, source, registry, env, { seen.notices += it }, { seen.trail += it })

    private fun fake(needs: ActionNeeds = ActionNeeds.NONE, seen: Seen, body: () -> Boolean = { true }) =
        ActionRegistry(listOf(ActionSpec("fake", R.string.nothing, OperationRisk.REVERSIBLE, needs) { _, _ -> seen.ran++; body() }))

    @Test fun `a run that works leaves one line in the trail`() {
        val seen = Seen()
        assertTrue(runner(fake(seen = seen), ActionRef("fake"), env(), seen))
        assertEquals(1, seen.ran)
        assertEquals(listOf("Action fake (icon) ran"), seen.trail)
        assertTrue(seen.notices.isEmpty())
    }

    @Test fun `a refused action does not run, says what to turn on, and is in the trail`() {
        val seen = Seen()
        assertFalse(runner(fake(ActionNeeds.ACCESSIBILITY, seen), ActionRef("fake"), env(accessibility = false), seen))
        assertEquals(0, seen.ran)
        assertEquals(listOf(R.string.needs_accessibility_service), seen.notices)
        assertEquals(listOf("Action fake (icon) refused: needs Accessibility"), seen.trail)
    }

    @Test fun `Do Not Disturb refused tells the person about its access`() {
        val seen = Seen()
        runner(fake(ActionNeeds.POLICY_ACCESS, seen), ActionRef("fake"), env(policy = false), seen)
        assertEquals(listOf(R.string.needs_dnd_access), seen.notices)
    }

    @Test fun `an unknown id is refused quietly and recorded`() {
        val seen = Seen()
        assertFalse(runner(ActionRegistry.standard, ActionRef("from.the.future"), env(), seen))
        assertTrue(seen.notices.isEmpty())
        assertEquals(listOf("Action from.the.future (icon) refused: unknown action"), seen.trail)
    }

    @Test fun `an action that throws never reaches the caller`() {
        val seen = Seen()
        val boom = fake(seen = seen) { throw IllegalStateException("a path /secret that must not be recorded") }
        assertFalse(runner(boom, ActionRef("fake"), env(), seen))
        val trail = Diagnostics.trailText()
        assertTrue(trail.contains("Action fake failed: IllegalStateException"))
        assertFalse("the exception message must stay out of the trail", trail.contains("/secret"))
    }

    @Test fun `an action that reports it did nothing leaves a line, and an accessibility one says what to turn on`() {
        val seen = Seen()
        assertFalse(runner(fake(ActionNeeds.ACCESSIBILITY, seen) { false }, ActionRef("fake"), env(accessibility = true), seen))
        assertEquals(listOf("Action fake (icon) did not run"), seen.trail)
        assertEquals(listOf(R.string.needs_accessibility_service), seen.notices)
    }

    @Test fun `the flashlight on a phone with no flash did not run, and the trail says so with where it came from`() {
        // Robolectric has no camera, which is a phone with no flash.
        assertFalse(FolioActions.perform(context, FolioAction.TORCH))
        FolioActions.run(context, FolioAction.TORCH, ActionSource.TRIGGER)
        val trail = Diagnostics.trailText()
        assertTrue(trail.contains("Action TORCH (trigger) did not run"))
    }

    @Test fun `an older caller is recorded as a button press`() {
        FolioActions.run(context, FolioAction.TORCH)
        assertTrue(Diagnostics.trailText().contains("Action TORCH (button) did not run"))
    }

    // Saving an action.

    @Test fun `an action with arguments round trips`() {
        val ref = ActionRef("shortcut.open", mapOf("pkg" to "com.example", "id" to "compose"))
        assertEquals(ref, ActionRef.fromJson(JSONObject(ref.toJson().toString())))
    }

    @Test fun `an action with no arguments writes only its id`() {
        assertEquals("{\"id\":\"TORCH\"}", ActionRef.of(FolioAction.TORCH).toJson().toString())
    }

    @Test fun `an id that is not known loads and writes back unchanged`() {
        val saved = """{"id":"from.the.future","a":{"x":"1"}}"""
        val ref = ActionRef.fromJson(JSONObject(saved))!!
        assertEquals(ActionRef("from.the.future", mapOf("x" to "1")), ref)
        assertEquals(JSONObject(saved).toString(), ref.toJson().toString())
    }

    @Test fun `a broken entry is dropped without throwing`() {
        assertNull(ActionRef.fromJson(null))
        assertNull(ActionRef.fromJson(JSONObject("{}")))
        assertNull(ActionRef.fromJson(JSONObject("""{"id":"  "}""")))
    }
}
