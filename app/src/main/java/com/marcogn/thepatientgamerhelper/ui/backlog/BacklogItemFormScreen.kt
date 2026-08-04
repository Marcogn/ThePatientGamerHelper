package com.marcogn.thepatientgamerhelper.ui.backlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.thepatientgamerhelper.R
import com.marcogn.thepatientgamerhelper.ui.common.GameSearchDialog
import com.marcogn.thepatientgamerhelper.ui.common.TagInputField
import com.marcogn.thepatientgamerhelper.ui.form.CoverImagePicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacklogItemFormScreen(
    onSaved: (String) -> Unit,
    onCancel: () -> Unit,
    viewModel: BacklogItemFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSearchDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.isEditMode) {
                            stringResource(R.string.backlog_item_edit_title)
                        } else {
                            stringResource(R.string.backlog_item_new_title)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
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
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
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
}
