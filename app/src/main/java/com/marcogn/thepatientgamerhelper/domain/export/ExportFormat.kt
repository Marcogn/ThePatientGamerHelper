package com.marcogn.thepatientgamerhelper.domain.export

/**
 * Export formats from spec section 3.3/5. JSON/CSV cover the whole library (raw data
 * backup/portability); Markdown covers a single review (Reddit post body); PDF covers both a
 * single review and the whole library in batch.
 */
enum class ExportFormat(val mimeType: String, val fileExtension: String) {
    JSON(mimeType = "application/json", fileExtension = "json"),
    CSV(mimeType = "text/csv", fileExtension = "csv"),
    MARKDOWN(mimeType = "text/markdown", fileExtension = "md"),
    PDF(mimeType = "application/pdf", fileExtension = "pdf"),
}
