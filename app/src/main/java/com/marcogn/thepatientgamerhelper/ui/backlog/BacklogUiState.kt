package com.marcogn.thepatientgamerhelper.ui.backlog

import com.marcogn.thepatientgamerhelper.domain.filter.BacklogFilters
import com.marcogn.thepatientgamerhelper.domain.model.BacklogItem
import com.marcogn.thepatientgamerhelper.domain.model.BacklogList
import com.marcogn.thepatientgamerhelper.domain.model.BacklogStatistics
import com.marcogn.thepatientgamerhelper.domain.model.Genre
import com.marcogn.thepatientgamerhelper.domain.model.Platform

data class BacklogUiState(
    val isLoading: Boolean = true,
    val lists: List<BacklogList> = emptyList(),
    val itemCountByList: Map<Long, Int> = emptyMap(),
    val statistics: BacklogStatistics = BacklogStatistics.EMPTY,
    val filters: BacklogFilters = BacklogFilters(),
    val searchResults: List<BacklogItem> = emptyList(),
    val listNamesById: Map<Long, String> = emptyMap(),
    val availablePlatforms: List<Platform> = emptyList(),
    val availableGenres: List<Genre> = emptyList(),
)
