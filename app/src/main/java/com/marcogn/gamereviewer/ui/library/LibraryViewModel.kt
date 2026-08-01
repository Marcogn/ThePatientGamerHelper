package com.marcogn.gamereviewer.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcogn.gamereviewer.domain.filter.LibraryFilters
import com.marcogn.gamereviewer.domain.filter.SortOption
import com.marcogn.gamereviewer.domain.filter.applyLibraryFilters
import com.marcogn.gamereviewer.domain.filter.sortReviews
import com.marcogn.gamereviewer.domain.model.Genre
import com.marcogn.gamereviewer.domain.model.Platform
import com.marcogn.gamereviewer.domain.model.Tag
import com.marcogn.gamereviewer.domain.repository.LookupRepository
import com.marcogn.gamereviewer.domain.repository.ReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class LookupOptions(
    val platforms: List<Platform>,
    val genres: List<Genre>,
    val tags: List<Tag>,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val reviewRepository: ReviewRepository,
    lookupRepository: LookupRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(LibraryFilters())
    private val sort = MutableStateFlow(SortOption.DEFAULT)

    private val lookupOptions = combine(
        lookupRepository.observePlatforms(),
        lookupRepository.observeGenres(),
        lookupRepository.observeTags(),
    ) { platforms, genres, tags -> LookupOptions(platforms, genres, tags) }

    val uiState: StateFlow<LibraryUiState> = combine(
        reviewRepository.observeAll(),
        filters,
        sort,
        lookupOptions,
    ) { reviews, filters, sort, lookup ->
        val filtered = applyLibraryFilters(reviews, filters)
        val sorted = sortReviews(filtered, sort)
        LibraryUiState(
            isLoading = false,
            allReviewsEmpty = reviews.isEmpty(),
            reviews = sorted,
            filters = filters,
            sort = sort,
            availablePlatforms = lookup.platforms,
            availableGenres = lookup.genres,
            availableTags = lookup.tags,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(),
    )

    fun onSearchQueryChange(query: String) {
        filters.update { it.copy(searchQuery = query) }
    }

    fun onFiltersChange(newFilters: LibraryFilters) {
        filters.value = newFilters
    }

    fun onSortChange(newSort: SortOption) {
        sort.value = newSort
    }

    fun onClearFilters() {
        filters.update { LibraryFilters(searchQuery = it.searchQuery) }
    }

    fun onDeleteReview(id: String) {
        viewModelScope.launch { reviewRepository.delete(id) }
    }
}
