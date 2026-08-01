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

    suspend fun delete(path: String?) = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext
        val file = File(path)
        if (file.exists() && file.parentFile == coversDir) {
            file.delete()
        }
    }
}
