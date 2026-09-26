package com.mccal.folio.market

/** `tweaks.json`: which built-in tweaks a `tweakBundle` package turns on, and on which screens. */
data class TweakBundle(val tweaks: List<TweakSetting>) {
    companion object {
        const val MAX_CHARS = 16 * 1024
        const val MAX_TWEAKS = 20

        fun parse(text: String): ParseResult<TweakBundle> {
            val p = Problems()
            val json = parseStrictObject(text, MAX_CHARS, p) ?: return p.result { error("unreachable") }
            val f = Fields(json, "", p, setOf("\$schema", "format", "tweaks"))
            f.anyString("\$schema")
            f.formatOne()
            val tweaks = f.objects("tweaks", true, minItems = 1, maxItems = MAX_TWEAKS)?.map { (i, item) ->
                val t = f.child(item, "${f.where("tweaks")}[$i]", setOf("id", "enabled", "screens"))
                val id = t.id("id", true, TweakId::from)
                val enabled = t.bool("enabled", true)
                val screens = t.obj("screens", false, setOf("cover", "inner"))
                TweakSetting(
                    id = id ?: TweakId.APP_PANELS,
                    enabled = enabled ?: false,
                    cover = screens?.bool("cover", false) ?: true,
                    inner = screens?.bool("inner", false) ?: true,
                )
            }
            return p.result { TweakBundle(tweaks!!) }
        }
    }
}

data class TweakSetting(val id: TweakId, val enabled: Boolean, val cover: Boolean = true, val inner: Boolean = true)

/** The built-in tweaks a package can configure. The ids match `TweakFeatures` in the launcher. */
enum class TweakId(val id: String, val capability: Capability) {
    APP_PANELS("appPanels", Capability.APP_PANELS),
    DOCK_MAGNIFY("dockMagnify", Capability.DOCK_MAGNIFY),
    NOTIFICATION_APP_ROW("notificationAppRow", Capability.NOTIFICATION_APP_ROW),
    TINT_NOTIFICATIONS("tintNotifications", Capability.TINT_NOTIFICATIONS),
    TINT_MEDIA("tintMedia", Capability.TINT_MEDIA),
    PAGE_EFFECTS("pageEffects", Capability.PAGE_EFFECTS);

    companion object {
        fun from(id: String) = entries.firstOrNull { it.id == id }
    }
}

/**
 * A package that has been opened and read: its manifest, its page, and the payload for each kind it declares.
 * Nothing here has been applied yet.
 */
data class FolioPackage(
    val manifest: PackageManifest,
    val depiction: Depiction?,
    val changes: List<PackageChange>,
    val assets: Map<String, ByteArray>,
    /** Fields and blocks from a newer format that this Folio skipped while reading the package. */
    val notes: List<String> = emptyList(),
    /** Everything the archive held, which is what an author's own signature is taken over. */
    val files: Map<String, ByteArray> = emptyMap(),
) {
    val id: String get() = manifest.id
    val version: DebVersion get() = manifest.version
}

/**
 * One thing a package changes. Folio applies these through the launcher ([PackageHost]); a package never touches
 * anything else, and removing it undoes exactly these.
 */
sealed interface PackageChange {
    /** The Folio capabilities this needs, so compatibility is settled before anything is applied. */
    val capabilities: Set<Capability>

    data class Theme(val json: String) : PackageChange {
        override val capabilities get() = setOf(Capability.THEME)
    }

    data class Tweaks(val bundle: TweakBundle) : PackageChange {
        override val capabilities get() = bundle.tweaks.map { it.id.capability }.toSet()
    }

    data class Layout(val json: String) : PackageChange {
        override val capabilities get() = setOf(Capability.HOME_LAYOUT)
    }

    /**
     * A picture, and the credit that lets Folio show it.
     *
     * The credit travels with the change rather than being looked up later, because the only moment it is reachable
     * is while the manifest is open: by the time a host is asked to apply this, it has the change and nothing else.
     * Folio's design standard requires an artist and a license for every image it shows, so a wallpaper that arrived
     * without them has to be refusable at the point of applying, and that needs them here.
     *
     * The fields are the package's own, not a new format: [artist] is the package author, [license] is the package
     * license, [title] is its name. See `docs/sdk/format-v1.md`, "Wallpapers".
     */
    data class Wallpaper(
        val path: String,
        val bytes: ByteArray,
        val id: String = "",
        val title: String = "",
        val artist: String = "",
        val license: String = "",
        val detail: String = "",
        val source: String = "",
        /** The picture's SHA-256, for a change read from a record, which keeps the hash and not the picture. */
        val sha256: String = "",
    ) : PackageChange {
        override val capabilities get() = setOf(Capability.WALLPAPER)

        /**
         * Which picture this change means. A record names it by hash so that putting an older version back puts back
         * that version's picture, not whichever one an update left in place.
         */
        val pictureSha256: String get() = if (bytes.isNotEmpty()) sha256Hex(bytes) else sha256

        /** True when this may be shown at all: an image with no author has no license to give. */
        val credited: Boolean get() = artist.isNotBlank() && license.isNotBlank()

        // Written out because ByteArray compares by identity, which would make two equal pictures unequal.
        override fun equals(other: Any?) = other is Wallpaper && other.path == path &&
            other.bytes.contentEquals(bytes) && other.id == id && other.title == title &&
            other.artist == artist && other.license == license && other.detail == detail && other.source == source &&
            other.sha256 == sha256

        override fun hashCode(): Int {
            var result = path.hashCode()
            result = 31 * result + bytes.contentHashCode()
            result = 31 * result + id.hashCode()
            result = 31 * result + title.hashCode()
            result = 31 * result + artist.hashCode()
            result = 31 * result + license.hashCode()
            result = 31 * result + detail.hashCode()
            result = 31 * result + source.hashCode()
            result = 31 * result + sha256.hashCode()
            return result
        }
    }

    /** Points at an icon pack app that's already installed; Folio only reads it through the usual intents. */
    data class IconPack(val packageName: String) : PackageChange {
        override val capabilities get() = setOf(Capability.ICON_PACKS)
    }
}
