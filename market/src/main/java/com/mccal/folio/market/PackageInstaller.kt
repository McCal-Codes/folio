package com.mccal.folio.market

import org.json.JSONArray
import org.json.JSONObject

/**
 * What the launcher lets a package change. `:market` never touches the Home screen itself, so Folio Lite can reuse the
 * store later with a different host.
 */
interface PackageHost {
    /** What this build of Folio can do. A package needing anything else is never applied. */
    val capabilities: Set<Capability>

    /**
     * Applies [change] and returns a snapshot of what it replaced, which Folio keeps until the user dismisses Undo.
     * Throwing means nothing was applied.
     */
    fun apply(change: PackageChange): String

    /** Puts back what [apply] replaced. Called for Undo, for Remove, and when a later step of an install fails. */
    fun restore(change: PackageChange, snapshot: String)
}

/** A package Folio has installed, and what it replaced. */
data class InstalledPackage(
    val id: String,
    val version: DebVersion,
    val name: String,
    val origin: Origin,
    val sourceUrl: String?,
    val installedAt: Long,
    /** What each change replaced, in the order it was applied, so Undo and Remove can walk back. */
    val snapshots: List<String>,
    val enabled: Boolean = true,
    /** Why Safe Mode turned this package off, when it did. */
    val disabledReason: String? = null,
) {
    enum class Origin(val id: String) {
        FOLIO_SOURCE("folio-source"), FILE("file"), PLAY_ICON_PACK("play-icon-pack"), LAUNCHER_IMPORT("launcher-import");

        companion object {
            fun from(id: String) = entries.firstOrNull { it.id == id } ?: FOLIO_SOURCE
        }
    }
}

sealed interface InstallResult {
    /** Applied. [undo] puts everything back, and stays valid until the user dismisses the message. */
    data class Installed(val installed: InstalledPackage, val replaced: InstalledPackage?, val notes: List<String>) : InstallResult

    /** The package is fine but this Folio can't run it. */
    data class NeedsNewerFolio(val missing: List<String>) : InstallResult

    data class Failed(val reason: Reason, val message: String) : InstallResult

    enum class Reason { HASH, SIZE, ARCHIVE, MANIFEST, MISMATCH, CONFLICT, DEPENDS, APPLY, REVOKED, NEEDS_NEWER, AUTHOR }
}

/**
 * Installs, removes and undoes packages (Phase 3).
 *
 * The order is download, hash, open, read, check, stage, apply, record — and every step happens before anything on the
 * Home screen changes. If applying the third change of a package fails, the first two are put back, so a package is
 * never half applied. The previous version is kept until Undo is dismissed.
 */
