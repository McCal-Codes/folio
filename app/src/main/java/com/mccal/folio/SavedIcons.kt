package com.mccal.folio

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest

/**
 * App icons and names kept on disk between starts, so a start doesn't load every app's icon again (J1 in
 * docs/standards/performance.md). Like Android's own launcher, a saved icon is used only while nothing it was drawn
 * from has changed: the same component, installed at the same path (every update installs to a new one), under the
 * same fingerprint (Folio's version, the Android build, density, languages, dark mode and the theme's assets).
 *
 * The files are in the cache folder: Android may clear it when space is short, which costs one slow start, and backups
 * leave it out. Each is written to a temporary file and renamed, so a reader finds a whole icon or none.
 */
internal class SavedIcons(private val dir: File) {
    class Saved(val id: String, val label: String, val icon: Bitmap, val component: ComponentName, val sourceDir: String)

    /** Starts over when what the saved icons were drawn under has changed. */
    fun check(fingerprint: String) {
        val marker = File(dir, FINGERPRINT)
        if (marker.isFile && marker.readText() == fingerprint) return
        clear()
        dir.mkdirs()
        writeAtomically(marker) { it.write(fingerprint.toByteArray()) }
    }

    /** [id]'s saved name and icon, when they were saved for this [component] installed at [sourceDir]. */
    fun read(id: String, component: ComponentName, sourceDir: String): Pair<String, Bitmap>? {
        val file = file(id)
        if (!file.isFile) return null
        return runCatching {
            DataInputStream(file.inputStream().buffered()).use { input ->
                if (input.readInt() != FORMAT || input.readUTF() != id || input.readUTF() != component.flattenToString() ||
                    input.readUTF() != sourceDir) return null
                val label = input.readUTF()
                val size = input.readInt()
                if (size !in 1..MAX_ICON_BYTES) return null
                val png = ByteArray(size).also(input::readFully)
                label to (BitmapFactory.decodeByteArray(png, 0, png.size) ?: return null)
            }
        }.getOrNull()
    }

    fun write(icons: List<Saved>) {
        // check() makes the folder; if Android cleared it since, these wait for the next start.
        if (!dir.isDirectory) return
        for (saved in icons) {
            val png = ByteArrayOutputStream().also { saved.icon.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            writeAtomically(file(saved.id)) { out ->
                DataOutputStream(out).run {
                    writeInt(FORMAT); writeUTF(saved.id); writeUTF(saved.component.flattenToString())
                    writeUTF(saved.sourceDir); writeUTF(saved.label); writeInt(png.size); write(png); flush()
                }
            }
        }
    }

    /** Forgets the apps that aren't listed any more. */
    fun prune(listed: Set<String>) {
        val keep = listed.mapTo(mutableSetOf(), ::fileName)
        dir.listFiles { file -> file.name.endsWith(SUFFIX) && file.name !in keep }?.forEach(File::delete)
    }

    fun clear() {
        dir.listFiles()?.forEach(File::delete)
    }

    private fun file(id: String) = File(dir, fileName(id))

    private inline fun writeAtomically(target: File, write: (OutputStream) -> Unit) {
        val temp = File.createTempFile(target.name, ".tmp", dir)
        try {
            temp.outputStream().use(write)
            if (!temp.renameTo(target)) temp.delete()
        } catch (failure: Exception) {
            temp.delete()
            throw failure
        }
    }

    companion object {
        /** Bump when [launcherIcon] draws icons differently, so the ones saved before are drawn again. */
        const val FORMAT = 1
        private const val FINGERPRINT = "fingerprint"
        private const val SUFFIX = ".icon"
        /** A 144-pixel icon is a few kilobytes; anything near this is a damaged file, not an icon. */
        private const val MAX_ICON_BYTES = 1 shl 20

        fun fileName(id: String): String =
            MessageDigest.getInstance("SHA-1").digest(id.toByteArray()).joinToString("") { "%02x".format(it) } + SUFFIX
    }
}
