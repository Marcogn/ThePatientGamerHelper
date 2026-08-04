package com.marcogn.thepatientgamerhelper.ui.stats

import com.marcogn.thepatientgamerhelper.domain.model.LibraryStatistics

data class StatsUiState(
    val isLoading: Boolean = true,
    val statistics: LibraryStatistics = LibraryStatistics.EMPTY,
)