class PackageInstaller(
    private val store: InstalledStore,
    private val host: PackageHost,
    private val safeMode: PackageSafeMode = PackageSafeMode(store.keyValue),
    /** Who owns which package id. Signatures are only checked for packages that came from a source. */
    private val authors: AuthorTrust = AuthorTrust(store.keyValue),
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
    /** This build's release number, for a package's `minFolio`. Null skips that check, which only a test does. */
    private val folioVersion: FolioVersion? = null,
) {
    /**
     * Reads [bytes] as a package and applies it. [expected] is the index entry it came from, when there was one: its
     * hash, size, id and version all have to match what's inside the file (T1, and the "no bait and switch" rule).
     */
    fun install(
        bytes: ByteArray,
        expected: IndexPackage? = null,
        origin: InstalledPackage.Origin = InstalledPackage.Origin.FOLIO_SOURCE,
        sourceUrl: String? = null,
    ): InstallResult {
        expected?.size?.let { if (bytes.size != it) return InstallResult.Failed(InstallResult.Reason.SIZE, "that download isn't the size the source listed") }
        expected?.sha256?.let {
            if (sha256Hex(bytes) != it) return InstallResult.Failed(InstallResult.Reason.HASH, "that download doesn't match the source's checksum")
        }
        // Who wrote it, which is a different question from who handed it over. Checked here, against the bytes that
        // actually arrived, so a mirror can carry a package but can't alter it or publish under its author's name.
        var pinning: Pair<String, String>? = null
        if (expected?.sha256 != null) {
            when (val author = authors.check(expected.id, expected.version, expected.sha256, expected.signedBy)) {
                is AuthorTrust.Result.FirstTime -> pinning = expected.id to author.keyBase64
                else -> if (!author.installable) {
                    return InstallResult.Failed(InstallResult.Reason.AUTHOR, author.message)
                }
            }
        }
        val pkg = when (val read = read(bytes)) {
            is ReadResult.Ok -> read.pkg
            is ReadResult.NeedsNewerFolio -> return InstallResult.NeedsNewerFolio(read.missing)
            is ReadResult.Failed -> return InstallResult.Failed(read.reason, read.message)
        }
        // A package shared as a file has no index to carry a signature, so it carries its own. The same rules
        // apply: a name that already belongs to another key is refused, whichever way the package arrived.
        if (expected?.signedBy == null) {
            when (val author = authors.checkFiles(pkg.id, pkg.version, pkg.files)) {
                is AuthorTrust.Result.FirstTime -> pinning = pkg.id to author.keyBase64
                else -> if (!author.installable) {
                    return InstallResult.Failed(InstallResult.Reason.AUTHOR, author.message)
                }
            }
        }
        // What the user was shown comes from the index's copy of the manifest, so the package has to be what that
        // copy said: a mirror could otherwise label a tweak bundle "Appearance only" and have it applied anyway.
        val shown = expected?.manifest
        if (shown != null && (shown.kinds != pkg.manifest.kinds || shown.permissions != pkg.manifest.permissions)) {
            return InstallResult.Failed(InstallResult.Reason.MISMATCH, "that package isn't the one the source listed")
        }
        if (expected != null && (expected.id != pkg.id || expected.version != pkg.version)) {
            return InstallResult.Failed(InstallResult.Reason.MISMATCH, "that package isn't the one the source listed")
        }
        return apply(pkg, origin, sourceUrl, pinning = pinning)
    }

    private fun apply(
        pkg: FolioPackage,
        origin: InstalledPackage.Origin,
        sourceUrl: String?,
        builtIn: Boolean = false,
        /** The id and author key to remember, once this package is really on. */
        pinning: Pair<String, String>? = null,
    ): InstallResult {
        val missing = pkg.manifest.missingCapabilities(host.capabilities).map { it.id } +
            pkg.changes.flatMap { it.capabilities }.filterNot { it in host.capabilities }.map { it.id }
        if (missing.isNotEmpty()) return InstallResult.NeedsNewerFolio(missing.distinct())
        // Capabilities catch a package that names something this build hasn't got; `minFolio` catches one that needs a
        // later Folio's behaviour without naming anything. Both mean the same thing to the user.
        val needs = pkg.manifest.minFolio
        if (!builtIn && folioVersion != null && needs > folioVersion) {
            return InstallResult.NeedsNewerFolio(listOf("Folio $needs"))
        }
        val already = store.installed()
        already.firstOrNull { it.id != pkg.id && pkg.manifest.conflicts.any { c -> c.id == it.id && c.matches(it.version) } }
            ?.let { return InstallResult.Failed(InstallResult.Reason.CONFLICT, "that package replaces ${it.name}") }
        // Dependencies have to be installed first; the store's queue sheet offers to add them (Phase 5).
        val missingDepends = pkg.manifest.depends.filterNot { needed ->
            already.any { it.id == needed.id && it.enabled && needed.matches(it.version) }
        }
        if (missingDepends.isNotEmpty()) {
            return InstallResult.Failed(InstallResult.Reason.DEPENDS, "that package needs ${missingDepends.joinToString { it.toString() }} first")
        }

        // Applying starts here. Safe Mode watches from now until the marker is cleared, so a crash while a package is
        // being applied turns that package off instead of leaving Home unusable.
        val replaced = store.find(pkg.id)
        safeMode.beginChange(pkg.id)
        val snapshots = mutableListOf<String>()
        try {
            // A version Safe Mode turned off has already had its changes taken off Home, so undoing them again
            // would put back what Home looked like before it, over whatever has happened since.
            replaced?.takeIf { it.enabled }?.let { undoChanges(it) }
            for (change in pkg.changes) snapshots += host.apply(change)
        } catch (e: Exception) {
            // Put back everything this install had already changed, newest first.
            pkg.changes.take(snapshots.size).zip(snapshots).reversed().forEach { (change, snapshot) ->
                runCatching { host.restore(change, snapshot) }
            }
            // An update undoes the version it replaces before applying the new one, so put that version back too:
            // a failed update leaves the phone exactly as it was, with the old version still working.
            replaced?.let { old ->
                val previous = store.changesFor(old.id, old.version)?.takeIf { old.enabled }
                if (previous != null) {
                    val again = mutableListOf<String>()
                    runCatching { previous.forEach { again += host.apply(it) } }
                    store.put(old.copy(snapshots = again), previous)
                }
            }
            safeMode.endChange()
            return InstallResult.Failed(InstallResult.Reason.APPLY, "Folio couldn't apply that package, so nothing changed")
        }
        val installed = InstalledPackage(
            id = pkg.id,
            version = pkg.version,
            name = pkg.manifest.name.english,
            origin = origin,
            sourceUrl = sourceUrl,
            installedAt = clock(),
            snapshots = snapshots,
        )
        // Nothing stays applied that Folio couldn't write down. A package on the Home screen and missing from the
        // list is one nobody can remove, so a store that won't write means the whole install is put back.
        if (!store.put(installed, changes = pkg.changes)) {
            pkg.changes.zip(snapshots).reversed().forEach { (change, snapshot) ->
                runCatching { host.restore(change, snapshot) }
            }
            safeMode.endChange()
            return InstallResult.Failed(InstallResult.Reason.APPLY, "Folio couldn't save that package, so nothing changed")
        }
        // Only once the package is really on: a key remembered for an install that failed would own the id anyway.
        pinning?.let { (id, key) -> authors.remember(id, key) }
        safeMode.endChange()
        return InstallResult.Installed(installed, replaced, pkg.notes)
    }

    /**
     * Installs a package that ships inside Folio, from files rather than a download: the built-in themes and tweaks in
     * `docs/sdk/source`. Everything after opening the archive is the same.
     */
    fun installBuiltIn(files: Map<String, ByteArray>): InstallResult {
        val pkg = when (val read = readFiles(files)) {
            is ReadResult.Ok -> read.pkg
            is ReadResult.NeedsNewerFolio -> return InstallResult.NeedsNewerFolio(read.missing)
            is ReadResult.Failed -> return InstallResult.Failed(read.reason, read.message)
        }
        // A package that ships inside this APK can't need a later Folio than the one it's part of, whatever its
        // manifest says: Folio's own packages name the release they're written for, and the version number is only
        // bumped when that release goes out.
        return apply(pkg, InstalledPackage.Origin.FOLIO_SOURCE, sourceUrl = null, builtIn = true)
    }

    /**
     * Turns a package off the way Safe Mode does: its changes come off the Home screen, and its record stays so it
     * can be put back with [enable].
     *
     * Taking the changes off is the point. Marking the record and leaving a theme applied turns nothing off — Folio
     * would start, crash on the same thing, and say it had already dealt with it.
     */
    fun disable(id: String, reason: String): Boolean {
        val installed = store.find(id)?.takeIf { it.enabled } ?: return false
        safeMode.beginChange(id)
        undoChanges(installed)
        store.setEnabled(id, enabled = false, reason = reason)
        safeMode.endChange()
        return true
    }

    /**
     * Puts a package that was turned off back on: Try Again, after Safe Mode.
     *
     * The changes are the ones recorded when it was installed, applied again from where Home is now, so the
     * snapshots are new. If applying fails halfway it goes back off rather than being left half on.
     */
    fun enable(id: String): Boolean {
        val installed = store.find(id)?.takeIf { !it.enabled } ?: return false
        val changes = store.changesFor(installed.id, installed.version) ?: return false
        safeMode.beginChange(id)
        val snapshots = mutableListOf<String>()
        try {
            for (change in changes) snapshots += host.apply(change)
        } catch (e: Exception) {
            changes.take(snapshots.size).zip(snapshots).reversed().forEach { (change, snapshot) ->
                runCatching { host.restore(change, snapshot) }
            }
            safeMode.endChange()
            return false
        }
        store.setEnabled(id, enabled = true, snapshots = snapshots)
        safeMode.endChange()
        return true
    }

    /** Takes a package off, putting back whatever it replaced. */
    fun remove(id: String): Boolean {
        val installed = store.find(id) ?: return false
        safeMode.beginChange(id)
        // One that Safe Mode turned off has already had its changes taken off; undoing them again would restore
        // whatever Home looked like before it, over whatever the user has done since.
        if (installed.enabled) undoChanges(installed)
        store.remove(id)
        safeMode.endChange()
        return true
    }

    /** Undo right after an install: remove what went on, and put the previous version back if there was one. */
    fun undo(result: InstallResult.Installed): Boolean {
        val previous = result.replaced
        // The previous version's own changes were recorded when it was installed. Read them before anything is taken
        // off: without them there is nothing to put back, and removing first would leave the user with neither
        // version.
        val changes = previous?.let { store.changesFor(it.id, it.version) }
        if (previous != null && changes == null) return false
        remove(result.installed.id)
        if (previous == null || changes == null) return true
        safeMode.beginChange(previous.id)
        val snapshots = mutableListOf<String>()
        // A version that was turned off goes back into the list still turned off: putting its changes on would
        // leave the record saying one thing and the Home screen showing another.
        if (previous.enabled) runCatching { changes.forEach { snapshots += host.apply(it) } }
        store.put(previous.copy(snapshots = snapshots, installedAt = clock()), changes)
        safeMode.endChange()
        return true
    }

    /** What [restoreBackup] put back. */
    data class Restore(
        /** Packages whose changes are on Home again. */
        val on: List<InstalledPackage>,
        /** Packages that were off when the backup was taken, and are off here too, with their reason kept. */
        val off: List<InstalledPackage>,
        /** Packages this Folio couldn't put back on. They are in the list, turned off, so they can still be removed. */
        val failed: List<InstalledPackage>,
    )

    /**
     * Puts back the packages a layout backup carried, around the launcher putting its own layout back.
     *
     * A backup records what each package *changed*, never what Home looked like when it changed: its snapshots were
     * taken on the phone it came from and describe nothing here, so nothing is replayed from them. Every package goes
     * in turned off, and the ones that were on when the backup was taken have their recorded changes applied again
     * from where this phone is now - which is what [enable] does for Try Again, and why a package Safe Mode had
     * turned off comes back off, with its changes not applied and its reason kept.
     *
     * The order is the point, because a package's changes and the layout being restored are the same launcher:
     *  1. this phone's packages come off first, while the snapshots saying what they replaced still describe it;
     *  2. [putLayoutBack] restores the layout, onto a Home with no package on it;
     *  3. the backup's packages go on over that, so what each one records replacing is what is really underneath.
     *
     * [offReason] is what the user is told about a package this Folio couldn't put back on. Returns null, leaving
     * this phone's own packages alone, when the backup's packages can't be read; [putLayoutBack] runs either way.
     */
    fun restoreBackup(text: String, offReason: String, putLayoutBack: () -> Unit): Restore? {
        val backup = store.readBackup(text)
        if (backup == null) {
            putLayoutBack()
            return null
        }
        // Newest first, for the same reason a package undoes its own changes in reverse: each snapshot is the Home
        // screen from before that package, so taking an older one off first would throw away everything stacked on
        // top of it and then put it back.
        // This phone's own packages, kept so a restore that fails after they come off can put them back. Without it
        // the caller said "this phone's were left as they are" about packages that were already gone.
        val mine = store.readBackup(store.export())
        store.installed().reversed().forEach { remove(it.id) }
        putLayoutBack()
        try {
            store.restore(backup)
        } catch (e: Exception) {
            mine?.let { own ->
                runCatching { store.restore(own) }
                own.wasOn.forEach { runCatching { enable(it) } }
            }
            throw e
        }
        val on = mutableListOf<InstalledPackage>()
        val failed = mutableListOf<InstalledPackage>()
        for (id in backup.wasOn) {
            val record = store.find(id) ?: continue
            // A backup can come from a Folio that does more than this one. A package needing something this build
            // hasn't got is left off rather than failing halfway through being applied, as at install.
            val changes = store.changesFor(record.id, record.version)
            val supported = changes != null && changes.flatMap { it.capabilities }.all { it in host.capabilities }
            if (supported && enable(id)) {
                store.find(id)?.let { on += it }
            } else {
                store.setEnabled(id, enabled = false, reason = offReason)
                store.find(id)?.let { failed += it }
            }
        }
        val couldNot = failed.mapTo(mutableSetOf()) { it.id }
        return Restore(on, store.installed().filter { !it.enabled && it.id !in couldNot }, failed)
    }

    private fun undoChanges(installed: InstalledPackage) {
        val changes = store.changesFor(installed.id, installed.version) ?: return
        changes.zip(installed.snapshots).reversed().forEach { (change, snapshot) ->
            runCatching { host.restore(change, snapshot) }
        }
    }

    sealed interface ReadResult {
        data class Ok(val pkg: FolioPackage) : ReadResult
        data class NeedsNewerFolio(val missing: List<String>) : ReadResult
        data class Failed(val reason: InstallResult.Reason, val message: String) : ReadResult
    }

    /** Opens a package and reads everything in it, without applying anything. Used by the install sheet's preview. */
    fun read(bytes: ByteArray): ReadResult = when (val archive = PackageArchive.read(bytes)) {
        is PackageArchive.Result.Ok -> readFiles(archive.files)
        is PackageArchive.Result.Rejected -> ReadResult.Failed(InstallResult.Reason.ARCHIVE, archive.reason)
    }

    /** The same, for a package whose files Folio already has: the built-in ones. */
    fun readFiles(files: Map<String, ByteArray>): ReadResult {
        if (PackageArchive.MANIFEST !in files) return ReadResult.Failed(InstallResult.Reason.ARCHIVE, "a package needs a manifest.json")
        val notes = mutableListOf<String>()
        val manifest = when (val parsed = PackageManifest.parse(files.getValue(PackageArchive.MANIFEST).decodeToString())) {
            is ParseResult.Ok -> parsed.value.also { notes += parsed.ignored }
            is ParseResult.Unsupported -> return ReadResult.NeedsNewerFolio(parsed.needs)
            is ParseResult.Invalid -> return ReadResult.Failed(InstallResult.Reason.MANIFEST, parsed.errors.first())
        }
        val depiction = manifest.depiction?.let { path ->
            files[path]?.let { bytes ->
                when (val parsed = Depiction.parse(bytes.decodeToString())) {
                    is ParseResult.Ok -> parsed.value.also { notes += parsed.ignored }
                    // A page that can't be read is a shame, not a reason to refuse the package.
                    else -> null.also { notes += "$path couldn't be read" }
                }
            }
        }
        val changes = mutableListOf<PackageChange>()
        for (kind in manifest.kinds) {
            val change = when (kind) {
                PackageKind.THEME -> files["theme.json"]?.let { PackageChange.Theme(it.decodeToString()) }
                    ?: return ReadResult.Failed(InstallResult.Reason.MANIFEST, "that package says it has a theme but has no theme.json")
                PackageKind.TWEAK_BUNDLE -> {
                    val text = files["tweaks.json"]?.decodeToString()
                        ?: return ReadResult.Failed(InstallResult.Reason.MANIFEST, "that package says it has tweaks but has no tweaks.json")
                    when (val parsed = TweakBundle.parse(text)) {
                        is ParseResult.Ok -> PackageChange.Tweaks(parsed.value)
                        is ParseResult.Unsupported -> return ReadResult.NeedsNewerFolio(parsed.needs)
                        is ParseResult.Invalid -> return ReadResult.Failed(InstallResult.Reason.MANIFEST, "tweaks.json: ${parsed.errors.first()}")
                    }
                }
                PackageKind.LAYOUT_PRESET -> files["layout.json"]?.let { PackageChange.Layout(it.decodeToString()) }
                    ?: return ReadResult.Failed(InstallResult.Reason.MANIFEST, "that package says it has a layout but has no layout.json")
                // A picture, named as one: the archive allows other file types under assets/, and handing one of
                // those to the wallpaper as image bytes is a guess about a name an author chose.
                PackageKind.WALLPAPER -> {
                    // Not "the first image under assets/": a depiction's hero and screenshots live there too, and a zip
                    // lists them in whatever order the author's tool wrote. The wallpaper is the image the page does
                    // not spend, and if there are several, the biggest, because a wallpaper is the largest picture
                    // in its own package.
                    val spent = buildSet {
                        depiction?.blocks?.forEach { block ->
                            when (block) {
                                is DepictionBlock.Hero -> add(block.image)
                                is DepictionBlock.Screenshots -> addAll(block.images)
                                else -> Unit
                            }
                        }
                    }
                    val image = files.entries
                        .filter { it.key.startsWith("assets/") && it.key.substringAfterLast('.').lowercase() in IMAGE_TYPES }
                        .filterNot { it.key in spent }
                        .maxByOrNull { it.value.size }
                        ?: return ReadResult.Failed(InstallResult.Reason.MANIFEST, "that package says it has a wallpaper but has no image of its own")
                    // The credit is read here because here is where the manifest is open. A host applying this
                    // change is handed the change and nothing else, so an artist and a license that are not
                    // carried along cannot be shown beside the picture later.
                    val artist = manifest.author.name.english
                    val license = manifest.license.orEmpty()
                    if (artist.isBlank() || license.isBlank()) return ReadResult.Failed(
                        InstallResult.Reason.MANIFEST,
                        "a wallpaper has to say who made it and what it is licensed under",
                    )
                    PackageChange.Wallpaper(
                        path = image.key, bytes = image.value, id = manifest.id,
                        title = manifest.name.english, artist = artist, license = license,
                        detail = manifest.description?.english.orEmpty(),
                        source = manifest.author.url.orEmpty(),
                    )
                }
                PackageKind.ICON_PACK_LINK -> {
                    val json = files["iconpack.json"]?.decodeToString()
                        ?: return ReadResult.Failed(InstallResult.Reason.MANIFEST, "that package says it links an icon pack but has no iconpack.json")
                    val name = readIconPackName(json)
                        ?: return ReadResult.Failed(InstallResult.Reason.MANIFEST, "iconpack.json needs the icon pack's package name")
                    PackageChange.IconPack(name)
                }
                // Reserved kinds: readable, but nothing is applied until the phase that builds them.
                PackageKind.SETTINGS_SCHEMA, PackageKind.SCRIPT, PackageKind.EXTERNAL_APP -> null
            }
            change?.let(changes::add)
        }
        val assets = files.filterKeys { it.startsWith("assets/") }
        return ReadResult.Ok(FolioPackage(manifest, depiction, changes, assets, notes.take(Problems.MAX_REPORTED), files))
    }

    private fun readIconPackName(text: String): String? {
        val problems = Problems()
        val json = parseStrictObject(text, 4096, problems) ?: return null
        val name = Fields(json, "", problems, setOf("\$schema", "format", "package")).string("package", true, ANDROID_PACKAGE, 200)
        return if (problems.errors.isEmpty()) name else null
    }

    private companion object {
        val ANDROID_PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+\\z")

        /** What a wallpaper picture can be, matching the archive's own list of allowed types. */
        val IMAGE_TYPES = setOf("png", "webp", "jpg", "jpeg")
    }
}

