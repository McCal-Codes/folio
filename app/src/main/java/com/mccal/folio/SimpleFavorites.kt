package com.mccal.folio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import org.json.JSONArray
import org.json.JSONObject

/**
 * The people on Simple Home: who to show, and the number to dial. Added one at a time from the system contact picker
 * (ACTION_PICK grants read access to the one row picked, so Folio needs no Contacts permission) and dialed with
 * ACTION_DIAL (which only opens the dialer, so Folio needs no call permission either). Nothing here reads the contact
 * list, and nothing places a call: the person presses call themselves.
 */
data class SimpleFavorite(val name: String, val phone: String)

/** Simple Home shows at most this many people, so each row or tile stays large. */
const val SIMPLE_MAX_FAVORITES = 4

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

/** The dialer with [phone] filled in. */
internal fun dialIntent(phone: String): Intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))

/** The picker for one phone number, to hand to a result launcher. */
internal fun pickContactIntent(): Intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

/** The name and number of the row the contact picker returned, or null when it can't be read or has no number. */
internal fun readPickedContact(context: Context, uri: Uri): SimpleFavorite? = runCatching {
    context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) null
        else SimpleFavorite(cursor.getString(0).orEmpty(), cursor.getString(1).orEmpty()).takeIf { it.phone.isNotBlank() }
    }
}.getOrNull()
