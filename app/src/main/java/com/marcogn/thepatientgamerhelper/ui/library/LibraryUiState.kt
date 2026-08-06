package com.marcogn.thepatientgamerhelper.ui.library

import com.marcogn.thepatientgamerhelper.domain.filter.LibraryFilters
import com.marcogn.thepatientgamerhelper.domain.filter.SortOption
import com.marcogn.thepatientgamerhelper.domain.model.Genre
import com.marcogn.thepatientgamerhelper.domain.model.Platform
import com.marcogn.thepatientgamerhelper.domain.model.Review
import com.marcogn.thepatientgamerhelper.domain.model.Tag
import com.marcogn.thepatientgamerhelper.domain.model.ViewMode

data class LibraryUiState(
    val isLoading: Boolean = true,
    val allReviewsEmpty: Boolean = false,
    val reviews: List<Review> = emptyList(),
    val filters: LibraryFilters = LibraryFilters(),
    val sort: SortOption = SortOption.DEFAULT,
    val availablePlatforms: List<Platform> = emptyList(),
    val availableGenres: List<Genre> = emptyList(),
    val availableTags: List<Tag> = emptyList(),
    val viewMode: ViewMode = ViewMode.LIST,
)
