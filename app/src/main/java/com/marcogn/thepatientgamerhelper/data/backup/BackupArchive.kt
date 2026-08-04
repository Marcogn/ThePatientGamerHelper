package com.marcogn.thepatientgamerhelper.data.backup

import com.marcogn.thepatientgamerhelper.data.image.ImageStorage
import com.marcogn.thepatientgamerhelper.domain.backup.BackupPayload
import com.marcogn.thepatientgamerhelper.domain.backup.toBackupPayload
import com.marcogn.thepatientgamerhelper.domain.backup.toJson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DATA_ENTRY = "data.json"
private const val IMAGES_PREFIX = "images/"

/** A single archive holds `data.json` (the full library) plus every cover under `images/`. */
@Singleton
class BackupArchiveBuilder @Inject constructor(
    private val imageStorage: ImageStorage,
) {
    suspend fun build(payload: BackupPayload): ByteArray = withContext(Dispatchers.IO) {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            zip.putNextEntry(ZipEntry(DATA_ENTRY))
            zip.write(payload.toJson().toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            imageStorage.listAll().forEach { file ->
                zip.putNextEntry(ZipEntry("$IMAGES_PREFIX${file.name}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        buffer.toByteArray()
    }
}

data class BackupArchiveContent(val payload: BackupPayload, val images: Map<String, ByteArray>)

@Singleton
class BackupArchiveReader @Inject constructor() {
    suspend fun read(bytes: ByteArray): BackupArchiveContent = withContext(Dispatchers.IO) {
        var payload: BackupPayload? = null
        val images = mutableMapOf<String, ByteArray>()

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val content = zip.readBytes()
                when {
                    entry.name == DATA_ENTRY -> payload = content.toString(Charsets.UTF_8).toBackupPayload()
                    entry.name.startsWith(IMAGES_PREFIX) -> images[entry.name.removePrefix(IMAGES_PREFIX)] = content
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        BackupArchiveContent(
            payload = payload ?: error("Backup non valido: manca $DATA_ENTRY nell'archivio"),
            images = images,
        )
    }
}
