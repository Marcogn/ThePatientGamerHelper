package com.marcogn.thepatientgamerhelper.ui.settings

import com.marcogn.thepatientgamerhelper.domain.model.BackupFile
import java.time.Instant

data class SettingsUiState(
    val isDriveConfigured: Boolean = true,
    val signedInEmail: String? = null,
    val autoBackupEnabled: Boolean = false,
    val isBusy: Boolean = false,
    val backups: List<BackupFile> = emptyList(),
    val lastBackupAt: Instant? = null,
    val lastBackupError: String? = null,
    val message: String? = null,
    val theGamesDbApiKey: String = "",
) {
    val isSignedIn: Boolean get() = signedInEmail != null
}
