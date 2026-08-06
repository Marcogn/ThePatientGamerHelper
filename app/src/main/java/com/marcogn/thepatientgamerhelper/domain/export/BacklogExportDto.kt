package com.marcogn.thepatientgamerhelper.domain.export

import com.marcogn.thepatientgamerhelper.domain.model.BacklogHistoryEventType
import com.marcogn.thepatientgamerhelper.domain.model.BacklogItem
import com.marcogn.thepatientgamerhelper.domain.model.BacklogItemStatus
import com.marcogn.thepatientgamerhelper.domain.model.BacklogList
import com.marcogn.thepatientgamerhelper.domain.model.ImportedBacklogComment
import com.marcogn.thepatientgamerhelper.domain.model.ImportedBacklogHistoryEntry
import com.marcogn.thepatientgamerhelper.domain.model.ImportedBacklogItem
import com.marcogn.thepatientgamerhelper.domain.model.ImportedBacklogList
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * User-facing backlog export/import (Fase 8) — deliberately a separate format from
 * `domain/backup` (Drive-only, whole *review* library, full-overwrite restore): this one is a
 * portable "share/merge your backlog" file the user creates/opens directly via SAF, and import is
 * always additive (see `BacklogRepository.importLists`), never a device restore. [copertina] is a
 * bare file name (same convention as `BackupReviewDto.coverImageFileName`), resolved against
 * `images/<copertina>` in the zip archive — see `data/export/BacklogExportArchive.kt`.
 */
@Serializable
data class BacklogCommentExportDto(val testo: String, val timestamp: String)

@Serializable
data class BacklogHistoryExportDto(val tipo: String, val timestamp: String, val dettaglio: String?)

@Serializable
data class BacklogItemExportDto(
    val titolo: String,
    val piattaforme: List<String>,
    val generi: List<String>,
    val tag: List<String>,
    val stato: String,
    val aggiuntoIl: String,
    val dataInizio: String?,
    val dataCompletamento: String?,
    val notaAbbandono: String?,
    val annoUscita: Int?,
    val sviluppatore: String?,
    val hltbStoriaPrincipaleOre: Double?,
    val hltbStoriaPiuExtraOre: Double?,
    val hltbCompletistaOre: Double?,
    val copertina: String?,
    val commenti: List<BacklogCommentExportDto>,
    val storico: List<BacklogHistoryExportDto>,
)

@Serializable
data class BacklogListExportDto(val nome: String, val elementi: List<BacklogItemExportDto>)

@Serializable
data class BacklogExportPayload(
    val schemaVersion: Int = 1,
    val esportatoIl: String,
    val liste: List<BacklogListExportDto>,
)

fun buildBacklogExportPayload(
    lists: List<BacklogList>,
    items: List<BacklogItem>,
    exportedAt: Instant = Instant.now(),
): BacklogExportPayload {
    val itemsByList = items.groupBy { it.listId }
    return BacklogExportPayload(
        esportatoIl = exportedAt.toString(),
        liste = lists.map { list ->
            BacklogListExportDto(
                nome = list.name,
                elementi = itemsByList[list.id].orEmpty().sortedBy { it.position }.map { it.toExportDto() },
            )
        },
    )
}

private fun BacklogItem.toExportDto(): BacklogItemExportDto = BacklogItemExportDto(
    titolo = title,
    piattaforme = platforms.map { it.name },
    generi = genres.map { it.name },
    tag = tags.map { it.name },
    stato = status.name,
    aggiuntoIl = addedAt.toString(),
    dataInizio = startDate?.toString(),
    dataCompletamento = completedDate?.toString(),
    notaAbbandono = abandonNote,
    annoUscita = releaseYear,
    sviluppatore = developer,
    hltbStoriaPrincipaleOre = hltbMainStoryHours,
    hltbStoriaPiuExtraOre = hltbMainExtraHours,
    hltbCompletistaOre = hltbCompletionistHours,
    copertina = coverImagePath?.substringAfterLast('/'),
    commenti = comments.map { BacklogCommentExportDto(testo = it.text, timestamp = it.timestamp.toString()) },
    storico = history.map { BacklogHistoryExportDto(tipo = it.type.name, timestamp = it.timestamp.toString(), dettaglio = it.detail) },
)

/**
 * [resolveCoverPath] receives the DTO's bare cover file name and must return the new absolute path
 * already written to local storage (or null if there's no cover / it couldn't be restored) — kept
 * as a callback so this stays pure Kotlin (no `ImageStorage`/Android dependency here).
 */
fun BacklogExportPayload.toImportedLists(resolveCoverPath: (String) -> String?): List<ImportedBacklogList> =
    liste.map { listDto ->
        ImportedBacklogList(
            name = listDto.nome,
            items = listDto.elementi.map { it.toImportedItem(resolveCoverPath) },
        )
    }

private fun BacklogItemExportDto.toImportedItem(resolveCoverPath: (String) -> String?): ImportedBacklogItem = ImportedBacklogItem(
    title = titolo,
    platformNames = piattaforme,
    genreNames = generi,
    tagNames = tag,
    coverImagePath = copertina?.let(resolveCoverPath),
    status = runCatching { BacklogItemStatus.valueOf(stato) }.getOrDefault(BacklogItemStatus.DA_INIZIARE),
    addedAt = runCatching { Instant.parse(aggiuntoIl) }.getOrDefault(Instant.now()),
    startDate = dataInizio?.let(LocalDate::parse),
    completedDate = dataCompletamento?.let(LocalDate::parse),
    abandonNote = notaAbbandono,
    releaseYear = annoUscita,
    developer = sviluppatore,
    hltbMainStoryHours = hltbStoriaPrincipaleOre,
    hltbMainExtraHours = hltbStoriaPiuExtraOre,
    hltbCompletionistHours = hltbCompletistaOre,
    comments = commenti.map { ImportedBacklogComment(text = it.testo, timestamp = Instant.parse(it.timestamp)) },
    history = storico.map {
        ImportedBacklogHistoryEntry(
            type = runCatching { BacklogHistoryEventType.valueOf(it.tipo) }.getOrDefault(BacklogHistoryEventType.CREATO),
            timestamp = Instant.parse(it.timestamp),
            detail = it.dettaglio,
        )
    },
)

private val backlogExportJson = Json {
    prettyPrint = false
    ignoreUnknownKeys = true
}

fun BacklogExportPayload.toJson(): String = backlogExportJson.encodeToString(this)

fun String.toBacklogExportPayload(): BacklogExportPayload = backlogExportJson.decodeFromString(this)
