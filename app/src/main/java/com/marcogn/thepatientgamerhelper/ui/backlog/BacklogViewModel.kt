package com.marcogn.thepatientgamerhelper.ui.backlog

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcogn.thepatientgamerhelper.R
import com.marcogn.thepatientgamerhelper.data.export.BacklogExporter
import com.marcogn.thepatientgamerhelper.data.export.BacklogImporter
import com.marcogn.thepatientgamerhelper.domain.filter.BacklogFilters
import com.marcogn.thepatientgamerhelper.domain.filter.applyBacklogFilters
import com.marcogn.thepatientgamerhelper.domain.filter.sortBacklogItemsByRecency
import com.marcogn.thepatientgamerhelper.domain.model.Genre
import com.marcogn.thepatientgamerhelper.domain.model.Platform
import com.marcogn.thepatientgamerhelper.domain.repository.BacklogRepository
import com.marcogn.thepatientgamerhelper.domain.repository.LookupRepository
import com.marcogn.thepatientgamerhelper.domain.stats.computeBacklogStatistics
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class LookupOptions(val platforms: List<Platform>, val genres: List<Genre>)

@HiltViewModel
class BacklogViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val backlogRepository: BacklogRepository,
    private val backlogExporter: BacklogExporter,
    private val backlogImporter: BacklogImporter,
    lookupRepository: LookupRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(BacklogFilters())
    private val _importExportMessage = MutableStateFlow<String?>(null)
    val importExportMessage: StateFlow<String?> = _importExportMessage.asStateFlow()

    private val lookupOptions = combine(
        lookupRepository.observePlatforms(),
        lookupRepository.observeGenres(),
    ) { platforms, genres -> LookupOptions(platforms, genres) }

    val uiState: StateFlow<BacklogUiState> = combine(
        backlogRepository.observeLists(),
        backlogRepository.observeAllItems(),
        filters,
        lookupOptions,
    ) { lists, items, filters, lookup ->
        val searchResults = if (filters.isActive) {
            sortBacklogItemsByRecency(applyBacklogFilters(items, filters))
        } else {
            emptyList()
        }
        BacklogUiState(
            isLoading = false,
            lists = lists,
            itemCountByList = items.groupingBy { it.listId }.eachCount(),
            statistics = computeBacklogStatistics(items),
            filters = filters,
            searchResults = searchResults,
            listNamesById = lists.associate { it.id to it.name },
            availablePlatforms = lookup.platforms,
            availableGenres = lookup.genres,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BacklogUiState(),
    )

    fun onSearchQueryChange(query: String) {
        filters.update { it.copy(searchQuery = query) }
    }

    fun onFiltersChange(newFilters: BacklogFilters) {
        filters.value = newFilters
    }

    fun onClearFilters() {
        filters.update { BacklogFilters(searchQuery = it.searchQuery) }
    }

    fun onCreateList(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { backlogRepository.createList(name) }
    }

    fun onRenameList(listId: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { backlogRepository.renameList(listId, name) }
    }

    fun onDeleteList(listId: Long) {
        viewModelScope.launch { backlogRepository.deleteList(listId) }
    }

    fun onMoveListUp(listId: Long) = swapListPosition(listId, offset = -1)

    fun onMoveListDown(listId: Long) = swapListPosition(listId, offset = 1)

    private fun swapListPosition(listId: Long, offset: Int) {
        val orderedIds = uiState.value.lists.map { it.id }
        val index = orderedIds.indexOf(listId)
        val swapIndex = index + offset
        if (index < 0 || swapIndex < 0 || swapIndex >= orderedIds.size) return
        val reordered = orderedIds.toMutableList().apply {
            val tmp = this[index]
            this[index] = this[swapIndex]
            this[swapIndex] = tmp
        }
        viewModelScope.launch { backlogRepository.reorderLists(reordered) }
    }

    /** Exports every list and item, ignoring active filters — same "always the whole thing" rule as the library export. */
    fun exportBacklog(destination: Uri) {
        viewModelScope.launch {
            _importExportMessage.value = runCatching { backlogExporter.export(destination) }.fold(
                onSuccess = { appContext.getString(R.string.export_completed) },
                onFailure = { appContext.getString(R.string.export_failed, it.message.orEmpty()) },
            )
        }
    }

    /** Additive import (Fase 8): adds the file's lists/items alongside whatever is already here, never replaces anything. */
    fun importBacklog(source: Uri) {
        viewModelScope.launch {
            _importExportMessage.value = runCatching { backlogImporter.import(source) }.fold(
                onSuccess = { result -> appContext.getString(R.string.backlog_import_completed, result.listsImported, result.itemsImported) },
                onFailure = { appContext.getString(R.string.import_failed, it.message.orEmpty()) },
            )
        }
    }

    fun consumeImportExportMessage() {
        _importExportMessage.value = null
    }
}
