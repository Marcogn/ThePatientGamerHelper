package com.marcogn.gamereviewer.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.marcogn.gamereviewer.domain.repository.ReviewRepository
import com.marcogn.gamereviewer.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reviewRepository: ReviewRepository,
) : ViewModel() {

    private val reviewId: String = savedStateHandle.toRoute<Destination.Detail>().reviewId

    val uiState: StateFlow<DetailUiState> = reviewRepository.observeById(reviewId)
        .map { review -> DetailUiState(isLoading = false, review = review) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DetailUiState(),
        )

    fun deleteReview(onDeleted: () -> Unit) {
        viewModelScope.launch {
            reviewRepository.delete(reviewId)
            onDeleted()
        }
    }
}
