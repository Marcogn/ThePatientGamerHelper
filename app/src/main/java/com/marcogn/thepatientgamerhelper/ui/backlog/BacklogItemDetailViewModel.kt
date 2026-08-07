package com.marcogn.thepatientgamerhelper.ui.backlog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.marcogn.thepatientgamerhelper.domain.model.BacklogItemStatus
import com.marcogn.thepatientgamerhelper.domain.repository.BacklogRepository
import com.marcogn.thepatientgamerhelper.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class BacklogItemDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val backlogRepository: BacklogRepository,
) : ViewModel() {

    val itemId: String = savedStateHandle.toRoute<Destination.BacklogItemDetail>().itemId

    private val newCommentText = MutableStateFlow("")

    val uiState: StateFlow<BacklogItemDetailUiState> = combine(
        backlogRepository.observeItem(itemId),
        backlogRepository.observeLists(),
        newCommentText,
    ) { item, lists, comment ->
        BacklogItemDetailUiState(isLoading = false, item = item, lists = lists, newCommentText = comment)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BacklogItemDetailUiState(),
    )

    /** One-shot "vuoi scrivere una recensione?" trigger, consumed by the screen after showing it. */
    private val _showReviewPrompt = MutableStateFlow(false)
    val showReviewPrompt: StateFlow<Boolean> = _showReviewPrompt.asStateFlow()

    /**
     * Commits a status change (and, only for ABBANDONATO, its note) picked in the UI's pending
     * selector — status edits are staged locally in the screen and only reach here on an explicit
     * "Salva", so the "vuoi scrivere una recensione?" prompt fires once, right when the user
     * confirms COMPLETATO, not on every chip tap while still deciding.
     */
    fun onSaveStatus(status: BacklogItemStatus, abandonNote: String?) {
        viewModelScope.launch {
            val current = backlogRepository.observeItem(itemId).first() ?: return@launch
            val statusChanged = current.status != status
            backlogRepository.updateStatus(itemId, status, abandonNote)
            if (statusChanged && status == BacklogItemStatus.COMPLETATO && current.reviewId == null) {
                _showReviewPrompt.value = true
            }
        }
    }

    fun onCommentTextChange(text: String) {
        newCommentText.update { text }
    }

    fun onAddComment() {
        val text = newCommentText.value
        if (text.isBlank()) return
        viewModelScope.launch {
            backlogRepository.addComment(itemId, text)
            newCommentText.value = ""
        }
    }

    fun onMoveToList(targetListId: Long) {
        viewModelScope.launch { backlogRepository.moveItem(itemId, targetListId) }
    }

    fun onDelete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            backlogRepository.deleteItem(itemId)
            onDeleted()
        }
    }

    fun onReviewPromptConsumed() {
        _showReviewPrompt.value = false
    }
}
