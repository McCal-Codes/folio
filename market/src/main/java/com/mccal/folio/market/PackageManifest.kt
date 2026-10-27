package com.mccal.folio.market

/** A package's `manifest.json` (format v1). See docs/sdk/format-v1.md; the schema is `manifest.schema.json`. */
data class PackageManifest(
    val id: String,
    val name: LocalizedText,
    val version: DebVersion,
    val author: Author,
    val minFolio: FolioVersion,
    val section: Section,
    val kinds: Set<PackageKind>,
    val permissions: Set<PackagePermission>,
    val screens: Set<Screen>,
    val depends: List<PackageRelation> = emptyList(),
    val conflicts: List<PackageRelation> = emptyList(),
    val icon: String? = null,
    val depiction: String? = null,
    val license: String? = null,
    val description: LocalizedText? = null,
    val aiAssisted: AiAssisted? = null,
    val via: List<ExternalSource> = emptyList(),
    val provides: Set<Provides> = emptySet(),
    val requiredFeatures: Set<Capability> = emptySet(),
    /** Android API levels the package works on (ADR 0013). Outside them it can't be installed: no Folio update helps. */
    val minSdk: Int? = null,
    val maxSdk: Int? = null,
    /** The newest Folio its author tried. Advice only: past it the page says so, and Get stays. */
    val maxFolio: FolioMinor? = null,
    /** One package at a time per slot, named after one of its own [requiredFeatures]. */
    val slot: Capability? = null,
    /** Installed versions this package doesn't work with, in the [depends] syntax: those have to be updated first. */
    val breaks: List<PackageRelation> = emptyList(),
) {
    data class Author(val name: LocalizedText, val url: String?)

    /** `aiAssisted`: the AI tools that helped make the package, and an optional note on what they did. */
    data class AiAssisted(val tools: List<String>, val note: LocalizedText?)

    /** Capabilities this package configures that [available] doesn't include ("Needs a newer Folio"). */
    fun missingCapabilities(available: Set<Capability>): Set<Capability> = requiredFeatures - available

    companion object {
        const val MAX_CHARS = 64 * 1024
        const val MAX_NAME = 40
        const val MAX_RELATIONS = 32
        const val MAX_AI_TOOLS = 5
        const val MAX_AI_TOOL = 60
        /** Folio's own minSdk: a package can't ask for an Android older than Folio runs on. */
        const val MIN_SDK = 31
        const val MAX_SDK = 99
        private val NOT_BLANK = Regex("\\S")
        val ID = Regex("^[a-z][a-z0-9-]*(\\.[a-z0-9][a-z0-9-]*)+\\z")
        const val MAX_ID = 120
        private val ANDROID_PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+\\z")

        private val KNOWN = setOf(
            "\$schema", "format", "id", "name", "version", "author", "minFolio", "section", "kind", "permissions",
            "screens", "depends", "conflicts", "icon", "depiction", "license", "description", "aiAssisted", "via", "provides", "requires",
            "minSdk", "maxSdk", "maxFolio", "slot", "breaks",
        )

        fun parse(text: String): ParseResult<PackageManifest> {
            val p = Problems()
            val json = parseStrictObject(text, MAX_CHARS, p) ?: return p.result { error("unreachable") }
            val f = Fields(json, "", p, KNOWN)
            f.anyString("\$schema")
            f.formatOne()
            val id = f.string("id", true, ID, MAX_ID, "must be lowercase reverse-DNS, like dev.maya.sunset-icons")
            val name = f.text("name", true, MAX_NAME)
            val version = readVersion(f, "version")
            val author = f.obj("author", true, setOf("name", "url"))?.let { a ->
                val authorName = a.text("name", true)
                val url = a.string("url", false, HTTPS_URL, MAX_URL, "must be an https:// link")
                authorName?.let { Author(it, url) }
            }
            val minFolio = f.string("minFolio", true)?.let { raw ->
                FolioVersion.parse(raw) ?: null.also { p.errors += "minFolio must look like 0.7.0" }
            }
            val section = f.id("section", true, Section::from)
            val kinds = f.ids("kind", true, PackageKind::from, minItems = 1)
            val permissions = f.ids("permissions", true, PackagePermission::from)
            val screens = f.ids("screens", false, Screen::from, minItems = 1)
            val depends = readRelations(f, "depends")
            val conflicts = readRelations(f, "conflicts")
            val icon = f.string("icon", false, SAFE_PATH, MAX_PATH, "must be a relative path inside the package")
            val depiction = f.string("depiction", false, SAFE_PATH, MAX_PATH, "must be a relative path inside the package")
            val license = f.string("license", false, maxLength = 64)
            val description = f.text("description", false)
            val aiAssisted = f.obj("aiAssisted", false, setOf("tools", "note"))?.let { a ->
                val tools = a.strings("tools", true, minItems = 1, maxItems = MAX_AI_TOOLS, unique = true) { value, at ->
                    a.checkString(value, at, NOT_BLANK, MAX_AI_TOOL, "must name a tool")
                }
                val note = a.text("note", false)
                tools?.let { AiAssisted(it, note) }
            }
            val via = readVia(f)
            val provides = f.ids("provides", false, Provides::from)
            val features = f.obj("requires", false, setOf("features"))?.ids("features", false, Capability::from)
            if (kinds?.contains(PackageKind.EXTERNAL_APP) == true && !f.has("via")) p.errors += "via is required for externalApp packages"
            // ADR 0013's limits. folio-pkg also requires minFolio 0.7.0 with any of them, so no older Folio skips them.
            val sdks = MIN_SDK.toLong()..MAX_SDK.toLong()
            val minSdk = f.long("minSdk", false, sdks)?.toInt()
            val maxSdk = f.long("maxSdk", false, sdks)?.toInt()
            if (minSdk != null && maxSdk != null && minSdk > maxSdk) p.errors += "minSdk must not be above maxSdk"
            val maxFolio = f.string("maxFolio", false, maxLength = 20)?.let { raw ->
                FolioMinor.parse(raw) ?: null.also { p.errors += "maxFolio must look like 0.7" }
            }
            if (maxFolio != null && minFolio != null && !maxFolio.covers(minFolio)) p.errors += "maxFolio must not be older than minFolio"
            val slot = f.id("slot", false, Capability::from)
            if (slot != null && features != null && slot !in features) p.errors += "slot must be one of requires.features"
            if (slot != null && features == null) p.errors += "slot must be one of requires.features"
            val breaks = readRelations(f, "breaks")
            return p.result {
                PackageManifest(
                    id = id!!, name = name!!, version = version!!, author = author!!, minFolio = minFolio!!, section = section!!,
                    kinds = kinds!!.toSet(), permissions = permissions!!.toSet(),
                    screens = screens?.toSet() ?: Screen.entries.toSet(),
                    depends = depends.orEmpty(), conflicts = conflicts.orEmpty(), icon = icon, depiction = depiction,
                    license = license, description = description, aiAssisted = aiAssisted, via = via.orEmpty(),
                    provides = provides.orEmpty().toSet(), requiredFeatures = features.orEmpty().toSet(),
                    minSdk = minSdk, maxSdk = maxSdk, maxFolio = maxFolio, slot = slot, breaks = breaks.orEmpty(),
                )
            }
        }

        internal fun readVersion(f: Fields, key: String): DebVersion? = f.string(key, true, maxLength = DebVersion.MAX_LENGTH)?.let { raw ->
            DebVersion.parse(raw) ?: null.also { f.problems.errors += "${f.where(key)} must be a version like 1.2.0 or 2:1.0~beta1-1" }
        }

        private fun readRelations(f: Fields, key: String): List<PackageRelation>? {
            val raw = f.strings(key, false, maxItems = MAX_RELATIONS) { value, at ->
                value.takeIf { PackageRelation.parse(it) != null } ?: null.also { f.problems.errors += "$at must look like \"dev.maya.icons\" or \"dev.maya.icons (>= 1.2)\"" }
            }
            return raw?.map { PackageRelation.parse(it)!! }
        }

        private fun readVia(f: Fields): List<ExternalSource>? {
            val items = f.objects("via", false) ?: return null
            val out = items.mapNotNull { (index, json) ->
                val v = f.child(json, "${f.where("via")}[$index]", setOf("store", "id", "repoUrl"))
                val store = v.id("store", true, ExternalSource.Store::from)
                val appId = v.string("id", false, ANDROID_PACKAGE, 200, "must be an Android package name")
                val repoUrl = v.string("repoUrl", false, HTTPS_URL, MAX_URL, "must be an https:// link")
                when (store) {
                    ExternalSource.Store.PLAY_STORE, ExternalSource.Store.FDROID ->
                        if (!v.has("id")) f.problems.errors += "${v.where("id")} is required for ${store.id}"
                    ExternalSource.Store.OBTAINIUM ->
                        if (!v.has("repoUrl")) f.problems.errors += "${v.where("repoUrl")} is required for obtainium"
                    null -> Unit
                }
                store?.let { ExternalSource(it, appId, repoUrl) }
            }
            return out
        }
    }
}