/**
 * Safe Mode for one package (T9), on top of the launcher's own. A marker is written while a package is being changed;
 * if Folio crashes twice within a minute of that, the package that was being changed starts turned off, with its
 * settings kept, so the user can Try Again, Remove it or look at the details.
 */
class PackageSafeMode(private val store: KeyValueStore, private val clock: () -> Long = { System.currentTimeMillis() / 1000 }) {
    fun beginChange(id: String) = store.set(KEY, JSONObject().put("id", id).put("at", clock()).toString())

    fun endChange() = store.set(KEY, null)

    /**
     * Called when Folio starts after a crash. Returns the package to turn off, if a change was in flight recently and
     * this is the second crash.
     */
    fun noteCrash(): String? {
        val marker = store.get(KEY)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        val id = marker.optString("id").takeIf { it.isNotEmpty() } ?: return null
        if (clock() - marker.optLong("at") > WINDOW_SECONDS) {
            store.set(KEY, null)
            return null
        }
        val crashes = marker.optInt("crashes") + 1
        if (crashes < 2) {
            store.set(KEY, marker.put("crashes", crashes).toString())
            return null
        }
        store.set(KEY, null)
        return id
    }

    private companion object {
        const val KEY = "market:safe-mode"
        const val WINDOW_SECONDS = 60L
    }
}

