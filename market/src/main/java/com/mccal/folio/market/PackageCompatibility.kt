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

    /**
     * `minSdk`/`maxSdk` against this phone's Android (ADR 0013). The one hard limit nothing on the phone can fix, so it
     * blocks. [have] is null only in tests, which skip the check.
     */
    data class Android(val min: Int?, val max: Int?, val have: Int?) : CompatCheck {
        override val state get() = when {
            have == null -> CompatState.OK
            min != null && have < min -> CompatState.BLOCKED
            max != null && have > max -> CompatState.BLOCKED
            else -> CompatState.OK
        }
    }

    /** `maxFolio`, the newest Folio its author tried. Advice only: a newer Folio is a note, never a block (ADR 0013). */
    data class FolioRange(val max: FolioMinor, val have: FolioVersion?) : CompatCheck {
        override val state get() = if (have != null && !max.covers(have)) CompatState.NOTE else CompatState.OK
    }

    /** `breaks`: installed packages at versions this one doesn't work with. They have to be updated first. */
    data class Breaks(val relations: List<PackageRelation>, val broken: List<InstalledPackage>) : CompatCheck {
        override val state get() = if (broken.isEmpty()) CompatState.OK else CompatState.BLOCKED
    }
}

/** Android's own name for an API level, for the words around `minSdk` and `maxSdk`. */
object AndroidRelease {
    fun name(api: Int): String = when (api) {
        31 -> "12"; 32 -> "12L"; 33 -> "13"; 34 -> "14"; 35 -> "15"; 36 -> "16"; 37 -> "17"
        else -> "API $api"
    }

    /** "Android 13 to 17", "Android 13 or later", "Android 16 or earlier": English, for the installer's messages. */
    fun range(min: Int?, max: Int?): String = when {
        min != null && max != null -> "Android ${name(min)} to ${name(max)}"
        min != null -> "Android ${name(min)} or later"
        max != null -> "Android ${name(max)} or earlier"
        else -> "any Android"
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
    /** This phone's Android API level. Null skips `minSdk`/`maxSdk`, which only a test does. */
    val androidSdk: Int? = null,
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
        // First, because nothing the person could do here would change it.
        if (manifest.minSdk != null || manifest.maxSdk != null) add(CompatCheck.Android(manifest.minSdk, manifest.maxSdk, context.androidSdk))

        val unsupported = manifest.kinds.filter { it.reserved }
        if (unsupported.isNotEmpty()) add(CompatCheck.Kinds(unsupported))

        val missingFeatures = (manifest.missingCapabilities(context.capabilities) +
            changes.flatMap { it.capabilities }.filterNot { it in context.capabilities }).distinct()
        add(CompatCheck.Features(missingFeatures.toList()))

        add(CompatCheck.Release(manifest.minFolio, context.folioVersion.takeUnless { builtIn }))
        manifest.maxFolio?.let { add(CompatCheck.FolioRange(it, context.folioVersion.takeUnless { builtIn })) }

        val required = (if (changes.isNotEmpty()) changes.mapNotNull { it.hostTweak } else manifest.kinds.mapNotNull { it.hostTweak }).distinct()
        if (required.isNotEmpty()) add(CompatCheck.Hosts(required, required.filterNot(context.hasTweak)))

        if (manifest.conflicts.isNotEmpty()) {
            add(CompatCheck.Replaces(context.installed.firstOrNull { other ->
                other.id != manifest.id && manifest.conflicts.any { it.id == other.id && it.matches(other.version) }
            }))
        }
        if (manifest.breaks.isNotEmpty()) {
            add(CompatCheck.Breaks(manifest.breaks, context.installed.filter { other ->
                other.id != manifest.id && manifest.breaks.any { it.id == other.id && it.matches(other.version) }
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

    /**
     * The one answer: works, works with a limit, or cannot be installed and why. The package page, the install sheet
     * and the installer all read this, so none of them can say yes while another says no.
     */
    fun verdict(checks: List<CompatCheck>): CompatVerdict {
        val blockers = checks.filter { it.state == CompatState.BLOCKED }
        blockers.firstOrNull()?.let { return CompatVerdict.CannotInstall(it.reason(), it, blockers) }
        val limits = checks.filter { it.state == CompatState.NOTE }
        return if (limits.isEmpty()) CompatVerdict.Works else CompatVerdict.WorksWithLimit(limits)
    }

    fun verdict(
        manifest: PackageManifest,
        context: CompatContext,
        changes: List<PackageChange> = emptyList(),
        builtIn: Boolean = false,
    ): CompatVerdict = verdict(check(manifest, context, changes, builtIn))
}

/** Why a package cannot be installed here. [key] is stable, so a string table can be keyed on it. */
enum class CompatReason(val key: String, val offersFix: Boolean = false) {
    ANDROID_VERSION("android_version"),
    BREAKS_INSTALLED("breaks_installed"),
    UNSUPPORTED_KIND("unsupported_kind"),
    MISSING_CAPABILITY("missing_capability"),
    NEEDS_NEWER_FOLIO("needs_newer_folio"),

    /** Get the tweak it adds to first; the page and sheet offer it, so this is not a dead end. */
    NEEDS_HOST_TWEAK("needs_host_tweak", offersFix = true),
    REPLACES_INSTALLED("replaces_installed"),
    NEEDS_PACKAGE("needs_package"),
}

/** What [PackageCompatibility.verdict] decides. */
sealed interface CompatVerdict {
    /** Nothing in the way and nothing to say. */
    data object Works : CompatVerdict

    /** It installs, with something worth knowing first (a screen it doesn't cover, a Folio newer than it was tried on). */
    data class WorksWithLimit(val limits: List<CompatCheck>) : CompatVerdict

    /**
     * It does not install. [cause] is the first blocking check, the one the installer refuses on; [blockers] is every
     * blocking check, so an action is decided on all of them and a fixable cause cannot hide an unfixable one.
     */
    data class CannotInstall(val reason: CompatReason, val cause: CompatCheck, val blockers: List<CompatCheck> = listOf(cause)) : CompatVerdict
}

/** True when Get must be off: it cannot install and something blocking it is not fixed in place by the page. */
val CompatVerdict.stopsGet: Boolean get() = this is CompatVerdict.CannotInstall && blockers.any { !it.reason().offersFix }

private fun CompatCheck.reason(): CompatReason = when (this) {
    is CompatCheck.Android -> CompatReason.ANDROID_VERSION
    is CompatCheck.Breaks -> CompatReason.BREAKS_INSTALLED
    is CompatCheck.Kinds -> CompatReason.UNSUPPORTED_KIND
    is CompatCheck.Features -> CompatReason.MISSING_CAPABILITY
    is CompatCheck.Release -> CompatReason.NEEDS_NEWER_FOLIO
    is CompatCheck.Hosts -> CompatReason.NEEDS_HOST_TWEAK
    is CompatCheck.Replaces -> CompatReason.REPLACES_INSTALLED
    is CompatCheck.Needs -> CompatReason.NEEDS_PACKAGE
    // These only ever note. If one is made to block, name its reason here.
    is CompatCheck.Screens, is CompatCheck.FolioRange -> error("${this::class.simpleName} never blocks")
}
