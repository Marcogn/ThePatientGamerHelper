package com.marcogn.gamereviewer.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.gamereviewer.domain.model.BackupFile
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timestampFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val consentRequest by viewModel.consentRequest.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var backupPendingRestore by remember { mutableStateOf<BackupFile?>(null) }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onConsentResult(result) }

    LaunchedEffect(consentRequest) {
        consentRequest?.let { consentLauncher.launch(it) }
    }
    LaunchedEffect(Unit) {
        if (!uiState.hasLoadedBackups) viewModel.onLoadBackups(context)
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
                title = { Text("Impostazioni") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
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
            BackupSection(
                uiState = uiState,
                onToggleAutoBackup = viewModel::onToggleAutoBackup,
                onBackupNow = { viewModel.onBackupNow(context) },
            )

            HorizontalDivider()

            RestoreSection(
                uiState = uiState,
                onRefresh = { viewModel.onLoadBackups(context) },
                onRestoreClick = { backupPendingRestore = it },
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
private fun BackupSection(
    uiState: SettingsUiState,
    onToggleAutoBackup: (Boolean) -> Unit,
    onBackupNow: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "Backup cloud", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Il backup viene salvato nella cartella privata dell'app su Google Drive " +
                "(appDataFolder): non visibile né condivisibile dall'interfaccia di Drive.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Backup automatico", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Una volta al giorno, quando il dispositivo è connesso",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = uiState.autoBackupEnabled, onCheckedChange = onToggleAutoBackup)
        }

        TextButton(onClick = onBackupNow, enabled = !uiState.isBusy) {
            Text("Esegui backup ora")
        }

        uiState.lastBackupAt?.let {
            Text(
                text = "Ultimo backup riuscito: ${timestampFormatter.format(it)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        uiState.lastBackupError?.let {
            Text(
                text = "Ultimo errore: $it",
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
            Text(text = "Ripristina da backup", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onRefresh, enabled = !uiState.isBusy) {
                Icon(Icons.Filled.Refresh, contentDescription = "Aggiorna elenco backup")
            }
        }

        when {
            uiState.isBusy && uiState.backups.isEmpty() -> CircularProgressIndicator()
            uiState.backups.isEmpty() -> Text(
                text = "Nessun backup trovato su Drive",
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
                        backup.sizeBytes?.let { "${it / 1024} KB" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRestoreClick, enabled = enabled) {
                Icon(Icons.Filled.CloudDownload, contentDescription = "Ripristina questo backup")
            }
        }
    }
}

@Composable
private fun RestoreConfirmationDialog(backup: BackupFile, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ripristinare questo backup?") },
        text = {
            Text(
                "Tutti i dati locali attuali (recensioni e copertine) verranno sostituiti dal " +
                    "contenuto di \"${backup.name}\". L'operazione non è reversibile.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Ripristina") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
