package com.marcogn.gamereviewer.data.image

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persists a picked cover image into the app's internal storage so it survives beyond the
 * transient permission granted by the photo picker, and exposes it as a plain absolute path.
 */
@Singleton
class ImageStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val coversDir: File
        get() = File(context.filesDir, "covers").apply { mkdirs() }

    suspend fun persist(sourceUri: Uri): String = withContext(Dispatchers.IO) {
        val destination = File(coversDir, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Impossibile leggere l'immagine selezionata")
        destination.absolutePath
    }

    /**
     * Copies an existing cover to a new file so it has an independent lifecycle — used when a
     * backlog item's cover is carried over into a pre-populated review, so deleting one doesn't
     * pull the image out from under the other.
     */
    suspend fun duplicate(sourcePath: String): String = withContext(Dispatchers.IO) {
        val destination = File(coversDir, "${UUID.randomUUID()}.jpg")
        File(sourcePath).copyTo(destination, overwrite = true)
        destination.absolutePath
    }

    suspend fun delete(path: String?) = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext
        val file = File(path)
        if (file.exists() && file.parentFile == coversDir) {
            file.delete()
        }
    }

    /** All cover files currently on disk, for the backup archive to include. */
    suspend fun listAll(): List<File> = withContext(Dispatchers.IO) {
        coversDir.listFiles()?.toList() ?: emptyList()
    }

    /** Writes [bytes] under [fileName] as-is, for restoring a cover from a backup archive. */
    suspend fun writeBytes(fileName: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val destination = File(coversDir, fileName)
        destination.writeBytes(bytes)
        destination.absolutePath
    }

    /** Wipes every stored cover, used before a restore overwrites the library. */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        coversDir.listFiles()?.forEach { it.delete() }
    }
}
