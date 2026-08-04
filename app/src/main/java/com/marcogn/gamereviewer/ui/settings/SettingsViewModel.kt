package com.marcogn.gamereviewer.ui.settings

import android.app.PendingIntent
import android.content.Context
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcogn.gamereviewer.data.backup.BackupManager
import com.marcogn.gamereviewer.data.backup.BackupPreferences
import com.marcogn.gamereviewer.data.backup.BackupScheduler
import com.marcogn.gamereviewer.data.drive.DriveAuthManager
import com.marcogn.gamereviewer.data.drive.DriveAuthorization
import com.marcogn.gamereviewer.domain.model.BackupFile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val driveAuthManager: DriveAuthManager,
    private val backupManager: BackupManager,
    private val backupScheduler: BackupScheduler,
    private val preferences: BackupPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            autoBackupEnabled = preferences.autoBackupEnabled,
            lastBackupAt = preferences.lastBackupAt,
            lastBackupError = preferences.lastBackupError,
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Set when [DriveAuthManager.authorize] needs consent UI; the screen launches it and reports back via [onConsentResult]. */
    private val _consentRequest = MutableStateFlow<IntentSenderRequest?>(null)
    val consentRequest: StateFlow<IntentSenderRequest?> = _consentRequest.asStateFlow()

    private var pendingConsent: CompletableDeferred<ActivityResult>? = null

    fun onToggleAutoBackup(enabled: Boolean) {
        preferences.autoBackupEnabled = enabled
        if (enabled) backupScheduler.enablePeriodicBackup() else backupScheduler.disablePeriodicBackup()
        _uiState.update { it.copy(autoBackupEnabled = enabled) }
    }

    fun onBackupNow(activityContext: Context) = runBusy {
        val accessToken = obtainAccessToken(activityContext)
        backupManager.createBackup(accessToken)
        _uiState.update {
            it.copy(lastBackupAt = preferences.lastBackupAt, lastBackupError = null, message = "Backup completato")
        }
        loadBackups(accessToken)
    }

    fun onLoadBackups(activityContext: Context) = runBusy {
        loadBackups(obtainAccessToken(activityContext))
    }

    fun onRestore(activityContext: Context, backup: BackupFile) = runBusy {
        val accessToken = obtainAccessToken(activityContext)
        backupManager.restoreBackup(accessToken, backup)
        _uiState.update { it.copy(message = "Ripristino completato") }
    }

    fun onConsentResult(result: ActivityResult) {
        _consentRequest.value = null
        pendingConsent?.complete(result)
        pendingConsent = null
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private suspend fun loadBackups(accessToken: String) {
        val backups = backupManager.listBackups(accessToken)
        _uiState.update { it.copy(backups = backups, hasLoadedBackups = true) }
    }

    private fun runBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                block()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(message = e.message ?: "Operazione non riuscita", lastBackupError = preferences.lastBackupError)
                }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    private suspend fun obtainAccessToken(activityContext: Context): String {
        driveAuthManager.signIn(activityContext)
        return resolve(driveAuthManager.authorize(activityContext), activityContext)
    }

    private suspend fun resolve(authorization: DriveAuthorization, activityContext: Context): String = when (authorization) {
        is DriveAuthorization.Authorized -> authorization.accessToken
        is DriveAuthorization.ConsentRequired -> {
            val result = awaitConsent(authorization.pendingIntent)
            val data = result.data ?: error("Autorizzazione Drive annullata")
            val completed = driveAuthManager.completeAuthorization(activityContext, data)
            check(completed is DriveAuthorization.Authorized) { "Autorizzazione Drive non completata" }
            completed.accessToken
        }
    }

    private suspend fun awaitConsent(pendingIntent: PendingIntent): ActivityResult {
        val deferred = CompletableDeferred<ActivityResult>()
        pendingConsent = deferred
        _consentRequest.value = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
        return deferred.await()
    }
}
