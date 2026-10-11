package com.marcogn.thepatientgamerhelper

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.marcogn.thepatientgamerhelper.data.debug.DebugSeeder
import com.marcogn.thepatientgamerhelper.data.image.CoverImageReconciler
import com.marcogn.thepatientgamerhelper.data.image.ImageStorage
import com.marcogn.thepatientgamerhelper.data.update.ApkDownloader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class ThePatientGamerHelperApplication : Application(), Configuration.Provider {

    @Inject lateinit var debugSeeder: DebugSeeder
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var coverImageReconciler: CoverImageReconciler
    @Inject lateinit var imageStorage: ImageStorage
    @Inject lateinit var apkDownloader: ApkDownloader

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.SEED_DEBUG_DATA) {
            applicationScope.launch { debugSeeder.seedIfEmpty() }
        }
        // Sequential, not two parallel launches: pruning first avoids wasting a recompression pass
        // on a file about to be deleted, and both touch the same covers/ directory.
        applicationScope.launch {
            coverImageReconciler.pruneOrphanedCovers()
            imageStorage.recompressOversizedCovers()
        }
        // Phase 9: update APKs from a previous process (installed, refused or interrupted).
        applicationScope.launch { apkDownloader.deleteLeftovers() }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
