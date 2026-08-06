package com.marcogn.thepatientgamerhelper.data.export

import android.net.Uri
import com.marcogn.thepatientgamerhelper.domain.export.buildBacklogExportPayload
import com.marcogn.thepatientgamerhelper.domain.repository.BacklogRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Writes the whole backlog (every list, every item) to a user-picked SAF destination as a zip archive (Fase 8). */
@Singleton
class BacklogExporter @Inject constructor(
    private val backlogRepository: BacklogRepository,
    private val archiveBuilder: BacklogExportArchiveBuilder,
    private val fileWriter: ExportFileWriter,
) {
    suspend fun export(destination: Uri) {
        val lists = backlogRepository.observeLists().first()
        val items = backlogRepository.observeAllItems().first()
        val payload = buildBacklogExportPayload(lists, items)
        val coverPathsByFileName = items.mapNotNull { it.coverImagePath }.associateBy { it.substringAfterLast('/') }
        val archive = archiveBuilder.build(payload, coverPathsByFileName)
        fileWriter.write(destination) { output -> output.write(archive) }
    }
}
