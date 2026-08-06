package com.marcogn.thepatientgamerhelper.ui.backlog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.marcogn.thepatientgamerhelper.data.settings.ViewModePreferences
import com.marcogn.thepatientgamerhelper.domain.model.ViewMode
import com.marcogn.thepatientgamerhelper.domain.repository.BacklogRepository
import com.marcogn.thepatientgamerhelper.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BacklogListDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val backlogRepository: BacklogRepository,
    private val viewModePreferences: ViewModePreferences,
) : ViewModel() {

    private val listId: Long = savedStateHandle.toRoute<Destination.BacklogListDetail>().listId
    private val viewMode = MutableStateFlow(viewModePreferences.backlogViewMode)

    val uiState: StateFlow<BacklogListDetailUiState> = combine(
        backlogRepository.observeLists().map { lists -> lists.firstOrNull { it.id == listId } },
        backlogRepository.observeItemsForList(listId),
        viewMode,
    ) { list, items, viewMode ->
        BacklogListDetailUiState(isLoading = false, list = list, items = items, viewMode = viewMode)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BacklogListDetailUiState(),
    )

    fun onReorder(orderedItemIds: List<String>) {
        viewModelScope.launch { backlogRepository.reorderItems(listId, orderedItemIds) }
    }

    fun onDeleteItem(itemId: String) {
        viewModelScope.launch { backlogRepository.deleteItem(itemId) }
    }

    fun onViewModeChange(mode: ViewMode) {
        viewMode.value = mode
        viewModePreferences.backlogViewMode = mode
    }
}
