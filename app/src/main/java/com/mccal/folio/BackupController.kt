package com.mccal.folio

import android.appwidget.AppWidgetManager
import android.content.Context
import android.net.Uri
import android.os.UserManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.mccal.folio.market.PackageInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupController(
    private val activity: ComponentActivity,
    private val model: LauncherModel,
    private val widgets: WidgetController,
    private val onExternalResultChanged: (Boolean) -> Unit,
) {
    var preview by mutableStateOf<LayoutImportPreview?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var successMessage by mutableStateOf<String?>(null)
        private set
    var pickerPending by mutableStateOf(false)
        private set

    private val store = activity.getSharedPreferences("layout_backup_pending", Context.MODE_PRIVATE)
    private val userManager = activity.getSystemService(UserManager::class.java)
    private val scope = layoutBackupScope(activity)
    // Built on first use, so a phone that never opens Settings never builds it.
    private val market by lazy { MarketSession(activity, ModelLauncher(model, activity)) }
    /** Whether this phone has the Market. A package it can't show is one nobody could turn off or remove. */
    private val marketOpen by lazy { runCatching { MarketAccess.isOpen(activity) }.getOrDefault(false) }
    private var operation: String? = null
    private var generation = 0
    private var importRaw: String? = null
    private val createDocument = activity.activityResultRegistry.register(
        "duo.backup.create", activity, ActivityResultContracts.CreateDocument("application/json")
    ) createCallback@{ uri ->
        if (operation != OP_EXPORT) return@createCallback
        if (uri == null) clearTransaction() else {
            store.edit().putString(KEY_URI, uri.toString()).apply()
            writeExport(uri, generation)
        }
    }
    private val openDocument = activity.activityResultRegistry.register(
        "duo.backup.open", activity, ActivityResultContracts.OpenDocument()
    ) openCallback@{ uri ->
        if (operation != OP_IMPORT) return@openCallback
        if (uri == null) clearTransaction() else {
            store.edit().putString(KEY_URI, uri.toString()).apply()
            readImport(uri, generation)
        }
    }

    fun restore() {
        val saved = runCatching { Triple(store.getString(KEY_OPERATION, null), store.getString(KEY_PREVIEW, null), store.getString(KEY_URI, null)) }.getOrNull()
        operation = saved?.first
        val raw = saved?.second
        importRaw = raw.takeIf { operation == OP_PREVIEW }
        if (raw != null || operation != null) onExternalResultChanged(true)
        if (operation == OP_PREVIEW && raw == null) clearTransaction()
        else if (raw != null) parsePreview(raw, persist = false)
        else if (saved?.third != null && operation == OP_IMPORT) readImport(Uri.parse(saved.third), generation)
        else if (saved?.third != null && operation == OP_EXPORT) writeExport(Uri.parse(saved.third), generation)
        else if (operation != null) {
            pickerPending = true
            onExternalResultChanged(true)
        } else onExternalResultChanged(false)
    }

    fun startExport(fileName: String = "folio-layout.json") {
        val state = model.state.value
        val raw = runCatching { encodeBackup(state) }.getOrElse {
            errorMessage = it.message ?: activity.getString(R.string.layout_backup_could_not_be_prepared); return
        }
        begin(OP_EXPORT, raw)
        try { createDocument.launch(fileName) }
        catch (error: Exception) { errorMessage = error.message ?: "The document picker is unavailable."; clearTransaction(false) }
    }

    /** Saves a backup straight to Download/Folio, no picker. */
    fun saveToFolioFolder(name: String? = null) {
        val state = model.state.value
        val raw = runCatching { encodeBackup(state) }.getOrElse {
            errorMessage = it.message ?: activity.getString(R.string.layout_backup_could_not_be_prepared); return
        }
        val name = FolioFiles.fileName(name, "folio-layout")
        activity.lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) { FolioFiles.save(activity, name, "application/json", raw.toByteArray())?.let { FolioFiles.displayName(activity, it) ?: name } }
            if (saved != null) successMessage = activity.getString(R.string.saved_to_as, FolioFiles.displayPath, saved)
            else errorMessage = activity.getString(R.string.layout_backup_could_not_be_saved)
        }
    }

    fun startImport() {
        begin(OP_IMPORT)
        try { openDocument.launch(arrayOf("application/json", "text/json", "text/plain")) }
        catch (error: Exception) { errorMessage = error.message ?: "The document picker is unavailable."; clearTransaction(false) }
    }

    fun applyImport(): Boolean {
        val raw = importRaw ?: return false
        val token = generation
        activity.lifecycleScope.launch {
            val state = model.state.first { !it.loading }
            val result = runCatching { withContext(Dispatchers.Default) {
                decodeLayoutBackup(raw, state.apps, state.profiles, scope)
            } }
            result.rethrowCancellation()
            if (token != generation || operation != OP_PREVIEW) return@launch
            result.onSuccess { imported ->
                // The packages are put back around the layout, not after it: what a package replaced has to be the
                // layout it was applied over. `:market` owns that order, so the layout goes back inside its call.
                var changed = false
                // Taken before the Market removes anything, so Undo and Layout History hold the Home the user had.
                val before = model.state.value
                val putLayoutBack = { changed = model.applyImportedLayout(imported, before) }
                val packages = if (marketOpen) imported.packages else null
                val restored = runCatching { market.restorePackages(packages, activity.getString(R.string.folio_couldn_t_put_this_package_back), putLayoutBack) }.getOrElse {
                    // The Market failing is no reason to lose the layout the user asked for.
                    if (!changed) putLayoutBack()
                    null
                }
                successMessage = restoredMessage(changed, imported.packages, restored)
                clearTransaction(clearMessages = false)
            }.onFailure { errorMessage = it.message ?: activity.getString(R.string.this_layout_backup_is_no_longer_valid) }
        }
        return true
    }

    fun cancelImport() = clearTransaction()
    fun clearMessage() { errorMessage = null; successMessage = null }

    fun resumePendingPicker(): Boolean = when (operation) {
        OP_EXPORT -> runCatching { createDocument.launch("folio-layout.json") }.isSuccess
        OP_IMPORT -> runCatching { openDocument.launch(arrayOf("application/json", "text/json", "text/plain")) }.isSuccess
        else -> false
    }

    /**
     * The backup, with this phone's packages when they fit. A wallpaper package carries its whole image, so one big one
     * pushed the file past 2 MB and no layout backup could be made at all; the layout alone is still worth saving.
     */
    private fun encodeBackup(state: LauncherState): String {
        val descriptors = widgetDescriptors(state)
        val packages = savedPackages()
        return runCatching { encodeLayoutBackup(state, descriptors, scope, packages) }.getOrElse { error ->
            if (packages == null) throw error
            encodeLayoutBackup(state, descriptors, scope, null)
        }
    }

    /** What this phone has installed from the Market, for the backup to carry. */
    private fun savedPackages(): String? = runCatching { market.exportPackages() }.getOrNull()

    /** What the user is told afterwards: the layout first, then whatever happened to the packages it carried. */
    private fun restoredMessage(changed: Boolean, packages: String?, restored: PackageInstaller.Restore?): String {
        val parts = mutableListOf(if (changed) activity.getString(R.string.layout_restored_widgets_are_ready_to_rec) else activity.getString(R.string.this_layout_is_already_active))
        when {
            packages == null -> Unit
            !marketOpen -> parts += activity.getString(R.string.its_packages_were_left_out)
            restored == null -> parts += activity.getString(R.string.folio_couldn_t_read_its_packages_so)
            else -> {
                val on = restored.on.size
                val off = restored.off.size + restored.failed.size
                if (on > 0) parts += activity.resources.getQuantityString(R.plurals.packages_are_back, on, on)
                if (off > 0) parts += activity.resources.getQuantityString(R.plurals.more_are_in_installed_turned_off, off, off)
            }
        }
        return parts.joinToString(" ")
    }

    private fun begin(value: String, payload: String? = null) {
        generation++
        preview = null; errorMessage = null; successMessage = null
        importRaw = null
        operation = value; pickerPending = true
        val editor = store.edit().clear().putString(KEY_OPERATION, value)
        payload?.let { editor.putString(KEY_EXPORT, it) }
        editor.apply()
        onExternalResultChanged(true)
    }

    private fun writeExport(uri: Uri, token: Int) {
        activity.lifecycleScope.launch {
            val result = runCatching {
                val raw = store.getString(KEY_EXPORT, null) ?: error("The export snapshot is unavailable")
                withContext(Dispatchers.IO) {
                    activity.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(raw) }
                        ?: error("The selected document could not be opened")
                }
            }
            result.rethrowCancellation()
            if (token != generation || operation != OP_EXPORT) return@launch
            result.onSuccess { successMessage = activity.getString(R.string.layout_backup_saved) }
                .onFailure { errorMessage = it.message ?: activity.getString(R.string.layout_backup_could_not_be_saved) }
            clearTransaction(clearMessages = false)
        }
    }

    private fun readImport(uri: Uri, token: Int) {
        activity.lifecycleScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { readBounded(uri) } }
            result.rethrowCancellation()
            if (token != generation || operation != OP_IMPORT) return@launch
            result.onSuccess {
                operation = OP_PREVIEW; importRaw = it
                store.edit().putString(KEY_OPERATION, OP_PREVIEW).putString(KEY_PREVIEW, it).remove(KEY_URI).apply()
                parsePreview(it, persist = false)
            }
                .onFailure {
                    errorMessage = it.message ?: activity.getString(R.string.layout_backup_could_not_be_read)
                    clearTransaction(clearMessages = false)
                }
        }
    }

    private fun parsePreview(raw: String, persist: Boolean) {
        val token = generation
        activity.lifecycleScope.launch {
            val state = model.state.first { !it.loading }
            val result = runCatching { withContext(Dispatchers.Default) {
                val imported = decodeLayoutBackup(raw, state.apps, state.profiles, scope)
                // Only the Market can read what it wrote, so the count is filled in here rather than in the decoder,
                // and off the main thread with the rest of the reading.
                imported.copy(packageCount = if (marketOpen) runCatching { market.countPackages(imported.packages) }.getOrDefault(0) else 0)
            } }
            result.rethrowCancellation()
            result.onSuccess {
                if (token != generation || operation != OP_PREVIEW) return@onSuccess
                importRaw = raw
                preview = it; pickerPending = false; operation = OP_PREVIEW
                if (persist) store.edit().putString(KEY_OPERATION, OP_PREVIEW).putString(KEY_PREVIEW, raw).apply()
                onExternalResultChanged(true)
            }.onFailure {
                if (token != generation) return@onFailure
                errorMessage = it.message ?: activity.getString(R.string.this_layout_backup_is_invalid)
                clearTransaction(clearMessages = false)
            }
        }
    }

    private fun readBounded(uri: Uri): String {
        val input = activity.contentResolver.openInputStream(uri) ?: error("The selected document could not be opened")
        return input.use {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_LAYOUT_BACKUP_BYTES) { "Layout backup is larger than 2 MB" }
                output.write(buffer, 0, count)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }

    private fun widgetDescriptors(state: LauncherState): List<BackupWidgetDescriptor> = state.widgetPlacements.mapNotNull { placement ->
        if (placement.id < 0) return@mapNotNull null
        val info = widgets.manager.getAppWidgetInfo(placement.id) ?: error("Widget ${placement.slot} is unavailable")
        BackupWidgetDescriptor(placement.slot, info.provider.flattenToString(), userManager.getSerialNumberForUser(info.profile),
            info.loadLabel(activity.packageManager).toString(), if (info.profile == android.os.Process.myUserHandle()) activity.getString(R.string.personal) else activity.getString(R.string.work),
            isWork = info.profile != android.os.Process.myUserHandle())
    }

    private fun clearTransaction(clearMessages: Boolean = true) {
        generation++
        operation = null; pickerPending = false; preview = null
        importRaw = null
        store.edit().clear().apply()
        onExternalResultChanged(false)
        if (clearMessages) clearMessage()
    }

    private fun Result<*>.rethrowCancellation() {
        exceptionOrNull()?.let { if (it is CancellationException) throw it }
    }

    companion object {
        private const val KEY_OPERATION = "operation"
        private const val KEY_PREVIEW = "preview"
        private const val KEY_EXPORT = "export"
        private const val KEY_URI = "uri"
        private const val OP_EXPORT = "export"
        private const val OP_IMPORT = "import"
        private const val OP_PREVIEW = "preview"

    }
}