/** What's installed, what each package changed, and what it replaced. */
class InstalledStore(internal val keyValue: KeyValueStore) {
    fun installed(): List<InstalledPackage> = read().values.toList()

    fun find(id: String): InstalledPackage? = read()[id]

    /**
     * Records a package and what it changed. False means nothing could be written down, which matters: a change Folio
     * can't remember is one the user can't undo or remove.
     */
    fun put(installed: InstalledPackage, changes: List<PackageChange>): Boolean {
        // The changes go first: a record pointing at changes that aren't there is worse than no record.
        if (!keyValue.set(changesKey(installed.id, installed.version), encodeChanges(changes))) return false
        val all = read().toMutableMap()
        all[installed.id] = installed
        return write(all)
    }

    fun remove(id: String) {
        val all = read().toMutableMap()
        // What that version changed goes with it. Kept, these pile up for ever; the only reader is Undo, which
        // runs before the record is dropped.
        all.remove(id)?.let { keyValue.set(changesKey(it.id, it.version), null) }
        write(all)
    }

    /**
     * Marks a package off or on again, keeping its record and what it changed. Only the record: putting the changes
     * back or taking them off is [PackageInstaller.disable] and [PackageInstaller.enable], because that touches Home.
     */
    fun setEnabled(id: String, enabled: Boolean, reason: String? = null, snapshots: List<String>? = null) {
        val all = read().toMutableMap()
        all[id]?.let {
            all[id] = it.copy(enabled = enabled, disabledReason = if (enabled) null else reason,
                snapshots = snapshots ?: it.snapshots)
        }
        write(all)
    }

