package com.mccal.folio

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

private const val BACKGROUND_PREFS = "launcher_background"
private const val BACKGROUND_ENABLED = "photoEnabled"
private const val BACKGROUND_ID = "photoId"
private const val BACKGROUND_FILE = "launcher-background.jpg"
private const val MAX_BACKGROUND_EDGE = 2048

internal object LauncherBackgroundCache {
    @Volatile var bitmap: Bitmap? = null
    @Volatile var identity: String? = null
    val revision = mutableIntStateOf(0)
    private val listeners = mutableSetOf<() -> Unit>()

    fun changed(bitmap: Bitmap?, identity: String? = null) {
        this.bitmap = bitmap
        this.identity = identity
        revision.intValue++
        listeners.toList().forEach { it() }
    }
    fun listen(listener: () -> Unit) { listeners += listener }
    fun forget(listener: () -> Unit) { listeners -= listener }
}

/** The one photo the user picked. Written by [LauncherBackgroundController] and nothing else. */
internal fun launcherPhotoFile(context: Context) = File(context.filesDir, BACKGROUND_FILE)

/**
 * The file behind Home right now, whichever kind of picture it is.
 *
 * Callers that want "the background" want this. Callers that want the picked photo specifically, which means the
 * picker staging a new one and the code that clears it, want [launcherPhotoFile].
 */
internal fun launcherBackgroundFile(context: Context): File = when (val choice = backgroundChoice(context)) {
    BackgroundChoice.None -> launcherPhotoFile(context)
    BackgroundChoice.Photo -> launcherPhotoFile(context)
    is BackgroundChoice.Art -> BackgroundLibrary.artFile(context, choice.id)
}
internal fun launcherBackgroundPreferences(context: Context) =
    context.getSharedPreferences(BACKGROUND_PREFS, Context.MODE_PRIVATE)
internal fun launcherBackgroundEnabled(context: Context) = when (val choice = backgroundChoice(context)) {
    BackgroundChoice.None -> false
    BackgroundChoice.Photo -> launcherPhotoFile(context).isFile
    is BackgroundChoice.Art -> BackgroundLibrary.exists(context, choice.id)
}
/**
 * What the cached bitmap is a picture of, so a stale copy is never mistaken for the current one.
 *
 * A photo's identity is the id written when it was committed, which changes every time one is picked, even if the
 * new photo happens to be the same size as the old. A piece of art is identified by itself: the id names the file
 * and the file does not change under it, so switching art and switching back reuses the decode rather than
 * discarding it.
 */
internal fun launcherBackgroundIdentity(context: Context): String? {
    if (!launcherBackgroundEnabled(context)) return null
    return when (val choice = backgroundChoice(context)) {
        BackgroundChoice.None -> null
        is BackgroundChoice.Art -> choice.save()
        BackgroundChoice.Photo -> launcherBackgroundPreferences(context).getString(BACKGROUND_ID, null)
            ?: "legacy-${launcherPhotoFile(context).lastModified()}"
    }
}

/**
 * The background image, decoded once and shared.
 *
 * **Why the decode publishes what it decoded.** Three places want this picture on a cold start: Home, the wallpaper
 * preview in Settings, and [DuneWallpaperService] drawing the live wallpaper. They are one process, so they should
 * be looking at one bitmap. This used to return the cached copy on a hit but hand back a private copy on a miss
 * without ever filling the cache, so a cold start decoded the same file two or three times over and held every
 * copy. At the size a background is stored today that is roughly 12 MiB each, and it is the reason the stored
 * resolution cannot go up until this is fixed.
 *
 * The publish is guarded rather than unconditional: a decode takes long enough that the user may have picked a new
 * photo, or cleared it, while this one was running. [LauncherBackgroundCache.revision] moving, or the saved
 * identity no longer matching the one this decode was for, both mean the result is already stale, so it is returned
 * to the caller that asked for it but not installed as the shared copy.
 */
internal fun loadLauncherBackground(context: Context): Bitmap? {
    if (!launcherBackgroundEnabled(context)) return null
    val file = launcherBackgroundFile(context)
    val identity = launcherBackgroundIdentity(context)
    LauncherBackgroundCache.bitmap?.let {
        if (!it.isRecycled && LauncherBackgroundCache.identity == identity) return it
    }
    val startingRevision = LauncherBackgroundCache.revision.intValue
    val decoded = decodeBackground(context, file) ?: return null
    if (LauncherBackgroundCache.revision.intValue == startingRevision &&
        launcherBackgroundIdentity(context) == identity
    ) LauncherBackgroundCache.changed(decoded, identity)
    return decoded
}

