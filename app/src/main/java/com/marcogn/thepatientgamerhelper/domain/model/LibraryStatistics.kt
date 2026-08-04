package com.marcogn.thepatientgamerhelper.domain.model

/** Aggregate metrics for the whole library (Fase 3), computed from every stored [Review]. */
data class LibraryStatistics(
    val totalReviews: Int,
    val averageRating: Double?,
    val totalHoursPlayed: Double,
    val platformDistribution: List<DistributionEntry>,
    val genreDistribution: List<DistributionEntry>,
    val statusBreakdown: List<StatusShare>,
) {
    companion object {
        val EMPTY = LibraryStatistics(
            totalReviews = 0,
            averageRating = null,
            totalHoursPlayed = 0.0,
            platformDistribution = emptyList(),
            genreDistribution = emptyList(),
            statusBreakdown = emptyList(),
        )
    }
}

/** One bar of a platform/genre distribution chart: how many reviews reference [label]. */
data class DistributionEntry(val label: String, val count: Int)

/** Share of the library in a given [status], as a count and a percentage of [LibraryStatistics.totalReviews]. */
data class StatusShare(val status: ReviewStatus, val count: Int, val percentage: Double)