    /**
     * Everything about installed packages, for Folio's layout backup: the records and what each package changed, so a
     * restored backup can put them back without downloading anything again.
     *
     * The snapshots are deliberately left behind. A snapshot is what one change replaced *on this phone*, and it
     * describes nothing on another one; putting a package back means applying its changes again from wherever that
     * phone is, which is [PackageInstaller.restoreBackup]'s job. Unreadable data is left out rather than thrown, as
     * everywhere else here.
     */
    fun export(): String {
        val stored = runCatching { JSONArray(keyValue.get(KEY) ?: "[]") }.getOrDefault(JSONArray())
        val packages = JSONArray()
        val changes = JSONObject()
        for (i in 0 until stored.length()) {
            val json = stored.optJSONObject(i) ?: continue
            val key = "${json.optString("id")}@${json.optString("version")}"
            json.remove("snapshots")
            packages.put(json)
            keyValue.get(changesKey(key))?.let { text -> runCatching { changes.put(key, JSONArray(text)) } }
        }
        return JSONObject().put("format", 1).put("packages", packages).put("changes", changes).toString()
    }

    /** A backup's packages, read but not written: nothing on this phone has been touched yet. */
    class Backup internal constructor(
        /** Every package the backup carries, on or off, as it was on the phone the backup came from. */
        val records: List<InstalledPackage>,
        /** What each of them changed, keyed `id@version` exactly as [export] wrote it. */
        internal val changes: Map<String, String>,
    ) {
        /** The ids that were on when the backup was taken. */
        val wasOn: List<String> = records.filter { it.enabled }.map { it.id }
    }

