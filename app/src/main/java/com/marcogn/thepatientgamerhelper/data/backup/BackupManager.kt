package com.marcogn.thepatientgamerhelper.data.backup

import com.marcogn.thepatientgamerhelper.data.drive.DriveApiClient
import com.marcogn.thepatientgamerhelper.data.image.ImageStorage
import com.marcogn.thepatientgamerhelper.domain.backup.buildBackupPayload
import com.marcogn.thepatientgamerhelper.domain.backup.suggestedBackupFileName
import com.marcogn.thepatientgamerhelper.domain.backup.toDomain
import com.marcogn.thepatientgamerhelper.domain.model.BackupFile
import com.marcogn.thepatientgamerhelper.domain.repository.BacklogRepository
import com.marcogn.thepatientgamerhelper.domain.repository.ReviewRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Orchestrates a backup/restore round trip: Room + covers <-> zip archive <-> Drive appDataFolder.
 * Takes an already-obtained [accessToken] — see [com.marcogn.thepatientgamerhelper.data.drive.DriveAuthManager]
 * for how callers (UI or the periodic worker) get one. Backs up (and restores) both the review
 * library and the whole backlog (every list, with its items and their comments/history) — the
 * backlog used to be silently excluded entirely, see `docs/implementation-decisions.md`.
 */
@Singleton
class BackupManager @Inject constructor(
    private val reviewRepository: ReviewRepository,
    private val backlogRepository: BacklogRepository,
    private val imageStorage: ImageStorage,
    private val archiveBuilder: BackupArchiveBuilder,
    private val archiveReader: BackupArchiveReader,
    private val driveApiClient: DriveApiClient,
    private val preferences: BackupPreferences,
) {
    suspend fun createBackup(accessToken: String): BackupFile = try {
        val reviews = reviewRepository.observeAll().first()
        val backlogLists = backlogRepository.observeLists().first()
        val backlogItems = backlogRepository.observeAllItems().first()
        val archive = archiveBuilder.build(buildBackupPayload(reviews, backlogLists, backlogItems))
        val result = driveApiClient.uploadBackup(accessToken, suggestedBackupFileName(), archive)
        pruneOldBackups(accessToken, keepId = result.id)
        preferences.lastBackupAt = Instant.now()
        preferences.lastBackupError = null
        result
    } catch (e: Exception) {
        preferences.lastBackupError = e.message ?: e::class.simpleName
        throw e
    }

    suspend fun listBackups(accessToken: String): List<BackupFile> =
        driveApiClient.listBackups(accessToken).sortedByDescending { it.createdAt ?: Instant.EPOCH }

    /**
     * Single-user app: each backup is a full snapshot, so there's no value in letting them pile
     * up in the private appDataFolder (invisible in the Drive UI, so the user can't clean it up
     * by hand). Keeps only the one just uploaded, deleting every other backup found on Drive.
     * Best-effort per file — a stray deletion failure doesn't fail the backup that already
     * succeeded; the next run tries again.
     */
    private suspend fun pruneOldBackups(accessToken: String, keepId: String) {
        val existing = driveApiClient.listBackups(accessToken)
        existing.filter { it.id != keepId }.forEach { backup ->
            runCatching { driveApiClient.deleteBackup(accessToken, backup.id) }
        }
    }

    /**
     * Full overwrite of local data — single-user app, no merge/conflict handling. Reviews are
     * restored before the backlog: `BacklogItemEntity.reviewId` has a foreign key onto `reviews`,
     * and restored backlog items trust their DTO's `reviewId` outright (see `BackupPayload.kt`'s
     * doc comment) rather than checking it exists first, so the review it points at must already
     * be in place.
     */
    suspend fun restoreBackup(accessToken: String, backup: BackupFile) {
        val content = archiveReader.read(driveApiClient.downloadBackup(accessToken, backup.id))
        imageStorage.clearAll()
        val restoredReviews = content.payload.reviews.map { dto ->
            dto.toDomain(resolvedCoverImagePath = resolveCoverPath(content, dto.coverImageFileName))
        }
        reviewRepository.replaceAll(restoredReviews)

        val restoredLists = content.payload.backlogLists.map { it.toDomain() }
        val restoredItems = content.payload.backlogLists.flatMap { listDto ->
            listDto.items.map { itemDto ->
                itemDto.toDomain(resolvedCoverImagePath = resolveCoverPath(content, itemDto.coverImageFileName))
            }
        }
        backlogRepository.replaceAll(restoredLists, restoredItems)
    }

    private suspend fun resolveCoverPath(content: BackupArchiveContent, fileName: String?): String? {
        if (fileName == null) return null
        return content.images[fileName]?.let { bytes -> imageStorage.writeBytes(fileName, bytes) }
    }
}
