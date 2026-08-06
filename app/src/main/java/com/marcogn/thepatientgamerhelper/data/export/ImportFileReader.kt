package com.marcogn.thepatientgamerhelper.data.export

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads content from a user-picked SAF source (`ActivityResultContracts.OpenDocument` result) — the read-side counterpart of [ExportFileWriter], used by both Markdown review import and backlog zip import (Fase 8). */
@Singleton
class ImportFileReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun readText(source: Uri): String = readBytes(source).toString(Charsets.UTF_8)

    suspend fun readBytes(source: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(source)?.use { it.readBytes() }
            ?: error("Impossibile leggere il file selezionato")
    }
}