    /** Reads what [export] wrote, and writes nothing. Null means Folio can't read it, and this phone is untouched. */
    fun readBackup(text: String): Backup? {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (json.optInt("format") != 1) return null
        // One record per id, as the store itself keeps: two records for one package would have the installer turn
        // it on and then, finding it already on, write it off again with the list and Home no longer agreeing.
        val records = parse(json.optJSONArray("packages") ?: return null).distinctBy { it.id }.take(MAX_BACKUP_PACKAGES)
        val changes = json.optJSONObject("changes") ?: JSONObject()
        // Only the keys its own records account for. A backup says what it carries; it doesn't get to name keys of
        // its own in Folio's store.
        val wanted = records.mapTo(mutableSetOf()) { "${it.id}@${it.version}" }
        return Backup(
            records,
            changes.keys().asSequence().filter { it in wanted }
                .mapNotNull { key -> changes.optJSONArray(key)?.let { key to it.toString() } }.toMap(),
        )
    }

    /**
     * Writes a backup's packages: every one of them turned off, with no snapshots, because whatever they replaced
     * they replaced on another phone. Turning the right ones back on is [PackageInstaller.restoreBackup], because
     * that puts changes on Home and only the installer does that.
     */
    fun restore(backup: Backup) {
        // The changes go first, as in [put]: a record pointing at changes that aren't there is worse than no record.
        for ((key, text) in backup.changes) keyValue.set(changesKey(key), text)
        write(backup.records.associate { it.id to it.copy(enabled = false, snapshots = emptyList()) })
    }

