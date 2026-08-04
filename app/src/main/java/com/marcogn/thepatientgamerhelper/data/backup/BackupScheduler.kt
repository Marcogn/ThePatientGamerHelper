package com.marcogn.thepatientgamerhelper.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Fixed daily cadence — no UI to configure the interval, matches the "don't over-engineer" call in CLAUDE.md. */
private const val BACKUP_INTERVAL_HOURS = 24L

@Singleton
class BackupScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    fun enablePeriodicBackup() {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(BACKUP_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BackupWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun disablePeriodicBackup() {
        WorkManager.getInstance(context).cancelUniqueWork(BackupWorker.UNIQUE_WORK_NAME)
    }
}
