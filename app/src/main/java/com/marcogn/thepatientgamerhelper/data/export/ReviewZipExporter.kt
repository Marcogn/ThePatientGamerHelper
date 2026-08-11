package com.marcogn.thepatientgamerhelper.data.export

import android.net.Uri
import com.marcogn.thepatientgamerhelper.domain.model.Review
import javax.inject.Inject
import javax.inject.Singleton

/** Multi-review ZIP export (v2 §2.3) — the round-trip-importable counterpart to the single-review Markdown export and to the batch PDF (display-only, see `PdfReviewRenderer`). Also the format Drive-independent "backup" of the review library uses per v2 §2.5, though today it's only reachable as a manual export, same as JSON/CSV. */
@Singleton
class ReviewZipExporter @Inject constructor(
    private val fileWriter: ExportFileWriter,
    private val archiveBuilder: ReviewZipArchiveBuilder,
) {
    suspend fun export(reviews: List<Review>, destination: Uri) {
        val coverPathsByFileName = reviews.mapNotNull { it.coverImagePath }
            .associateBy { it.substringAfterLast('/') }
        val archiveBytes = archiveBuilder.build(reviews, coverPathsByFileName)
        fileWriter.write(destination) { output -> output.write(archiveBytes) }
    }
}
