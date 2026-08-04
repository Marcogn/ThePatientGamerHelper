package com.marcogn.thepatientgamerhelper.data.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.marcogn.thepatientgamerhelper.data.drive.DriveAuthManager
import com.marcogn.thepatientgamerhelper.data.drive.DriveAuthorization
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic, unattended backup. Uses [DriveAuthManager.authorize] with the application context
 * only — no Activity means no UI, so a [DriveAuthorization.ConsentRequired] result (first grant,
 * or a revoked one) can't be resolved here: the worker just fails and waits for the user to run
 * a manual backup from Settings, which re-establishes consent.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val driveAuthManager: DriveAuthManager,
    private val backupManager: BackupManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val authorization = runCatching { driveAuthManager.authorize(applicationContext) }
            .getOrElse { return Result.failure() }
        val accessToken = when (authorization) {
            is DriveAuthorization.Authorized -> authorization.accessToken
            is DriveAuthorization.ConsentRequired -> return Result.failure()
        }
        return try {
            backupManager.createBackup(accessToken)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "periodic_drive_backup"
    }
}
