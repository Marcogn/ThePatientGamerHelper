package com.marcogn.thepatientgamerhelper.ui.form

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.thepatientgamerhelper.R
import com.marcogn.thepatientgamerhelper.domain.model.ReviewStatus
import com.marcogn.thepatientgamerhelper.ui.common.DatePickerField
import com.marcogn.thepatientgamerhelper.ui.common.GameSearchDialog
import com.marcogn.thepatientgamerhelper.ui.common.TagInputField
import com.marcogn.thepatientgamerhelper.ui.common.displayName
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewFormScreen(
    onSaved: (String) -> Unit,
    onCancel: () -> Unit,
    viewModel: ReviewFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val pendingMove by viewModel.pendingMove.collectAsState()
    var showSearchDialog by remember { mutableStateOf(false) }

    // The toolbar arrow's onClick only fires on a tap — the system back gesture/button bypasses it
    // entirely and defaults to a bare popBackStack(), skipping the draft-save-and-link and the
    // "move to list?" offer below. Without this, a swipe-back on a backlog-originated review loses
    // the draft silently and leaves the backlog item's reviewId null, so reopening it re-offers the
    // "want to write a review?" prompt — the exact duplication reported on a real device.
    BackHandler { viewModel.onBackPressed { onCancel() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isEditMode) {
                            stringResource(R.string.review_edit_title)
                        } else {
                            stringResource(R.string.review_new_title)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onBackPressed { onCancel() } }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save(onSaved) }, enabled = !uiState.isSaving) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_save))
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
                label = { Text(stringResource(R.string.form_field_title)) },
                singleLine = true,
                isError = uiState.errorMessage != null && uiState.draft.title.isBlank(),
                trailingIcon = {
                    IconButton(onClick = {
                        viewModel.onSearchOnlineOpened()
                        showSearchDialog = true
                    }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.game_search_action))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            TagInputField(
                label = stringResource(R.string.label_platforms),
                selected = uiState.draft.platformNames,
                suggestions = uiState.availablePlatformNames,
                onSelectedChange = viewModel::onPlatformsChange,
            )

            TagInputField(
                label = stringResource(R.string.label_genres),
                selected = uiState.draft.genreNames,
                suggestions = uiState.availableGenreNames,
                onSelectedChange = viewModel::onGenresChange,
            )

            TagInputField(
                label = stringResource(R.string.label_tags),
                selected = uiState.draft.tagNames,
                suggestions = uiState.availableTagNames,
                onSelectedChange = viewModel::onTagsChange,
            )

            RatingField(rating = uiState.draft.rating, onRatingChange = viewModel::onRatingChange)

            StatusSelector(status = uiState.draft.status, onStatusChange = viewModel::onStatusChange)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DatePickerField(
                    label = stringResource(R.string.form_date_start),
                    date = uiState.draft.startDate,
                    clearable = false,
                    modifier = Modifier.weight(1f),
                    onDateChange = { it?.let(viewModel::onStartDateChange) },
                )
                DatePickerField(
                    label = stringResource(R.string.form_date_end),
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
                label = { Text(stringResource(R.string.label_hours_played)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            DynamicStringListEditor(
                label = stringResource(R.string.label_pros),
                items = uiState.draft.pros,
                onItemsChange = viewModel::onProsChange,
            )

            DynamicStringListEditor(
                label = stringResource(R.string.label_cons),
                items = uiState.draft.cons,
                onItemsChange = viewModel::onConsChange,
            )

            HorizontalDivider()

            OutlinedTextField(
                value = uiState.draft.reviewText,
                onValueChange = viewModel::onReviewTextChange,
                label = { Text(stringResource(R.string.form_field_review_text)) },
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

    if (showSearchDialog) {
        GameSearchDialog(
            query = uiState.searchQuery,
            onQueryChange = viewModel::onSearchQueryChange,
            isSearching = uiState.isSearchingOnline,
            results = uiState.searchResults,
            infoMessage = uiState.searchMessage,
            onSearch = viewModel::onSearchOnline,
            onResultSelected = { result ->
                viewModel.onSearchResultSelected(result)
                showSearchDialog = false
            },
            onDismiss = {
                viewModel.onSearchDialogDismissed()
                showSearchDialog = false
            },
        )
    }

    pendingMove?.let { move ->
        AlertDialog(
            onDismissRequest = viewModel::onDeclineMove,
            title = { Text(stringResource(R.string.backlog_move_confirm_title)) },
            text = { Text(stringResource(R.string.backlog_move_confirm_message, move.itemTitle, move.targetListName)) },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmMove) { Text(stringResource(R.string.action_move)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDeclineMove) { Text(stringResource(R.string.action_dont_move)) }
            },
        )
    }
}

@Composable
private fun RatingField(rating: Double, onRatingChange: (Double) -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.form_rating_label, "%.1f".format(Locale.getDefault(), rating)),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusSelector(status: ReviewStatus, onStatusChange: (ReviewStatus) -> Unit) {
    Column {
        Text(text = stringResource(R.string.label_status), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReviewStatus.entries.forEach { candidate ->
                FilterChip(
                    selected = status == candidate,
                    onClick = { onStatusChange(candidate) },
                    label = { Text(candidate.displayName(), maxLines = 1) },
                )
            }
        }
    }
}

private fun formatHours(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
