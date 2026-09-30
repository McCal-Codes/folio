package com.mccal.folio.market

/**
 * What a package may and may not do, worked out from its manifest alone — never from anything its author wrote.
 *
 * The "does not" list is the point: a Folio package is data, so the things people worry about (reading your messages,
 * phoning home, touching other apps) aren't things it's trusted not to do, they're things it has no way to do. The
 * format's rules are in docs/sdk/format-v1.md; ADR 0004 is why.
 */
data class PackageSafety(
    /** True when the package ships a script. Folio doesn't run scripts (the kind is reserved), so it can't be installed. */
    val runsCode: Boolean,
    /** What it changes, in the words the privacy label uses. Empty means appearance only. */
    val changes: List<String>,
    /** What no package can reach, whatever it declares. */
    val cannotAccess: List<String>,
) {
    /** The one-line summary for the top of a package's page. */
    val summary: String get() = buildString {
        append(if (runsCode) "Has a script Folio doesn't run" else "No code")
        append(" · No network · No personal data")
        append(if (changes.isEmpty()) " · Appearance only" else " · Changes ${changes.size} thing${if (changes.size == 1) "" else "s"}")
    }

    companion object {
        /**
         * Things a package can never do, because Folio holds its own Android permissions and a package only configures
         * what Folio already does. Worth saying out loud on every package page.
         */
        val CANNOT_ACCESS = listOf(
            "Your apps or their data",
            "Notifications, contacts or your calendar",
            "The network, or anything outside Folio",
            "Other apps' settings, or Android's",
        )

        fun of(manifest: PackageManifest): PackageSafety = PackageSafety(
            runsCode = PackageKind.SCRIPT in manifest.kinds,
            // The label wording comes from the permission, not the author: an appearance-only permission says nothing.
            changes = manifest.permissions.mapNotNull { it.label }.distinct().sorted(),
            cannotAccess = CANNOT_ACCESS,
        )
    }
}
