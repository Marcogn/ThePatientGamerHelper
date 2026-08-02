package com.marcogn.gamereviewer.data.export

import android.net.Uri
import com.marcogn.gamereviewer.domain.export.toCsvExport
import com.marcogn.gamereviewer.domain.export.toJsonExport
import com.marcogn.gamereviewer.domain.export.toRedditMarkdown
import com.marcogn.gamereviewer.domain.model.Review
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes review data to a user-picked SAF destination. Formatting itself is pure Kotlin in
 * `domain.export` (unit-testable without Android); this class only owns the Android-side I/O.
 */
@Singleton
class ReviewExporter @Inject constructor(
    private val fileWriter: ExportFileWriter,
) {
    suspend fun exportJson(reviews: List<Review>, destination: Uri) {
        fileWriter.writeText(destination, reviews.toJsonExport())
    }

    suspend fun exportCsv(reviews: List<Review>, destination: Uri) {
        fileWriter.writeText(destination, reviews.toCsvExport())
    }

    suspend fun exportMarkdown(review: Review, destination: Uri) {
        fileWriter.writeText(destination, review.toRedditMarkdown())
    }
}
