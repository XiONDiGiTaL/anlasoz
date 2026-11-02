package net.xiondigital.anlasoz.local

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class LocalModelFile(
    val name: String,
    val file: File,
) {
    val bytes: Long get() = file.length()
    val displaySize: String get() = ModelCatalog.formatBytes(bytes)
}

/**
 * Files on the device that the engine can load.
 *
 * Everything lives in app-private storage: models are large, the picker cannot
 * grant lasting read access, and a partially copied file must never look
 * installed. A `.part` suffix marks an unfinished download or import.
 */
class ModelStore(private val filesDir: File, private val resolver: ContentResolver?) {

    val directory: File = File(filesDir, "models").apply { mkdirs() }

    fun list(): List<LocalModelFile> =
        directory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".gguf", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { LocalModelFile(it.name, it) }
            .orEmpty()

    fun file(name: String): File = File(directory, name)

    fun has(name: String): Boolean = file(name).let { it.isFile && it.length() > 0 }

    fun delete(name: String): Boolean {
        val target = file(name)
        return target.exists() && target.delete()
    }

    fun freeBytes(): Long = directory.usableSpace

    /** True when the first bytes are the GGUF magic, i.e. not a renamed zip. */
    fun looksLikeGguf(file: File): Boolean {
        if (!file.isFile || file.length() < 8) return false
        return runCatching {
            FileInputStream(file).use { input ->
                val head = ByteArray(4)
                input.read(head) == 4 &&
                    head[0] == 'G'.code.toByte() &&
                    head[1] == 'G'.code.toByte() &&
                    head[2] == 'U'.code.toByte() &&
                    head[3] == 'F'.code.toByte()
            }
        }.getOrDefault(false)
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Copies a `.gguf` chosen through the system picker into private storage.
     * Returns the installed file, or throws when the source is not a GGUF or
     * there is not enough space.
     */
    fun import(uri: Uri, onProgress: (received: Long, total: Long) -> Unit = { _, _ -> }): LocalModelFile {
        val contentResolver = resolver ?: error("No content resolver")
        val displayName = queryName(contentResolver, uri) ?: "imported.gguf"
        val safeName = displayName
            .substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .let { if (it.endsWith(".gguf", ignoreCase = true)) it else "$it.gguf" }

        val total = querySize(contentResolver, uri)
        if (total > 0 && total > freeBytes()) {
            error("Not enough space: the file needs ${ModelCatalog.formatBytes(total)}")
        }

        val target = uniqueName(safeName)
        val partial = File(directory, "$target.part")
        var copied = 0L
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "The file could not be opened" }
            partial.outputStream().use { output ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    onProgress(copied, total)
                }
            }
        }

        if (!looksLikeGguf(partial)) {
            partial.delete()
            error("That file is not a GGUF model")
        }
        val installed = File(directory, target)
        if (!partial.renameTo(installed)) {
            partial.delete()
            error("The file could not be moved into place")
        }
        return LocalModelFile(installed.name, installed)
    }

    private fun uniqueName(name: String): String {
        if (!file(name).exists()) return name
        val base = name.removeSuffix(".gguf")
        var index = 2
        while (file("$base-$index.gguf").exists()) index++
        return "$base-$index.gguf"
    }

    private fun queryName(resolver: ContentResolver, uri: Uri): String? =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()

    private fun querySize(resolver: ContentResolver, uri: Uri): Long =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
            } ?: 0L
        }.getOrDefault(0L)
}
