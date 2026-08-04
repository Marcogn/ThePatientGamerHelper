package com.marcogn.gamereviewer.domain.filter

import com.marcogn.gamereviewer.domain.model.BacklogItem
import java.util.Locale

/**
 * Pure, Android-free filtering used to derive the cross-list backlog search results from the
 * full reactive set of items loaded from Room — mirrors `LibraryFiltering`.
 */
fun applyBacklogFilters(items: List<BacklogItem>, filters: BacklogFilters): List<BacklogItem> {
    val query = filters.searchQuery.trim().lowercase(Locale.ROOT)

    return items.filter { item ->
        matchesSearch(item, query) &&
            (filters.listIds.isEmpty() || item.listId in filters.listIds) &&
            (filters.statuses.isEmpty() || item.status in filters.statuses) &&
            matchesNames(filters.platforms, item.platforms.map { it.name }) &&
            matchesNames(filters.genres, item.genres.map { it.name })
    }
}

/** Cross-list search results have no manual order to preserve, unlike a single list's [BacklogItem.position]. */
fun sortBacklogItemsByRecency(items: List<BacklogItem>): List<BacklogItem> =
    items.sortedByDescending { it.addedAt }

private fun matchesSearch(item: BacklogItem, query: String): Boolean {
    if (query.isEmpty()) return true
    val haystacks = buildList {
        add(item.title)
        addAll(item.tags.map { it.name })
    }
    return haystacks.any { it.lowercase(Locale.ROOT).contains(query) }
}

private fun matchesNames(selected: Set<String>, values: List<String>): Boolean {
    if (selected.isEmpty()) return true
    val normalizedValues = values.map { it.lowercase(Locale.ROOT) }.toSet()
    return selected.any { it.lowercase(Locale.ROOT) in normalizedValues }
}
