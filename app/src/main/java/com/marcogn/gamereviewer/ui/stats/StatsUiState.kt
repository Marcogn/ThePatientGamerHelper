package com.marcogn.gamereviewer.ui.stats

import com.marcogn.gamereviewer.domain.model.LibraryStatistics

data class StatsUiState(
    val isLoading: Boolean = true,
    val statistics: LibraryStatistics = LibraryStatistics.EMPTY,
)
