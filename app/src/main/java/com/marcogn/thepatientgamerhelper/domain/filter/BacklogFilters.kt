package com.marcogn.thepatientgamerhelper.domain.filter

import com.marcogn.thepatientgamerhelper.domain.model.BacklogItemStatus

/**
 * Combinable backlog filters, same ANDed-dimensions/ORed-values shape as [LibraryFilters].
 * An empty set/blank query means "no restriction on this dimension".
 */
data class BacklogFilters(
    val searchQuery: String = "",
    val listIds: Set<Long> = emptySet(),
    val statuses: Set<BacklogItemStatus> = emptySet(),
    val platforms: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
) {
    val isActive: Boolean
        get() = searchQuery.isNotBlank() ||
            listIds.isNotEmpty() ||
            statuses.isNotEmpty() ||
            platforms.isNotEmpty() ||
            genres.isNotEmpty()
}