/** `dev.maya.icons` or `dev.maya.icons (>= 1.2)`, with dpkg's operators. */
data class PackageRelation(val id: String, val op: Op?, val version: DebVersion?) {
    enum class Op(val token: String) { EARLIER("<<"), EARLIER_OR_EQUAL("<="), EQUAL("="), LATER_OR_EQUAL(">="), LATER(">>") }

    fun matches(candidate: DebVersion): Boolean {
        val wanted = version ?: return true
        val c = candidate.compareTo(wanted)
        return when (op) {
            Op.EARLIER -> c < 0
            Op.EARLIER_OR_EQUAL -> c <= 0
            Op.EQUAL -> c == 0
            Op.LATER_OR_EQUAL -> c >= 0
            Op.LATER -> c > 0
            null -> true
        }
    }

    override fun toString() = if (op == null) id else "$id (${op.token} $version)"

    companion object {
        const val MAX_LENGTH = 200
        private val PATTERN = Regex("^([a-z][a-z0-9.-]*)(?: \\((<<|<=|=|>=|>>) ([^)]+)\\))?\\z")

        fun parse(text: String): PackageRelation? {
            if (text.length > MAX_LENGTH) return null
            val match = PATTERN.find(text) ?: return null
            val (id, token, rawVersion) = match.destructured
            if (token.isEmpty()) return PackageRelation(id, null, null)
            val version = DebVersion.parse(rawVersion) ?: return null
            return PackageRelation(id, Op.entries.first { it.token == token }, version)
        }
    }
}

