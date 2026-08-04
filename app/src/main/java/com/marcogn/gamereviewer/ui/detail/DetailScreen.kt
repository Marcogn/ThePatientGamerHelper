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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.marcogn.gamereviewer.R
import com.marcogn.gamereviewer.domain.export.ExportFormat
import com.marcogn.gamereviewer.domain.export.suggestedReviewFileName
import com.marcogn.gamereviewer.domain.model.Review
import com.marcogn.gamereviewer.ui.common.RatingBadge
import com.marcogn.gamereviewer.ui.common.displayName
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            review == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.review_not_found)) }
            else -> ReviewDetailContent(review = review, modifier = Modifier.padding(padding))
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.detail_delete_confirm_title)) },
            text = { Text(stringResource(R.string.detail_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    viewModel.deleteReview(onDeleted = onDeleted)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text(stringResource(R.string.action_cancel)) }
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

        Text(text = review.status.displayName(), style = MaterialTheme.typography.titleSmall)

        InfoRow(label = stringResource(R.string.label_platforms), value = review.platforms.joinToString { it.name }.ifBlank { "—" })
        InfoRow(label = stringResource(R.string.label_genres), value = review.genres.joinToString { it.name }.ifBlank { "—" })
        InfoRow(label = stringResource(R.string.label_tags), value = review.tags.joinToString { it.name }.ifBlank { "—" })
        InfoRow(label = stringResource(R.string.detail_started_label), value = review.startDate.format(dateFormatter))
        InfoRow(label = stringResource(R.string.detail_finished_label), value = review.endDate?.format(dateFormatter) ?: "—")
        InfoRow(
            label = stringResource(R.string.label_hours_played),
            value = review.hoursPlayed?.let { stringResource(R.string.detail_hours_value, it) } ?: "—",
        )

        if (review.pros.isNotEmpty()) {
            HorizontalDivider()
            Text(text = stringResource(R.string.label_pros), style = MaterialTheme.typography.titleSmall)
            review.pros.forEach { Text(text = "• $it") }
        }

        if (review.cons.isNotEmpty()) {
            HorizontalDivider()
            Text(text = stringResource(R.string.label_cons), style = MaterialTheme.typography.titleSmall)
            review.cons.forEach { Text(text = "• $it") }
        }

        if (review.reviewText.isNotBlank()) {
            HorizontalDivider()
            Text(text = stringResource(R.string.detail_review_text_label), style = MaterialTheme.typography.titleSmall)
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
