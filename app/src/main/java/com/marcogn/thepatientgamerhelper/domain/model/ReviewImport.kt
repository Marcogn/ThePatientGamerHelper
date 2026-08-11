package com.marcogn.thepatientgamerhelper.domain.model

/**
 * Outcome of a multi-review ZIP import (reviews/backlog import-export spec v2 §2.4) — validation is
 * atomic on content: [ValidationFailed] means nothing was imported at all, [Success] means every
 * review in the archive validated and was upserted.
 */
sealed interface ReviewZipImportResult {
    data class Success(val importedCount: Int) : ReviewZipImportResult
    data class ValidationFailed(val failures: List<String>) : ReviewZipImportResult
}
