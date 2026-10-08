package com.mccal.folio

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import org.json.JSONObject

/**
 * One thing an icon, a trigger or a button can run: an action id and a few string arguments, never code. The 13
 * original [FolioAction]s keep their enum names as ids (they are saved by name); every newer action has a stable
 * namespaced id such as `app.open`. An id this build does not know is kept as it is and reported as unavailable, so a
 * backup from a newer Folio loses nothing when it passes through an older one.
 */
data class ActionRef(val id: String, val args: Map<String, String> = emptyMap()) {
    fun toJson(): JSONObject = JSONObject().put("id", id).apply {
        if (args.isNotEmpty()) put("a", JSONObject().apply { args.toSortedMap().forEach { (k, v) -> put(k, v) } })
    }

    companion object {
        fun of(action: FolioAction) = ActionRef(action.name)

        fun fromJson(json: JSONObject?): ActionRef? {
            val id = json?.optString("id")?.takeIf { it.isNotBlank() } ?: return null
            val a = json.optJSONObject("a")
            val args = a?.keys()?.asSequence()?.associateWith { a.optString(it) } ?: emptyMap()
            return ActionRef(id, args)
        }
    }
}

/** What an action needs before it can run. Access beyond a normal app is asked for by the broker, not assumed. */
internal enum class ActionNeeds {
    /** Nothing: an ordinary app can do it. */
    NONE,
    /** Folio's accessibility service (tier A2), connected and allowed to act. */
    ACCESSIBILITY,
    /** Do Not Disturb access, granted once in Settings. */
    POLICY_ACCESS,
}

/** One registered action. [run] returns whether it did the thing; throwing is caught by [ActionRunner]. */
internal class ActionSpec(
    val id: String,
    @StringRes val label: Int,
    val risk: OperationRisk,
    val needs: ActionNeeds = ActionNeeds.NONE,
    /** The lowest Android API level this works on; 0 for all. */
    val minSdk: Int = 0,
    val run: (Context, Map<String, String>) -> Boolean,
)

/** The answer to "can this action run right now", in the order a person would fix it. Always a state, never an error. */
internal sealed interface ActionVerdict {
    data object Allowed : ActionVerdict
    data object NeedsAccessibility : ActionVerdict
    data object NeedsPolicyAccess : ActionVerdict
    /** This Android is too old for the action. */
    data class NeedsAndroid(val sdk: Int) : ActionVerdict
    /** Safe Mode is on, so only the ordinary built-in behavior runs. */
    data object SafeMode : ActionVerdict
    /** Folio has no action with this id (a newer backup, or a package that is gone). */
    data object Unknown : ActionVerdict

    val allowed get() = this == Allowed
}

/** What the verdict reads from the phone, so every rule is a plain JVM test. */
internal class ActionEnv(
    val accessibilityConnected: () -> Boolean,
    val policyAccess: () -> Boolean,
    val sdk: Int,
    val safeMode: () -> Boolean,
) {
    companion object {
        fun live(context: Context) = ActionEnv(
            accessibilityConnected = { SystemShadeAccessibilityService.isConnected() },
            policyAccess = { context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted },
            sdk = Build.VERSION.SDK_INT,
            safeMode = { SafeMode.active },
        )
    }
}

/** Where a run came from: the trail names it, and the paths added with Icon Actions stay off in Safe Mode. */
internal enum class ActionSource(val trailName: String, val honorsSafeMode: Boolean) {
    /** The older callers (triggers, lock cover): behave as they always did. */
    DIRECT("button", false),
    TRIGGER("trigger", false),
    ICON("icon", true),
    TRY("try", true),
}

internal class ActionRegistry(specs: List<ActionSpec>) {
    private val byId = specs.associateBy { it.id }.also { require(it.size == specs.size) { "Two actions share an id" } }
    val all: List<ActionSpec> = specs

    fun spec(id: String): ActionSpec? = byId[id]

