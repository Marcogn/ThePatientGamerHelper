package com.marcogn.thepatientgamerhelper.domain.backup

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

@Serializable
data class BackupPayload(
    val schemaVersion: Int = 1,
    val createdAt: String,
    val reviews: List<BackupReviewDto>,
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

private val backupJson = Json {
    prettyPrint = false
    ignoreUnknownKeys = true
}

fun BackupPayload.toJson(): String = backupJson.encodeToString(this)

fun String.toBackupPayload(): BackupPayload = backupJson.decodeFromString(this)
