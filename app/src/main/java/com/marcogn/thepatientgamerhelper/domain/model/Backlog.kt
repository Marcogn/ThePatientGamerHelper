package com.marcogn.thepatientgamerhelper.domain.model

import java.time.Instant
import java.time.LocalDate

enum class BacklogItemStatus { DA_INIZIARE, IN_CORSO, COMPLETATO, ABBANDONATO, IN_PAUSA }

enum class BacklogHistoryEventType { CREATO, CAMBIO_STATO, CAMBIO_LISTA, COMMENTO, RECENSIONE_COLLEGATA }

/**
 * [systemKind] mirrors `BacklogListEntity.systemKind` (see [BacklogListKind]) — carried on the
 * domain model purely for Drive backup/restore round-trip fidelity, so a restored "Completed with
 * review" list is still recognized as that system list instead of `getOrCreateSystemList()`
 * creating a duplicate. `null` for a regular, user-created list.
 */
data class BacklogList(
    val id: Long,
    val name: String,
    val position: Int,
    val createdAt: Instant,
    val systemKind: String? = null,
)

data class BacklogComment(
    val id: Long,
    val itemId: String,
    val text: String,
    val timestamp: Instant,
)

data class BacklogHistoryEntry(
    val id: Long,
    val itemId: String,
    val type: BacklogHistoryEventType,
    val timestamp: Instant,
    val detail: String?,
)

/**
 * A single backlog entry: a game the user intends to play, tracked through a lightweight
 * workflow. [releaseYear]/[developer] are cataloging metadata only settable via the TheGamesDB
 * "cerca online" flow (Tappa 2) — no manual field for them, consistent with the rest of the
 * backlog form staying minimal.
 */
data class BacklogItem(
    val id: String,
    val listId: Long,
    val title: String,
    val platforms: List<Platform>,
    val genres: List<Genre>,
    val tags: List<Tag>,
    val coverImagePath: String?,
    val status: BacklogItemStatus,
    val position: Int,
    val addedAt: Instant,
    val startDate: LocalDate?,
    val completedDate: LocalDate?,
    val reviewId: String?,
    val abandonNote: String?,
    val releaseYear: Int?,
    val developer: String?,
    /** Best-effort HowLongToBeat estimate (Fase 8) — only ever set via the "cerca online" flow, no manual editor. */
    val hltbMainStoryHours: Double?,
    val hltbMainExtraHours: Double?,
    val hltbCompletionistHours: Double?,
    val comments: List<BacklogComment>,
    val history: List<BacklogHistoryEntry>,
)

/** Editable draft used by the backlog item create/edit form — status changes go through a dedicated selector, not this form. */
data class BacklogItemDraft(
    val title: String,
    val platformNames: List<String>,
    val genreNames: List<String>,
    val tagNames: List<String>,
    val coverImagePath: String?,
    val releaseYear: Int? = null,
    val developer: String? = null,
    val hltbMainStoryHours: Double? = null,
    val hltbMainExtraHours: Double? = null,
    val hltbCompletionistHours: Double? = null,
) {
    companion object {
        fun empty() = BacklogItemDraft(
            title = "",
            platformNames = emptyList(),
            genreNames = emptyList(),
            tagNames = emptyList(),
            coverImagePath = null,
            releaseYear = null,
            developer = null,
            hltbMainStoryHours = null,
            hltbMainExtraHours = null,
            hltbCompletionistHours = null,
        )
    }
}

fun BacklogItem.toDraft(): BacklogItemDraft = BacklogItemDraft(
    title = title,
    platformNames = platforms.map { it.name },
    genreNames = genres.map { it.name },
    tagNames = tags.map { it.name },
    coverImagePath = coverImagePath,
    releaseYear = releaseYear,
    developer = developer,
    hltbMainStoryHours = hltbMainStoryHours,
    hltbMainExtraHours = hltbMainExtraHours,
    hltbCompletionistHours = hltbCompletionistHours,
)
