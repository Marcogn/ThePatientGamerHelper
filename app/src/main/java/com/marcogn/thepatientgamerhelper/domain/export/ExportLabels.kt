package com.marcogn.thepatientgamerhelper.domain.export

import com.marcogn.thepatientgamerhelper.domain.model.ReviewStatus

/**
 * The human-readable words of the exported files (CSV header and status values, PDF captions, suggested
 * file names), as plain strings so `domain/export` stays free of Android resources: `data/export`
 * builds an instance from the string resources of the app language ([ExportLabelsProvider]), the pure
 * formatters just take it. Defaults are Italian, the project's default language.
 *
 * What is **not** here, on purpose: the front-matter keys and `## Pros`/`## Cons`/`## Review`
 * headings of the Markdown format, the JSON field names and the backup/backlog-archive structure —
 * those are the file formats, identical in every language, so a file exported in one language imports
 * in the other (reviews/backlog import-export spec v2).
 */
data class ExportLabels(
    /** One name per CSV column, in the order of [ReviewCsvFormatter]'s row. */
    val csvColumns: List<String> = ITALIAN_CSV_COLUMNS,
    val statusInProgress: String = "In corso",
    val statusCompleted: String = "Completato",
    val statusAbandoned: String = "Abbandonato",
    val pdfScore: String = "Voto",
    val pdfStatus: String = "Stato",
    val pdfPlatforms: String = "Piattaforme",
    val pdfGenres: String = "Generi",
    val pdfTags: String = "Tag",
    val pdfStarted: String = "Iniziato il",
    val pdfFinished: String = "Terminato il",
    val pdfHoursPlayed: String = "Ore di gioco",
    val pdfPros: String = "Pro",
    val pdfCons: String = "Contro",
    val pdfReview: String = "Recensione",
    /** Slug used when a review title has no letters or digits. */
    val fallbackReviewFileName: String = "recensione",
    val libraryFilePrefix: String = "recensioni-videogiochi",
    val backlogFilePrefix: String = "backlog",
) {
    fun statusLabel(status: ReviewStatus): String = when (status) {
        ReviewStatus.IN_CORSO -> statusInProgress
        ReviewStatus.COMPLETATO -> statusCompleted
        ReviewStatus.ABBANDONATO -> statusAbandoned
    }

    companion object {
        val ITALIAN = ExportLabels()

        const val CSV_COLUMN_COUNT = 16
    }
}

private val ITALIAN_CSV_COLUMNS = listOf(
    "id", "titolo", "piattaforme", "generi", "tag", "voto", "dataInizio", "dataFine",
    "oreGioco", "stato", "pro", "contro", "testoRecensione", "copertina", "creatoIl", "modificatoIl",
)
