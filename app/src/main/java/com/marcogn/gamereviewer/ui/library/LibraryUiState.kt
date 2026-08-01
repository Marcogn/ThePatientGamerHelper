package com.marcogn.gamereviewer.ui.library

import com.marcogn.gamereviewer.domain.filter.LibraryFilters
import com.marcogn.gamereviewer.domain.filter.SortOption
import com.marcogn.gamereviewer.domain.model.Genre
import com.marcogn.gamereviewer.domain.model.Platform
import com.marcogn.gamereviewer.domain.model.Review
import com.marcogn.gamereviewer.domain.model.Tag

data class LibraryUiState(
    val isLoading: Boolean = true,
    val allReviewsEmpty: Boolean = false,
    val reviews: List<Review> = emptyList(),
    val filters: LibraryFilters = LibraryFilters(),
    val sort: SortOption = SortOption.DEFAULT,
    val availablePlatforms: List<Platform> = emptyList(),
    val availableGenres: List<Genre> = emptyList(),
    val availableTags: List<Tag> = emptyList(),
)
