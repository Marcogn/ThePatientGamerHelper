package com.marcogn.thepatientgamerhelper.domain.export

import com.marcogn.thepatientgamerhelper.domain.model.Review
import com.marcogn.thepatientgamerhelper.domain.model.label
import java.time.format.DateTimeFormatter
import java.util.Locale

private val markdownDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * Reddit-flavored markdown for a single review, meant for copy-paste into an r/patientgamer
 * post. Metadata lines use a bullet list rather than trailing-space hard breaks, since the
 * latter tends to get silently stripped by clipboards/editors before it ever reaches Reddit.
 */
fun Review.toRedditMarkdown(): String = buildString {
    appendLine("# $title")
    appendLine()
    appendLine("- **Voto:** ${"%.1f".format(Locale.ROOT, rating)}/10")
    appendLine("- **Stato:** ${status.label()}")
    if (platforms.isNotEmpty()) appendLine("- **Piattaforme:** ${platforms.joinToString(", ") { it.name }}")
    if (genres.isNotEmpty()) appendLine("- **Generi:** ${genres.joinToString(", ") { it.name }}")
    if (tags.isNotEmpty()) appendLine("- **Tag:** ${tags.joinToString(", ") { it.name }}")
    appendLine("- **Iniziato il:** ${startDate.format(markdownDateFormatter)}")
    appendLine("- **Terminato il:** ${endDate?.format(markdownDateFormatter) ?: "—"}")
    hoursPlayed?.let { appendLine("- **Ore di gioco:** ${"%.1f".format(Locale.ROOT, it)}") }

    if (pros.isNotEmpty()) {
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("## Pro")
        pros.forEach { appendLine("- $it") }
    }

    if (cons.isNotEmpty()) {
        appendLine()
        appendLine("## Contro")
        cons.forEach { appendLine("- $it") }
    }

    if (reviewText.isNotBlank()) {
        appendLine()
        appendLine("---")
        appendLine()
        append(reviewText.trim())
        appendLine()
    }
}