/**
 * Reads the picture, from wherever this one lives.
 *
 * Art that shipped with Folio is an asset inside the APK and has no path on the filesystem, so it cannot go through
 * [BitmapFactory.decodeFile] the way a photo or an installed piece does. Everything after this point is the same
 * bitmap either way, which is the whole reason art and photos share one path.
 */
private fun decodeBackground(context: Context, file: File): Bitmap? {
    val choice = backgroundChoice(context)
    if (choice is BackgroundChoice.Art && BackgroundLibrary.isBuiltIn(choice.id)) {
        return runCatching {
            context.assets.open(BackgroundLibrary.assetPath(choice.id)).use(BitmapFactory::decodeStream)
        }.getOrNull()
    }
    return BitmapFactory.decodeFile(file.absolutePath)
}

internal fun cachedLauncherBackground(context: Context): Bitmap? {
    val identity = launcherBackgroundIdentity(context)
    return LauncherBackgroundCache.bitmap?.takeIf {
        identity != null && !it.isRecycled && LauncherBackgroundCache.identity == identity
    }
}

class LauncherBackgroundController(
    private val activity: ComponentActivity,
    private val onExternalResultChanged: (Boolean) -> Unit,
) : DefaultLifecycleObserver {
    var photoSelected by mutableStateOf(launcherBackgroundEnabled(activity))
        private set
    var loading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var successMessage by mutableStateOf<String?>(null)
        private set
    var pickerPending by mutableStateOf(false)
        private set
    var previewBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var previewPending by mutableStateOf(false)
        private set
    private var generation = 0
    private var preview: StagedBackground? = null
    private val prefs = launcherBackgroundPreferences(activity)
    private val picker = activity.activityResultRegistry.register(
        "duo.background.pick", activity, ActivityResultContracts.PickVisualMedia()
    ) callback@{ uri ->
        if (!pickerPending) return@callback
        if (uri == null) clearPickerPending() else beginPreview(uri, pendingOperation())
    }

    init {
        activity.lifecycle.addObserver(this)
        val previewPhase = previewPhase()
        val persistedPreviewFile = if (previewPhase == PHASE_READY) previewFile() else null
        cleanupStagedFiles(persistedPreviewFile)
        val pendingUri = runCatching { prefs.getString(PENDING_URI, null) }.getOrNull()
        pickerPending = runCatching { prefs.getBoolean(PICKER_PENDING, false) }.getOrDefault(false)
        val operation = pendingOperation()
        releaseStalePreviewGrant(if (previewPhase == PHASE_DECODING) operation else null)
        if (pickerPending && pendingUri != null && operation != null) {
            // Compatibility with controllers that were recreated after receiving a picker URI
            // but before separating picker and preview state.
            beginPreview(Uri.parse(pendingUri), operation)
        } else if (previewPhase == PHASE_DECODING) {
            pickerPending = false
            previewPending = true
            loading = true
            onExternalResultChanged(false)
            if (pendingUri != null && operation != null) decode(Uri.parse(pendingUri), operation)
            else failPreviewRestore(activity.getString(R.string.the_pending_photo_selection_could_not_be))
        } else if (previewPhase == PHASE_READY) {
            pickerPending = false
            previewPending = true
            loading = true
            onExternalResultChanged(false)
            prefs.edit().remove(PENDING_URI).apply()
            if (operation != null && persistedPreviewFile?.isFile == true) {
                restorePreview(persistedPreviewFile, operation)
            } else failPreviewRestore(activity.getString(R.string.the_pending_photo_preview_could_not_be_r))
        } else if (pickerPending) {
            onExternalResultChanged(true)
        } else onExternalResultChanged(false)
        if (photoSelected && LauncherBackgroundCache.bitmap == null) activity.lifecycleScope.launch {
            val startingRevision = LauncherBackgroundCache.revision.intValue
            val loaded = withContext(Dispatchers.IO) { loadLauncherBackground(activity.applicationContext) }
            if (loaded != null && startingRevision == LauncherBackgroundCache.revision.intValue &&
                launcherBackgroundEnabled(activity)) LauncherBackgroundCache.changed(loaded, launcherBackgroundIdentity(activity))
        }
    }

    fun choosePhoto() {
        discardPreview()
        releasePreviewGrant()
        cleanupStagedFiles()
        generation++; pickerPending = true; loading = false; errorMessage = null; successMessage = null
        prefs.edit().putBoolean(PICKER_PENDING, true).putString(PENDING_OPERATION, UUID.randomUUID().toString())
            .remove(PENDING_URI).remove(PREVIEW_PHASE).remove(PREVIEW_FILE).apply()
        onExternalResultChanged(true)
        try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        catch (error: Exception) {
            errorMessage = error.message ?: activity.getString(R.string.the_photo_picker_is_unavailable)
            clearPickerPending()
        }
    }

    fun reset() {
        generation++; pickerPending = false; loading = false
        discardPreview()
        releasePreviewGrant()
        // A Compose or wallpaper-service canvas may still be drawing the old bitmap.
        LauncherBackgroundCache.changed(null)
        launcherPhotoFile(activity).delete()
        // Clearing the photo clears the background: a piece of art is chosen in the picker, not fallen back to.
        setBackgroundChoice(activity, BackgroundChoice.None)
        prefs.edit().putBoolean(BACKGROUND_ENABLED, false).remove(BACKGROUND_ID).remove(PICKER_PENDING).remove(PENDING_URI)
            .remove(PENDING_OPERATION).remove(PREVIEW_PHASE).remove(PREVIEW_FILE).apply()
        photoSelected = false; errorMessage = null; successMessage = activity.getString(R.string.using_folio_dunes)
        onExternalResultChanged(false)
        cleanupStagedFiles()
    }

    fun clearMessage() { errorMessage = null; successMessage = null }

    fun cancelPendingSelection() {
        if (!pickerPending) return
        generation++; loading = false; clearPickerPending()
    }

    fun applyPreview() {
        val staged = preview ?: return
        if (!previewPending || pendingOperation() != staged.operation ||
            previewFile()?.absolutePath != staged.file.absolutePath) return
        runCatching { staged.commit(launcherPhotoFile(activity)) }
            .onSuccess {
                releasePreviewGrant(staged.operation)
                setBackgroundChoice(activity, BackgroundChoice.Photo)
                prefs.edit().putBoolean(BACKGROUND_ENABLED, true).putString(BACKGROUND_ID, staged.operation)
                    .remove(PENDING_URI).remove(PENDING_OPERATION).remove(PREVIEW_PHASE).remove(PREVIEW_FILE).apply()
                preview = null
                previewBitmap = null
                previewPending = false
                loading = false
                LauncherBackgroundCache.changed(staged.bitmap, staged.operation)
                photoSelected = true
                errorMessage = null
                successMessage = activity.getString(R.string.launcher_background_updated)
                cleanupStagedFiles()
            }
            .onFailure { errorMessage = it.message ?: activity.getString(R.string.that_photo_could_not_be_saved) }
    }

    fun cancelPreview() {
        if (!previewPending) return
        generation++
        loading = false
        discardPreview()
        clearPreviewPersistence()
        errorMessage = null
        successMessage = null
        cleanupStagedFiles()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        generation++
        preview?.releaseBitmap()
        preview = null
        previewBitmap = null
        activity.lifecycle.removeObserver(this)
    }

    private fun beginPreview(uri: Uri, operation: String?) {
        if (operation == null) {
            errorMessage = activity.getString(R.string.the_pending_photo_selection_could_not_be)
            clearPickerPending()
            return
        }
        pickerPending = false
        previewPending = true
        loading = true
        val statePersisted = prefs.edit().remove(PICKER_PENDING).putString(PENDING_URI, uri.toString())
            .putString(PREVIEW_PHASE, PHASE_DECODING).remove(PREVIEW_FILE).commit()
        if (statePersisted && pendingOperation() == operation && !hasPersistedReadAccess(uri)) {
            // Record ownership before requesting the grant so process death cannot leave an
            // untracked permission. Grants that predate this operation remain owner-managed.
            val ownershipRecorded = prefs.edit().putString(PREVIEW_GRANT_URI, uri.toString())
                .putString(PREVIEW_GRANT_OPERATION, operation).commit()
            if (ownershipRecorded && pendingOperation() == operation) {
                val granted = runCatching {
                    activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }.isSuccess
                if (!granted) forgetPreviewGrant(operation)
            }
        }
        onExternalResultChanged(false)
        decode(uri, operation)
    }

    private fun decode(uri: Uri, operation: String?) {
        if (operation == null) { failPreviewRestore(activity.getString(R.string.the_pending_photo_selection_could_not_be)); return }
        val token = generation
        activity.lifecycleScope.launch {
            // withContext has prompt cancellation: its IO block can finish creating the file
            // and bitmap, then throw cancellation instead of returning that value to us.
            // Retain ownership across that handoff so the abandoned result can be reclaimed.
            val stagedAcrossDispatch = AtomicReference<StagedBackground?>(null)
            val result = try {
                val staged = withContext(Dispatchers.IO) {
                    stage(uri, operation).also(stagedAcrossDispatch::set)
                }
                stagedAcrossDispatch.set(null)
                Result.success(staged)
            } catch (cancel: CancellationException) {
                stagedAcrossDispatch.getAndSet(null)?.discard()
                // A recreated controller owns the persisted operation. The old activity must
                // not turn lifecycle cancellation into a failure that clears that operation.
                throw cancel
            } catch (error: Throwable) {
                Result.failure(error)
            }
            if (token != generation || !previewPending || pendingOperation() != operation) {
                result.getOrNull()?.discard()
                return@launch
            }
            val staged = result.getOrElse {
                failPreviewRestore(it.message ?: activity.getString(R.string.that_photo_could_not_be_used))
                return@launch
            }
            val persisted = prefs.edit().putString(PREVIEW_PHASE, PHASE_READY)
                .putString(PREVIEW_FILE, staged.file.name).remove(PENDING_URI).commit()
            if (!persisted || token != generation || !previewPending || pendingOperation() != operation) {
                staged.discard()
                if (!persisted) failPreviewRestore(activity.getString(R.string.that_photo_preview_could_not_be_saved))
                return@launch
            }
            releasePreviewGrant(operation)
            preview = staged
            previewBitmap = staged.publish()
            loading = false
        }
    }

    private fun restorePreview(file: File, operation: String) {
        val token = generation
        activity.lifecycleScope.launch {
            val bitmapAcrossDispatch = AtomicReference<Bitmap?>(null)
            val bitmap = try {
                val restored = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(file.absolutePath).also(bitmapAcrossDispatch::set)
                }
                bitmapAcrossDispatch.set(null)
                restored
            } catch (cancel: CancellationException) {
                bitmapAcrossDispatch.getAndSet(null)
                    ?.takeUnless { it === LauncherBackgroundCache.bitmap || it.isRecycled }?.recycle()
                throw cancel
            } catch (_: Throwable) {
                null
            }
            if (token != generation || !previewPending || pendingOperation() != operation ||
                previewFile()?.absolutePath != file.absolutePath) {
                bitmap?.takeUnless { it === LauncherBackgroundCache.bitmap || it.isRecycled }?.recycle()
                return@launch
            }
            if (bitmap == null) {
                file.delete()
                failPreviewRestore(activity.getString(R.string.the_pending_photo_preview_could_not_be_r))
                return@launch
            }
            val staged = StagedBackground(bitmap, file, operation)
            preview = staged
            previewBitmap = staged.publish()
            loading = false
        }
    }

    private fun clearPickerPending() {
        pickerPending = false
        if (!previewPending) {
            releasePreviewGrant(pendingOperation())
            prefs.edit().remove(PICKER_PENDING).remove(PENDING_URI).remove(PENDING_OPERATION)
                .remove(PREVIEW_PHASE).remove(PREVIEW_FILE).apply()
        } else prefs.edit().remove(PICKER_PENDING).apply()
        onExternalResultChanged(false)
    }

    private fun failPreviewRestore(message: String) {
        loading = false
        errorMessage = message
        discardPreview()
        clearPreviewPersistence()
        cleanupStagedFiles()
    }

    private fun discardPreview() {
        preview?.discard()
        preview = null
        previewBitmap = null
        previewPending = false
    }

    private fun clearPreviewPersistence() {
        releasePreviewGrant(pendingOperation())
        prefs.edit().remove(PICKER_PENDING).remove(PENDING_URI).remove(PENDING_OPERATION)
            .remove(PREVIEW_PHASE).remove(PREVIEW_FILE).apply()
        pickerPending = false
        previewPending = false
        onExternalResultChanged(false)
    }

    private fun pendingOperation() = runCatching { prefs.getString(PENDING_OPERATION, null) }.getOrNull()
    private fun previewPhase() = runCatching { prefs.getString(PREVIEW_PHASE, null) }.getOrNull()
    private fun hasPersistedReadAccess(uri: Uri): Boolean = runCatching {
        activity.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }.getOrDefault(false)

    private fun releaseStalePreviewGrant(activeOperation: String?) {
        val recordedOperation = runCatching { prefs.getString(PREVIEW_GRANT_OPERATION, null) }.getOrNull()
        if (recordedOperation == null || recordedOperation != activeOperation) releasePreviewGrant(recordedOperation)
    }

    private fun releasePreviewGrant(operation: String? = null) {
        val recordedOperation = runCatching { prefs.getString(PREVIEW_GRANT_OPERATION, null) }.getOrNull()
        if (operation != null && recordedOperation != operation) return
        val uri = runCatching { prefs.getString(PREVIEW_GRANT_URI, null) }.getOrNull()?.let(Uri::parse)
        if (uri != null) runCatching {
            activity.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        forgetPreviewGrant(recordedOperation)
    }

    private fun forgetPreviewGrant(operation: String?) {
        val recordedOperation = runCatching { prefs.getString(PREVIEW_GRANT_OPERATION, null) }.getOrNull()
        if (operation != null && recordedOperation != operation) return
        prefs.edit().remove(PREVIEW_GRANT_URI).remove(PREVIEW_GRANT_OPERATION).commit()
    }

    private fun previewFile(): File? {
        val name = runCatching { prefs.getString(PREVIEW_FILE, null) }.getOrNull() ?: return null
        if (File(name).name != name || !name.startsWith("$BACKGROUND_FILE.") || !name.endsWith(".tmp")) return null
        return File(launcherPhotoFile(activity).parentFile, name)
    }

    private fun stage(uri: Uri, operation: String): StagedBackground {
        val source = ImageDecoder.createSource(activity.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width; val height = info.size.height
            require(width > 0 && height > 0) { "The selected image has no usable pixels." }
            val scale = minOf(1f, MAX_BACKGROUND_EDGE.toFloat() / maxOf(width, height))
            decoder.setTargetSize(maxOf(1, (width * scale).toInt()), maxOf(1, (height * scale).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
        if (bitmap.byteCount > MAX_BACKGROUND_EDGE * MAX_BACKGROUND_EDGE * 4) {
            bitmap.recycle()
            throw IllegalArgumentException("The selected image is too large.")
        }
        val temporary = File(launcherPhotoFile(activity).parentFile,
            "$BACKGROUND_FILE.$operation.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                require(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) { "The selected image could not be saved." }
                output.fd.sync()
            }
        } catch (error: Exception) {
            temporary.delete()
            bitmap.recycle()
            throw error
        }
        return StagedBackground(bitmap, temporary, operation)
    }

    private class StagedBackground(val bitmap: Bitmap, val file: File, val operation: String) {
        private var published = false

        fun publish(): Bitmap {
            published = true
            return bitmap
        }

        fun commit(destination: File) {
            try { Files.move(file.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: Exception) { Files.move(file.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        }
        fun discard() { file.delete(); releaseBitmap() }
        fun releaseBitmap() {
            // Once Compose has observed this bitmap, RenderThread may retain it beyond the
            // state update that closes preview. Drop controller references and let GC release
            // it after every renderer is finished. Unpublished decode results are controller-owned.
            if (!published && bitmap !== LauncherBackgroundCache.bitmap && !bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun cleanupStagedFiles(keep: File? = null) {
        launcherPhotoFile(activity).parentFile?.listFiles { file ->
            file.name.startsWith("$BACKGROUND_FILE.") && file.name.endsWith(".tmp")
        }?.filterNot { keep != null && it.absolutePath == keep.absolutePath }?.forEach(File::delete)
    }

    companion object {
        private const val PICKER_PENDING = "pickerPending"
        private const val PENDING_URI = "pendingUri"
        private const val PENDING_OPERATION = "pendingOperation"
        private const val PREVIEW_PHASE = "previewPhase"
        private const val PREVIEW_FILE = "previewFile"
        private const val PREVIEW_GRANT_URI = "previewGrantUri"
        private const val PREVIEW_GRANT_OPERATION = "previewGrantOperation"
        private const val PHASE_DECODING = "decoding"
        private const val PHASE_READY = "ready"
    }
}
