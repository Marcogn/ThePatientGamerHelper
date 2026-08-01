package com.marcogn.gamereviewer.domain.filter

import com.marcogn.gamereviewer.domain.model.Review
import java.time.LocalDate
import java.util.Locale

/**
 * Pure, Android-free filtering + sorting used to derive the library screen's visible list
 * from the full reactive set of reviews loaded from Room. Kept dependency-free on purpose
 * so it can be unit tested on the plain JVM.
 */
fun applyLibraryFilters(reviews: List<Review>, filters: LibraryFilters): List<Review> {
    val query = filters.searchQuery.trim().lowercase(Locale.ROOT)

    return reviews.filter { review ->
        matchesSearch(review, query) &&
            matchesNames(filters.platforms, review.platforms.map { it.name }) &&
            matchesNames(filters.genres, review.genres.map { it.name }) &&
            matchesNames(filters.tags, review.tags.map { it.name }) &&
            (filters.statuses.isEmpty() || review.status in filters.statuses) &&
            (filters.minRating == null || review.rating >= filters.minRating) &&
            (filters.maxRating == null || review.rating <= filters.maxRating) &&
            matchesDateRange(review.referenceDate, filters.dateFrom, filters.dateTo)
    }
}

fun sortReviews(reviews: List<Review>, sort: SortOption): List<Review> {
    val comparator = when (sort.field) {
        SortField.DATE -> compareBy<Review> { it.referenceDate }
        SortField.RATING -> compareBy { it.rating }
        SortField.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        SortField.HOURS_PLAYED -> compareBy { it.hoursPlayed ?: -1.0 }
    }
    val ordered = reviews.sortedWith(comparator)
    return if (sort.direction == SortDirection.DESC) ordered.asReversed() else ordered
}

/** Date used for date-range filtering and default sort: when the game ended, or started. */
private val Review.referenceDate: LocalDate
    get() = endDate ?: startDate

private fun matchesSearch(review: Review, query: String): Boolean {
    if (query.isEmpty()) return true
    val haystacks = buildList {
        add(review.title)
        add(review.reviewText)
        addAll(review.pros)
        addAll(review.cons)
        addAll(review.tags.map { it.name })
    }
    return haystacks.any { it.lowercase(Locale.ROOT).contains(query) }
}

private fun matchesNames(selected: Set<String>, values: List<String>): Boolean {
    if (selected.isEmpty()) return true
    val normalizedValues = values.map { it.lowercase(Locale.ROOT) }.toSet()
    return selected.any { it.lowercase(Locale.ROOT) in normalizedValues }
}

private fun matchesDateRange(date: LocalDate, from: LocalDate?, to: LocalDate?): Boolean {
    if (from != null && date.isBefore(from)) return false
    if (to != null && date.isAfter(to)) return false
    return true
}
