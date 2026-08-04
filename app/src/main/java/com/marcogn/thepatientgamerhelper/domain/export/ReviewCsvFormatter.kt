package com.marcogn.thepatientgamerhelper.domain.export

import com.marcogn.thepatientgamerhelper.domain.model.Review

private val CSV_HEADER = listOf(
    "id", "titolo", "piattaforme", "generi", "tag", "voto", "dataInizio", "dataFine",
    "oreGioco", "stato", "pro", "contro", "testoRecensione", "copertina", "creatoIl", "modificatoIl",
)

/** RFC 4180-ish CSV export of the whole library. Multi-value fields are joined with "; ". */
fun List<Review>.toCsvExport(): String {
    val builder = StringBuilder()
    builder.append(CSV_HEADER.joinToString(",") { it.csvEscaped() }).append("\r\n")
    for (review in this) {
        builder.append(review.toCsvRow().joinToString(",") { it.csvEscaped() }).append("\r\n")
    }
    return builder.toString()
}

private fun Review.toCsvRow(): List<String> = listOf(
    id,
    title,
    platforms.joinToString("; ") { it.name },
    genres.joinToString("; ") { it.name },
    tags.joinToString("; ") { it.name },
    rating.toString(),
    startDate.toString(),
    endDate?.toString().orEmpty(),
    hoursPlayed?.toString().orEmpty(),
    status.name,
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
