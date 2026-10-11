package com.marcogn.thepatientgamerhelper.ui.update

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcogn.thepatientgamerhelper.data.update.ApkInstaller
import com.marcogn.thepatientgamerhelper.data.update.InstallStart
import com.marcogn.thepatientgamerhelper.data.update.UpdateManager
import com.marcogn.thepatientgamerhelper.data.update.UpdatePreferences
import com.marcogn.thepatientgamerhelper.domain.update.UpdateFrequency
import com.marcogn.thepatientgamerhelper.domain.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Thin UI layer over the process-wide `UpdateManager` (whose state is the single source of truth, so
 * the app-wide dialog host and the Settings frequency row can each own an instance). Holds the
 * "install unknown apps" round trip and the frequency setting.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateManager: UpdateManager,
    private val apkInstaller: ApkInstaller,
    private val preferences: UpdatePreferences,
) : ViewModel() {

    val state: StateFlow<UpdateState> = updateManager.state

    private val _frequency = MutableStateFlow(preferences.frequency)
    val frequency: StateFlow<UpdateFrequency> = _frequency.asStateFlow()

    /** One-shot: the "install unknown apps" screen to open, consumed by [onPermissionScreenLaunched]. */
    private val _permissionIntent = MutableStateFlow<Intent?>(null)
    val permissionIntent: StateFlow<Intent?> = _permissionIntent.asStateFlow()

    fun onFrequencySelected(value: UpdateFrequency) {
        preferences.frequency = value
        _frequency.value = value
    }

    fun onDownload() = updateManager.startDownload()
    fun onCancelDownload() = updateManager.cancelDownload()
    fun onDismiss() = updateManager.dismiss()

    fun onInstall() {
        viewModelScope.launch {
            val start = updateManager.install()
            if (start is InstallStart.PermissionRequired) _permissionIntent.value = start.settingsIntent
        }
    }

    /** The host launched the screen: forget the intent so a recreated activity doesn't launch it again. */
    fun onPermissionScreenLaunched() {
        _permissionIntent.value = null
    }

    /** Back from the settings screen: retry only if the toggle is now on, or it would reopen forever. */
    fun onPermissionScreenClosed() {
        if (apkInstaller.canInstall()) onInstall()
    }
}
