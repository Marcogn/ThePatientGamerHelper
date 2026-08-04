package com.marcogn.gamereviewer.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcogn.gamereviewer.domain.repository.ReviewRepository
import com.marcogn.gamereviewer.domain.stats.computeLibraryStatistics
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class StatsViewModel @Inject constructor(
    reviewRepository: ReviewRepository,
) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = reviewRepository.observeAll()
        .map { reviews -> StatsUiState(isLoading = false, statistics = computeLibraryStatistics(reviews)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState(),
        )
}
