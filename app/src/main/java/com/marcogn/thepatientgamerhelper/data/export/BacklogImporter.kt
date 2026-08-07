package com.marcogn.thepatientgamerhelper.data.export

import android.net.Uri
import com.marcogn.thepatientgamerhelper.data.image.ImageStorage
import com.marcogn.thepatientgamerhelper.domain.export.toImportedLists
import com.marcogn.thepatientgamerhelper.domain.model.BacklogImportResult
import com.marcogn.thepatientgamerhelper.domain.repository.BacklogRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Reads a backlog export archive from a user-picked SAF source and imports it additively (Fase 8) — see `BacklogRepository.importLists`. */
@Singleton
class BacklogImporter @Inject constructor(
    private val fileReader: ImportFileReader,
    private val archiveReader: BacklogExportArchiveReader,
    private val imageStorage: ImageStorage,
    private val backlogRepository: BacklogRepository,
) {
    suspend fun import(source: Uri): BacklogImportResult {
        val content = archiveReader.read(fileReader.readBytes(source))
        // toImportedLists()/toImportedItem() live in domain/export (pure, non-suspend), so the
        // suspend cover writes can't happen inside the resolveCoverPath callback passed to them —
        // resolve every referenced cover up front instead (same shape as BackupManager.restoreBackup).
        val referencedCoverFileNames = content.payload.liste
            .flatMap { it.elementi }
            .mapNotNull { it.copertina }
            .toSet()
        val coverPathsByFileName = referencedCoverFileNames.mapNotNull { fileName ->
            content.images[fileName]?.let { bytes -> fileName to imageStorage.writeBytes(newCoverFileName(fileName), bytes) }
        }.toMap()
        val importedLists = content.payload.toImportedLists { fileName -> coverPathsByFileName[fileName] }
        backlogRepository.importLists(importedLists)
        return BacklogImportResult(
            listsImported = importedLists.size,
            itemsImported = importedLists.sumOf { it.items.size },
        )
    }

    /**
     * Always mints a fresh name: the shared `covers/` directory already holds review *and*
     * backlog covers from this device's own data, so reusing the export's own basename risks
     * colliding with an unrelated file already present locally.
     */
    private fun newCoverFileName(originalFileName: String): String {
        val extension = originalFileName.substringAfterLast('.', missingDelimiterValue = "jpg")
        return "${UUID.randomUUID()}.$extension"
    }
}
