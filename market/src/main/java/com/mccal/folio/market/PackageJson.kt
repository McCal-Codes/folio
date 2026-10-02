package com.mccal.folio.market

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/** The outcome of reading a package file. */
sealed interface ParseResult<out T> {
    /** Read fine. [ignored] lists fields and blocks from newer format versions that this Folio skipped. */
    data class Ok<T>(val value: T, val ignored: List<String> = emptyList()) : ParseResult<T>

    /** Well formed, but uses kinds, permissions or capabilities this Folio doesn't know: "Needs a newer Folio". */
    data class Unsupported(val needs: List<String>) : ParseResult<Nothing>

    /** Broken or unsafe. Nothing from the file is used. */
    data class Invalid(val errors: List<String>) : ParseResult<Nothing>
}

/** Everything found while reading one file. Messages name the field, e.g. `blocks[2].images[0]`. */
internal class Problems {
    val errors = mutableListOf<String>()
    val unsupported = LinkedHashSet<String>()
    val ignored = mutableListOf<String>()

    fun <T> result(build: () -> T): ParseResult<T> = when {
        errors.isNotEmpty() -> ParseResult.Invalid(errors.take(MAX_REPORTED))
        unsupported.isNotEmpty() -> ParseResult.Unsupported(unsupported.toList())
        else -> ParseResult.Ok(build(), ignored.take(MAX_REPORTED))
    }

    companion object {
        const val MAX_REPORTED = 20
    }
}

/** Parses [text] as one strict JSON object, or reports why it can't. */
internal fun parseStrictObject(text: String, maxChars: Int, problems: Problems): JSONObject? {
    JsonGuard.check(text, maxChars)?.let { problems.errors += it; return null }
    return try {
        JSONObject(text)
    } catch (e: Exception) {
        problems.errors += "not valid JSON"
        null
    }
}

// Same patterns as the schemas, but ending in \z: Java's $ would also accept a trailing newline.
internal val SAFE_PATH = Regex("^(?!/)(?!.*\\.\\.)[A-Za-z0-9._/-]+\\z")
internal val HTTPS_URL = Regex("^https://\\S+\\z")
internal const val MAX_PATH = 200
internal const val MAX_URL = 2048
internal const val MAX_TEXT = 400

/**
 * Strict field reader in the `LayoutBackup` style. Every problem is recorded with its path; getters return null
 * instead of throwing, so one bad field never hides the rest of the report.
 */
internal class Fields(private val json: JSONObject, private val path: String, private val p: Problems, known: Set<String>) {
    init {
        for (key in json.keys()) if (key !in known) p.ignored += where(key.take(60))
    }

    fun where(key: String) = if (path.isEmpty()) key else "$path.$key"

    fun has(key: String) = json.has(key)

    private fun missing(key: String, required: Boolean): Boolean {
        if (json.has(key)) return false
        if (required) p.errors += "${where(key)} is required"
        return true
    }

    fun formatOne(key: String = "format") {
        if (missing(key, true)) return
        val value = json.opt(key)
        val one = (value as? Number)?.let { runCatching { BigDecimal(it.toString()).compareTo(BigDecimal.ONE) == 0 }.getOrDefault(false) } ?: false
        if (!one) {
            // A higher number is a newer format; anything else is broken.
            val newer = (value as? Number)?.let { runCatching { BigDecimal(it.toString()) > BigDecimal.ONE }.getOrDefault(false) } ?: false
            if (newer) p.unsupported += "format ${value}" else p.errors += "${where(key)} must be 1"
        }
    }

    fun string(key: String, required: Boolean, pattern: Regex? = null, maxLength: Int = MAX_TEXT, rule: String = "has the wrong form"): String? {
        if (missing(key, required)) return null
        val value = json.opt(key)
        if (value !is String) { p.errors += "${where(key)} must be a string"; return null }
        return checkString(value, where(key), pattern, maxLength, rule)
    }

    fun checkString(value: String, at: String, pattern: Regex?, maxLength: Int, rule: String): String? {
        val length = value.codePointCount(0, value.length)
        if (length > maxLength) { p.errors += "$at is longer than $maxLength characters"; return null }
        if (pattern != null && !pattern.containsMatchIn(value)) { p.errors += "$at $rule"; return null }
        return value
    }

    /** A JSON integer within [range]. `1.0` counts as an integer, like the schemas; `1.5` and `"1"` don't. */
    fun long(key: String, required: Boolean, range: LongRange): Long? {
        if (missing(key, required)) return null
        val number = json.opt(key) as? Number ?: return null.also { p.errors += "${where(key)} must be a whole number" }
        val exact = runCatching { BigDecimal(number.toString()).toBigIntegerExact() }.getOrNull()
            ?: return null.also { p.errors += "${where(key)} must be a whole number" }
        if (exact < BigDecimal(range.first.toString()).toBigInteger() || exact > BigDecimal(range.last.toString()).toBigInteger()) {
            return null.also { p.errors += "${where(key)} must be between ${range.first} and ${range.last}" }
        }
        return exact.toLong()
    }

