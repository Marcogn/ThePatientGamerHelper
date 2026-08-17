package com.marcogn.thepatientgamerhelper.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.thepatientgamerhelper.R
import com.marcogn.thepatientgamerhelper.domain.model.BackupFile
import com.marcogn.thepatientgamerhelper.domain.model.ThemeMode
import com.marcogn.thepatientgamerhelper.ui.theme.ThemeViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timestampFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val consentRequest by viewModel.consentRequest.collectAsState()
    val themeMode by themeViewModel.themeMode.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var backupPendingRestore by remember { mutableStateOf<BackupFile?>(null) }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onConsentResult(result) }

    // Compose Navigation's default back gesture/button bypasses onBack (a bare popBackStack(),
    // same class of bug already found and fixed in ReviewFormScreen, Phase 8) — without this, the
    // system back gesture would still destroy the Drive login state onBack was just changed to
    // preserve.
    BackHandler(onBack = onBack)

    LaunchedEffect(consentRequest) {
        consentRequest?.let { consentLauncher.launch(it) }
    }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PreferencesSection(
                themeMode = themeMode,
                onThemeModeSelected = themeViewModel::onThemeModeSelected,
            )

            HorizontalDivider()

            when {
                !uiState.isDriveConfigured -> DriveNotConfiguredCard()
                !uiState.isSignedIn -> GoogleLoginCard(isBusy = uiState.isBusy, onLoginClick = { viewModel.onLoginClick(context) })
                else -> {
                    ConnectedAccountRow(email = uiState.signedInEmail.orEmpty(), onLogout = viewModel::onLogout)

                    HorizontalDivider()

                    BackupSection(
                        uiState = uiState,
                        onToggleAutoBackup = viewModel::onToggleAutoBackup,
                        onBackupNow = { viewModel.onBackupNow(context) },
                    )

                    HorizontalDivider()

                    RestoreSection(
                        uiState = uiState,
                        onRefresh = { viewModel.onRefreshBackups(context) },
                        onRestoreClick = { backupPendingRestore = it },
                    )
                }
            }

            HorizontalDivider()

            TheGamesDbSection(
                apiKey = uiState.theGamesDbApiKey,
                onApiKeyChange = viewModel::onTheGamesDbApiKeyChange,
                onSaveClick = viewModel::onSaveTheGamesDbApiKey,
            )
        }
    }

    backupPendingRestore?.let { backup ->
        RestoreConfirmationDialog(
            backup = backup,
            onConfirm = {
                viewModel.onRestore(context, backup)
                backupPendingRestore = null
            },
            onDismiss = { backupPendingRestore = null },
        )
    }
}

@Composable
private fun PreferencesSection(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = stringResource(R.string.settings_preferences_section_title), style = MaterialTheme.typography.titleMedium)

        Text(text = stringResource(R.string.settings_theme_label), style = MaterialTheme.typography.bodyLarge)
        Column(modifier = Modifier.selectableGroup()) {
            ThemeMode.entries.forEach { mode ->
                RadioOptionRow(
                    label = stringResource(mode.labelRes()),
                    selected = themeMode == mode,
                    onClick = { onThemeModeSelected(mode) },
                )
            }
        }

        Text(text = stringResource(R.string.settings_language_label), style = MaterialTheme.typography.bodyLarge)
        var selectedLanguage by remember { mutableStateOf(currentAppLanguage()) }
        Column(modifier = Modifier.selectableGroup()) {
            AppLanguage.entries.forEach { language ->
                RadioOptionRow(
                    label = stringResource(language.labelRes()),
                    selected = selectedLanguage == language,
                    onClick = {
                        selectedLanguage = language
                        applyAppLanguage(language)
                    },
                )
            }
        }
    }
}

@Composable
private fun RadioOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SISTEMA -> R.string.theme_system
    ThemeMode.CHIARO -> R.string.theme_light
    ThemeMode.SCURO -> R.string.theme_dark
}

private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SISTEMA -> R.string.language_system
    AppLanguage.ITALIANO -> R.string.language_italian
    AppLanguage.ENGLISH -> R.string.language_english
}

@Composable
private fun DriveNotConfiguredCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    text = stringResource(R.string.settings_drive_not_configured_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                text = stringResource(R.string.settings_drive_not_configured_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun GoogleLoginCard(isBusy: Boolean, onLoginClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = stringResource(R.string.settings_backup_section_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.settings_drive_login_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onLoginClick, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp).size(18.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
            } else {
                Icon(Icons.Filled.AccountCircle, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            }
            Text(stringResource(R.string.settings_login_with_google))
        }
    }
}

@Composable
private fun ConnectedAccountRow(email: String, onLogout: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = stringResource(R.string.settings_connected_to_drive), style = MaterialTheme.typography.bodyLarge)
            Text(text = email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onLogout) { Text(stringResource(R.string.settings_logout)) }
    }
}

@Composable
private fun BackupSection(
    uiState: SettingsUiState,
    onToggleAutoBackup: (Boolean) -> Unit,
    onBackupNow: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = stringResource(R.string.settings_backup_section_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.settings_backup_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.settings_auto_backup_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.settings_auto_backup_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = uiState.autoBackupEnabled, onCheckedChange = onToggleAutoBackup)
        }

        TextButton(onClick = onBackupNow, enabled = !uiState.isBusy) {
            Text(stringResource(R.string.settings_backup_now))
        }

        uiState.lastBackupAt?.let {
            Text(
                text = stringResource(R.string.settings_last_backup_success, timestampFormatter.format(it)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        uiState.lastBackupError?.let {
            Text(
                text = stringResource(R.string.settings_last_backup_error, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RestoreSection(
    uiState: SettingsUiState,
    onRefresh: () -> Unit,
    onRestoreClick: (BackupFile) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(R.string.settings_restore_section_title), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onRefresh, enabled = !uiState.isBusy) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cd_refresh_backups))
            }
        }

        when {
            uiState.isBusy && uiState.backups.isEmpty() -> CircularProgressIndicator()
            uiState.backups.isEmpty() -> Text(
                text = stringResource(R.string.settings_no_backups),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.backups.forEach { backup ->
                    BackupListItem(backup = backup, enabled = !uiState.isBusy, onRestoreClick = { onRestoreClick(backup) })
                }
            }
        }
    }
}

@Composable
private fun BackupListItem(backup: BackupFile, enabled: Boolean, onRestoreClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = backup.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = listOfNotNull(
                        backup.createdAt?.let { timestampFormatter.format(it) },
                        backup.sizeBytes?.let { stringResource(R.string.settings_backup_size_kb, it / 1024) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRestoreClick, enabled = enabled) {
                Icon(Icons.Filled.CloudDownload, contentDescription = stringResource(R.string.cd_restore_backup))
            }
        }
    }
}

@Composable
private fun TheGamesDbSection(apiKey: String, onApiKeyChange: (String) -> Unit, onSaveClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = stringResource(R.string.settings_thegamesdb_section_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.settings_thegamesdb_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text(stringResource(R.string.settings_thegamesdb_key_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onSaveClick, enabled = apiKey.isNotBlank()) {
            Text(stringResource(R.string.action_save))
        }
    }
}

@Composable
private fun RestoreConfirmationDialog(backup: BackupFile, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_restore_confirm_title)) },
        text = { Text(stringResource(R.string.settings_restore_confirm_message, backup.name)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_restore_confirm_button)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
