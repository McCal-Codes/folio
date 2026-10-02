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
                val t = f.child(item, "${f.where("tweaks")}[$i]", setOf("id", "enabled", "screens", "options"))
                val id = t.id("id", true, TweakId::from)
                val enabled = t.bool("enabled", true)
                val screens = t.obj("screens", false, setOf("cover", "inner"))
                TweakSetting(
                    id = id ?: TweakId.APP_PANELS,
                    enabled = enabled ?: false,
                    cover = screens?.bool("cover", false) ?: true,
                    inner = screens?.bool("inner", false) ?: true,
                    options = id?.let { readOptions(t, it) }.orEmpty(),
                )
            }
            return p.result { TweakBundle(tweaks!!) }
        }

        /** `options`, checked against [TweakOptions] for that tweak. A tweak with no options listed takes none. */
        private fun readOptions(t: Fields, id: TweakId): Map<String, Any> {
            val specs = TweakOptions.forTweak(id)
            // Keys this Folio doesn't know are skipped and reported, like every other newer field.
            val o = t.obj("options", false, specs.keys) ?: return emptyMap()
            return specs.mapNotNull { (key, spec) ->
                when (spec) {
                    is TweakOptions.Choice -> o.choice(key, false, spec.values)
                    is TweakOptions.Number -> o.number(key, false, spec.range)
                }?.let { key to it }
            }.toMap()
        }
    }
}

/**
 * [options] are the tweak's own settings, already checked: a [String] for a choice, a [Double] for a number. Folio
 * clamps them again when it applies them, because a record can come back from an older backup.
 */
data class TweakSetting(val id: TweakId, val enabled: Boolean, val cover: Boolean = true, val inner: Boolean = true,
    val options: Map<String, Any> = emptyMap())

/**
 * The options each tweak takes in `tweaks.json`. `:market` can't see the launcher's DuetStyles/DuetOptions, so this
 * repeats them; DuetTest in `:app` is the contract that fails if the two drift apart.
 */
object TweakOptions {
    sealed interface Spec
    data class Choice(val values: List<String>) : Spec
    data class Number(val range: ClosedFloatingPointRange<Double>) : Spec

    /** Duet: the style, and multipliers on it (1 = as the style comes). Intensity is the fold's existing slider. */
    val DUET: Map<String, Spec> = linkedMapOf(
        "style" to Choice(listOf("iphone", "duo", "classic", "deep", "subtle", "minimal")),
        "direction" to Choice(listOf("both", "opening", "closing")),
        "intensity" to Number(.3..1.5),
        "frost" to Number(0.0..2.0),
        "darkening" to Number(0.0..2.0),
        "perspective" to Number(0.0..1.33),
    )

    fun forTweak(id: TweakId): Map<String, Spec> = if (id == TweakId.DUET) DUET else emptyMap()
}

/** The built-in tweaks a package can configure. The ids match `TweakFeatures` in the launcher. */
enum class TweakId(val id: String, val capability: Capability) {
    APP_PANELS("appPanels", Capability.APP_PANELS),
    DOCK_MAGNIFY("dockMagnify", Capability.DOCK_MAGNIFY),
    NOTIFICATION_APP_ROW("notificationAppRow", Capability.NOTIFICATION_APP_ROW),
    TINT_NOTIFICATIONS("tintNotifications", Capability.TINT_NOTIFICATIONS),
    TINT_MEDIA("tintMedia", Capability.TINT_MEDIA),
    PAGE_EFFECTS("pageEffects", Capability.PAGE_EFFECTS),
    DUET("duet", Capability.FOLD_TRANSITION);

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
    /** The tweak this change is an add-on to ([PackageKind.hostTweak]): on without it, the change would do nothing. */
    val hostTweak: String? get() = null

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
     * How Home's pages turn as you swipe, described as numbers (ADR 0008: the engine is Folio's, the effect is the
     * package's). The numbers are the package's own and untrusted: the app clamps them to what its engine draws well
     * before drawing anything. [id] is the package id, [name] what the Page Effects picker shows.
     */
    data class PageEffect(
        val id: String, val name: String,
        val maxRotation: Float, val pivot: String, val shrink: Float, val cameraWidths: Float,
    ) : PackageChange {
        override val capabilities get() = setOf(Capability.PAGE_EFFECTS)
        override val hostTweak get() = PackageKind.PAGE_EFFECT.hostTweak

        companion object {
            val PIVOTS = setOf("seam", "center")

            /** Reads `effect.json`; null when a number is missing or not finite, or the pivot isn't one Folio knows. */
            fun parse(id: String, name: String, text: String): PageEffect? {
                val json = runCatching { org.json.JSONObject(text) }.getOrNull() ?: return null
                fun number(key: String) = json.optDouble(key, Double.NaN).toFloat().takeIf { it.isFinite() }
                val pivot = json.optString("pivot").takeIf { it in PIVOTS } ?: return null
                return PageEffect(id, name, number("maxRotation") ?: return null, pivot,
                    number("shrink") ?: return null, number("cameraWidths") ?: return null)
            }
        }
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
