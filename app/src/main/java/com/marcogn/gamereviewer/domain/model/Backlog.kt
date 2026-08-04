package com.marcogn.gamereviewer.domain.model

import java.time.Instant
import java.time.LocalDate

enum class BacklogItemStatus { DA_INIZIARE, IN_CORSO, COMPLETATO, ABBANDONATO, IN_PAUSA }

enum class BacklogHistoryEventType { CREATO, CAMBIO_STATO, CAMBIO_LISTA, COMMENTO, RECENSIONE_COLLEGATA }

data class BacklogList(
    val id: Long,
    val name: String,
    val position: Int,
    val createdAt: Instant,
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
)
