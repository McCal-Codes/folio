package com.mccal.folio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

/** One person in Simple Mode's Call section: what to show, and the number to dial. */
data class SimpleFavorite(val name: String, val phone: String)

/** Simple Mode shows at most this many people, so each tile stays large. */
const val SIMPLE_MAX_FAVORITES = 4

/** [LauncherState.simpleFor]: the phone is for the person using it, or for someone they are helping. */
const val SIMPLE_ME = "ME"
const val SIMPLE_HELPING = "HELPING"

/** Saved as a JSON array of {name, phone}; JSON does the escaping, so commas and quotes in a name are safe. */
internal fun simpleFavoritesToJson(favorites: List<SimpleFavorite>): JSONArray =
    JSONArray().also { array -> favorites.forEach { array.put(JSONObject().put("name", it.name).put("phone", it.phone)) } }

/** The people saved by [simpleFavoritesToJson]; an entry without a number is dropped, and only the first [SIMPLE_MAX_FAVORITES] are kept. */
internal fun simpleFavoritesFromJson(array: JSONArray?): List<SimpleFavorite> =
    (0 until (array?.length() ?: 0)).mapNotNull { i ->
        array?.optJSONObject(i)?.let { SimpleFavorite(it.optString("name"), it.optString("phone")) }?.takeIf { it.phone.isNotBlank() }
    }.take(SIMPLE_MAX_FAVORITES)

/** [favorite] added to the end, or moved there if that number is already in the list; the list never passes [SIMPLE_MAX_FAVORITES]. */
internal fun List<SimpleFavorite>.withFavorite(favorite: SimpleFavorite): List<SimpleFavorite> =
    (filterNot { it.phone == favorite.phone } + favorite).takeLast(SIMPLE_MAX_FAVORITES)

/** The list with the person at [index] one place earlier; the first stays first, and a bad index changes nothing. */
internal fun List<SimpleFavorite>.movedUp(index: Int): List<SimpleFavorite> =
    if (index !in 1 until size) this else toMutableList().also { java.util.Collections.swap(it, index, index - 1) }

/** The list with the person at [index] one place later; the last stays last, and a bad index changes nothing. */
internal fun List<SimpleFavorite>.movedDown(index: Int): List<SimpleFavorite> =
    if (index !in 0 until size - 1) this else toMutableList().also { java.util.Collections.swap(it, index, index + 1) }

/** The list without the person at [index], the rest in order; a bad index changes nothing. */
internal fun List<SimpleFavorite>.removedAt(index: Int): List<SimpleFavorite> =
    if (index !in indices) this else filterIndexed { i, _ -> i != index }

/** The dialer with [phone] filled in. ACTION_DIAL only opens the dialer, so Folio needs no call permission. */
internal fun dialIntent(phone: String): Intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))

/**
 * Simple Mode's Home: a big clock, up to four people to call, and Messages, Camera and Photos as large tiles. It replaces
 * the usual Home while [LauncherState.simpleMode] is on. Holding the clock for two seconds asks to leave Simple Mode,
 * since Settings is not reachable from here.
 *
 * TODO(simple-mode): Help button, PIN lock (and a PIN-protected way out instead of the held clock), the travel tile,
 * the setup screen (who it is for, removing or reordering people) and the mockup's fixed Back/Home bar. The bar is
 * left out for now so a phone that already shows system navigation does not get two sets of buttons.
 */