    fun changesFor(id: String, version: DebVersion): List<PackageChange>? =
        keyValue.get(changesKey(id, version))?.let(::decodeChanges)

    private fun changesKey(id: String, version: DebVersion) = changesKey("$id@$version")

    private fun changesKey(key: String) = "installed:changes:$key"

    private fun read(): Map<String, InstalledPackage> {
        val text = keyValue.get(KEY) ?: return emptyMap()
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyMap()
        return parse(array).associateBy { it.id }
    }

    /** The records in [array], skipping any Folio can't read. Used for the store's own list and for a backup's. */
    private fun parse(array: JSONArray): List<InstalledPackage> =
        (0 until array.length()).mapNotNull { i ->
            val json = array.optJSONObject(i) ?: return@mapNotNull null
            val version = DebVersion.parse(json.optString("version")) ?: return@mapNotNull null
            InstalledPackage(
                id = json.optString("id"),
                version = version,
                name = json.optString("name"),
                origin = InstalledPackage.Origin.from(json.optString("origin")),
                sourceUrl = json.optString("sourceUrl").takeIf { it.isNotEmpty() },
                installedAt = json.optLong("installedAt"),
                snapshots = json.optJSONArray("snapshots")?.let { a -> (0 until a.length()).map(a::optString) }.orEmpty(),
                enabled = json.optBoolean("enabled", true),
                disabledReason = json.optString("disabledReason").takeIf { it.isNotEmpty() },
            ).takeIf { it.id.isNotEmpty() }
        }

