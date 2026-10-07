package com.mccal.folio.market

import org.json.JSONArray
import org.json.JSONObject

/** One package Folio updated by itself: what, from which version to which, and when. [undone] once it was put back. */
data class AutoUpdate(val id: String, val name: String, val from: DebVersion, val to: DebVersion, val at: Long, val undone: Boolean = false)

/**
 * The last few automatic updates, newest first, kept so a silent update leaves a trail and can be undone. It holds only
 * what the list needs (names and versions, never the package), and trims itself to [max]. A damaged value reads as empty.
 */
class AutoUpdateLog(private val store: KeyValueStore, private val max: Int = 10) {
    fun recent(): List<AutoUpdate> {
        val array = runCatching { JSONArray(store.get(KEY) ?: return emptyList()) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                AutoUpdate(o.getString("id"), o.getString("name"), DebVersion.parse(o.getString("from"))!!, DebVersion.parse(o.getString("to"))!!,
                    o.getLong("at"), o.optBoolean("undone", false))
            }.getOrNull()
        }
    }

    fun record(update: AutoUpdate) = write(listOf(update) + recent())

    /** Marks the newest entry of [id] that went to [to] as undone. */
    fun markUndone(id: String, to: DebVersion) {
        var done = false
        write(recent().map { if (!done && it.id == id && it.to == to) { done = true; it.copy(undone = true) } else it })
    }

    private fun write(list: List<AutoUpdate>) {
        val array = JSONArray()
        list.take(max).forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.name).put("from", it.from.text).put("to", it.to.text).put("at", it.at).put("undone", it.undone))
        }
        store.set(KEY, array.toString())
    }

    private companion object { const val KEY = "market:auto-update-log" }
}
