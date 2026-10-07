package com.mccal.folio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.LruCache
import android.util.Xml
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser

/**
 * Third-party icon packs in the common ADW/Nova format: an `appfilter` file mapping
 * `ComponentInfo{package/activity}` to drawable names.
 */
internal object IconPacks {
    data class Pack(val packageName: String, val label: String)

    // The actions and categories icon packs declare for other launchers (ADW, Nova, Lawnchair, Apex); a pack made for
    // any of them lists here. Each needs a matching <queries> entry in the manifest (Android 11 package visibility).
    private val PACK_INTENTS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.teslacoilsw.launcher.THEME",
        "ch.deletescape.lawnchair.ICONPACK").map(::Intent) +
        listOf("com.anddoes.launcher.THEME", "com.fede.launcher.THEME_ICONPACK").map { Intent(Intent.ACTION_MAIN).addCategory(it) }
    private val mappings = HashMap<String, Map<String, String>>()
    /** When a pack last failed to read, so a pack with no usable list isn't re-read for every icon. */
    private val failedAt = HashMap<String, Long>()
    private const val RETRY_AFTER_MS = 30_000L
    private val icons = LruCache<String, Bitmap>(160)

    fun installed(context: Context): List<Pack> {
        val pm = context.packageManager
        return PACK_INTENTS.flatMap { intent -> runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList()) }
            .map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }
            .map { Pack(it.packageName, pm.getApplicationLabel(it).toString()) }.sortedBy { it.label.lowercase() }
    }

    /** Pack icon for [component], or null when the pack has none. Call off the main thread. */
    fun icon(context: Context, pack: String, component: ComponentName, sizePx: Int): Bitmap? {
        val key = "$pack|${component.flattenToString()}"
        icons.get(key)?.let { return it }
        val name = mapping(context, pack)[component.flattenToString()] ?: return null
        return runCatching {
            val res = context.packageManager.getResourcesForApplication(pack)
            val id = res.getIdentifier(name, "drawable", pack).takeIf { it != 0 } ?: return null
            ResourcesCompat.getDrawable(res, id, null)?.toBitmap(sizePx, sizePx)
        }.getOrNull()?.also { icons.put(key, it) }
    }

    /**
     * A pack that couldn't be read is not remembered as empty for good, so a pack mid-update gets another try, but not
     * for every icon: it is left alone for [RETRY_AFTER_MS] first.
     */
    @Synchronized private fun mapping(context: Context, pack: String): Map<String, String> {
        mappings[pack]?.let { return it }
        val now = android.os.SystemClock.elapsedRealtime()
        failedAt[pack]?.let { if (now - it < RETRY_AFTER_MS) return emptyMap() }
        val read = runCatching { readMapping(context, pack) }.getOrNull()
        if (read == null) { failedAt[pack] = now; return emptyMap() }
        failedAt.remove(pack)
        mappings[pack] = read
        return read
    }

    private fun readMapping(context: Context, pack: String): Map<String, String> {
            val res = context.packageManager.getResourcesForApplication(pack)
            val xmlId = res.getIdentifier("appfilter", "xml", pack)
            val stream = if (xmlId == 0) res.assets.open("appfilter.xml") else null
            val parser: XmlPullParser = if (xmlId != 0) res.getXml(xmlId)
                else Xml.newPullParser().apply { setInput(stream, "UTF-8") }
            try {
            val map = HashMap<String, String>()
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG || parser.name != "item") continue
                val raw = parser.getAttributeValue(null, "component") ?: continue
                val drawable = parser.getAttributeValue(null, "drawable") ?: continue
                val inner = raw.substringAfter('{', "").substringBefore('}', "")
                val pkg = inner.substringBefore('/'); var cls = inner.substringAfter('/', "")
                if (pkg.isEmpty() || cls.isEmpty()) continue
                if (cls.startsWith(".")) cls = pkg + cls
                map["$pkg/$cls"] = drawable
            }
            return map
            } finally {
                stream?.close()
                (parser as? android.content.res.XmlResourceParser)?.close()
            }
    }

    fun clear() { icons.evictAll(); synchronized(this) { mappings.clear(); failedAt.clear() } }
}
