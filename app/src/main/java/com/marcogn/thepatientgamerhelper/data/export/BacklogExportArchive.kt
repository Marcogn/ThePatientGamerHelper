package com.marcogn.thepatientgamerhelper.data.export

import com.marcogn.thepatientgamerhelper.domain.export.BacklogExportPayload
import com.marcogn.thepatientgamerhelper.domain.export.toBacklogExportPayload
import com.marcogn.thepatientgamerhelper.domain.export.toJson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DATA_ENTRY = "data.json"
private const val IMAGES_PREFIX = "images/"

/**
 * A backlog export archive holds `data.json` (see `domain/export/BacklogExportDto.kt`) plus every
 * referenced cover under `images/` — same zip shape as `data/backup/BackupArchive.kt`, but scoped
 * only to the covers the exported items actually reference, not the whole `ImageStorage` directory
 * (which also holds review covers, unrelated to a backlog export).
 */
@Singleton
class BacklogExportArchiveBuilder @Inject constructor() {
    suspend fun build(payload: BacklogExportPayload, coverPathsByFileName: Map<String, String>): ByteArray =
        withContext(Dispatchers.IO) {
            val buffer = ByteArrayOutputStream()
            ZipOutputStream(buffer).use { zip ->
                zip.putNextEntry(ZipEntry(DATA_ENTRY))
                zip.write(payload.toJson().toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                coverPathsByFileName.forEach { (fileName, absolutePath) ->
                    val file = File(absolutePath)
                    if (!file.exists()) return@forEach
                    zip.putNextEntry(ZipEntry("$IMAGES_PREFIX$fileName"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            buffer.toByteArray()
        }
}

data class BacklogExportArchiveContent(val payload: BacklogExportPayload, val images: Map<String, ByteArray>)

@Singleton
class BacklogExportArchiveReader @Inject constructor() {
    suspend fun read(bytes: ByteArray): BacklogExportArchiveContent = withContext(Dispatchers.IO) {
        var payload: BacklogExportPayload? = null
        val images = mutableMapOf<String, ByteArray>()

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val content = zip.readBytes()
                when {
                    entry.name == DATA_ENTRY -> payload = content.toString(Charsets.UTF_8).toBacklogExportPayload()
                    entry.name.startsWith(IMAGES_PREFIX) -> images[entry.name.removePrefix(IMAGES_PREFIX)] = content
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        BacklogExportArchiveContent(
            payload = payload ?: error("File non valido: manca $DATA_ENTRY nell'archivio"),
            images = images,
        )
    }
}
