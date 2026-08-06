package com.marcogn.thepatientgamerhelper.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcogn.thepatientgamerhelper.domain.repository.BacklogRepository
import com.marcogn.thepatientgamerhelper.domain.repository.ReviewRepository
import com.marcogn.thepatientgamerhelper.domain.stats.computeBacklogTimeEstimateStatistics
import com.marcogn.thepatientgamerhelper.domain.stats.computeLibraryStatistics
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class StatsViewModel @Inject constructor(
    reviewRepository: ReviewRepository,
    backlogRepository: BacklogRepository,
) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = combine(
        reviewRepository.observeAll(),
        backlogRepository.observeAllItems(),
    ) { reviews, backlogItems ->
        StatsUiState(
            isLoading = false,
            statistics = computeLibraryStatistics(reviews),
            backlogTimeEstimate = computeBacklogTimeEstimateStatistics(backlogItems),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState(),
    )
}
