package com.mccal.folio

import java.util.concurrent.ConcurrentHashMap

/**
 * Chinese app names spelled in pinyin, the way iOS searches and indexes them: "weixin" and "wx" find 微信, and the
 * App Library files it under W instead of under 微. Uses Android's own ICU data, so nothing is downloaded; where ICU
 * isn't there (plain JVM tests) names simply have no pinyin.
 */
internal object Pinyin {
    private val transliterator by lazy {
        runCatching { android.icu.text.Transliterator.getInstance("Han-Latin; Latin-ASCII; Any-Lower") }.getOrNull()
    }
    private val cache = ConcurrentHashMap<String, List<String>>()

    fun hasHan(text: String) = text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }

    /** "微信" → ["wei", "xin"]; empty when the text has no Chinese characters. */
    fun syllables(text: String): List<String> {
        if (!hasHan(text)) return emptyList()
        val latin = transliterator ?: return emptyList()
        val key = text.lowercase()
        return cache.getOrPut(key) {
            runCatching { latin.transliterate(key) }.getOrNull().orEmpty()
                .split(' ', '-', '.', '_', '·').filter { it.isNotEmpty() }
        }
    }

    /** The A–Z heading for a name: its first letter, or for a Chinese name the first letter of its pinyin. */
    fun heading(label: String): String {
        val first = label.firstOrNull() ?: return "#"
        if (Character.UnicodeScript.of(first.code) == Character.UnicodeScript.HAN)
            syllables(label).firstOrNull()?.firstOrNull()?.takeIf { it in 'a'..'z' }?.let { return it.uppercase() }
        return first.takeIf(Char::isLetter)?.uppercaseChar()?.toString() ?: "#"
    }

    /** Whether typed Latin letters spell part of the name ("weix", "xin") or its initials ("wx"). */
    fun matches(label: String, query: String): Boolean {
        val q = query.lowercase().filterNot(Char::isWhitespace)
        if (q.isEmpty() || q.any { it !in 'a'..'z' && !it.isDigit() }) return false
        val words = syllables(label).ifEmpty { return false }
        return words.joinToString("").contains(q) || words.joinToString("") { it.take(1) }.startsWith(q)
    }
}
