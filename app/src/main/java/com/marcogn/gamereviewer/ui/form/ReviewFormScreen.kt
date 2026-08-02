package com.marcogn.gamereviewer.ui.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.gamereviewer.domain.model.ReviewStatus
import com.marcogn.gamereviewer.domain.model.label
import com.marcogn.gamereviewer.ui.common.DatePickerField
import com.marcogn.gamereviewer.ui.common.TagInputField
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewFormScreen(
    onSaved: (String) -> Unit,
    onCancel: () -> Unit,
    viewModel: ReviewFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "Modifica recensione" else "Nuova recensione") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Annulla")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save(onSaved) }, enabled = !uiState.isSaving) {
                        Icon(Icons.Filled.Check, contentDescription = "Salva")
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CoverImagePicker(
                coverImagePath = uiState.draft.coverImagePath,
                onImagePicked = viewModel::onCoverImagePicked,
                onImageRemoved = viewModel::onRemoveCoverImage,
            )

            OutlinedTextField(
                value = uiState.draft.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Titolo") },
                singleLine = true,
                isError = uiState.errorMessage != null && uiState.draft.title.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )

            TagInputField(
                label = "Piattaforme",
                selected = uiState.draft.platformNames,
                suggestions = uiState.availablePlatformNames,
                onSelectedChange = viewModel::onPlatformsChange,
            )

            TagInputField(
                label = "Generi",
                selected = uiState.draft.genreNames,
                suggestions = uiState.availableGenreNames,
                onSelectedChange = viewModel::onGenresChange,
            )

            TagInputField(
                label = "Tag",
                selected = uiState.draft.tagNames,
                suggestions = uiState.availableTagNames,
                onSelectedChange = viewModel::onTagsChange,
            )

            RatingField(rating = uiState.draft.rating, onRatingChange = viewModel::onRatingChange)

            StatusSelector(status = uiState.draft.status, onStatusChange = viewModel::onStatusChange)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DatePickerField(
                    label = "Data inizio",
                    date = uiState.draft.startDate,
                    clearable = false,
                    modifier = Modifier.weight(1f),
                    onDateChange = { it?.let(viewModel::onStartDateChange) },
                )
                DatePickerField(
                    label = "Data fine",
                    date = uiState.draft.endDate,
                    modifier = Modifier.weight(1f),
                    onDateChange = viewModel::onEndDateChange,
                )
            }

            OutlinedTextField(
                value = uiState.draft.hoursPlayed?.let { formatHours(it) } ?: "",
                onValueChange = { text ->
                    viewModel.onHoursPlayedChange(text.replace(',', '.').toDoubleOrNull())
                },
                label = { Text("Ore di gioco") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            DynamicStringListEditor(
                label = "Pro",
                items = uiState.draft.pros,
                onItemsChange = viewModel::onProsChange,
            )

            DynamicStringListEditor(
                label = "Contro",
                items = uiState.draft.cons,
                onItemsChange = viewModel::onConsChange,
            )

            HorizontalDivider()

            OutlinedTextField(
                value = uiState.draft.reviewText,
                onValueChange = viewModel::onReviewTextChange,
                label = { Text("Recensione (markdown)") },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            uiState.errorMessage?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }

            if (uiState.isSaving) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun RatingField(rating: Double, onRatingChange: (Double) -> Unit) {
    Column {
        Text(
            text = "Voto: ${"%.1f".format(Locale.getDefault(), rating)}",
            style = MaterialTheme.typography.titleSmall,
        )
        Slider(
            value = rating.toFloat(),
            onValueChange = { onRatingChange(it.toDouble()) },
            valueRange = 0f..10f,
            steps = 99,
        )
    }
}

@Composable
private fun StatusSelector(status: ReviewStatus, onStatusChange: (ReviewStatus) -> Unit) {
    Column {
        Text(text = "Stato", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReviewStatus.entries.forEach { candidate ->
                FilterChip(
                    selected = status == candidate,
                    onClick = { onStatusChange(candidate) },
                    label = { Text(candidate.label()) },
                )
            }
        }
    }
}

private fun formatHours(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
