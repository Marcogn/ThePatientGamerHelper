package com.marcogn.gamereviewer.data.export

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Writes export content to a user-picked SAF destination (ACTION_CREATE_DOCUMENT result). */
@Singleton
class ExportFileWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun writeText(destination: Uri, content: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(destination)?.use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
        } ?: error("Impossibile scrivere sulla destinazione selezionata")
    }

    suspend fun writeBytes(destination: Uri, content: ByteArray) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(destination)?.use { output ->
            output.write(content)
        } ?: error("Impossibile scrivere sulla destinazione selezionata")
    }
}
