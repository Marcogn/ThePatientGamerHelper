package com.marcogn.gamereviewer.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.gamereviewer.R
import com.marcogn.gamereviewer.domain.export.ExportFormat
import com.marcogn.gamereviewer.domain.export.suggestedLibraryFileName
import com.marcogn.gamereviewer.domain.model.Review

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onReviewClick: (String) -> Unit,
    onAddClick: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val exportMessage by viewModel.exportMessage.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExportFormat.JSON.mimeType),
    ) { uri -> uri?.let(viewModel::exportJson) }
    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExportFormat.CSV.mimeType),
    ) { uri -> uri?.let(viewModel::exportCsv) }

    LaunchedEffect(exportMessage) {
        exportMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeExportMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    LibraryExportMenu(
                        onExportJson = { jsonExportLauncher.launch(suggestedLibraryFileName(ExportFormat.JSON)) },
                        onExportCsv = { csvExportLauncher.launch(suggestedLibraryFileName(ExportFormat.CSV)) },
                    )
                    BadgedBox(badge = { if (uiState.filters.isActive) Badge() }) {
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(Icons.Filled.FilterList, contentDescription = "Filtri")
                        }
                    }
                    SortMenu(sort = uiState.sort, onSortChange = viewModel::onSortChange)
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = "Nuova recensione")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SearchField(
                query = uiState.filters.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
            )

            when {
                uiState.isLoading -> Unit
                uiState.reviews.isEmpty() && uiState.allReviewsEmpty -> EmptyLibraryMessage()
                uiState.reviews.isEmpty() -> NoResultsMessage(onClearFilters = viewModel::onClearFilters)
                else -> ReviewList(reviews = uiState.reviews, onReviewClick = onReviewClick)
            }
        }
    }

    if (showFilterSheet) {
        FilterSheet(
            filters = uiState.filters,
            availablePlatforms = uiState.availablePlatforms,
            availableGenres = uiState.availableGenres,
            availableTags = uiState.availableTags,
            onFiltersChange = viewModel::onFiltersChange,
            onClear = viewModel::onClearFilters,
            onDismiss = { showFilterSheet = false },
        )
    }
}

@Composable
private fun ReviewList(reviews: List<Review>, onReviewClick: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(reviews, key = { it.id }) { review ->
            ReviewListItem(review = review, onClick = { onReviewClick(review.id) })
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Cerca titolo, testo, pro/contro…") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
    )
}

@Composable
private fun EmptyLibraryMessage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Nessuna recensione ancora", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Tocca + per aggiungere la prima recensione",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoResultsMessage(onClearFilters: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Nessun risultato con i filtri attuali", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onClearFilters) { Text("Reimposta filtri") }
        }
    }
}