/** A Folio release number (`minFolio`): three numbers. */
data class FolioVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<FolioVersion> {
    override fun compareTo(other: FolioVersion) = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("^([0-9]{1,9})\\.([0-9]{1,9})\\.([0-9]{1,9})\\z")

        /** Null unless [text] is exactly three numbers of at most 9 digits each. */
        fun parse(text: String): FolioVersion? {
            val (a, b, c) = PATTERN.find(text)?.destructured ?: return null
            return FolioVersion(a.toInt(), b.toInt(), c.toInt())
        }

        /** Reads an app `versionName` such as `0.7.0`, `0.7.0-beta2` or `0.7.0.dev`, using its first three numbers. */
        fun fromAppVersion(versionName: String): FolioVersion? =
            Regex("^[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}(?![0-9])").find(versionName)?.value?.let(::parse)
    }
}

/** A Folio release line (`maxFolio`): `0.7` stands for every 0.7.x. */
data class FolioMinor(val major: Int, val minor: Int) : Comparable<FolioMinor> {
    override fun compareTo(other: FolioMinor) = compareValuesBy(this, other, { it.major }, { it.minor })
    override fun toString() = "$major.$minor"

    /** Whether [version] is on this line or an older one. */
    fun covers(version: FolioVersion) = of(version) <= this

