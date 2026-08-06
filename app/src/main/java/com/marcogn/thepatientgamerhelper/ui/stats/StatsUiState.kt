package com.marcogn.thepatientgamerhelper.ui.stats

import com.marcogn.thepatientgamerhelper.domain.model.BacklogTimeEstimateStatistics
import com.marcogn.thepatientgamerhelper.domain.model.LibraryStatistics

data class StatsUiState(
    val isLoading: Boolean = true,
    val statistics: LibraryStatistics = LibraryStatistics.EMPTY,
    /** HowLongToBeat estimates across the backlog (Fase 8) — kept separate from [statistics], which is review-only. */
    val backlogTimeEstimate: BacklogTimeEstimateStatistics = BacklogTimeEstimateStatistics.EMPTY,
)
