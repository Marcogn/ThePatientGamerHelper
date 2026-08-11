package com.marcogn.thepatientgamerhelper.domain.export

import com.marcogn.thepatientgamerhelper.domain.model.Genre
import com.marcogn.thepatientgamerhelper.domain.model.Platform
import com.marcogn.thepatientgamerhelper.domain.model.Review
import com.marcogn.thepatientgamerhelper.domain.model.ReviewDraft
import com.marcogn.thepatientgamerhelper.domain.model.ReviewStatus
import com.marcogn.thepatientgamerhelper.domain.model.Tag
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Front-matter Markdown format for a single review (reviews/backlog import-export spec v2
 * §2.1/§2.2/§2.4), replacing the earlier bare "Reddit-style" format (`ReviewMarkdownFormatter`/
 * `ReviewMarkdownParser`, Fase 8): a YAML front matter block carries every field — including ones
 * the create/edit form never shows, kept only for round-trip fidelity, see [Review]'s doc comment —
 * followed by the same Pros/Cons/free-text body every review already has. `platforms`/`genres` are
 * emitted as arrays, not the single string the spec's example fixture happens to show for a review
 * with exactly one of each: the app's data model is many-to-many for both (see CLAUDE.md, "Product
 * decisions already made"), so an array is the only lossless shape.
 *
 * The front matter reader/writer here is a small, fixed `key: value` implementation, not a general
 * YAML parser — the schema is entirely our own fixed export shape, so a YAML library isn't
 * warranted (same "no dependency without genuine need" reasoning as everywhere else in this repo).
 */

private val statusToWord = mapOf(
    ReviewStatus.IN_CORSO to "in_progress",
    ReviewStatus.COMPLETATO to "completed",
    ReviewStatus.ABBANDONATO to "abandoned",
)
private val wordToStatus = statusToWord.entries.associate { (key, value) -> value to key }