@Composable
internal fun SimpleHome(state: LauncherState, model: LauncherModel) {
    val context = LocalContext.current
    val palette = LocalDuoPalette.current
    var asking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf(runCatching { !simplePrefs(context).getBoolean(HINT_SEEN, false) }.getOrDefault(false)) }
    val addPeople = stringResource(R.string.simple_add_people)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        readPickedContact(context, uri)?.let { model.setSimpleFavorites(state.simpleFavorites.withFavorite(it)) }
    }
    val pickContact: () -> Unit = {
        runCatching { picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
            .onFailure { IslandEvents.notice(context, context.getString(R.string.app_is_unavailable, addPeople)) }
    }
    val messages: () -> Unit = {
        val chosen = state.messagesApp?.let { context.packageManager.getLaunchIntentForPackage(it) }
        openOrNotice(context, chosen ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING), R.string.simple_messages)
    }
    val camera: () -> Unit = { openOrNotice(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), R.string.simple_camera) }
    val photos: () -> Unit = { openOrNotice(context, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY), R.string.simple_photos) }
    if (editing) {
        SimplePeopleEditor(state, model, onAdd = pickContact, onDone = { editing = false })
        return
    }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(palette.backgroundTop, palette.backgroundBottom)))
        .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.XL.dp),
        verticalArrangement = Arrangement.spacedBy(FolioSpace.XL.dp)) {
        if (hint) Text(stringResource(R.string.simple_leave_hint), color = Ink, fontSize = FolioSimpleType.HINT.sp,
            modifier = Modifier.fillMaxWidth().heightIn(min = FolioTouch.MIN.dp).clip(RoundedCornerShape(FolioRadius.CARD.dp)).background(Glass)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.simple_hint_dismiss)) {
                    hint = false; runCatching { simplePrefs(context).edit().putBoolean(HINT_SEEN, true).apply() }
                }.padding(FolioSpace.MEDIUM.dp))
        SimpleClock(onHold = { asking = true })
        SectionLabel(stringResource(R.string.simple_call))
        val people = state.simpleFavorites.take(SIMPLE_MAX_FAVORITES)
        // Each person, then an Add people tile while there is room; with nobody saved yet it is the only tile, full width.
        TileRows(people.size + if (people.size < SIMPLE_MAX_FAVORITES) 1 else 0) { index, modifier ->
            val person = people.getOrNull(index)
            if (person == null) SimpleTile(Icons.Rounded.Add, addPeople, modifier, pickContact)
            else PersonTile(person, modifier) { openOrNotice(context, dialIntent(person.phone), R.string.simple_call, person.name) }
        }
        TileRows(3) { index, modifier ->
            when (index) {
                0 -> SimpleTile(Icons.Rounded.Message, stringResource(R.string.simple_messages), modifier, messages)
                1 -> SimpleTile(Icons.Rounded.CameraAlt, stringResource(R.string.simple_camera), modifier, camera)
                else -> SimpleTile(Icons.Rounded.PhotoLibrary, stringResource(R.string.simple_photos), modifier, photos)
            }
        }
    }
    // TODO(simple-mode): when state.simpleFor == SIMPLE_HELPING, ask for the PIN before this dialog opens.
    if (asking) AlertDialog(onDismissRequest = { asking = false },
        title = { Text(stringResource(R.string.simple_leave_title)) },
        text = {
            Column {
                TextButton(onClick = {
                    asking = false
                    if (!model.setSimpleMode(false)) IslandEvents.notice(context, context.getString(R.string.simple_not_saved))
                }, modifier = Modifier.fillMaxWidth().heightIn(min = FolioTouch.MIN.dp)) { Text(stringResource(R.string.simple_leave_confirm), fontSize = FolioSimpleType.HINT.sp) }
                TextButton(onClick = { asking = false; editing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = FolioTouch.MIN.dp)) {
                    Text(stringResource(R.string.simple_edit_people), fontSize = FolioSimpleType.HINT.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { asking = false }, modifier = Modifier.heightIn(min = FolioTouch.MIN.dp)) { Text(stringResource(R.string.simple_leave_stay), fontSize = FolioSimpleType.HINT.sp) } })
}

private const val HINT_SEEN = "hint_seen"
private fun simplePrefs(context: Context) = context.getSharedPreferences("simple_mode", Context.MODE_PRIVATE)

/** Tiles in rows of two, each row as tall as its tallest tile; a last tile on its own takes the whole row. */
@Composable
internal fun TileRows(count: Int, tile: @Composable (Int, Modifier) -> Unit) {
    (0 until count).chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
            row.forEach { tile(it, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

/**
 * The time and date in the phone's own format and language, redrawn each minute. The time is sized to the width and
 * ignores the font scale, so it never wraps; holding it for two seconds runs [onHold] (with a buzz), and TalkBack gets
 * the same as a long press.
 */
@Composable
private fun SimpleClock(onHold: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L + 50L)
        }
    }
    val holdLabel = stringResource(R.string.simple_leave)
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = FolioTouch.MIN.dp)
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown()
                // Null only when the hold outlasted HOLD_MS: a lift or a cancelled touch (a scroll) inside it is not a hold.
                if (withTimeoutOrNull(HOLD_MS) { waitForUpOrCancellation(); true } == null) {
                    haptic.perform(FolioHaptic.PickedUp); onHold()
                    waitForUpOrCancellation()
                }
            }
        }
        .semantics { onLongClick(holdLabel) { onHold(); true } }) {
        // About 4.6 ems is the widest a 12-hour time gets; the cap is the token size.
        val clockSize = minOf(FolioSimpleType.CLOCK.toFloat(), maxWidth.value / 4.6f)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
                Text(DateFormat.getTimeFormat(context).format(Date(now)), color = Ink, fontSize = clockSize.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
            Text(DateUtils.formatDateTime(context, now, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE), color = Ink,
                fontSize = FolioSimpleType.DATE.sp, textAlign = TextAlign.Center)
        }
    }
}

