package com.marcogn.thepatientgamerhelper.ui.backlog

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.thepatientgamerhelper.R
import com.marcogn.thepatientgamerhelper.domain.model.BacklogItem
import com.marcogn.thepatientgamerhelper.domain.model.ViewMode
import com.marcogn.thepatientgamerhelper.ui.common.GameGridTile
import com.marcogn.thepatientgamerhelper.ui.common.ViewModeToggle
import kotlin.math.roundToInt

private val REORDER_ROW_HEIGHT = 88.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacklogListDetailScreen(
    onBack: () -> Unit,
    onAddItemClick: () -> Unit,
    onItemClick: (String) -> Unit,
    viewModel: BacklogListDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.list?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    ViewModeToggle(viewMode = uiState.viewMode, onViewModeChange = viewModel::onViewModeChange)
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddItemClick) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.backlog_add_item))
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (!uiState.isLoading && uiState.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.backlog_list_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (!uiState.isLoading && uiState.viewMode == ViewMode.GRID) {
                BacklogItemGrid(items = uiState.items, onItemClick = onItemClick)
            } else if (!uiState.isLoading) {
                BacklogItemReorderList(
                    items = uiState.items,
                    onReordered = viewModel::onReorder,
                    onItemClick = onItemClick,
                )
            }
        }
    }
}

/**
 * Grid presentation (Fase 8) of the same list — deliberately read-only order: the drag-to-reorder
 * handle only exists in [BacklogItemReorderList] (list mode), extending the drag gesture math to a
 * 2D grid wasn't worth it for a cosmetic browsing view. Switch back to list mode to reorder.
 */
@Composable
private fun BacklogItemGrid(items: List<BacklogItem>, onItemClick: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { item ->
            GameGridTile(
                title = item.title,
                subtitle = item.platforms.takeIf { it.isNotEmpty() }?.joinToString { it.name },
                coverImagePath = item.coverImagePath,
                onClick = { onItemClick(item.id) },
            )
        }
    }
}

/**
 * Manual long-press-free drag-to-reorder on a dedicated handle icon (kept separate from the row's
 * own click target to avoid gesture conflicts), committed to the repository once the drag ends.
 * Compose Foundation only — no external reorder library, consistent with CLAUDE.md's
 * dependency-minimalism.
 */
@Composable
private fun BacklogItemReorderList(
    items: List<BacklogItem>,
    onReordered: (List<String>) -> Unit,
    onItemClick: (String) -> Unit,
) {
    var orderedItems by remember { mutableStateOf(items) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { REORDER_ROW_HEIGHT.toPx() }

    LaunchedEffect(items) {
        if (draggingId == null) orderedItems = items
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(orderedItems, key = { _, item -> item.id }) { _, item ->
            val isDragging = item.id == draggingId
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) dragOffsetY else 0f },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BacklogItemRow(
                        item = item,
                        listName = null,
                        onClick = { onItemClick(item.id) },
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Filled.DragHandle,
                        contentDescription = stringResource(R.string.cd_drag_handle),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .pointerInput(item.id) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingId = item.id
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { change, offset ->
                                        change.consume()
                                        dragOffsetY += offset.y
                                        val currentIndex = orderedItems.indexOfFirst { it.id == draggingId }
                                        if (currentIndex < 0) return@detectDragGestures
                                        val targetIndex = (currentIndex + (dragOffsetY / rowHeightPx).roundToInt())
                                            .coerceIn(0, orderedItems.lastIndex)
                                        if (targetIndex != currentIndex) {
                                            orderedItems = orderedItems.toMutableList().apply {
                                                add(targetIndex, removeAt(currentIndex))
                                            }
                                            dragOffsetY -= (targetIndex - currentIndex) * rowHeightPx
                                        }
                                    },
                                    onDragEnd = {
                                        draggingId = null
                                        dragOffsetY = 0f
                                        onReordered(orderedItems.map { it.id })
                                    },
                                    onDragCancel = {
                                        draggingId = null
                                        dragOffsetY = 0f
                                    },
                                )
                            },
                    )
                }
            }
        }
    }
}