    /** A string that is one of [values]. */
    fun choice(key: String, required: Boolean, values: List<String>): String? {
        val value = string(key, required, maxLength = 64) ?: return null
        return value.takeIf { it in values } ?: null.also { p.errors += "${where(key)} must be one of ${values.joinToString()}" }
    }

    /** A JSON number within [range]. `"1"` is not a number. */
    fun number(key: String, required: Boolean, range: ClosedFloatingPointRange<Double>): Double? {
        if (missing(key, required)) return null
        val value = (json.opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() }
            ?: return null.also { p.errors += "${where(key)} must be a number" }
        if (value !in range) return null.also { p.errors += "${where(key)} must be between ${range.start} and ${range.endInclusive}" }
        return value
    }

    fun bool(key: String, required: Boolean): Boolean? {
        if (missing(key, required)) return null
        return json.opt(key) as? Boolean ?: null.also { p.errors += "${where(key)} must be true or false" }
    }

    fun anyString(key: String) {
        if (json.has(key) && json.opt(key) !is String) p.errors += "${where(key)} must be a string"
    }

    fun text(key: String, required: Boolean, maxLength: Int = MAX_TEXT): LocalizedText? {
        if (missing(key, required)) return null
        return LocalizedText.read(json.opt(key), maxLength) { p.errors += "${where(key)} $it" }
    }

    fun obj(key: String, required: Boolean, known: Set<String>): Fields? {
        if (missing(key, required)) return null
        val value = json.opt(key)
        if (value !is JSONObject) { p.errors += "${where(key)} must be an object"; return null }
        return Fields(value, where(key), p, known)
    }

    fun array(key: String, required: Boolean, minItems: Int = 0, maxItems: Int = Int.MAX_VALUE): List<Any?>? {
        if (missing(key, required)) return null
        val value = json.opt(key)
        if (value !is JSONArray) { p.errors += "${where(key)} must be a list"; return null }
        if (value.length() < minItems) { p.errors += "${where(key)} needs at least $minItems item${if (minItems == 1) "" else "s"}"; return null }
        if (value.length() > maxItems) { p.errors += "${where(key)} has more than $maxItems items"; return null }
        return (0 until value.length()).map { value.opt(it) }
    }

    fun strings(key: String, required: Boolean, minItems: Int = 0, maxItems: Int = Int.MAX_VALUE, unique: Boolean = false,
                check: (String, String) -> String? = { s, _ -> s }): List<String>? {
        val items = array(key, required, minItems, maxItems) ?: return null
        val out = ArrayList<String>(items.size)
        var ok = true
        items.forEachIndexed { index, item ->
            val at = "${where(key)}[$index]"
            if (item !is String) { p.errors += "$at must be a string"; ok = false; return@forEachIndexed }
            check(item, at)?.let(out::add) ?: run { ok = false }
        }
        if (unique && items.filterIsInstance<String>().let { it.size != it.toSet().size }) { p.errors += "${where(key)} lists something twice"; ok = false }
        return if (ok) out else null
    }

    /**
     * A list of ids from a closed set. Ids this Folio doesn't know make the file [ParseResult.Unsupported]
     * (they come from a newer format), unless [unknownIsIgnored], which just skips them.
     */
    fun <E> ids(key: String, required: Boolean, lookup: (String) -> E?, minItems: Int = 0, unique: Boolean = true,
                unknownIsIgnored: Boolean = false): List<E>? {
        val raw = strings(key, required, minItems, unique = unique) ?: return null
        return raw.mapNotNull { id ->
            lookup(id) ?: null.also {
                if (unknownIsIgnored) p.ignored += "${where(key)}: ${id.take(60)}" else p.unsupported += "${where(key)}: ${id.take(60)}"
            }
        }
    }

    fun <E> id(key: String, required: Boolean, lookup: (String) -> E?): E? {
        val raw = string(key, required) ?: return null
        return lookup(raw) ?: null.also { p.unsupported += "${where(key)}: ${raw.take(60)}" }
    }

    fun objects(key: String, required: Boolean, minItems: Int = 0, maxItems: Int = Int.MAX_VALUE): List<Pair<Int, JSONObject>>? {
        val items = array(key, required, minItems, maxItems) ?: return null
        var ok = true
        val out = items.mapIndexedNotNull { index, item ->
            (item as? JSONObject)?.let { index to it } ?: null.also { p.errors += "${where(key)}[$index] must be an object"; ok = false }
        }
        return if (ok) out else null
    }

    fun child(json: JSONObject, at: String, known: Set<String>) = Fields(json, at, p, known)

    val problems: Problems get() = p
}
