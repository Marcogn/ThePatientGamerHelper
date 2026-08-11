package com.marcogn.thepatientgamerhelper.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Input to [com.marcogn.thepatientgamerhelper.domain.repository.BacklogRepository.importLists] —
 * an already-decoded backlog export (see `domain/export/BacklogExportDto.kt`) ready to be written
 * to Room. Import is additive, never a replace: every imported list becomes a brand new list
 * (even if same-named one exists) and every item gets a fresh id, so importing the same file twice
 * is safe but not idempotent — see CLAUDE.md, Fase 8, for the reasoning.
 */
data class ImportedBacklogList(
    val name: String,
    val items: List<ImportedBacklogItem>,
)

data class ImportedBacklogItem(
    val title: String,
    val platformNames: List<String>,
    val genreNames: List<String>,
    val tagNames: List<String>,
    val coverImagePath: String?,
    val status: BacklogItemStatus,
    val addedAt: Instant,
    val startDate: LocalDate?,
    val completedDate: LocalDate?,
    val abandonNote: String?,
    val releaseYear: Int?,
    val developer: String?,
    val hltbMainStoryHours: Double?,
    val hltbMainExtraHours: Double?,
    val hltbCompletionistHours: Double?,
    /**
     * The exported [reviewId], carried through only as a candidate — `BacklogRepositoryImpl.importLists`
     * links it back onto the imported item *only if* a review with that id already exists on this
     * device (best-effort, never a validation gate: the review usually belongs to whichever library
     * exported the file and may well not exist here).
     */
    val reviewId: String?,
    val comments: List<ImportedBacklogComment>,
    val history: List<ImportedBacklogHistoryEntry>,
)

data class ImportedBacklogComment(val text: String, val timestamp: Instant)

data class ImportedBacklogHistoryEntry(val type: BacklogHistoryEventType, val timestamp: Instant, val detail: String?)

/** Outcome summary shown to the user after an import, e.g. "2 liste, 14 elementi importati". */
data class BacklogImportResult(val listsImported: Int, val itemsImported: Int)
