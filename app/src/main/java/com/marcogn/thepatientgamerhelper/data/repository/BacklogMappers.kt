package com.marcogn.thepatientgamerhelper.data.repository

import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogCommentEntity
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogHistoryEntryEntity
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogItemWithDetails
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogListEntity
import com.marcogn.thepatientgamerhelper.domain.model.BacklogComment
import com.marcogn.thepatientgamerhelper.domain.model.BacklogHistoryEntry
import com.marcogn.thepatientgamerhelper.domain.model.BacklogItem
import com.marcogn.thepatientgamerhelper.domain.model.BacklogList

fun BacklogListEntity.toDomain() =
    BacklogList(id = id, name = name, position = position, createdAt = createdAt, systemKind = systemKind)

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
    hltbMainStoryHours = item.hltbMainStoryHours,
    hltbMainExtraHours = item.hltbMainExtraHours,
    hltbCompletionistHours = item.hltbCompletionistHours,
    comments = comments.sortedBy { it.timestamp }.map { it.toDomain() },
    history = history.sortedBy { it.timestamp }.map { it.toDomain() },
)
