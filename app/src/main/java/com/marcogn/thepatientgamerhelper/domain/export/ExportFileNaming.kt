package com.marcogn.thepatientgamerhelper.domain.export

import java.time.LocalDate

/** Turns free text into a filesystem-safe, lowercase-with-dashes slug. */
fun String.toFileSlug(): String {
    val normalized = trim().lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
    return normalized.ifEmpty { "recensione" }
}

fun suggestedReviewFileName(title: String, format: ExportFormat): String =
    "${title.toFileSlug()}.${format.fileExtension}"

fun suggestedLibraryFileName(format: ExportFormat, today: LocalDate = LocalDate.now()): String =
    "recensioni-videogiochi-$today.${format.fileExtension}"
