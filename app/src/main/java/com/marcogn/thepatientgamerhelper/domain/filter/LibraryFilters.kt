package com.marcogn.thepatientgamerhelper.domain.filter

import com.marcogn.thepatientgamerhelper.domain.model.ReviewStatus
import java.time.LocalDate

enum class SortField { DATE, RATING, TITLE, HOURS_PLAYED }

enum class SortDirection { ASC, DESC }

data class SortOption(val field: SortField, val direction: SortDirection) {
    companion object {
        val DEFAULT = SortOption(SortField.DATE, SortDirection.DESC)
    }
}

/**
 * Combinable library filters. An empty set/null field means "no restriction on this
 * dimension" — all filters are ANDed together, values within [platforms]/[genres]/[tags]
 * are ORed (a review matches if it has at least one of the selected values).
 */
data class LibraryFilters(
    val searchQuery: String = "",
    val platforms: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val statuses: Set<ReviewStatus> = emptySet(),
    val minRating: Double? = null,
    val maxRating: Double? = null,
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
) {
    val isActive: Boolean
        get() = searchQuery.isNotBlank() ||
            platforms.isNotEmpty() ||
            genres.isNotEmpty() ||
            tags.isNotEmpty() ||
            statuses.isNotEmpty() ||
            minRating != null ||
            maxRating != null ||
            dateFrom != null ||
            dateTo != null
}