    companion object {
        private val PATTERN = Regex("^([0-9]{1,9})\\.([0-9]{1,9})\\z")

        fun parse(text: String): FolioMinor? {
            val (a, b) = PATTERN.find(text)?.destructured ?: return null
            return FolioMinor(a.toInt(), b.toInt())
        }

        fun of(version: FolioVersion) = FolioMinor(version.major, version.minor)
    }
}

enum class Section(val id: String) {
    THEMES("themes"), TWEAKS("tweaks"), LAYOUTS("layouts"), WALLPAPERS("wallpapers"), SCRIPTS("scripts");

    companion object {
        fun from(id: String) = entries.firstOrNull { it.id == id }
    }
}

enum class PackageKind(
    val id: String,
    /**
     * The tweak this kind plugs into, when it's an add-on rather than something that stands alone: a page effect is
     * one more effect for Flipbook, the way a Cylinder script is one more effect for Cylinder. The kind says it, not
     * each package, so an author can't leave it out or point it at the wrong id, and a new host is one line here.
     */
    val hostTweak: String? = null,
    /**
     * Folio reads the kind but doesn't install it yet, so a package of it is refused ("needs a newer Folio") rather
     * than installed as a record that does nothing. `script` stays reserved on purpose: a script is code, a higher
     * trust class than JSON and images, and ADR 0004's sandbox has never been built.
     */
    val reserved: Boolean = false,
) {
    THEME("theme"), LAYOUT_PRESET("layoutPreset"), WALLPAPER("wallpaper"), ICON_PACK_LINK("iconPackLink"),
    TWEAK_BUNDLE("tweakBundle"), SETTINGS_SCHEMA("settingsSchema", reserved = true), SCRIPT("script", reserved = true),
    EXTERNAL_APP("externalApp"),
    PAGE_EFFECT("pageEffect", hostTweak = "pageEffects");

    companion object {
        fun from(id: String) = entries.firstOrNull { it.id == id }
    }
}

/**
 * What a package may change. Packages never get Android permissions; these are Folio's rules, and the privacy label is
 * built from them. [label] is the label wording, or null for appearance-only permissions.
 */
enum class PackagePermission(val id: String, val label: String?) {
    HOME_APPEARANCE("home.appearance", null),
    HOME_LAYOUT("home.layout", "Changes your Home layout"),
    ICONS("icons", null),
    WALLPAPER("wallpaper", null),
    TWEAKS("tweaks", "Changes Folio tweaks"),
    ISLAND_MESSAGES("island.messages", "Shows island messages"),
    FOCUS_SWITCH("focus.switch", "Switches Home Modes"),
    FOLD_STATE("fold.state", "Reads fold state"),
    TIME("time", "Runs on a schedule"),
    APPS_OPEN("apps.open", "Opens apps");

    companion object {
        fun from(id: String) = entries.firstOrNull { it.id == id }
    }
}

enum class Screen(val id: String) {
    COVER("cover"), INNER("inner");

    companion object {
        fun from(id: String) = entries.firstOrNull { it.id == id }
    }
}

enum class Provides(val id: String) {
    ICON_PACK("iconPack"), WALLPAPERS("wallpapers"), WIDGETS("widgets"), FOLIO_THEME("folioTheme");

    companion object {
        fun from(id: String) = entries.firstOrNull { it.id == id }
    }
}