/** Every field parsed from a backup Markdown file — a superset of [ReviewDraft], since multi-review import (§2.4) needs [id]/[createdAt]/[updatedAt] and the form-invisible fields too, not just what the create/edit form shows. */
data class ParsedReviewBackup(
    val id: String,
    val title: String,
    val platformNames: List<String>,
    val genreNames: List<String>,
    val tagNames: List<String>,
    val rating: Double,
    val status: ReviewStatus,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val hoursPlayed: Double?,
    val coverImageFileName: String?,
    val developer: String?,
    val publisher: String?,
    val releaseYear: Int?,
    val metadataSource: String?,
    val externalId: String?,
    val linkedBacklogItemId: String?,
    val pros: List<String>,
    val cons: List<String>,
    val reviewText: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Review.toBackupMarkdown(): String = buildString {
    appendLine("---")
    appendLine("id: $id")
    appendLine("title: ${yamlString(title)}")
    if (platforms.isNotEmpty()) appendLine("platforms: ${yamlArray(platforms.map { it.name })}")
    if (genres.isNotEmpty()) appendLine("genres: ${yamlArray(genres.map { it.name })}")
    if (tags.isNotEmpty()) appendLine("tags: ${yamlArray(tags.map { it.name })}")
    appendLine("score: ${"%.1f".format(Locale.ROOT, rating)}")
    appendLine("status: ${statusToWord.getValue(status)}")
    appendLine("startDate: $startDate")
    endDate?.let { appendLine("endDate: $it") }
    hoursPlayed?.let { appendLine("hoursPlayed: $it") }
    coverImagePath?.let { appendLine("coverImage: ${yamlString("../images/${it.substringAfterLast('/')}")}") }
    developer?.let { appendLine("developer: ${yamlString(it)}") }
    publisher?.let { appendLine("publisher: ${yamlString(it)}") }
    releaseYear?.let { appendLine("releaseYear: $it") }
    metadataSource?.let { appendLine("metadataSource: $it") }
    externalId?.let { appendLine("externalId: ${yamlString(it)}") }
    linkedBacklogItemId?.let { appendLine("linkedBacklogItemId: $it") }
    appendLine("createdAt: $createdAt")
    appendLine("updatedAt: $updatedAt")
    appendLine("---")

    if (pros.isNotEmpty()) {
        appendLine()
        appendLine("## Pros")
        pros.forEach { appendLine("- $it") }
    }
    if (cons.isNotEmpty()) {
        appendLine()
        appendLine("## Cons")
        cons.forEach { appendLine("- $it") }
    }

    appendLine()
    appendLine("## Review")
    appendLine()
    append(reviewText.trim())
    appendLine()
}

/**
 * Parses a [Review.toBackupMarkdown] file. Strict on the fields the exporter always writes
 * unconditionally (id/title/score/status/startDate) since a file missing one of those isn't a
 * review this app produced; lenient on everything conditionally emitted.
 */
fun parseReviewBackupMarkdown(content: String): Result<ParsedReviewBackup> = runCatching {
    val lines = content.replace("\r\n", "\n").split("\n")
    check(lines.isNotEmpty() && lines[0].trim() == "---") { "Front matter mancante (attesa riga \"---\" iniziale)" }

    var index = 1
    val frontMatter = LinkedHashMap<String, String>()
    while (index < lines.size && lines[index].trim() != "---") {
        val line = lines[index]
        if (line.isNotBlank()) {
            val separator = line.indexOf(':')
            check(separator > 0) { "Riga non valida nel front matter: \"$line\"" }
            frontMatter[line.substring(0, separator).trim()] = line.substring(separator + 1).trim()
        }
        index++
    }
    check(index < lines.size) { "Front matter non terminato (manca la riga \"---\" finale)" }
    index++

    val id = frontMatter["id"]?.takeIf { it.isNotBlank() }
        ?: error("Campo \"id\" mancante nel front matter")
    val title = frontMatter["title"]?.let(::parseYamlString)?.takeIf { it.isNotBlank() }
        ?: error("Campo \"title\" mancante nel front matter")
    val rating = frontMatter["score"]?.toDoubleOrNull()
        ?: error("Campo \"score\" mancante o non valido")
    val status = frontMatter["status"]?.trim()?.let { wordToStatus[it] }
        ?: error("Campo \"status\" mancante o non riconosciuto (atteso in_progress/completed/abandoned)")
    val startDate = frontMatter["startDate"]?.let(::parseYamlDate)
        ?: error("Campo \"startDate\" mancante o non valido (atteso aaaa-mm-gg)")

    val endDate = frontMatter["endDate"]?.takeIf { it.isNotBlank() }?.let(::parseYamlDate)
    val hoursPlayed = frontMatter["hoursPlayed"]?.toDoubleOrNull()
    val platformNames = frontMatter["platforms"]?.let(::parseYamlArray).orEmpty()
    val genreNames = frontMatter["genres"]?.let(::parseYamlArray).orEmpty()
    val tagNames = frontMatter["tags"]?.let(::parseYamlArray).orEmpty()
    val coverImageFileName = frontMatter["coverImage"]?.let(::parseYamlString)
        ?.takeIf { it.isNotBlank() }?.substringAfterLast('/')
    val developer = frontMatter["developer"]?.let(::parseYamlString)?.takeIf { it.isNotBlank() }
    val publisher = frontMatter["publisher"]?.let(::parseYamlString)?.takeIf { it.isNotBlank() }
    val releaseYear = frontMatter["releaseYear"]?.toIntOrNull()
    val metadataSource = frontMatter["metadataSource"]?.let(::parseYamlString)?.takeIf { it.isNotBlank() }
    val externalId = frontMatter["externalId"]?.let(::parseYamlString)?.takeIf { it.isNotBlank() }
    val linkedBacklogItemId = frontMatter["linkedBacklogItemId"]?.takeIf { it.isNotBlank() }
    val createdAt = frontMatter["createdAt"]?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()
    val updatedAt = frontMatter["updatedAt"]?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: createdAt

    fun skipBlank() {
        while (index < lines.size && lines[index].isBlank()) index++
    }

    val pros = mutableListOf<String>()
    val cons = mutableListOf<String>()

    skipBlank()
    if (index < lines.size && lines[index].trim() == "## Pros") {
        index++
        while (index < lines.size && lines[index].startsWith("- ")) {
            pros.add(lines[index].removePrefix("- ").trim())
            index++
        }
    }

    skipBlank()
    if (index < lines.size && lines[index].trim() == "## Cons") {
        index++
        while (index < lines.size && lines[index].startsWith("- ")) {
            cons.add(lines[index].removePrefix("- ").trim())
            index++
        }
    }

    skipBlank()
    if (index < lines.size && lines[index].trim() == "## Review") index++
    val reviewText = lines.subList(index, lines.size).joinToString("\n").trim()

    ParsedReviewBackup(
        id = id,
        title = title,
        platformNames = platformNames,
        genreNames = genreNames,
        tagNames = tagNames,
        rating = rating,
        status = status,
        startDate = startDate,
        endDate = endDate,
        hoursPlayed = hoursPlayed,
        coverImageFileName = coverImageFileName,
        developer = developer,
        publisher = publisher,
        releaseYear = releaseYear,
        metadataSource = metadataSource,
        externalId = externalId,
        linkedBacklogItemId = linkedBacklogItemId,
        pros = pros,
        cons = cons,
        reviewText = reviewText,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

/** Full [Review] for multi-review import (§2.4) — [resolvedCoverImagePath] is the local absolute path a matching image was already written to, or null (best-effort: no cover). */
fun ParsedReviewBackup.toReview(resolvedCoverImagePath: String?): Review = Review(
    id = id,
    title = title,
    platforms = platformNames.map { Platform(id = 0L, name = it) },
    genres = genreNames.map { Genre(id = 0L, name = it) },
    tags = tagNames.map { Tag(id = 0L, name = it) },
    rating = rating,
    startDate = startDate,
    endDate = endDate,
    hoursPlayed = hoursPlayed,
    status = status,
    pros = pros,
    cons = cons,
    reviewText = reviewText,
    coverImagePath = resolvedCoverImagePath,
    developer = developer,
    publisher = publisher,
    releaseYear = releaseYear,
    metadataSource = metadataSource,
    externalId = externalId,
    linkedBacklogItemId = linkedBacklogItemId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/**
 * [ReviewDraft] for the single-review "replace form content" import (§2.2) — only the fields the
 * create/edit form actually shows. [id]/[createdAt]/[updatedAt]/[developer]/[publisher]/
 * [releaseYear]/[metadataSource]/[externalId]/[linkedBacklogItemId] are parsed (needed for
 * validation, and for the multi-import path above) but never applied here: the review being
 * edited keeps its own identity and whatever it already had for those fields, per the spec.
 * [resolvedCoverImagePath] is best-effort, same as the batch path — a standalone single `.md` pick
 * never carries accompanying image bytes, so this is always null for this entry point today.
 */
fun ParsedReviewBackup.toFormDraft(resolvedCoverImagePath: String?): ReviewDraft = ReviewDraft(
    title = title,
    platformNames = platformNames,
    genreNames = genreNames,
    tagNames = tagNames,
    rating = rating,
    startDate = startDate,
    endDate = endDate,
    hoursPlayed = hoursPlayed,
    status = status,
    pros = pros,
    cons = cons,
    reviewText = reviewText,
    coverImagePath = resolvedCoverImagePath,
)

private fun yamlString(value: String): String = "\"${value.replace("\"", "\\\"")}\""

private fun yamlArray(values: List<String>): String = values.joinToString(", ", "[", "]") { yamlString(it) }

private fun parseYamlString(raw: String): String =
    if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
        raw.substring(1, raw.length - 1).replace("\\\"", "\"")
    } else {
        raw
    }

private fun parseYamlDate(raw: String): LocalDate = try {
    LocalDate.parse(parseYamlString(raw))
} catch (e: DateTimeParseException) {
    error("Data non valida: \"$raw\" (atteso aaaa-mm-gg)")
}

private fun parseYamlArray(raw: String): List<String> {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
    val inner = trimmed.substring(1, trimmed.length - 1).trim()
    if (inner.isEmpty()) return emptyList()
    return splitYamlArrayItems(inner).map(::parseYamlString).map { it.trim() }.filter { it.isNotEmpty() }
}

private fun splitYamlArrayItems(inner: String): List<String> {
    val items = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    for (c in inner) {
        when {
            c == '"' -> {
                inQuotes = !inQuotes
                current.append(c)
            }
            c == ',' && !inQuotes -> {
                items.add(current.toString().trim())
                current.clear()
            }
            else -> current.append(c)
        }
    }
    if (current.isNotBlank()) items.add(current.toString().trim())
    return items
}
