package com.marcogn.thepatientgamerhelper.domain.export

import java.time.LocalDate

/** Turns free text into a filesystem-safe, lowercase-with-dashes slug. */
fun String.toFileSlug(fallback: String = "recensione"): String {
    val normalized = trim().lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
    return normalized.ifEmpty { fallback }
}

fun suggestedReviewFileName(title: String, format: ExportFormat, labels: ExportLabels = ExportLabels.ITALIAN): String =
    "${title.toFileSlug(labels.fallbackReviewFileName)}.${format.fileExtension}"

fun suggestedLibraryFileName(
    format: ExportFormat,
    today: LocalDate = LocalDate.now(),
    labels: ExportLabels = ExportLabels.ITALIAN,
): String = "${labels.libraryFilePrefix}-$today.${format.fileExtension}"

/** Backlog export (Fase 8) is always a zip archive (data + covers) — see `BacklogExportArchive`. */
fun suggestedBacklogExportFileName(today: LocalDate = LocalDate.now(), labels: ExportLabels = ExportLabels.ITALIAN): String =
    "${labels.backlogFilePrefix}-$today.zip"

/** Multi-review ZIP export (v2 §2.3) — one `.md` per review plus covers, see `ReviewZipArchive`. */
fun suggestedReviewZipFileName(today: LocalDate = LocalDate.now(), labels: ExportLabels = ExportLabels.ITALIAN): String =
    "${labels.libraryFilePrefix}-$today.zip"
