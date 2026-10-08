package com.mccal.folio.market

/** How one compatibility check came out. A [NOTE] is worth saying but never stops an install. */
enum class CompatState { OK, NOTE, BLOCKED }

/**
 * One thing worth telling a person about a package before they tap Get. Typed rather than worded, so the page, the
 * list rows and the installer's refusals all come from the same answer and each is free to phrase it.
 */
sealed interface CompatCheck {
    val state: CompatState

    /** `minFolio` against this build. [have] is null only in tests, which skip the check. */
    data class Release(val needs: FolioVersion, val have: FolioVersion?) : CompatCheck {
        override val state get() = if (have != null && needs > have) CompatState.BLOCKED else CompatState.OK
    }

    /** Kinds Folio reads but doesn't act on yet (a script, a settings schema). */
    data class Kinds(val unsupported: List<PackageKind>) : CompatCheck {
        override val state get() = if (unsupported.isEmpty()) CompatState.OK else CompatState.BLOCKED
    }

    /** Capabilities the package configures, and the ones this build lacks. */
    data class Features(val missing: List<Capability>) : CompatCheck {
        override val state get() = if (missing.isEmpty()) CompatState.OK else CompatState.BLOCKED
    }

    /** Tweaks an add-on needs on the phone first ([PackageKind.hostTweak]), and the ones that aren't there. */
    data class Hosts(val required: List<String>, val missing: List<String>) : CompatCheck {
        override val state get() = if (missing.isEmpty()) CompatState.OK else CompatState.BLOCKED
    }

    /** An installed package this one replaces, when it replaces one. */
    data class Replaces(val installed: InstalledPackage?) : CompatCheck {
        override val state get() = if (installed == null) CompatState.OK else CompatState.BLOCKED
    }

    /** Packages this one needs first, and the ones that are not yet installed and on. */
    data class Needs(val required: List<PackageRelation>, val missing: List<PackageRelation>) : CompatCheck {
        override val state get() = if (missing.isEmpty()) CompatState.OK else CompatState.BLOCKED
    }

    /** Which of this phone's screens the package covers. Never blocks: a package for the other screen is harmless. */
    data class Screens(val supported: Set<Screen>, val device: Set<Screen>) : CompatCheck {
        override val state get() = if (supported.containsAll(device)) CompatState.OK else CompatState.NOTE
    }
}

/** What a check needs to know about this phone and what is on it. */
class CompatContext(
    val capabilities: Set<Capability>,
    /** This build's release number. Null skips the `minFolio` check, which only a test does. */
    val folioVersion: FolioVersion?,
    val installed: List<InstalledPackage>,
    val hasTweak: (String) -> Boolean = { false },
    /** The screens this phone has, or null when that is not known (nothing is said about screens then). */
    val screens: Set<Screen>? = null,
)

/**
 * Whether a package can be used here, answered from the manifest alone (so a listing can show it before anything is
 * downloaded) and, at install time, from the changes as well. The installer refuses on exactly the checks that come
 * back [CompatState.BLOCKED], in this order, so a page can never say "works" while the installer says no.
 */
object PackageCompatibility {
    /**
     * [changes] are what the package would apply; empty for a listing, where the manifest's kinds stand in for them.
     * [builtIn] packages ship inside Folio, so their `minFolio` is not checked against the build they are part of.
     */
    fun check(
        manifest: PackageManifest,
        context: CompatContext,
        changes: List<PackageChange> = emptyList(),
        builtIn: Boolean = false,
    ): List<CompatCheck> = buildList {
        val unsupported = manifest.kinds.filter { it.reserved }
        if (unsupported.isNotEmpty()) add(CompatCheck.Kinds(unsupported))

        val missingFeatures = (manifest.missingCapabilities(context.capabilities) +
            changes.flatMap { it.capabilities }.filterNot { it in context.capabilities }).distinct()
        add(CompatCheck.Features(missingFeatures.toList()))

        add(CompatCheck.Release(manifest.minFolio, context.folioVersion.takeUnless { builtIn }))

        val required = (if (changes.isNotEmpty()) changes.mapNotNull { it.hostTweak } else manifest.kinds.mapNotNull { it.hostTweak }).distinct()
        if (required.isNotEmpty()) add(CompatCheck.Hosts(required, required.filterNot(context.hasTweak)))

        if (manifest.conflicts.isNotEmpty()) {
            add(CompatCheck.Replaces(context.installed.firstOrNull { other ->
                other.id != manifest.id && manifest.conflicts.any { it.id == other.id && it.matches(other.version) }
            }))
        }
        if (manifest.depends.isNotEmpty()) {
            add(CompatCheck.Needs(manifest.depends, manifest.depends.filterNot { needed ->
                context.installed.any { it.id == needed.id && it.enabled && needed.matches(it.version) }
            }))
        }
        context.screens?.let { add(CompatCheck.Screens(manifest.screens, it)) }
    }

    /** The first check that stops an install, or null when none does. */
    fun blocking(checks: List<CompatCheck>): CompatCheck? = checks.firstOrNull { it.state == CompatState.BLOCKED }
}
