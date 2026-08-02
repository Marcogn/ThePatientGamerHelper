package com.marcogn.gamereviewer.ui.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.marcogn.gamereviewer.domain.export.ExportFormat
import com.marcogn.gamereviewer.domain.export.suggestedReviewFileName
import com.marcogn.gamereviewer.domain.model.Review
import com.marcogn.gamereviewer.domain.model.label
import com.marcogn.gamereviewer.ui.common.RatingBadge
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDeleted: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val exportMessage by viewModel.exportMessage.collectAsState()
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val review = uiState.review
    val snackbarHostState = remember { SnackbarHostState() }

    val markdownExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExportFormat.MARKDOWN.mimeType),
    ) { uri -> uri?.let(viewModel::exportMarkdown) }
    val pdfExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExportFormat.PDF.mimeType),
    ) { uri -> uri?.let(viewModel::exportPdf) }

    LaunchedEffect(exportMessage) {
        exportMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeExportMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(review?.title.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    if (review != null) {
                        DetailExportMenu(
                            onExportMarkdown = {
                                markdownExportLauncher.launch(
                                    suggestedReviewFileName(review.title, ExportFormat.MARKDOWN),
                                )
                            },
                            onExportPdf = {
                                pdfExportLauncher.launch(
                                    suggestedReviewFileName(review.title, ExportFormat.PDF),
                                )
                            },
                        )
                        IconButton(onClick = { onEdit(review.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Modifica")
                        }
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Elimina")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            review == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Recensione non trovata") }
            else -> ReviewDetailContent(review = review, modifier = Modifier.padding(padding))
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Eliminare la recensione?") },
            text = { Text("L'azione non può essere annullata.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    viewModel.deleteReview(onDeleted = onDeleted)
                }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Annulla") }
            },
        )
    }
}

@Composable
private fun ReviewDetailContent(review: Review, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (review.coverImagePath != null) {
            AsyncImage(
                model = review.coverImagePath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            )
        }

        RatingBadge(rating = review.rating)

        Text(text = review.status.label(), style = MaterialTheme.typography.titleSmall)

        InfoRow(label = "Piattaforme", value = review.platforms.joinToString { it.name }.ifBlank { "—" })
        InfoRow(label = "Generi", value = review.genres.joinToString { it.name }.ifBlank { "—" })
        InfoRow(label = "Tag", value = review.tags.joinToString { it.name }.ifBlank { "—" })
        InfoRow(label = "Iniziato il", value = review.startDate.format(dateFormatter))
        InfoRow(label = "Terminato il", value = review.endDate?.format(dateFormatter) ?: "—")
        InfoRow(label = "Ore di gioco", value = review.hoursPlayed?.let { "%.1f h".format(it) } ?: "—")

        if (review.pros.isNotEmpty()) {
            HorizontalDivider()
            Text(text = "Pro", style = MaterialTheme.typography.titleSmall)
            review.pros.forEach { Text(text = "• $it") }
        }

        if (review.cons.isNotEmpty()) {
            HorizontalDivider()
            Text(text = "Contro", style = MaterialTheme.typography.titleSmall)
            review.cons.forEach { Text(text = "• $it") }
        }

        if (review.reviewText.isNotBlank()) {
            HorizontalDivider()
            Text(text = "Recensione", style = MaterialTheme.typography.titleSmall)
            Text(text = review.reviewText, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