private const val HOLD_MS = 2000L

@Composable
internal fun SectionLabel(text: String) =
    Text(text, color = Ink, fontSize = FolioSimpleType.SECTION.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = FolioSpace.TINY.dp))

/** A person to call: their initial in a circle and their name in large type. TalkBack reads "Call <name>" once, for the tile. */
@Composable
private fun PersonTile(person: SimpleFavorite, modifier: Modifier, onCall: () -> Unit) {
    val label = person.name.ifBlank { person.phone }
    val description = stringResource(R.string.simple_call_person, label)
    TileSurface(modifier.clearAndSetSemantics { contentDescription = description; role = Role.Button; onClick { onCall(); true } }, onCall) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(LocalAccent.current.fill), contentAlignment = Alignment.Center) {
            Text(label.substring(0, label.offsetByCodePoints(0, 1)).uppercase(), color = androidx.compose.ui.graphics.Color.White,
                fontSize = FolioSimpleType.INITIAL.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(label, color = Ink, fontSize = FolioSimpleType.LABEL.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A large tile with an icon above its label; the label is the accessible name, so the icon has no description of its own. */
@Composable
internal fun SimpleTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) =
    TileSurface(modifier, onClick) {
        Icon(icon, null, tint = Ink, modifier = Modifier.size(40.dp))
        Text(label, color = Ink, fontSize = FolioSimpleType.LABEL.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }

/** The shared tile body: at least 120 dp tall, the whole of it a button. */
@Composable
internal fun TileSurface(modifier: Modifier, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) =
    Column(modifier.heightIn(min = 120.dp).clip(RoundedCornerShape(FolioRadius.PANEL.dp)).background(Glass)
        .clickable(role = Role.Button, onClick = onClick).padding(FolioSpace.MEDIUM.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp, Alignment.CenterVertically), content = content)

private fun openOrNotice(context: Context, intent: Intent, label: Int, name: String? = null) {
    if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isFailure)
        IslandEvents.notice(context, context.getString(R.string.app_is_unavailable, name ?: context.getString(label)))
}

/** The name and number of the row the contact picker returned. ACTION_PICK grants read access to that one row, so READ_CONTACTS is not needed. */
internal fun readPickedContact(context: Context, uri: Uri): SimpleFavorite? = runCatching {
    context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) null
        else SimpleFavorite(cursor.getString(0).orEmpty(), cursor.getString(1).orEmpty()).takeIf { it.phone.isNotBlank() }
    }
}.getOrNull()
