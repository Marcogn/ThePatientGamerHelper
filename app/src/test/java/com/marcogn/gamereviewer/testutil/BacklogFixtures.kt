package com.marcogn.gamereviewer.testutil

import com.marcogn.gamereviewer.domain.model.BacklogItem
import com.marcogn.gamereviewer.domain.model.BacklogItemStatus
import com.marcogn.gamereviewer.domain.model.Genre
import com.marcogn.gamereviewer.domain.model.Platform
import com.marcogn.gamereviewer.domain.model.Tag
import java.time.Instant

fun sampleBacklogItem(
    id: String = "item-1",
    listId: Long = 1L,
    title: String = "Hades",
    platforms: List<String> = listOf("PC"),
    genres: List<String> = listOf("Roguelike"),
    tags: List<String> = emptyList(),
    status: BacklogItemStatus = BacklogItemStatus.DA_INIZIARE,
    position: Int = 0,
    addedAt: Instant = Instant.parse("2026-01-01T10:00:00Z"),
): BacklogItem = BacklogItem(
    id = id,
    listId = listId,
    title = title,
    platforms = platforms.mapIndexed { index, name -> Platform(index.toLong(), name) },
    genres = genres.mapIndexed { index, name -> Genre(index.toLong(), name) },
    tags = tags.mapIndexed { index, name -> Tag(index.toLong(), name) },
    coverImagePath = null,
    status = status,
    position = position,
    addedAt = addedAt,
    startDate = null,
    completedDate = null,
    reviewId = null,
    abandonNote = null,
    releaseYear = null,
    developer = null,
    comments = emptyList(),
    history = emptyList(),
)
