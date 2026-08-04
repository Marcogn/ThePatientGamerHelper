package com.marcogn.thepatientgamerhelper.data.export

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Writes export content to a user-picked SAF destination (ACTION_CREATE_DOCUMENT result). */
@Singleton
class ExportFileWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun writeText(destination: Uri, content: String) {
        write(destination) { output -> output.write(content.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun write(destination: Uri, block: (OutputStream) -> Unit) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(destination)?.use(block)
            ?: error("Impossibile scrivere sulla destinazione selezionata")
    }
}
