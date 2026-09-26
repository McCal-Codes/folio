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
        private val NOT_BLANK = Regex("\\S")
        val ID = Regex("^[a-z][a-z0-9-]*(\\.[a-z0-9][a-z0-9-]*)+\\z")
        const val MAX_ID = 120
        private val ANDROID_PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+\\z")

        private val KNOWN = setOf(
            "\$schema", "format", "id", "name", "version", "author", "minFolio", "section", "kind", "permissions",
            "screens", "depends", "conflicts", "icon", "depiction", "license", "description", "aiAssisted", "via", "provides", "requires",
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
            return p.result {
                PackageManifest(
                    id = id!!, name = name!!, version = version!!, author = author!!, minFolio = minFolio!!, section = section!!,
                    kinds = kinds!!.toSet(), permissions = permissions!!.toSet(),
                    screens = screens?.toSet() ?: Screen.entries.toSet(),
                    depends = depends.orEmpty(), conflicts = conflicts.orEmpty(), icon = icon, depiction = depiction,
                    license = license, description = description, aiAssisted = aiAssisted, via = via.orEmpty(),
                    provides = provides.orEmpty().toSet(), requiredFeatures = features.orEmpty().toSet(),
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

enum class Section(val id: String) {
    THEMES("themes"), TWEAKS("tweaks"), LAYOUTS("layouts"), WALLPAPERS("wallpapers"), SCRIPTS("scripts");

    companion object {
        fun from(id: String) = entries.firstOrNull { it.id == id }
    }
}

enum class PackageKind(val id: String) {
    THEME("theme"), LAYOUT_PRESET("layoutPreset"), WALLPAPER("wallpaper"), ICON_PACK_LINK("iconPackLink"),
    TWEAK_BUNDLE("tweakBundle"), SETTINGS_SCHEMA("settingsSchema"), SCRIPT("script"), EXTERNAL_APP("externalApp");

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

/** Folio capabilities a package can configure (`requires.features`). The table in format-v1.md says when each arrived. */
enum class Capability(val id: String) {
    THEME("theme"), HOME_LAYOUT("home.layout"), WALLPAPER("wallpaper"), ICONS("icons"), ICON_PACKS("icons.packs"),
    APP_PANELS("tweaks.appPanels"), DOCK_MAGNIFY("tweaks.dockMagnify"), NOTIFICATION_APP_ROW("tweaks.notificationAppRow"),
    TINT_NOTIFICATIONS("tweaks.tintNotifications"), TINT_MEDIA("tweaks.tintMedia"),
    PAGE_EFFECTS("tweaks.pageEffects"),
    ISLAND_MESSAGES("island.messages"), FOCUS_MODES("focus.modes"), SETTINGS_PAGES("settings.pages"), SCRIPTS("scripts");

    companion object {
        fun from(id: String) = entries.firstOrNull { it.id == id }
    }
}

/** Where an `externalApp` package installs from. */
data class ExternalSource(val store: Store, val appId: String?, val repoUrl: String?) {
    enum class Store(val id: String) {
        PLAY_STORE("playStore"), FDROID("fdroid"), OBTAINIUM("obtainium");

        companion object {
            fun from(id: String) = entries.firstOrNull { it.id == id }
        }
    }
}
