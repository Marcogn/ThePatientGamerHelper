package com.marcogn.gamereviewer.ui.backlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.gamereviewer.R
import com.marcogn.gamereviewer.domain.model.BacklogItem
import com.marcogn.gamereviewer.domain.model.BacklogItemStatus
import com.marcogn.gamereviewer.domain.model.BacklogList
import com.marcogn.gamereviewer.ui.common.CoverThumbnail
import com.marcogn.gamereviewer.ui.common.displayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacklogScreen(
    onBack: () -> Unit,
    onListClick: (Long) -> Unit,
    onItemClick: (String) -> Unit,
    viewModel: BacklogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }
    var showCreateListDialog by remember { mutableStateOf(false) }
    var listPendingRename by remember { mutableStateOf<BacklogList?>(null) }
    var listPendingDelete by remember { mutableStateOf<BacklogList?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backlog_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    BadgedBox(badge = { if (uiState.filters.isActive) Badge() }) {
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.cd_filters))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateListDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.backlog_new_list))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = uiState.filters.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.backlog_search_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )

            if (!uiState.filters.isActive) {
                BacklogStatsHeader(uiState = uiState)
            }

            when {
                uiState.isLoading -> Unit
                uiState.filters.isActive -> BacklogSearchResults(
                    results = uiState.searchResults,
                    listNamesById = uiState.listNamesById,
                    onItemClick = onItemClick,
                )
                uiState.lists.isEmpty() -> EmptyBacklogMessage()
                else -> BacklogListsColumn(
                    lists = uiState.lists,
                    itemCountByList = uiState.itemCountByList,
                    onListClick = onListClick,
                    onMoveUp = viewModel::onMoveListUp,
                    onMoveDown = viewModel::onMoveListDown,
                    onRenameClick = { listPendingRename = it },
                    onDeleteClick = { listPendingDelete = it },
                )
            }
        }
    }

    if (showFilterSheet) {
        BacklogFilterSheet(
            filters = uiState.filters,
            lists = uiState.lists,
            availablePlatforms = uiState.availablePlatforms,
            availableGenres = uiState.availableGenres,
            onFiltersChange = viewModel::onFiltersChange,
            onClear = viewModel::onClearFilters,
            onDismiss = { showFilterSheet = false },
        )
    }

    if (showCreateListDialog) {
        BacklogListNameDialog(
            title = stringResource(R.string.backlog_new_list),
            initialName = "",
            onConfirm = { name ->
                viewModel.onCreateList(name)
                showCreateListDialog = false
            },
            onDismiss = { showCreateListDialog = false },
        )
    }

    listPendingRename?.let { list ->
        BacklogListNameDialog(
            title = stringResource(R.string.backlog_rename_list),
            initialName = list.name,
            onConfirm = { name ->
                viewModel.onRenameList(list.id, name)
                listPendingRename = null
            },
            onDismiss = { listPendingRename = null },
        )
    }

    listPendingDelete?.let { list ->
        AlertDialog(
            onDismissRequest = { listPendingDelete = null },
            title = { Text(stringResource(R.string.backlog_delete_list_confirm_title)) },
            text = { Text(stringResource(R.string.backlog_delete_list_confirm_message, list.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDeleteList(list.id)
                    listPendingDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { listPendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun BacklogStatsHeader(uiState: BacklogUiState) {
    if (uiState.statistics.totalItems == 0) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = pluralStringResource(R.plurals.backlog_total_items, uiState.statistics.totalItems, uiState.statistics.totalItems),
                style = MaterialTheme.typography.titleSmall,
            )
            val statusCounts = BacklogItemStatus.entries.map { status ->
                "${status.displayName()}: ${uiState.statistics.countByStatus[status] ?: 0}"
            }
            Text(
                text = statusCounts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BacklogListsColumn(
    lists: List<BacklogList>,
    itemCountByList: Map<Long, Int>,
    onListClick: (Long) -> Unit,
    onMoveUp: (Long) -> Unit,
    onMoveDown: (Long) -> Unit,
    onRenameClick: (BacklogList) -> Unit,
    onDeleteClick: (BacklogList) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(lists, key = { it.id }) { list ->
            BacklogListRow(
                list = list,
                itemCount = itemCountByList[list.id] ?: 0,
                onClick = { onListClick(list.id) },
                onMoveUp = { onMoveUp(list.id) },
                onMoveDown = { onMoveDown(list.id) },
                onRenameClick = { onRenameClick(list) },
                onDeleteClick = { onDeleteClick(list) },
            )
        }
    }
}

@Composable
private fun BacklogListRow(
    list: BacklogList,
    itemCount: Int,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = list.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = pluralStringResource(R.plurals.backlog_item_count, itemCount, itemCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.ArrowDropUp, contentDescription = stringResource(R.string.cd_move_up))
                }
                IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.cd_move_down))
                }
            }
            IconButton(onClick = onRenameClick) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.backlog_rename_list))
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
}

@Composable
private fun BacklogSearchResults(results: List<BacklogItem>, listNamesById: Map<Long, String>, onItemClick: (String) -> Unit) {
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.backlog_no_results_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(results, key = { it.id }) { item ->
            BacklogItemRow(
                item = item,
                listName = listNamesById[item.listId],
                onClick = { onItemClick(item.id) },
            )
        }
    }
}

@Composable
internal fun BacklogItemRow(item: BacklogItem, listName: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverThumbnail(item.coverImagePath)

            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = listOfNotNull(
                        listName,
                        item.platforms.takeIf { it.isNotEmpty() }?.joinToString { it.name },
                    ).joinToString(" · ").ifEmpty { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = item.status.displayName(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun EmptyBacklogMessage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(R.string.backlog_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.backlog_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BacklogListNameDialog(title: String, initialName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.backlog_list_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
