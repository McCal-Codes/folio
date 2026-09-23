package com.mccal.folio

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/**
 * The Supporters list in Settings: the people who backed Folio with $15 or more and said their name could be here.
 *
 * Kept the way the Roadmap is, in one file in Folio's repository, so a new name needs no release: a copy ships in the
 * app, and opening the page fetches the latest at most every few hours. A bad or oversized file is ignored in favour
 * of the last good one.
 *
 * Nothing about the reader is sent: this is a plain request for a public file, the same one every phone asks for.
 * A name goes in only when its owner has said yes, and comes out again the same way.
 */
internal object Supporters {
    const val URL = "https://raw.githubusercontent.com/McCal-Codes/folio/main/app/src/main/assets/supporters.json"
    private const val ASSET = "supporters.json"
    private const val CACHE = "supporters.json"
    private const val MAX_BYTES = 64 * 1024
    private const val REFRESH_MS = 6 * 60 * 60 * 1000L

    /** One person, as they asked to be written, and the month they backed Folio ("2026-09"). */
    data class Person(val name: String, val since: String?)
    data class Content(val note: String?, val people: List<Person>)

    fun parse(raw: String): Content? = runCatching {
        if (raw.length > MAX_BYTES) return null
        val json = JSONObject(raw)
        if (json.optInt("supporters", 0) != 1) return null
        val people = json.getJSONArray("people")
        Content(
            json.optString("note").takeIf { it.isNotBlank() }?.take(300),
            (0 until minOf(people.length(), 500)).mapNotNull { index ->
                val person = people.getJSONObject(index)
                val name = person.optString("name").trim().takeIf { it.isNotEmpty() }?.take(60) ?: return@mapNotNull null
                val since = person.optString("since").trim()
                    .takeIf { it.length == 7 && it[4] == '-' && it.removeRange(4, 5).all(Char::isDigit) }
                Person(name, since)
            },
        )
    }.getOrNull()

    /** The newest copy on the phone: the last good download, else the one shipped with Folio. */
    fun local(context: Context): Content? =
        runCatching { File(context.filesDir, CACHE).takeIf { it.exists() }?.readText()?.let(::parse) }.getOrNull()
            ?: runCatching { context.assets.open(ASSET).bufferedReader().use { it.readText() }.let(::parse) }.getOrNull()

    /** Fetches the latest list if the saved one is old. Returns it, or null when nothing new was saved. Off the main thread. */
    fun refresh(context: Context, now: Long = System.currentTimeMillis()): Content? {
        val cache = File(context.filesDir, CACHE)
        if (cache.exists() && now - cache.lastModified() < REFRESH_MS) return null
        return runCatching {
            val c = URL(URL).openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent", "Folio")  // HTTP header, never shown // english-only
            c.connectTimeout = 8_000; c.readTimeout = 10_000; c.useCaches = false
            try {
                if (c.responseCode != 200) return null
                val bytes = c.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (out.size() <= MAX_BYTES) {
                        val n = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - out.size()))
                        if (n < 0) break
                        out.write(buffer, 0, n)
                    }
                    out.toByteArray()
                }
                if (bytes.size > MAX_BYTES) return null
                val raw = String(bytes, Charsets.UTF_8)
                parse(raw)?.also { cache.writeText(raw) }
            } finally { c.disconnect() }
        }.getOrNull()
    }

    /** People newest first, grouped by the year they backed Folio; names with no month come last, under null. */
    fun byYear(content: Content): List<Pair<String?, List<Person>>> = content.people
        .groupBy { it.since?.take(4) }
        .toList()
        .sortedWith(compareByDescending<Pair<String?, List<Person>>> { it.first ?: "" }.thenBy { it.first == null })
        .map { (year, people) -> year to people.sortedByDescending { it.since.orEmpty() } }
}

/**
 * Settings › Supporters: the people who backed Folio and said their name could be here, newest year first.
 *
 * The list comes from [Supporters], so it grows without a release. An empty list is the honest state of a new list
 * rather than an error, and says so.
 */
@androidx.compose.runtime.Composable
internal fun SupportersPage() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var content by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Supporters.local(context)) }
    // Opening the page is when it looks for a newer list, at most every few hours.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Supporters.refresh(context) }?.let { content = it }
    }
    CardNote(androidx.compose.ui.res.stringResource(R.string.people_who_backed_folio_with_15_or_more))
    val people = content?.people.orEmpty()
    if (people.isEmpty()) {
        // An empty list is a card of its own, so it reads as the list rather than as another note.
        SheetGroup(androidx.compose.ui.Modifier.padding(top = 4.dp, bottom = 10.dp)) {
            androidx.compose.material3.Text(
                androidx.compose.ui.res.stringResource(R.string.no_names_yet_yours_could_be_the_first),
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = .55f),
                fontSize = 15.sp,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 18.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    } else {
        for ((year, group) in Supporters.byYear(content!!)) {
            SheetGroupLabel(year ?: androidx.compose.ui.res.stringResource(R.string.supporters))
            SheetGroup(androidx.compose.ui.Modifier.padding(bottom = 10.dp)) {
                group.forEachIndexed { index, person ->
                    if (index > 0) MenuDivider()
                    SupporterNameRow(person)
                }
            }
        }
    }
    CardNote(androidx.compose.ui.res.stringResource(R.string.backed_folio_and_want_your_name_here))
}

@androidx.compose.runtime.Composable
private fun SupporterNameRow(person: Supporters.Person) {
    androidx.compose.foundation.layout.Row(
        androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text(person.name, color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp,
            modifier = androidx.compose.ui.Modifier.weight(1f))
        person.since?.let { since ->
            androidx.compose.material3.Text(monthLabel(since), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .55f), fontSize = 14.sp)
        }
    }
}

/** "2026-09" as the reader's own short month, so the list reads the same way dates do everywhere else in Folio. */
internal fun monthLabel(yearMonth: String): String = runCatching {
    val (year, month) = yearMonth.split('-')
    java.time.YearMonth.of(year.toInt(), month.toInt())
        .format(java.time.format.DateTimeFormatter.ofPattern("LLL").withLocale(java.util.Locale.getDefault()))
}.getOrDefault(yearMonth)
