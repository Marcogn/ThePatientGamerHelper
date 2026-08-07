package com.marcogn.thepatientgamerhelper.domain.export

import com.marcogn.thepatientgamerhelper.domain.model.ReviewDraft
import com.marcogn.thepatientgamerhelper.domain.model.ReviewStatus
import com.marcogn.thepatientgamerhelper.domain.model.label
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val markdownImportDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val bulletPattern = Regex("""^-\s+\*\*(.+?):\*\*\s*(.*)$""")

/**
 * Reverse of [toRedditMarkdown] (Fase 8) — reconstructs a [ReviewDraft] from a previously exported
 * (or hand-written in the same shape) Markdown review. Strict on the fields the exporter always
 * writes unconditionally (title/voto/stato/data inizio) since a file missing one of those isn't a
 * review this app produced; lenient on everything conditionally emitted (platforms/genres/tags/
 * hours/pro/contro/body), matching exactly what [toRedditMarkdown] omits when empty. Labels stay
 * fixed Italian, same as the exporter itself (see CLAUDE.md, Fase 5: export content doesn't follow
 * the app's language setting) — this is a parser for that one fixed format, not a localized one.
 */
fun parseReviewMarkdown(content: String): Result<ReviewDraft> = runCatching {
    val lines = content.replace("\r\n", "\n").split("\n")
    var index = 0

    fun skipBlank() {
        while (index < lines.size && lines[index].isBlank()) index++
    }

    fun skipBlankAndDividers() {
        while (index < lines.size && (lines[index].isBlank() || lines[index].trim() == "---")) index++
    }

    skipBlank()
    check(index < lines.size && lines[index].startsWith("# ")) { "Manca il titolo (riga \"# Titolo\")" }
    val title = lines[index].removePrefix("# ").trim()
    check(title.isNotEmpty()) { "Il titolo non può essere vuoto" }
    index++
    skipBlank()

    val metadata = LinkedHashMap<String, String>()
    while (index < lines.size) {
        val match = bulletPattern.find(lines[index]) ?: break
        metadata[match.groupValues[1].trim()] = match.groupValues[2].trim()
        index++
    }

    val rating = metadata["Voto"]?.substringBefore("/")?.trim()?.toDoubleOrNull()
        ?: error("Voto mancante o non valido (atteso \"- **Voto:** X.X/10\")")
    val status = metadata["Stato"]?.let(::parseStatusLabel)
        ?: error("Stato mancante o non riconosciuto (atteso \"- **Stato:** In corso/Completato/Abbandonato\")")
    val startDate = metadata["Iniziato il"]?.let(::parseMarkdownDate)
        ?: error("Data di inizio mancante (atteso \"- **Iniziato il:** gg/mm/aaaa\")")
    val endDate = metadata["Terminato il"]?.takeIf { it != "—" }?.let(::parseMarkdownDate)
    val hoursPlayed = metadata["Ore di gioco"]?.toDoubleOrNull()
    val platforms = metadata["Piattaforme"]?.toBulletList().orEmpty()
    val genres = metadata["Generi"]?.toBulletList().orEmpty()
    val tags = metadata["Tag"]?.toBulletList().orEmpty()

    val pros = mutableListOf<String>()
    val cons = mutableListOf<String>()

    skipBlankAndDividers()
    if (index < lines.size && lines[index].trim() == "## Pro") {
        index++
        while (index < lines.size && lines[index].startsWith("- ")) {
            pros.add(lines[index].removePrefix("- ").trim())
            index++
        }
    }

    skipBlankAndDividers()
    if (index < lines.size && lines[index].trim() == "## Contro") {
        index++
        while (index < lines.size && lines[index].startsWith("- ")) {
            cons.add(lines[index].removePrefix("- ").trim())
            index++
        }
    }

    skipBlankAndDividers()
    val reviewText = lines.subList(index, lines.size).joinToString("\n").trim()

    ReviewDraft(
        title = title,
        platformNames = platforms,
        genreNames = genres,
        tagNames = tags,
        rating = rating,
        startDate = startDate,
        endDate = endDate,
        hoursPlayed = hoursPlayed,
        status = status,
        pros = pros,
        cons = cons,
        reviewText = reviewText,
        coverImagePath = null,
    )
}

private fun String.toBulletList(): List<String> = split(",").map { it.trim() }.filter { it.isNotEmpty() }

private fun parseStatusLabel(label: String): ReviewStatus? =
    ReviewStatus.entries.firstOrNull { it.label().equals(label, ignoreCase = true) }

private fun parseMarkdownDate(value: String): LocalDate = try {
    LocalDate.parse(value, markdownImportDateFormatter)
} catch (e: DateTimeParseException) {
    error("Data non valida: \"$value\" (atteso gg/mm/aaaa)")
}
