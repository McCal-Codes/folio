package com.mccal.folio

import org.json.JSONObject

/**
 * What an app icon does on a swipe up, a swipe down and a double tap, chosen by the person. A gesture left null keeps
 * today's behavior. An [ActionRef] with an id this build does not know is kept and written back as it was, so a backup
 * from a newer Folio loses nothing when it passes through this one.
 */
data class IconActions(val up: ActionRef? = null, val down: ActionRef? = null, val double: ActionRef? = null) {
    val isEmpty get() = up == null && down == null && double == null

    /** Only the gestures that are set, so an icon with none writes nothing. */
    fun toJson(): JSONObject = JSONObject().apply {
        up?.let { put("up", it.toJson()) }
        down?.let { put("down", it.toJson()) }
        double?.let { put("double", it.toJson()) }
    }

    companion object {
        /** A damaged gesture is dropped on its own; the others stay. */
        fun fromJson(json: JSONObject?): IconActions = IconActions(
            up = ActionRef.fromJson(json?.optJSONObject("up")),
            down = ActionRef.fromJson(json?.optJSONObject("down")),
            double = ActionRef.fromJson(json?.optJSONObject("double")),
        )
    }
}

/** One icon's actions set or cleared; an icon with no gesture left has no entry at all. */
fun editIconActions(map: Map<String, IconActions>, id: String, actions: IconActions): Map<String, IconActions> =
    if (actions.isEmpty) map - id else map + (id to actions)

fun iconActionsToJson(map: Map<String, IconActions>): JSONObject =
    JSONObject().also { o -> map.forEach { (id, actions) -> if (!actions.isEmpty) o.put(id, actions.toJson()) } }

/** Saved actions back into a map; a damaged entry or one with no gesture left is dropped. */
fun iconActionsFromJson(o: JSONObject?): Map<String, IconActions> =
    o?.keys()?.asSequence()?.mapNotNull { id -> IconActions.fromJson(o.optJSONObject(id)).takeUnless { it.isEmpty }?.let { id to it } }?.toMap().orEmpty()
