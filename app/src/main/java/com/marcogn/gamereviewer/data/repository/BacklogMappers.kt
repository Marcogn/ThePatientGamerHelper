package com.marcogn.gamereviewer.data.repository

import com.marcogn.gamereviewer.data.local.entity.BacklogCommentEntity
import com.marcogn.gamereviewer.data.local.entity.BacklogHistoryEntryEntity
import com.marcogn.gamereviewer.data.local.entity.BacklogItemWithDetails
import com.marcogn.gamereviewer.data.local.entity.BacklogListEntity
import com.marcogn.gamereviewer.domain.model.BacklogComment
import com.marcogn.gamereviewer.domain.model.BacklogHistoryEntry
import com.marcogn.gamereviewer.domain.model.BacklogItem
import com.marcogn.gamereviewer.domain.model.BacklogList

fun BacklogListEntity.toDomain() = BacklogList(id = id, name = name, position = position, createdAt = createdAt)

fun BacklogCommentEntity.toDomain() = BacklogComment(id = id, itemId = itemId, text = text, timestamp = timestamp)

fun BacklogHistoryEntryEntity.toDomain() =
    BacklogHistoryEntry(id = id, itemId = itemId, type = eventType, timestamp = timestamp, detail = detail)

fun BacklogItemWithDetails.toDomain(): BacklogItem = BacklogItem(
    id = item.id,
    listId = item.listId,
    title = item.title,
    platforms = platforms.map { it.toDomain() }.sortedBy { it.name },
    genres = genres.map { it.toDomain() }.sortedBy { it.name },
    tags = tags.map { it.toDomain() }.sortedBy { it.name },
    coverImagePath = item.coverImagePath,
    status = item.status,
    position = item.position,
    addedAt = item.addedAt,
    startDate = item.startDate,
    completedDate = item.completedDate,
    reviewId = item.reviewId,
    abandonNote = item.abandonNote,
    releaseYear = item.releaseYear,
    developer = item.developer,
    comments = comments.sortedBy { it.timestamp }.map { it.toDomain() },
    history = history.sortedBy { it.timestamp }.map { it.toDomain() },
)
