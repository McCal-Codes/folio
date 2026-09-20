package com.mccal.folio

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import org.json.JSONObject

/**
 * How much room Big Buttons take at the bottom of the screen, for anything that has to stay clear of them.
 *
 * Big Buttons float above Android's own navigation, so the system tells no other app they are there — a keyboard
 * sizing itself from window insets alone will sit underneath them. Keyd reads this; anything else may too.
 *
 * It answers with one number and nothing else: no identifiers, no settings, nothing about the person using the phone.
 * Reading it is allowed without a permission because the answer is a height in dp, and a keyboard that had to ask for
 * a permission to avoid covering a button would be a worse trade than the one this makes.
 *
 * **Zero when the buttons have been lifted.** Dragging them up the screen is how someone moves them out of a
 * keyboard's way; reserving space for them after that would undo the very thing they did.
 */
class ButtonsRoomProvider : ContentProvider() {

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (uri.path?.trim('/') != PATH) return null
        val context = context ?: return null
        val cursor = MatrixCursor(arrayOf(COLUMN))
        cursor.addRow(arrayOf(roomDp(context)))
        return cursor
    }

    /** The strip the buttons occupy at the bottom, in dp, or zero when they aren't sitting in it. */
    private fun roomDp(context: Context): Float = runCatching {
        val prefs = context.getSharedPreferences(SettingKeys.PREFS, Context.MODE_PRIVATE)
        // The lift is saved per screen and orientation, so ask about the one the keyboard is opening on: taking the
        // largest of all four made a bar lifted on the inner screen report no room on the cover, where it still sits
        // at the bottom.
        val configuration = context.resources.configuration
        val lift = ButtonBarPosition.load(context, configuration.smallestScreenWidthDp >= 600,
            configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
        buttonsRoomDp(prefs.getString(SettingKeys.STATE, "{}") ?: "{}", lift)
    }.getOrDefault(0f)

    override fun getType(uri: Uri): String? =
        if (uri.path?.trim('/') == PATH) "vnd.android.cursor.item/vnd.com.mccal.folio.room" else null

    // Read-only, on purpose: nothing outside Folio decides where Folio's buttons are.
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val PATH = "room"
        const val COLUMN = "heightDp"

        /** Past this, the buttons have been moved on purpose rather than left where Folio put them. */
        const val LIFTED_DP = 8f
    }
}

/**
 * The decision itself, apart from Android so it can be checked: the buttons' height when they are sitting at the
 * bottom, and zero when they are off or have been lifted out of the way.
 */
internal fun buttonsRoomDp(stateJson: String, liftDp: Float): Float = runCatching {
    val state = JSONObject(stateJson)
    if (!state.optBoolean(SettingKeys.BUTTON_BAR, false)) return@runCatching 0f
    if (liftDp > ButtonsRoomProvider.LIFTED_DP) return@runCatching 0f
    state.optDouble(SettingKeys.BUTTON_BAR_HEIGHT, 52.0).toFloat().coerceIn(44f, 60f)
}.getOrDefault(0f)
