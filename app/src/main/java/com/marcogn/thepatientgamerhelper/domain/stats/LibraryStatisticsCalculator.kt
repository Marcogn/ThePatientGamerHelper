package com.marcogn.thepatientgamerhelper.domain.stats

import com.marcogn.thepatientgamerhelper.domain.model.DistributionEntry
import com.marcogn.thepatientgamerhelper.domain.model.LibraryStatistics
import com.marcogn.thepatientgamerhelper.domain.model.Review
import com.marcogn.thepatientgamerhelper.domain.model.ReviewStatus
import com.marcogn.thepatientgamerhelper.domain.model.StatusShare
import java.util.Locale

/**
 * Pure, Android-free aggregation used to derive the stats screen from the full reactive set of
 * reviews loaded from Room. Kept dependency-free on purpose so it can be unit tested on the
 * plain JVM, mirroring `domain/filter`.
 */
fun computeLibraryStatistics(reviews: List<Review>): LibraryStatistics {
    if (reviews.isEmpty()) return LibraryStatistics.EMPTY

    val totalReviews = reviews.size
    val averageRating = reviews.sumOf { it.rating } / totalReviews
    val totalHoursPlayed = reviews.sumOf { it.hoursPlayed ?: 0.0 }

    val platformDistribution = distributionOf(reviews.flatMap { review -> review.platforms.map { it.name } })
    val genreDistribution = distributionOf(reviews.flatMap { review -> review.genres.map { it.name } })

    val statusBreakdown = ReviewStatus.entries.map { status ->
        val count = reviews.count { it.status == status }
        StatusShare(status = status, count = count, percentage = count.toDouble() / totalReviews * 100.0)
    }

    return LibraryStatistics(
        totalReviews = totalReviews,
        averageRating = averageRating,
        totalHoursPlayed = totalHoursPlayed,
        platformDistribution = platformDistribution,
        genreDistribution = genreDistribution,
        statusBreakdown = statusBreakdown,
    )
}

/**
 * Counts occurrences per label (a review with multiple platforms/genres counts once per value),
 * sorted by count descending. No percentage here: with many-to-many fields, shares would not sum
 * to 100% and would misread as if they did — see docs/implementation-decisions.md.
 */
private fun distributionOf(values: List<String>): List<DistributionEntry> =
    values.groupingBy { it }.eachCount()
        .map { (label, count) -> DistributionEntry(label, count) }
        .sortedWith(compareByDescending<DistributionEntry> { it.count }.thenBy { it.label.lowercase(Locale.ROOT) })
