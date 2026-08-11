package com.marcogn.thepatientgamerhelper.data.export

import android.net.Uri
import com.marcogn.thepatientgamerhelper.domain.export.toBackupMarkdown
import com.marcogn.thepatientgamerhelper.domain.export.toCsvExport
import com.marcogn.thepatientgamerhelper.domain.export.toJsonExport
import com.marcogn.thepatientgamerhelper.domain.model.Review
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes review data to a user-picked SAF destination. Formatting itself is pure Kotlin in
 * `domain.export` (unit-testable without Android); this class only owns the Android-side I/O.
 */
@Singleton
class ReviewExporter @Inject constructor(
    private val fileWriter: ExportFileWriter,
    private val pdfRenderer: PdfReviewRenderer,
) {
    suspend fun exportJson(reviews: List<Review>, destination: Uri) {
        fileWriter.writeText(destination, reviews.toJsonExport())
    }

    suspend fun exportCsv(reviews: List<Review>, destination: Uri) {
        fileWriter.writeText(destination, reviews.toCsvExport())
    }

    suspend fun exportMarkdown(review: Review, destination: Uri) {
        fileWriter.writeText(destination, review.toBackupMarkdown())
    }

    /** Works for both a single review ([reviews] with one element) and a whole-library batch. */
    suspend fun exportPdf(reviews: List<Review>, destination: Uri) {
        val document = pdfRenderer.render(reviews)
        try {
            fileWriter.write(destination) { output -> document.writeTo(output) }
        } finally {
            document.close()
        }
    }
}