/**
 * The access a package needs on this phone, the same three Android grants as ADR 0008's A0 to A2 (Standard needs nothing,
 * then notification access, then Folio's accessibility service), ordered from least to most. Nobody declares it: it
 * follows from a package's `requires.features` ([packageAccess]). It stops at Accessibility on purpose: Shizuku, root and
 * hooks are never a package tier; a package that ever needs one asks for it as its own, separate requirement.
 */
enum class PackageAccess { STANDARD, NOTIFICATIONS, ACCESSIBILITY }

/**
 * Folio capabilities a package can configure (`requires.features`). The table in format-v1.md says when each arrived.
 * [needs] is the access the capability can't work without; [betterWith] is access that makes it do more (it works
 * without it). Every capability names [needs], so a new one can't arrive without saying what it needs.
 */
enum class Capability(val id: String, val needs: PackageAccess, val betterWith: PackageAccess? = null) {
    THEME("theme", PackageAccess.STANDARD),
    HOME_LAYOUT("home.layout", PackageAccess.STANDARD),
    WALLPAPER("wallpaper", PackageAccess.STANDARD),
    ICONS("icons", PackageAccess.STANDARD),
    ICON_PACKS("icons.packs", PackageAccess.STANDARD),
    /** Cabinet: shortcuts without notification access, the app's notifications and music with it (AppPanel.kt). */
    APP_PANELS("tweaks.appPanels", PackageAccess.STANDARD, betterWith = PackageAccess.NOTIFICATIONS),
    DOCK_MAGNIFY("tweaks.dockMagnify", PackageAccess.STANDARD),
    /** Roll Call: Notification Center only says "allow notification access" without it (TopPanels.kt). */
    NOTIFICATION_APP_ROW("tweaks.notificationAppRow", PackageAccess.NOTIFICATIONS),
    /** Palette tints notifications, which Folio only has with notification access. */
    TINT_NOTIFICATIONS("tweaks.tintNotifications", PackageAccess.NOTIFICATIONS),
    /** Afterglow: what's playing comes only from the notification listener's media sessions (Island.kt). */
    TINT_MEDIA("tweaks.tintMedia", PackageAccess.NOTIFICATIONS),
    PAGE_EFFECTS("tweaks.pageEffects", PackageAccess.STANDARD),
    FOLD_TRANSITION("tweaks.foldTransition", PackageAccess.STANDARD),
    /** On Home without accessibility, over other apps with it. Nothing consumes it yet, so this is the plan, not a fact. */
    ISLAND_MESSAGES("island.messages", PackageAccess.STANDARD, betterWith = PackageAccess.ACCESSIBILITY),
    /** Silencing needs Do Not Disturb access, a Folio permission prompt rather than a tier, so it isn't counted here. */
    FOCUS_MODES("focus.modes", PackageAccess.STANDARD),
    SETTINGS_PAGES("settings.pages", PackageAccess.STANDARD),
    /** A reserved kind that is refused today. When scripts exist, their access is per action, not per capability. */
    SCRIPTS("scripts", PackageAccess.STANDARD);

    companion object {
        fun from(id: String) = entries.firstOrNull { it.id == id }
    }
}

/** The access a package with these features needs: the most any of them needs, and Standard for none. */
fun packageAccess(features: Set<Capability>): PackageAccess = features.maxOfOrNull { it.needs } ?: PackageAccess.STANDARD

/** Access that would let a package with these features do more than [packageAccess] allows, or null if none would. */
fun packageBetterWith(features: Set<Capability>): PackageAccess? =
    features.mapNotNull { it.betterWith }.maxOrNull()?.takeIf { it > packageAccess(features) }

/** Where an `externalApp` package installs from. */
data class ExternalSource(val store: Store, val appId: String?, val repoUrl: String?) {
    enum class Store(val id: String) {
        PLAY_STORE("playStore"), FDROID("fdroid"), OBTAINIUM("obtainium");

        companion object {
            fun from(id: String) = entries.firstOrNull { it.id == id }
        }
    }
}
