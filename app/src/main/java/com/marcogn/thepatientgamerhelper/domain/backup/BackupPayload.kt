package com.marcogn.thepatientgamerhelper.domain.backup

import com.marcogn.thepatientgamerhelper.domain.model.BacklogComment
import com.marcogn.thepatientgamerhelper.domain.model.BacklogHistoryEntry
import com.marcogn.thepatientgamerhelper.domain.model.BacklogHistoryEventType
import com.marcogn.thepatientgamerhelper.domain.model.BacklogItem
import com.marcogn.thepatientgamerhelper.domain.model.BacklogItemStatus
import com.marcogn.thepatientgamerhelper.domain.model.BacklogList
import com.marcogn.thepatientgamerhelper.domain.model.Genre
import com.marcogn.thepatientgamerhelper.domain.model.Platform
import com.marcogn.thepatientgamerhelper.domain.model.Review
import com.marcogn.thepatientgamerhelper.domain.model.ReviewStatus
import com.marcogn.thepatientgamerhelper.domain.model.Tag
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Internal roundtrip format for Drive backups — deliberately separate from
 * `domain/export/ReviewExportDto` (Fase 2), which is a human-facing export with Italian field
 * names tied to the export feature's own evolution. [coverImageFileName] is a bare file name
 * (the cover's basename), not the absolute on-device path in [Review.coverImagePath]: the path
 * is device/install-specific and meaningless after a restore, while the backup zip carries the
 * image itself under `images/<coverImageFileName>`.
 */
@Serializable
data class BackupReviewDto(
    val id: String,
    val title: String,
    val platforms: List<String>,
    val genres: List<String>,
    val tags: List<String>,
    val rating: Double,
    val startDate: String,
    val endDate: String?,
    val hoursPlayed: Double?,
    val status: String,
    val pros: List<String>,
    val cons: List<String>,
    val reviewText: String,
    val coverImageFileName: String?,
    val developer: String? = null,
    val publisher: String? = null,
    val releaseYear: Int? = null,
    val metadataSource: String? = null,
    val externalId: String? = null,
    val linkedBacklogItemId: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

/**
 * Backlog DTOs, added alongside [BackupReviewDto] once it turned out the Drive backup format had
 * silently never covered the backlog at all — only the review library was ever restorable, so
 * every list/item/comment/history entry was permanently lost on a restore even though
 * `BackupArchiveBuilder` was (bug notwithstanding) shipping backlog *cover images* in the zip the
 * whole time with nothing in `data.json` to restore them against. IDs are preserved exactly like
 * [BackupReviewDto]'s — this is a same-device restore, not a merge, so there's no reason to
 * regenerate them the way the separate, portable `domain/export/BacklogExportDto.kt` format does.
 * [BackupBacklogItemDto.reviewId] is trusted directly (not re-validated against "does this review
 * exist on this device" like the export format's best-effort relink) because [BackupManager]
 * always restores reviews before backlog in the same operation, from the same backup — the linked
 * review is guaranteed to exist by construction, not merely likely to.
 */
@Serializable
data class BackupBacklogCommentDto(val id: Long, val text: String, val timestamp: String)

@Serializable
data class BackupBacklogHistoryDto(val id: Long, val type: String, val timestamp: String, val detail: String?)

@Serializable
data class BackupBacklogItemDto(
    val id: String,
    val listId: Long,
    val title: String,
    val platforms: List<String>,
    val genres: List<String>,
    val tags: List<String>,
    val coverImageFileName: String?,
    val status: String,
    val position: Int,
    val addedAt: String,
    val startDate: String?,
    val completedDate: String?,
    val reviewId: String?,
    val abandonNote: String?,
    val releaseYear: Int?,
    val developer: String?,
    val hltbMainStoryHours: Double?,
    val hltbMainExtraHours: Double?,
    val hltbCompletionistHours: Double?,
    val comments: List<BackupBacklogCommentDto>,
    val history: List<BackupBacklogHistoryDto>,
)

@Serializable
data class BackupBacklogListDto(
    val id: Long,
    val name: String,
    val position: Int,
    val createdAt: String,
    val systemKind: String?,
    val items: List<BackupBacklogItemDto>,
)

@Serializable
data class BackupPayload(
    val schemaVersion: Int = 1,
    val createdAt: String,
    val reviews: List<BackupReviewDto>,
    // Missing (not merely null) in every backup taken before this field existed — kotlinx.serialization's
    // default only covers a missing key, which is exactly this case, so those old backups still decode.
    val backlogLists: List<BackupBacklogListDto> = emptyList(),
)

fun Review.toBackupDto(): BackupReviewDto = BackupReviewDto(
    id = id,
    title = title,
    platforms = platforms.map { it.name },
    genres = genres.map { it.name },
    tags = tags.map { it.name },
    rating = rating,
    startDate = startDate.toString(),
    endDate = endDate?.toString(),
    hoursPlayed = hoursPlayed,
    status = status.name,
    pros = pros,
    cons = cons,
    reviewText = reviewText,
    coverImageFileName = coverImagePath?.let { path -> path.substringAfterLast('/') },
    developer = developer,
    publisher = publisher,
    releaseYear = releaseYear,
    metadataSource = metadataSource,
    externalId = externalId,
    linkedBacklogItemId = linkedBacklogItemId,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

/**
 * Rebuilds a [Review] from its backup DTO. [resolvedCoverImagePath] is the absolute path the
 * cover was restored to on *this* device (already written by the caller), not the file name
 * carried by the DTO. Platform/genre/tag ids are placeholders (0L): `ReviewRepository.replaceAll`
 * re-resolves each name to a lookup row, the same way `save()` does for the create/edit form.
 */
fun BackupReviewDto.toDomain(resolvedCoverImagePath: String?): Review = Review(
    id = id,
    title = title,
    platforms = platforms.map { Platform(id = 0L, name = it) },
    genres = genres.map { Genre(id = 0L, name = it) },
    tags = tags.map { Tag(id = 0L, name = it) },
    rating = rating,
    startDate = LocalDate.parse(startDate),
    endDate = endDate?.let(LocalDate::parse),
    hoursPlayed = hoursPlayed,
    status = ReviewStatus.valueOf(status),
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
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(updatedAt),
)

fun List<Review>.toBackupPayload(createdAt: Instant = Instant.now()): BackupPayload =
    BackupPayload(createdAt = createdAt.toString(), reviews = map { it.toBackupDto() })

fun buildBackupPayload(
    reviews: List<Review>,
    backlogLists: List<BacklogList>,
    backlogItems: List<BacklogItem>,
    createdAt: Instant = Instant.now(),
): BackupPayload {
    val itemsByList = backlogItems.groupBy { it.listId }
    return BackupPayload(
        createdAt = createdAt.toString(),
        reviews = reviews.map { it.toBackupDto() },
        backlogLists = backlogLists.map { list ->
            BackupBacklogListDto(
                id = list.id,
                name = list.name,
                position = list.position,
                createdAt = list.createdAt.toString(),
                systemKind = list.systemKind,
                items = itemsByList[list.id].orEmpty().sortedBy { it.position }.map { it.toBackupDto() },
            )
        },
    )
}

private fun BacklogItem.toBackupDto(): BackupBacklogItemDto = BackupBacklogItemDto(
    id = id,
    listId = listId,
    title = title,
    platforms = platforms.map { it.name },
    genres = genres.map { it.name },
    tags = tags.map { it.name },
    coverImageFileName = coverImagePath?.substringAfterLast('/'),
    status = status.name,
    position = position,
    addedAt = addedAt.toString(),
    startDate = startDate?.toString(),
    completedDate = completedDate?.toString(),
    reviewId = reviewId,
    abandonNote = abandonNote,
    releaseYear = releaseYear,
    developer = developer,
    hltbMainStoryHours = hltbMainStoryHours,
    hltbMainExtraHours = hltbMainExtraHours,
    hltbCompletionistHours = hltbCompletionistHours,
    comments = comments.map { BackupBacklogCommentDto(id = it.id, text = it.text, timestamp = it.timestamp.toString()) },
    history = history.map {
        BackupBacklogHistoryDto(id = it.id, type = it.type.name, timestamp = it.timestamp.toString(), detail = it.detail)
    },
)

/** Rebuilds a [BacklogList] from its backup DTO — items are restored separately via [BackupBacklogItemDto.toDomain]. */
fun BackupBacklogListDto.toDomain(): BacklogList = BacklogList(
    id = id,
    name = name,
    position = position,
    createdAt = Instant.parse(createdAt),
    systemKind = systemKind,
)

/** Mirrors [BackupReviewDto.toDomain]: [resolvedCoverImagePath] is the absolute path already written on this device. */
fun BackupBacklogItemDto.toDomain(resolvedCoverImagePath: String?): BacklogItem = BacklogItem(
    id = id,
    listId = listId,
    title = title,
    platforms = platforms.map { Platform(id = 0L, name = it) },
    genres = genres.map { Genre(id = 0L, name = it) },
    tags = tags.map { Tag(id = 0L, name = it) },
    coverImagePath = resolvedCoverImagePath,
    status = BacklogItemStatus.valueOf(status),
    position = position,
    addedAt = Instant.parse(addedAt),
    startDate = startDate?.let(LocalDate::parse),
    completedDate = completedDate?.let(LocalDate::parse),
    reviewId = reviewId,
    abandonNote = abandonNote,
    releaseYear = releaseYear,
    developer = developer,
    hltbMainStoryHours = hltbMainStoryHours,
    hltbMainExtraHours = hltbMainExtraHours,
    hltbCompletionistHours = hltbCompletionistHours,
    comments = comments.map { BacklogComment(id = it.id, itemId = id, text = it.text, timestamp = Instant.parse(it.timestamp)) },
    history = history.map {
        BacklogHistoryEntry(
            id = it.id,
            itemId = id,
            type = BacklogHistoryEventType.valueOf(it.type),
            timestamp = Instant.parse(it.timestamp),
            detail = it.detail,
        )
    },
)

private val backupJson = Json {
    prettyPrint = false
    ignoreUnknownKeys = true
}

fun BackupPayload.toJson(): String = backupJson.encodeToString(this)

fun String.toBackupPayload(): BackupPayload = backupJson.decodeFromString(this)
