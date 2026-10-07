package com.mccal.folio

import android.content.Context
import org.json.JSONObject

/** One thing for a beta tester to try: what it is, where to find it, and the steps. [action] is "setup" for the optional new-setup item. */
internal data class TestItem(val id: String, val title: String, val where: String, val steps: List<String>, val action: String? = null)

/** The testers' list for one beta, read from `assets/what-to-test.json` so it can change between betas without code. */
internal data class TestList(val release: String, val items: List<TestItem>)

internal object WhatToTest {
    private const val ASSET = "what-to-test.json"
    private const val PREFS = "what_to_test"
    private const val MAX_ITEMS = 40
    private const val MAX_STEPS = 8

    /** True for a beta build ("0.6.9-beta.1") and for Folio Dev, where the list is for the person who builds it. */
    fun available(versionName: String, packageName: String): Boolean = "-beta" in versionName || packageName.endsWith(".dev")

    /** Reads the list. Null when it isn't JSON, names no release or has no usable item; a bad item is skipped, not fatal. */
    fun parse(raw: String): TestList? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val release = root.optString("release").takeIf { it.isNotBlank() } ?: return null
        val array = root.optJSONArray("items") ?: return null
        val items = (0 until minOf(array.length(), MAX_ITEMS)).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = o.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val steps = o.optJSONArray("steps")?.let { s -> (0 until minOf(s.length(), MAX_STEPS)).map { s.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
            if (steps.isEmpty()) return@mapNotNull null
            TestItem(id, title, o.optString("where"), steps, o.optString("action").takeIf { it.isNotBlank() })
        }.distinctBy { it.id }
        return items.takeIf { it.isNotEmpty() }?.let { TestList(release, it) }
    }

    fun load(context: Context): TestList? =
        runCatching { context.assets.open(ASSET).use { parse(it.readBytes().toString(Charsets.UTF_8)) } }.getOrNull()

    enum class Result(val key: String) { OK("ok"), BAD("bad"), SKIP("skip");
        companion object { fun from(key: String?) = entries.firstOrNull { it.key == key } }
    }

    /** How many items are answered (any answer counts), of the list's size. */
    fun answered(list: TestList, results: Map<String, Result>): Int = list.items.count { it.id in results }

    /** The ticks, kept on the phone per release (a new beta starts clean) and never sent anywhere. */
    class Results(context: Context, private val release: String) {
        private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        private fun key(id: String) = "$release|$id"
        fun all(list: TestList): Map<String, Result> = list.items.mapNotNull { item -> Result.from(prefs.getString(key(item.id), null))?.let { item.id to it } }.toMap()
        fun set(id: String, result: Result?) { prefs.edit().apply { if (result == null) remove(key(id)) else putString(key(id), result.key) }.apply() }
    }
}