    private fun write(all: Map<String, InstalledPackage>): Boolean {
        val array = JSONArray()
        for (p in all.values) {
            array.put(
                JSONObject()
                    .put("id", p.id).put("version", p.version.text).put("name", p.name)
                    .put("origin", p.origin.id).put("sourceUrl", p.sourceUrl).put("installedAt", p.installedAt)
                    .put("snapshots", JSONArray(p.snapshots)).put("enabled", p.enabled).put("disabledReason", p.disabledReason),
            )
        }
        return keyValue.set(KEY, array.toString())
    }

    // Changes are stored as data, so Undo and Remove work after a restart without keeping the package file around.
    private fun encodeChanges(changes: List<PackageChange>): String {
        val array = JSONArray()
        for (change in changes) {
            val json = JSONObject()
            when (change) {
                is PackageChange.Theme -> json.put("kind", "theme").put("json", change.json)
                is PackageChange.Layout -> json.put("kind", "layout").put("json", change.json)
                is PackageChange.IconPack -> json.put("kind", "iconPack").put("package", change.packageName)
                // Deliberately without the image. A record is a description of what a package changed, not a
                // second copy of it (STA-11). Base64 of a picture is 1.33x the picture, and the picture is already
                // on disk where the host put it, so writing it here again cost a wallpaper 2.33x its own size for
                // nothing. What the host needs to put it back is the id and the credit, which are here.
                is PackageChange.Wallpaper -> json.put("kind", "wallpaper").put("path", change.path)
                    .put("id", change.id).put("title", change.title).put("artist", change.artist)
                    .put("license", change.license).put("detail", change.detail).put("source", change.source)
                    .put("sha256", change.pictureSha256)
                is PackageChange.Tweaks -> json.put("kind", "tweaks").put(
                    "tweaks",
                    JSONArray().apply {
                        change.bundle.tweaks.forEach {
                            put(JSONObject().put("id", it.id.id).put("enabled", it.enabled).put("cover", it.cover).put("inner", it.inner))
                        }
                    },
                )
            }
            array.put(json)
        }
        return array.toString()
    }

    private fun decodeChanges(text: String): List<PackageChange>? {
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return null
        return (0 until array.length()).mapNotNull { i ->
            val json = array.optJSONObject(i) ?: return@mapNotNull null
            when (json.optString("kind")) {
                "theme" -> PackageChange.Theme(json.optString("json"))
                "layout" -> PackageChange.Layout(json.optString("json"))
                "iconPack" -> PackageChange.IconPack(json.optString("package"))
                // A record written before wallpapers carried their credit decodes with blank fields, which
                // PackageChange.Wallpaper.credited reads as "do not show", so an old record cannot smuggle an
                // uncredited picture back in through a restore.
                "wallpaper" -> PackageChange.Wallpaper(
                    json.optString("path"),
                    // No bytes in a record, by design. An empty array means "the picture is wherever it was put",
                    // which is true on the phone that installed it and false on a phone restoring someone else's
                    // backup; the host is what tells those two apart.
                    ByteArray(0),
                    id = json.optString("id"), title = json.optString("title"), artist = json.optString("artist"),
                    license = json.optString("license"), detail = json.optString("detail"),
                    source = json.optString("source"), sha256 = json.optString("sha256"),
                )
                "tweaks" -> {
                    val list = json.optJSONArray("tweaks") ?: return@mapNotNull null
                    PackageChange.Tweaks(
                        TweakBundle(
                            (0 until list.length()).mapNotNull { k ->
                                val t = list.optJSONObject(k) ?: return@mapNotNull null
                                TweakId.from(t.optString("id"))?.let {
                                    TweakSetting(it, t.optBoolean("enabled"), t.optBoolean("cover", true), t.optBoolean("inner", true))
                                }
                            },
                        ),
                    )
                }
                else -> null
            }
        }
    }

    private companion object {
        const val KEY = "installed:packages"

        /** As many packages as a backup may carry, in the spirit of every other cap here. */
        const val MAX_BACKUP_PACKAGES = 200
    }
}
