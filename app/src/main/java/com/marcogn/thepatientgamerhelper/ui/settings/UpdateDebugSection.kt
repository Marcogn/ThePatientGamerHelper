package com.marcogn.thepatientgamerhelper.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcogn.thepatientgamerhelper.BuildConfig
import com.marcogn.thepatientgamerhelper.R
import com.marcogn.thepatientgamerhelper.data.update.ApkInstaller
import com.marcogn.thepatientgamerhelper.data.update.InstallStart
import com.marcogn.thepatientgamerhelper.data.update.UpdateManager
import com.marcogn.thepatientgamerhelper.domain.update.UpdateCheckResult
import com.marcogn.thepatientgamerhelper.domain.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/*
 * TEMPORARY (Phase 9, Stage 9.1): the bare minimum to try the update core on a device — check,
 * download, install, with the raw state as text. Stage 9.2 replaces this whole file with the real
 * update dialog, the frequency setting and the Info screen; nothing else depends on it.
 */

@HiltViewModel
class UpdateDebugViewModel @Inject constructor(
    private val updateManager: UpdateManager,
    private val apkInstaller: ApkInstaller,
) : ViewModel() {

    val state: StateFlow<UpdateState> = updateManager.state

    private val _lastCheck = MutableStateFlow<UpdateCheckResult?>(null)
    val lastCheck: StateFlow<UpdateCheckResult?> = _lastCheck.asStateFlow()

    /** One-shot: the "install unknown apps" screen to open, consumed by [onPermissionScreenClosed]. */
    private val _permissionIntent = MutableStateFlow<Intent?>(null)
    val permissionIntent: StateFlow<Intent?> = _permissionIntent.asStateFlow()

    fun onCheckNow() {
        viewModelScope.launch { _lastCheck.value = updateManager.checkNow() }
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

    /** Back from the settings screen: retry only if the toggle is now on, or it would reopen forever. */
    fun onPermissionScreenClosed() {
        _permissionIntent.value = null
        if (apkInstaller.canInstall()) onInstall()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UpdateDebugSection(viewModel: UpdateDebugViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val lastCheck by viewModel.lastCheck.collectAsState()
    val permissionIntent by viewModel.permissionIntent.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.onPermissionScreenClosed() }
    LaunchedEffect(permissionIntent) {
        permissionIntent?.let { permissionLauncher.launch(it) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.update_debug_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.update_debug_installed, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
        Text(state.describe())
        lastCheck?.let { Text(it.describe(), style = MaterialTheme.typography.bodySmall) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                is UpdateState.Available -> {
                    Button(onClick = viewModel::onDownload) { Text(stringResource(R.string.update_debug_download)) }
                    OutlinedButton(onClick = viewModel::onDismiss) { Text(stringResource(R.string.update_debug_not_now)) }
                }
                is UpdateState.Downloading ->
                    OutlinedButton(onClick = viewModel::onCancelDownload) { Text(stringResource(R.string.update_debug_cancel)) }
                is UpdateState.ReadyToInstall -> {
                    Button(onClick = viewModel::onInstall) { Text(stringResource(R.string.update_debug_install)) }
                    OutlinedButton(onClick = viewModel::onDismiss) { Text(stringResource(R.string.update_debug_not_now)) }
                }
                is UpdateState.Failed ->
                    OutlinedButton(onClick = viewModel::onDismiss) { Text(stringResource(R.string.update_debug_not_now)) }
                UpdateState.Idle ->
                    Button(onClick = viewModel::onCheckNow) { Text(stringResource(R.string.update_debug_check_now)) }
                UpdateState.Checking, is UpdateState.Installing -> Unit
            }
        }
    }
}

@Composable
private fun UpdateState.describe(): String = when (this) {
    UpdateState.Idle -> stringResource(R.string.update_debug_state_idle)
    UpdateState.Checking -> stringResource(R.string.update_debug_state_checking)
    is UpdateState.Available -> stringResource(
        R.string.update_debug_state_available,
        update.version.toString(),
        update.notes.headlines.joinToString("\n") { "• $it" },
    )
    is UpdateState.Downloading -> stringResource(
        R.string.update_debug_state_downloading,
        if (totalBytes > 0) (bytesRead * 100 / totalBytes).toInt() else 0,
    )
    is UpdateState.ReadyToInstall -> stringResource(R.string.update_debug_state_ready, update.version.toString())
    is UpdateState.Installing -> stringResource(R.string.update_debug_state_installing)
    is UpdateState.Failed -> stringResource(R.string.update_debug_state_failed, failure.toString())
}

@Composable
private fun UpdateCheckResult.describe(): String = when (this) {
    is UpdateCheckResult.Available -> stringResource(R.string.update_debug_result_available, update.version.toString())
    UpdateCheckResult.UpToDate -> stringResource(R.string.update_debug_result_up_to_date)
    UpdateCheckResult.NoRelease -> stringResource(R.string.update_debug_result_no_release)
    is UpdateCheckResult.Failed -> stringResource(R.string.update_debug_result_failed, failure.toString())
}
