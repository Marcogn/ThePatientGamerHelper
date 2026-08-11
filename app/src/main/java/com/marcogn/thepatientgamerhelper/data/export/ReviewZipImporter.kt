package com.marcogn.thepatientgamerhelper.data.export

import android.net.Uri
import com.marcogn.thepatientgamerhelper.data.image.ImageStorage
import com.marcogn.thepatientgamerhelper.domain.export.ParsedReviewBackup
import com.marcogn.thepatientgamerhelper.domain.export.parseReviewBackupMarkdown
import com.marcogn.thepatientgamerhelper.domain.export.toReview
import com.marcogn.thepatientgamerhelper.domain.model.ReviewZipImportResult
import com.marcogn.thepatientgamerhelper.domain.repository.ReviewRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multi-review ZIP import (v2 §2.4). Validation is atomic on review *content*: every `.md` file in
 * the archive must parse before anything is written; if even one fails, nothing is imported and the
 * failing file names/reasons are reported back. Images are always best-effort — an absent/empty
 * `images/` folder, or one missing a specific referenced file, never fails validation and never
 * blocks the rest of the batch, it just leaves that one review without a cover (v2 §4).
 */
@Singleton
class ReviewZipImporter @Inject constructor(
    private val fileReader: ImportFileReader,
    private val archiveReader: ReviewZipArchiveReader,
    private val imageStorage: ImageStorage,
    private val reviewRepository: ReviewRepository,
) {
    suspend fun import(source: Uri): ReviewZipImportResult {
        val content = archiveReader.read(fileReader.readBytes(source))
        if (content.reviewMarkdownFiles.isEmpty()) {
            return ReviewZipImportResult.ValidationFailed(listOf("Nessun file .md trovato nell'archivio"))
        }

        val parsedByFileName = content.reviewMarkdownFiles.mapValues { (_, text) -> parseReviewBackupMarkdown(text) }
        val failures = parsedByFileName.filterValues { it.isFailure }
        if (failures.isNotEmpty()) {
            val failureMessages = failures.map { (fileName, result) ->
                "$fileName: ${result.exceptionOrNull()?.message.orEmpty()}"
            }
            return ReviewZipImportResult.ValidationFailed(failureMessages)
        }

        val parsedReviews: List<ParsedReviewBackup> = parsedByFileName.values.map { it.getOrThrow() }
        val reviews = parsedReviews.map { parsed ->
            val coverPath = parsed.coverImageFileName
                ?.let { fileName -> content.images[fileName] }
                ?.let { bytes -> imageStorage.writeBytes(newCoverFileName(parsed.coverImageFileName), bytes) }
            parsed.toReview(coverPath)
        }
        reviewRepository.upsertImported(reviews)
        return ReviewZipImportResult.Success(reviews.size)
    }

    /** Same reasoning as `BacklogImporter.newCoverFileName`: always mint a fresh name to avoid colliding with an unrelated file already present in the shared `covers/` directory. */
    private fun newCoverFileName(originalFileName: String?): String {
        val extension = originalFileName?.substringAfterLast('.', missingDelimiterValue = "jpg") ?: "jpg"
        return "${UUID.randomUUID()}.$extension"
    }
}
