package com.marcogn.thepatientgamerhelper.data.backup

import com.marcogn.thepatientgamerhelper.data.drive.DriveApiClient
import com.marcogn.thepatientgamerhelper.data.image.ImageStorage
import com.marcogn.thepatientgamerhelper.domain.backup.suggestedBackupFileName
import com.marcogn.thepatientgamerhelper.domain.backup.toBackupPayload
import com.marcogn.thepatientgamerhelper.domain.backup.toDomain
import com.marcogn.thepatientgamerhelper.domain.model.BackupFile
import com.marcogn.thepatientgamerhelper.domain.repository.ReviewRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Orchestrates a backup/restore round trip: Room + covers <-> zip archive <-> Drive appDataFolder.
 * Takes an already-obtained [accessToken] — see [com.marcogn.thepatientgamerhelper.data.drive.DriveAuthManager]
 * for how callers (UI or the periodic worker) get one.
 */
@Singleton
class BackupManager @Inject constructor(
    private val reviewRepository: ReviewRepository,
    private val imageStorage: ImageStorage,
    private val archiveBuilder: BackupArchiveBuilder,
    private val archiveReader: BackupArchiveReader,
    private val driveApiClient: DriveApiClient,
    private val preferences: BackupPreferences,
) {
    suspend fun createBackup(accessToken: String): BackupFile = try {
        val reviews = reviewRepository.observeAll().first()
        val archive = archiveBuilder.build(reviews.toBackupPayload())
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

    /** Full overwrite of local data — single-user app, no merge/conflict handling. */
    suspend fun restoreBackup(accessToken: String, backup: BackupFile) {
        val content = archiveReader.read(driveApiClient.downloadBackup(accessToken, backup.id))
        imageStorage.clearAll()
        val restoredReviews = content.payload.reviews.map { dto ->
            val coverFileName = dto.coverImageFileName
            val coverImagePath = if (coverFileName == null) {
                null
            } else {
                content.images[coverFileName]?.let { bytes -> imageStorage.writeBytes(coverFileName, bytes) }
            }
            dto.toDomain(resolvedCoverImagePath = coverImagePath)
        }
        reviewRepository.replaceAll(restoredReviews)
    }
}
