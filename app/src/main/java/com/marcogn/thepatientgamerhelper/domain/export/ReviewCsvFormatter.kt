package com.marcogn.thepatientgamerhelper.domain.export

import com.marcogn.thepatientgamerhelper.domain.model.Review

/**
 * RFC 4180-ish CSV export of the whole library. Multi-value fields are joined with "; ". The column
 * names and the status values come from [labels] (app language); everything else is data.
 */
fun List<Review>.toCsvExport(labels: ExportLabels = ExportLabels.ITALIAN): String {
    require(labels.csvColumns.size == ExportLabels.CSV_COLUMN_COUNT) { "CSV needs ${ExportLabels.CSV_COLUMN_COUNT} column names" }
    val builder = StringBuilder()
    builder.append(labels.csvColumns.joinToString(",") { it.csvEscaped() }).append("\r\n")
    for (review in this) {
        builder.append(review.toCsvRow(labels).joinToString(",") { it.csvEscaped() }).append("\r\n")
    }
    return builder.toString()
}

private fun Review.toCsvRow(labels: ExportLabels): List<String> = listOf(
    id,
    title,
    platforms.joinToString("; ") { it.name },
    genres.joinToString("; ") { it.name },
    tags.joinToString("; ") { it.name },
    rating.toString(),
    startDate.toString(),
    endDate?.toString().orEmpty(),
    hoursPlayed?.toString().orEmpty(),
    labels.statusLabel(status),
    pros.joinToString("; "),
    cons.joinToString("; "),
    reviewText,
    coverImagePath.orEmpty(),
    createdAt.toString(),
    updatedAt.toString(),
)

private fun String.csvEscaped(): String =
    if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + replace("\"", "\"\"") + "\""
    } else {
        this
    }