    fun verdict(ref: ActionRef, env: ActionEnv, source: ActionSource = ActionSource.DIRECT): ActionVerdict {
        val spec = byId[ref.id] ?: return ActionVerdict.Unknown
        if (source.honorsSafeMode && env.safeMode()) return ActionVerdict.SafeMode
        if (env.sdk < spec.minSdk) return ActionVerdict.NeedsAndroid(spec.minSdk)
        return when (spec.needs) {
            ActionNeeds.NONE -> ActionVerdict.Allowed
            ActionNeeds.ACCESSIBILITY -> if (env.accessibilityConnected()) ActionVerdict.Allowed else ActionVerdict.NeedsAccessibility
            ActionNeeds.POLICY_ACCESS -> if (env.policyAccess()) ActionVerdict.Allowed else ActionVerdict.NeedsPolicyAccess
        }
    }

    companion object {
        /** The registry Folio runs with. The original actions are registered unchanged under their enum names. */
        val standard: ActionRegistry by lazy { ActionRegistry(FolioAction.entries.filter { it != FolioAction.NONE }.map(::legacy)) }

        private fun legacy(action: FolioAction) = ActionSpec(
            id = action.name,
            label = action.label,
            risk = when (action) {
                FolioAction.LOCK, FolioAction.SCREENSHOT -> OperationRisk.DISRUPTIVE
                FolioAction.SPOTLIGHT, FolioAction.NOTIFICATIONS, FolioAction.CONTROL_CENTER -> OperationRisk.OBSERVE
                else -> OperationRisk.REVERSIBLE
            },
            needs = when (action) {
                FolioAction.LOCK, FolioAction.SCREENSHOT -> ActionNeeds.ACCESSIBILITY
                FolioAction.DND_ON, FolioAction.DND_OFF -> ActionNeeds.POLICY_ACCESS
                else -> ActionNeeds.NONE
            },
            run = { context, _ -> FolioActions.perform(context, action) },
        )
    }
}

/**
 * The one way an action runs: ask for a verdict, run inside a catch, and leave a line in the trail either way, so a
 * refused or failed action is never silent. The trail holds ids and outcomes only, no app names or arguments. Trail
 * lines are read in reports by the developer, not shown to people, so they stay English (marked english-only below).
 */
internal object ActionRunner {
    /** Returns whether the action ran. A refusal also tells the person what to turn on, as the old actions did. */
    fun run(
        context: Context,
        ref: ActionRef,
        source: ActionSource = ActionSource.DIRECT,
        registry: ActionRegistry = ActionRegistry.standard,
        env: ActionEnv = ActionEnv.live(context),
        notice: (Int) -> Unit = { IslandEvents.notice(context, context.getString(it)) },
        trail: (String) -> Unit = Diagnostics::event,
    ): Boolean {
        val spec = registry.spec(ref.id)
        val verdict = registry.verdict(ref, env, source)
        if (spec == null || !verdict.allowed) {
            trail("Action ${ref.id} (${source.trailName}) refused: ${verdict.trailText()}")
            when (verdict) {
                ActionVerdict.NeedsAccessibility -> notice(R.string.needs_accessibility_service)
                ActionVerdict.NeedsPolicyAccess -> notice(R.string.needs_dnd_access)
                else -> Unit
            }
            return false
        }
        val done = try {
            spec.run(context, ref.args)
        } catch (e: Exception) {
            // Diagnostics.caught leaves the one trail line ("Action X failed: <class>"); nothing is added here.
            Diagnostics.caught("Action ${ref.id}", e)
            return false
        }
        if (!done) {
            trail("Action ${ref.id} (${source.trailName}) did not run")  // english-only
            if (spec.needs == ActionNeeds.ACCESSIBILITY) notice(R.string.needs_accessibility_service)
            return false
        }
        trail("Action ${ref.id} (${source.trailName}) ran")  // english-only
        return true
    }

    private fun ActionVerdict.trailText() = when (this) {
        ActionVerdict.Allowed -> "allowed"
        ActionVerdict.NeedsAccessibility -> "needs Accessibility"
        ActionVerdict.NeedsPolicyAccess -> "needs Do Not Disturb access"
        is ActionVerdict.NeedsAndroid -> "needs Android API $sdk"
        ActionVerdict.SafeMode -> "Safe Mode is on"  // english-only
        ActionVerdict.Unknown -> "unknown action"
    }
}
